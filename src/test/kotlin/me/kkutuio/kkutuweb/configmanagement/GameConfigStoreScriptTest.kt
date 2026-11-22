package me.kkutuio.kkutuweb.configmanagement

import me.kkutuio.kkutuweb.servermanagement.CommandResult
import me.kkutuio.kkutuweb.setting.GameServerManagementSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** ssh 대신 같은 스크립트를 로컬 셸에서 그대로 실행해, 실제 git 작업 트리에서 읽기·쓰기 스크립트가 맞게 동작하는지 본다. git 이 없는 환경에서는 건너뛴다. */
class GameConfigStoreScriptTest {
    @TempDir lateinit var dir: Path

    private fun sh(cwd: Path, vararg args: String): Int =
        ProcessBuilder(*args).directory(cwd.toFile()).redirectErrorStream(true).start().also { it.inputStream.readBytes() }.let {
            it.waitFor(30, TimeUnit.SECONDS); it.exitValue()
        }

    private fun tools(): Boolean = runCatching { sh(dir, "git", "--version") == 0 && sh(dir, "sh", "-c", "command -v base64 >/dev/null && (command -v sha256sum >/dev/null || command -v shasum >/dev/null)") == 0 }.getOrDefault(false)

    /** ssh 는 인자들을 공백으로 이어 원격 셸에 넘긴다. 그대로 흉내 낸다. */
    private fun localRunner(home: Path) = { command: List<String>, _: Long ->
        val process = ProcessBuilder("sh", "-c", "sh -lc " + command.last()).redirectErrorStream(true)
            .also { it.environment()["HOME"] = home.toString() }.start()
        val output = process.inputStream.bufferedReader().readText().trimEnd()
        process.waitFor(30, TimeUnit.SECONDS)
        CommandResult(process.exitValue(), output, false)
    }

    @Test
    fun `read and write scripts behave correctly in a real git work tree`() {
        assumeTrue(tools(), "git/base64/sha256 not available")
        val repo = dir.resolve("game").also(Files::createDirectories)
        val home = dir.resolve("home").also(Files::createDirectories)
        val config = repo.resolve("server/config").also(Files::createDirectories)
        Files.writeString(config.resolve("reward.json"), "{\"tracked\": true}\n")
        Files.writeString(config.resolve("api.json"), "{\"a\": 1}\n")
        assertEquals(0, sh(repo, "git", "init", "-q"))
        assertEquals(0, sh(repo, "git", "add", "server/config/reward.json"))
        assertEquals(0, sh(repo, "git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "-m", "init"))

        val store = GameConfigStore(localRunner(home)).forServer(GameServerManagementSetting("local", repo.toString(), "p"))

        val api = store.read("api.json")!!
        assertFalse(api.tracked)
        assertEquals("{\"a\": 1}", api.text)
        assertEquals(sha256Hex("{\"a\": 1}\n".toByteArray()), api.sha256)
        assertTrue(store.read("reward.json")!!.tracked)
        assertNull(store.read("nick.json"))

        // 정상 저장: 내용 교체 + 홈 아래 백업 + 임시 파일 없음
        val newText = "{\n  \"a\": 2,\n  \"한글\": \"it's \\\"quoted\\\" \$HOME\"\n}\n"
        val backup = store.write("api.json", newText, api.sha256)
        assertEquals(newText, Files.readString(config.resolve("api.json")))
        assertEquals("{\"a\": 1}\n", Files.readString(Path.of(backup)))
        assertTrue(backup.startsWith(home.toString()), backup)
        assertTrue(Files.list(config).use { s -> s.noneMatch { it.fileName.toString().contains(".tmp.") } })
        assertEquals(sha256Hex(newText.toByteArray()), store.read("api.json")!!.sha256)

        // 옛 해시로 저장하면 충돌, 파일은 그대로
        assertThrows<ConfigConflictException> { store.write("api.json", "{}\n", api.sha256) }
        assertEquals(newText, Files.readString(config.resolve("api.json")))

        // 추적 파일은 저장 단계에서도 막힌다
        val tracked = store.read("reward.json")!!
        assertThrows<ConfigStoreException> { store.write("reward.json", "{}\n", tracked.sha256) }
        assertEquals("{\"tracked\": true}\n", Files.readString(config.resolve("reward.json")))

        // git 저장소가 아니면 안전하게 추적 파일로 본다
        val plain = dir.resolve("plain").also { Files.createDirectories(it.resolve("server/config")) }
        Files.writeString(plain.resolve("server/config/api.json"), "{}\n")
        val plainStore = GameConfigStore(localRunner(home)).forServer(GameServerManagementSetting("local", plain.toString(), "p"))
        assertTrue(plainStore.read("api.json")!!.tracked)
        assertThrows<ConfigStoreException> { plainStore.write("api.json", "{\"b\": 1}\n", plainStore.read("api.json")!!.sha256) }
    }
}
