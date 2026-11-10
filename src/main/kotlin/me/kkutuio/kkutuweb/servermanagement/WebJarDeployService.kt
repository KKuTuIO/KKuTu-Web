package me.kkutuio.kkutuweb.servermanagement

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.JarFile

enum class WebDeployStatus { RUNNING, SUCCEEDED, FAILED }

data class WebRuntimeInfo(
    val jarPath: String?,
    val replaceable: Boolean,
    val container: Boolean,
    val currentSha256: String?,
    val reason: String?
)

data class WebReleaseView(
    val tag: String,
    val name: String?,
    val publishedAt: String?,
    val notes: String?,
    val size: Long,
    val sha256: String?,
    val current: Boolean
)

data class WebReleaseList(val releases: List<WebReleaseView>, val runtime: WebRuntimeInfo)

data class WebDeploySnapshot(
    val id: UUID,
    val tag: String,
    val status: WebDeployStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val output: String
)

data class WebDeployRequest(val tag: String)

/** 현재 실행 중인 jar 파일의 위치를 찾는다. jar로 실행 중이 아니면 null. */
fun interface RunningJarLocator {
    fun locate(): Path?
}

@Component
class ClassPathRunningJarLocator : RunningJarLocator {
    override fun locate(): Path? {
        val classPath = System.getProperty("java.class.path").orEmpty()
        if (classPath.isBlank() || classPath.contains(java.io.File.pathSeparator)) return null
        val path = Path.of(classPath)
        val absolute = if (path.isAbsolute) path else Path.of(System.getProperty("user.dir")).resolve(path)
        if (!absolute.fileName.toString().endsWith(".jar") || !Files.isRegularFile(absolute)) return null
        return absolute.toRealPath()
    }
}

/** 새 jar가 기동에 실패할 때 이전 jar로 되돌리는 보조 프로세스를 띄운다. 최선 노력(best-effort)이다. */
fun interface RollbackWatchdog {
    fun launch(jar: Path, previous: Path)
}

@Component
class ShellRollbackWatchdog(
    @Value("\${server.port:8080}") private val port: Int,
    @Value("\${server-management.web-deploy.rollback-timeout-seconds:180}") timeoutSeconds: Long
) : RollbackWatchdog {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val timeout = timeoutSeconds.coerceIn(30, 900)

    override fun launch(jar: Path, previous: Path) {
        try {
            val builder = ProcessBuilder(
                "sh", "-c",
                "if command -v setsid >/dev/null 2>&1; then exec setsid nohup sh -c \"\$WD\"; else exec nohup sh -c \"\$WD\"; fi"
            )
            builder.environment().apply {
                put("WD", WATCHDOG_SCRIPT)
                put("JAR", jar.toString())
                put("PREV", previous.toString())
                put("OLD_PID", ProcessHandle.current().pid().toString())
                put("PORT", port.toString())
                put("TIMEOUT", timeout.toString())
            }
            builder.redirectErrorStream(true)
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(jar.resolveSibling(jar.fileName.toString() + ".rollback.log").toFile()))
            builder.start()
        } catch (error: Exception) {
            logger.warn("web-deploy rollback watchdog could not start: {}", error.message)
        }
    }

    private companion object {
        // 구 프로세스가 종료될 때까지 기다린 뒤 새 프로세스의 헬스체크를 본다. 실패하면 jar를 복원하고 새 java 프로세스만 종료해
        // 외부 실행기(web.sh 등)가 이전 jar로 다시 띄우게 한다. jar 경로는 argv가 아닌 환경변수로 받아 자기 자신을 지우지 않는다.
        val WATCHDOG_SCRIPT = """
            echo "${'$'}(date) watchdog start jar=${'$'}JAR"
            if ! command -v curl >/dev/null 2>&1 && ! command -v wget >/dev/null 2>&1; then
              echo "no curl/wget; skipping health verification"; exit 0
            fi
            i=0
            while kill -0 "${'$'}OLD_PID" 2>/dev/null && [ ${'$'}i -lt 60 ]; do sleep 1; i=${'$'}((i+1)); done
            t=0
            while [ ${'$'}t -lt "${'$'}TIMEOUT" ]; do
              if command -v curl >/dev/null 2>&1; then
                curl -fsS -o /dev/null --max-time 3 "http://127.0.0.1:${'$'}PORT/" 2>/dev/null && { echo "${'$'}(date) healthy"; exit 0; }
              else
                wget -q -O /dev/null -T 3 "http://127.0.0.1:${'$'}PORT/" 2>/dev/null && { echo "${'$'}(date) healthy"; exit 0; }
              fi
              sleep 3; t=${'$'}((t+3))
            done
            echo "${'$'}(date) unhealthy after ${'$'}TIMEOUT s; rolling back"
            cp "${'$'}PREV" "${'$'}JAR.rollback" && mv -f "${'$'}JAR.rollback" "${'$'}JAR"
            for p in ${'$'}(pgrep -f "${'$'}JAR" 2>/dev/null); do
              [ "${'$'}(ps -o comm= -p "${'$'}p" 2>/dev/null | tr -d ' ')" = "java" ] && kill "${'$'}p"
            done
            echo "${'$'}(date) rollback done"
        """.trimIndent()
    }
}

@Service
class WebJarDeployService(
    private val source: WebReleaseSource,
    private val locator: RunningJarLocator,
    private val restartService: WebServerRestartService,
    private val watchdog: RollbackWatchdog
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val operations = ConcurrentHashMap<UUID, MutableDeploy>()
    private val busy = AtomicBoolean(false)
    private var shaCache: Triple<Path, Pair<Long, Long>, String>? = null
    private val startClass: String? by lazy { locator.locate()?.let { readStartClass(it) } }

    fun listReleases(): WebReleaseList {
        val runtime = inspectRuntime()
        val releases = source.listReleases().map {
            WebReleaseView(
                it.tag, it.name, it.publishedAt, it.notes, it.size, it.sha256,
                current = it.sha256 != null && it.sha256 == runtime.currentSha256
            )
        }
        return WebReleaseList(releases, runtime)
    }

    fun inspectRuntime(): WebRuntimeInfo {
        val container = Files.exists(Path.of("/.dockerenv"))
        val jar = try {
            locator.locate()
        } catch (error: IOException) {
            null
        } ?: return WebRuntimeInfo(null, false, container, null, "jar 파일로 실행 중이 아니어서 교체할 수 없습니다.")
        val directory = jar.parent
        val reason = when {
            !Files.isWritable(jar) -> "실행 중인 jar에 쓰기 권한이 없습니다."
            directory == null || !Files.isWritable(directory) -> "jar가 있는 디렉터리에 쓰기 권한이 없습니다."
            else -> null
        }
        return WebRuntimeInfo(jar.toString(), reason == null, container, currentSha(jar), reason)
    }

    fun deploy(tag: String, actorId: String): WebDeploySnapshot {
        if (!TAG_PATTERN.matches(tag)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "올바르지 않은 릴리스 태그입니다.")
        }
        val release = source.listReleases().firstOrNull { it.tag == tag }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "목록에 없는 릴리스입니다.")
        val expected = release.sha256
            ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "릴리스에 SHA-256 정보가 없어 검증할 수 없습니다.")
        val runtime = inspectRuntime()
        if (!runtime.replaceable) {
            throw ResponseStatusException(HttpStatus.CONFLICT, runtime.reason ?: "jar를 교체할 수 없습니다.")
        }
        if (runtime.currentSha256 == expected) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 실행 중인 버전입니다.")
        }
        if (!busy.compareAndSet(false, true)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "다른 웹 서버 배포가 진행 중입니다.")
        }
        val jar = Path.of(runtime.jarPath!!)
        val operation = MutableDeploy(UUID.randomUUID(), tag)
        operations[operation.id] = operation
        trimOperations()
        logger.warn("web-deploy started actor={} tag={} sha256={}", actorId, tag, expected)
        executor.execute {
            try {
                execute(operation, release, expected, jar, actorId)
            } catch (error: ResponseStatusException) {
                operation.complete(WebDeployStatus.FAILED, error.reason ?: "배포에 실패했습니다.")
                logger.error("web-deploy failed actor={} tag={} reason={}", actorId, tag, error.reason)
            } catch (error: Exception) {
                operation.complete(WebDeployStatus.FAILED, "배포 중 오류가 발생했습니다: ${error.message?.take(200)}")
                logger.error("web-deploy failed actor={} tag={}", actorId, tag, error)
            } finally {
                busy.set(false)
            }
        }
        return operation.snapshot()
    }

    fun operation(id: UUID): WebDeploySnapshot =
        operations[id]?.snapshot() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "배포 작업을 찾을 수 없습니다.")

    private fun execute(operation: MutableDeploy, release: WebRelease, expectedSha256: String, jar: Path, actorId: String) {
        val directory = jar.parent
        val download = directory.resolve(jar.fileName.toString() + ".download")
        val previous = directory.resolve(jar.fileName.toString() + ".prev")
        try {
            val required = release.size + FREE_SPACE_MARGIN
            if (Files.getFileStore(directory).usableSpace < required) {
                throw ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "디스크 여유 공간이 부족합니다.")
            }
            operation.log("${release.tag} 다운로드 중 (${release.size / 1024 / 1024}MB)")
            Files.deleteIfExists(download)
            source.download(release, download, MAX_JAR_BYTES)

            operation.log("SHA-256 검증 중")
            val actual = sha256(download)
            if (actual != expectedSha256) {
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "SHA-256이 일치하지 않아 중단합니다.")
            }
            operation.log("jar 구조 검증 중")
            validateJar(download)

            operation.log("이전 jar 백업 및 교체")
            Files.copy(jar, previous, StandardCopyOption.REPLACE_EXISTING)
            copyPermissions(jar, download)
            Files.move(download, jar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

            operation.log("롤백 감시 시작 후 재시작 예약")
            watchdog.launch(jar, previous)
            val restart = restartService.schedule(actorId)
            operation.complete(
                WebDeployStatus.SUCCEEDED,
                "교체 완료. ${restart.shutdownDelayMillis}ms 후 종료되며 외부 실행기가 새 버전으로 다시 띄웁니다."
            )
            logger.warn("web-deploy replaced jar actor={} tag={} sha256={}", actorId, release.tag, actual)
        } finally {
            try {
                Files.deleteIfExists(download)
            } catch (ignored: IOException) {
            }
        }
    }

    internal fun validateJar(file: Path) {
        val attributes = try {
            JarFile(file.toFile()).use { it.manifest?.mainAttributes }
        } catch (error: IOException) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "올바른 jar 파일이 아닙니다.")
        } ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "jar 매니페스트가 없습니다.")
        val newStartClass = attributes.getValue("Start-Class")
        if (attributes.getValue("Main-Class").isNullOrBlank() || newStartClass.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Spring Boot 실행 jar가 아닙니다.")
        }
        val expectedStartClass = startClass
        if (expectedStartClass != null && expectedStartClass != newStartClass) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "현재 애플리케이션과 다른 jar입니다.")
        }
    }

    @Synchronized
    private fun currentSha(jar: Path): String? = try {
        val stamp = Files.size(jar) to Files.getLastModifiedTime(jar).toMillis()
        shaCache?.takeIf { it.first == jar && it.second == stamp }?.third
            ?: sha256(jar).also { shaCache = Triple(jar, stamp, it) }
    } catch (error: IOException) {
        null
    }

    private fun readStartClass(jar: Path): String? = try {
        JarFile(jar.toFile()).use { it.manifest?.mainAttributes?.getValue("Start-Class") }
    } catch (error: IOException) {
        null
    }

    private fun copyPermissions(from: Path, to: Path) {
        try {
            Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from))
        } catch (ignored: Exception) {
        }
    }

    private fun trimOperations() {
        if (operations.size <= MAX_OPERATIONS) return
        operations.values.sortedBy { it.startedAt }.take(operations.size - MAX_OPERATIONS)
            .filter { it.status != WebDeployStatus.RUNNING }.forEach { operations.remove(it.id) }
    }

    @PreDestroy
    fun close() = executor.shutdownNow()

    private class MutableDeploy(val id: UUID, private val tag: String) {
        val startedAt: Instant = Instant.now()
        @Volatile var status = WebDeployStatus.RUNNING
        @Volatile private var finishedAt: Instant? = null
        private val lines = StringBuffer()

        fun log(line: String) {
            lines.append(line).append('\n')
        }

        fun complete(status: WebDeployStatus, message: String) {
            lines.append(message).append('\n')
            finishedAt = Instant.now()
            this.status = status
        }

        fun snapshot() = WebDeploySnapshot(id, tag, status, startedAt, finishedAt, lines.toString().trimEnd())
    }

    companion object {
        private val TAG_PATTERN = Regex("^kkutu-web-v\\d{1,9}$")
        private const val MAX_JAR_BYTES = 400L * 1024 * 1024
        private const val FREE_SPACE_MARGIN = 64L * 1024 * 1024
        private const val MAX_OPERATIONS = 10

        internal fun sha256(file: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
