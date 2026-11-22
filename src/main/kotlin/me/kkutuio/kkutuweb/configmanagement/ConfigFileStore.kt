package me.kkutuio.kkutuweb.configmanagement

import me.kkutuio.kkutuweb.servermanagement.GameServerManagementService
import me.kkutuio.kkutuweb.servermanagement.RemoteCommandRunner
import me.kkutuio.kkutuweb.setting.GameServerManagementSetting
import org.springframework.boot.ApplicationArguments
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

/** `tracked` 는 게임 서버 파일이 저장소에서 추적 중인가(웹 파일은 항상 false). 알 수 없으면 true 로 취급해 수정을 막는다. */
data class StoredConfig(val text: String, val sha256: String, val sizeBytes: Int, val tracked: Boolean)

/** 읽은 뒤 파일이 바뀌었다(다른 사람이 먼저 저장했거나 서버에서 직접 고쳤다). */
class ConfigConflictException(message: String) : RuntimeException(message)

/** 저장소에 접근하지 못했거나 파일이 다룰 수 없는 상태다. */
class ConfigStoreException(message: String) : RuntimeException(message)

interface ConfigStore {
    /** 파일이 없으면 null. */
    fun read(file: String): StoredConfig?

    /** `expectedSha256` 가 현재 파일과 같을 때만 백업을 남기고 교체한다. 백업 위치 설명을 돌려준다. */
    fun write(file: String, text: String, expectedSha256: String): String
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private const val KEEP_BACKUPS = 20

/** 웹 서버가 시작할 때 읽는 설정 폴더(SETTING_DIR)의 파일. */
@Component
class WebConfigStore(private val applicationArguments: ApplicationArguments) : ConfigStore {
    private val lock = Any()
    private val directory: Path by lazy {
        val dir = applicationArguments.getOptionValues("SETTING_DIR")?.firstOrNull()
            ?: throw ConfigStoreException("SETTING_DIR 값이 없어 웹 설정 파일을 찾을 수 없습니다.")
        Paths.get(dir).toAbsolutePath().normalize()
    }

    private fun resolve(file: String): Path {
        val path = directory.resolve(file).normalize()
        if (path.parent != directory) throw ConfigStoreException("허용되지 않는 파일 이름입니다.")
        return path
    }

    override fun read(file: String): StoredConfig? {
        val path = resolve(file)
        if (!Files.isRegularFile(path)) return null
        val bytes = Files.readAllBytes(path)
        if (bytes.size > ConfigCatalog.MAX_CONFIG_BYTES) throw ConfigStoreException("파일이 너무 커서 이 화면에서 다룰 수 없습니다.")
        return StoredConfig(String(bytes, StandardCharsets.UTF_8), sha256Hex(bytes), bytes.size, tracked = false)
    }

    override fun write(file: String, text: String, expectedSha256: String): String = synchronized(lock) {
        val path = resolve(file)
        if (!Files.isRegularFile(path)) throw ConfigStoreException("파일이 없습니다.")
        if (sha256Hex(Files.readAllBytes(path)) != expectedSha256) throw ConfigConflictException("파일이 바뀌었습니다.")
        val backups = directory.resolve(".config-backups").also(Files::createDirectories)
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        val backup = backups.resolve("$file.$stamp-${UUID.randomUUID().toString().take(4)}")
        Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES)
        val temp = directory.resolve("$file.tmp-${UUID.randomUUID()}")
        try {
            Files.write(temp, text.toByteArray(StandardCharsets.UTF_8))
            runCatching { Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(path)) }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
        Files.list(backups).use { stream ->
            stream.filter { it.fileName.toString().startsWith("$file.") }.sorted(Comparator.reverseOrder()).skip(KEEP_BACKUPS.toLong())
                .forEach { runCatching { Files.deleteIfExists(it) } }
        }
        ".config-backups/${backup.fileName}"
    }
}

/**
 * 게임 서버의 `<workingDirectory>/server/config/` 파일. 서버 관리 화면과 같은 SSH 접속 정보(management)를 쓴다.
 * 쓰기는 원격에서 "읽은 뒤 바뀌지 않았는지 해시로 확인 → 백업 → 임시 파일에 쓰고 검증 → 교체" 를 한 번에 한다.
 */
@Component
class GameConfigStore(private val runner: RemoteCommandRunner) {
    fun forServer(management: GameServerManagementSetting): ConfigStore = object : ConfigStore {
        override fun read(file: String) = readRemote(management, file)
        override fun write(file: String, text: String, expectedSha256: String) = writeRemote(management, file, text, expectedSha256)
    }

    private fun quote(value: String) = GameServerManagementService.shellQuote(value)

    private fun ssh(management: GameServerManagementSetting, script: String): List<String> {
        val command = mutableListOf(
            "ssh", "-n", "-T", "-o", "BatchMode=yes", "-o", "PreferredAuthentications=publickey",
            "-o", "ConnectTimeout=10", "-o", "ConnectionAttempts=1"
        )
        management.sshPort?.let { command += listOf("-p", it.toString()) }
        management.sshKeyPath?.let { command += listOf("-o", "IdentitiesOnly=yes", "-i", it) }
        command += listOf(management.sshTarget, "sh", "-lc", quote(script))
        return command
    }

    private fun sanitize(output: String) = output.lineSequence().take(6).joinToString(" / ").take(400).ifBlank { "출력 없음" }

    private fun readRemote(management: GameServerManagementSetting, file: String): StoredConfig? {
        val path = "server/config/$file"
        val script = """
            set -eu
            cd -- ${quote(management.workingDirectory)}
            f=${quote(path)}
            if [ ! -f "${'$'}f" ]; then echo __MISSING__; exit 0; fi
            if git rev-parse --is-inside-work-tree >/dev/null 2>&1 && ! git ls-files --error-unmatch -- "${'$'}f" >/dev/null 2>&1; then echo "__STATE__ UNTRACKED"; else echo "__STATE__ TRACKED"; fi
            size=${'$'}(wc -c < "${'$'}f" | tr -d ' ')
            echo "__SIZE__ ${'$'}size"
            if [ "${'$'}size" -gt ${ConfigCatalog.MAX_CONFIG_BYTES} ]; then echo __TOOBIG__; exit 0; fi
            sha=${'$'}( (sha256sum "${'$'}f" 2>/dev/null || shasum -a 256 "${'$'}f") | cut -d' ' -f1)
            echo "__SHA__ ${'$'}sha"
            echo __BEGIN__
            cat -- "${'$'}f"
        """.trimIndent()
        val result = runner.run(ssh(management, script), 25)
        if (result.timedOut) throw ConfigStoreException("SSH 명령이 제한 시간 안에 끝나지 않았습니다.")
        if (result.exitCode != 0) throw ConfigStoreException("SSH 명령이 실패했습니다 (종료 코드 ${result.exitCode}): ${sanitize(result.output)}")
        val output = result.output
        if (output.lineSequence().firstOrNull()?.trim() == "__MISSING__") return null
        if (output.contains("__TOOBIG__")) throw ConfigStoreException("파일이 너무 커서 이 화면에서 다룰 수 없습니다.")
        val begin = output.indexOf("__BEGIN__")
        if (begin < 0) throw ConfigStoreException("원격 응답을 해석하지 못했습니다: ${sanitize(output)}")
        val header = output.substring(0, begin).lines()
        val sha = header.firstOrNull { it.startsWith("__SHA__ ") }?.removePrefix("__SHA__ ")?.trim()
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: throw ConfigStoreException("원격 파일의 해시를 확인하지 못했습니다.")
        val tracked = header.firstOrNull { it.startsWith("__STATE__ ") }?.removePrefix("__STATE__ ")?.trim() != "UNTRACKED"
        val size = header.firstOrNull { it.startsWith("__SIZE__ ") }?.removePrefix("__SIZE__ ")?.trim()?.toIntOrNull() ?: 0
        val text = output.substring(begin + "__BEGIN__".length).removePrefix("\n")
        return StoredConfig(text, sha, size, tracked)
    }

    private fun writeRemote(management: GameServerManagementSetting, file: String, text: String, expectedSha256: String): String {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val newSha = sha256Hex(bytes)
        val path = "server/config/$file"
        val script = """
            set -eu
            cd -- ${quote(management.workingDirectory)}
            f=${quote(path)}
            [ -f "${'$'}f" ] || { echo MISSING >&2; exit 22; }
            if git rev-parse --is-inside-work-tree >/dev/null 2>&1 && ! git ls-files --error-unmatch -- "${'$'}f" >/dev/null 2>&1; then :; else echo TRACKED >&2; exit 20; fi
            hash_of() { (sha256sum "${'$'}1" 2>/dev/null || shasum -a 256 "${'$'}1") | cut -d' ' -f1; }
            [ "${'$'}(hash_of "${'$'}f")" = ${quote(expectedSha256)} ] || { echo CHANGED >&2; exit 21; }
            bdir="${'$'}HOME/.kkutu-config-backups/${'$'}(pwd | tr '/' '_')"
            mkdir -p "${'$'}bdir"
            backup="${'$'}bdir/${quote(file)}.${'$'}(date +%Y%m%d-%H%M%S)"
            cp -p -- "${'$'}f" "${'$'}backup"
            tmp="${'$'}f.tmp.${'$'}${'$'}"
            cp -p -- "${'$'}f" "${'$'}tmp"
            printf '%s' ${quote(Base64.getEncoder().encodeToString(bytes))} | base64 -d > "${'$'}tmp"
            [ "${'$'}(hash_of "${'$'}tmp")" = ${quote(newSha)} ] || { rm -f -- "${'$'}tmp"; echo VERIFY >&2; exit 23; }
            mv -f -- "${'$'}tmp" "${'$'}f"
            ls -1t "${'$'}bdir" | grep -F -- ${quote("$file.")} | tail -n +${KEEP_BACKUPS + 1} | while read -r old; do rm -f -- "${'$'}bdir/${'$'}old"; done
            echo "__OK__ ${'$'}backup"
        """.trimIndent()
        val result = runner.run(ssh(management, script), 30)
        if (result.timedOut) throw ConfigStoreException("SSH 명령이 제한 시간 안에 끝나지 않았습니다. 저장 여부를 서버에서 확인해 주세요.")
        when (result.exitCode) {
            0 -> Unit
            20 -> throw ConfigStoreException("저장소가 추적하는 파일은 수정할 수 없습니다.")
            21 -> throw ConfigConflictException("파일이 바뀌었습니다.")
            22 -> throw ConfigStoreException("서버에 파일이 없습니다.")
            23 -> throw ConfigStoreException("쓴 파일의 해시가 맞지 않아 교체하지 않았습니다. 원본은 그대로입니다.")
            else -> throw ConfigStoreException("SSH 명령이 실패했습니다 (종료 코드 ${result.exitCode}): ${sanitize(result.output)}")
        }
        return result.output.lineSequence().firstOrNull { it.startsWith("__OK__ ") }?.removePrefix("__OK__ ")?.trim()
            ?: throw ConfigStoreException("저장 결과를 확인하지 못했습니다. 서버에서 파일을 확인해 주세요.")
    }
}
