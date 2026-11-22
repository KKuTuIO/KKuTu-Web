package me.kkutuio.kkutuweb.configmanagement

import me.kkutuio.kkutuweb.servermanagement.CommandResult
import me.kkutuio.kkutuweb.setting.GameServerManagementSetting
import me.kkutuio.kkutuweb.setting.GameServerReconnectSetting
import me.kkutuio.kkutuweb.setting.GameServerSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private class FakeStore(var text: String, var tracked: Boolean = false) : ConfigStore {
    var writes = 0
    override fun read(file: String) = StoredConfig(text, sha256Hex(text.toByteArray()), text.length, tracked)
    override fun write(file: String, text: String, expectedSha256: String): String {
        if (expectedSha256 != sha256Hex(this.text.toByteArray())) throw ConfigConflictException("changed")
        this.text = text; writes++
        return "backup-$writes"
    }
}

private class StubWebStore(val fake: FakeStore) : WebConfigStore(DefaultApplicationArguments()) {
    override fun read(file: String) = fake.read(file)
    override fun write(file: String, text: String, expectedSha256: String) = fake.write(file, text, expectedSha256)
}

private class StubGameStore(val fake: FakeStore) : GameConfigStore({ _, _ -> error("not used") }) {
    override fun forServer(management: GameServerManagementSetting): ConfigStore = fake
}

class ConfigManagementServiceTest {
    private val management = GameServerManagementSetting("kkutu-test", "/srv/game", "kkutu-test")
    private fun game(name: String, management: GameServerManagementSetting?) =
        GameServerSetting(true, "host", "KEY", "127.0.0.1", 8080, 1, GameServerReconnectSetting(true, 30), name, management)

    private fun service(web: FakeStore, gameFake: FakeStore = FakeStore("{}")): ConfigManagementService {
        val setting = Mockito.mock(KKuTuSetting::class.java)
        Mockito.`when`(setting.getGameServers()).thenReturn(listOf(game("다래", management), game("관리 없음", null)))
        return ConfigManagementService(setting, StubWebStore(web), StubGameStore(gameFake))
    }

    private val games = """{ "RULE": { "A": {} }, "OPTIONS": { "man": { "name": "Manner" } } }"""
    private val gameApi = """{ "PG_PASS": "hunter2", "PG_PORT": 5432, "MODE": "a" }"""

    private fun status(block: () -> Unit): HttpStatus = (assertThrows<ResponseStatusException> { block() }).statusCode.let { HttpStatus.valueOf(it.value()) }

    @Test
    fun `overview lists the web target and every channel and marks channels without SSH settings unavailable`() {
        val overview = service(FakeStore(games)).overview()
        assertEquals(listOf("web", "game-0", "game-1"), overview.targets.map { it.id })
        assertTrue(overview.targets[0].files.any { it.name == "moremi.json" })
        assertTrue(overview.targets[0].files.none { it.name.contains("oauth") })
        assertTrue(overview.targets[1].available && overview.targets[1].files.any { it.name == "api.json" && it.editable })
        assertFalse(overview.targets[2].available)
        assertTrue(overview.targets[2].files.isEmpty())
    }

    @Test
    fun `viewing hides secrets, keeps a stable hash and reports locked paths`() {
        val view = service(FakeStore(games), FakeStore(gameApi)).view("game-0", "api.json")
        assertTrue(view.editable)
        assertFalse(view.content.contains("hunter2"))
        assertTrue(view.content.contains(ConfigJson.SECRET_SENTINEL))
        assertEquals(listOf("/PG_PASS"), view.locked.map { it.path })
        assertEquals(sha256Hex(gameApi.toByteArray()), view.hash)
    }

    @Test
    fun `tracked game files and inc defaults can be viewed but not edited`() {
        val tracked = service(FakeStore(games), FakeStore(gameApi, tracked = true))
        assertFalse(tracked.view("game-0", "reward.json").editable)
        assertEquals(HttpStatus.BAD_REQUEST, status { tracked.save("game-0", "reward.json", ConfigSaveRequest(gameApi, sha256Hex(gameApi.toByteArray())), "a") })
        val untracked = service(FakeStore(games), FakeStore(gameApi))
        assertFalse(untracked.view("game-0", "api.inc.json").editable)
        assertEquals(HttpStatus.BAD_REQUEST, status { untracked.save("game-0", "api.inc.json", ConfigSaveRequest(gameApi, sha256Hex(gameApi.toByteArray())), "a") })
    }

    @Test
    fun `saving an ordinary edit writes pretty json with secrets restored`() {
        val fake = FakeStore(gameApi)
        val service = service(FakeStore(games), fake)
        val view = service.view("game-0", "api.json")
        val result = service.save("game-0", "api.json", ConfigSaveRequest(view.content.replace("\"MODE\": \"a\"", "\"MODE\": \"b\""), view.hash), "admin-1")
        assertEquals("{\n  \"PG_PASS\": \"hunter2\",\n  \"PG_PORT\": 5432,\n  \"MODE\": \"b\"\n}\n", fake.text)
        assertEquals(listOf("/MODE"), result.changed)
        assertEquals("backup-1", result.backup)
        assertEquals(sha256Hex(fake.text.toByteArray()), result.hash)
        assertTrue(result.restartHint.contains("다래"))
        // 같은 해시로 다시 저장하면 다른 사람이 먼저 바꾼 것으로 본다.
        assertEquals(HttpStatus.CONFLICT, status { service.save("game-0", "api.json", ConfigSaveRequest(view.content, view.hash), "admin-1") })
    }

    @Test
    fun `invalid saves are rejected without touching the file`() {
        val fake = FakeStore(gameApi)
        val service = service(FakeStore(games), fake)
        val view = service.view("game-0", "api.json")
        fun attempt(content: String) = status { service.save("game-0", "api.json", ConfigSaveRequest(content, view.hash), "a") }
        assertEquals(HttpStatus.BAD_REQUEST, attempt("{"))                                                       // JSON 아님
        assertEquals(HttpStatus.BAD_REQUEST, attempt(view.content))                                              // 변경 없음
        assertEquals(HttpStatus.BAD_REQUEST, attempt(view.content.replace(ConfigJson.SECRET_SENTINEL, "new")))   // 비밀 값 변경
        assertEquals(HttpStatus.BAD_REQUEST, attempt(view.content.replace("5432", "\"5432\"")))                  // 숫자 → 문자열
        assertEquals(HttpStatus.BAD_REQUEST, attempt("[]"))                                                      // 최상위 종류 변경
        assertEquals(0, fake.writes)
        assertEquals(gameApi, fake.text)
    }

    @Test
    fun `web files are checked against the structure the web server reads`() {
        val web = FakeStore(games)
        val service = service(web)
        val view = service.view("web", "games.json")
        assertEquals(HttpStatus.BAD_REQUEST, status { service.save("web", "games.json", ConfigSaveRequest("""{ "RULE": {}, "OPTIONS": [] }""", view.hash), "a") })
        assertEquals(0, web.writes)
        val ok = service.save("web", "games.json", ConfigSaveRequest("""{ "RULE": { "A": {}, "B": {} }, "OPTIONS": { "man": { "name": "Manner" } } }""", view.hash), "a")
        assertEquals(listOf("/RULE/B"), ok.changed)
        assertTrue(ok.restartHint.contains("웹"))
    }

    @Test
    fun `unknown targets and file names are not found, including path tricks`() {
        val service = service(FakeStore(games))
        for (file in listOf("evil.json", "../kkutu.json", "..%2Fkkutu.json", "oauth.json", "kkutu.json/../x")) {
            assertEquals(HttpStatus.NOT_FOUND, status { service.view("web", file) }, file)
        }
        assertEquals(HttpStatus.NOT_FOUND, status { service.view("nope", "games.json") })
        assertEquals(HttpStatus.NOT_FOUND, status { service.view("game-9", "api.json") })
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, status { service.view("game-1", "api.json") })
    }
}

class GameConfigStoreTest {
    private val management = GameServerManagementSetting("kkutu-test", "/srv/it's game", "kkutu-test", sshPort = 2222)
    private val sha = "a".repeat(64)

    private class Recorder(private val result: CommandResult) : (List<String>, Long) -> CommandResult {
        var command: List<String> = emptyList()
        override fun invoke(command: List<String>, timeout: Long): CommandResult { this.command = command; return result }
    }

    private fun store(result: CommandResult, recorder: Recorder = Recorder(result)) =
        GameConfigStore { command, timeout -> recorder(command, timeout) }.forServer(management) to recorder

    @Test
    fun `reads an untracked file and quotes the working directory`() {
        val (store, recorder) = store(CommandResult(0, "__STATE__ UNTRACKED\n__SIZE__ 9\n__SHA__ $sha\n__BEGIN__\n{\"a\": 1}", false))
        val stored = store.read("api.json")!!
        assertEquals("{\"a\": 1}", stored.text)
        assertEquals(sha, stored.sha256)
        assertFalse(stored.tracked)
        assertTrue(recorder.command.containsAll(listOf("ssh", "-n", "-T", "kkutu-test", "-p", "2222")))
        // 스크립트는 ssh 인자로 한 번 더 인용되어 나가므로 바깥 인용을 풀고 본다. 작업 폴더의 작은따옴표가 안전하게 이스케이프돼야 한다.
        val script = recorder.command.last().removePrefix("'").removeSuffix("'").replace("'\\''", "'")
        assertTrue(script.contains("cd -- '/srv/it'\\''s game'"), script)
    }

    @Test
    fun `a tracked file, a missing file and an unknown state are handled conservatively`() {
        assertTrue(store(CommandResult(0, "__STATE__ TRACKED\n__SIZE__ 2\n__SHA__ $sha\n__BEGIN__\n{}", false)).first.read("reward.json")!!.tracked)
        assertTrue(store(CommandResult(0, "__SIZE__ 2\n__SHA__ $sha\n__BEGIN__\n{}", false)).first.read("reward.json")!!.tracked)
        assertNull(store(CommandResult(0, "__MISSING__", false)).first.read("api.json"))
        assertThrows<ConfigStoreException> { store(CommandResult(0, "__STATE__ UNTRACKED\n__SIZE__ 99999\n__TOOBIG__", false)).first.read("api.json") }
        assertThrows<ConfigStoreException> { store(CommandResult(255, "ssh: connect to host failed", false)).first.read("api.json") }
        assertThrows<ConfigStoreException> { store(CommandResult(-1, "", true)).first.read("api.json") }
        assertThrows<ConfigStoreException> { store(CommandResult(0, "__STATE__ UNTRACKED\n__SIZE__ 2\n__SHA__ nothex\n__BEGIN__\n{}", false)).first.read("api.json") }
    }

    @Test
    fun `write sends the content as base64 with a compare and swap on the hash and maps exit codes`() {
        val (store, recorder) = store(CommandResult(0, "__OK__ /home/u/.kkutu-config-backups/x/api.json.20260101-000000", false))
        val backup = store.write("api.json", "{\"a\": 1}\n", sha)
        assertEquals("/home/u/.kkutu-config-backups/x/api.json.20260101-000000", backup)
        val script = recorder.command.last()
        assertTrue(script.contains(java.util.Base64.getEncoder().encodeToString("{\"a\": 1}\n".toByteArray())))
        assertTrue(script.contains(sha))
        assertTrue(script.contains("git ls-files --error-unmatch"))
        assertThrows<ConfigConflictException> { store(CommandResult(21, "CHANGED", false)).first.write("api.json", "{}\n", sha) }
        assertThrows<ConfigStoreException> { store(CommandResult(20, "TRACKED", false)).first.write("api.json", "{}\n", sha) }
        assertThrows<ConfigStoreException> { store(CommandResult(23, "VERIFY", false)).first.write("api.json", "{}\n", sha) }
        assertThrows<ConfigStoreException> { store(CommandResult(0, "no marker", false)).first.write("api.json", "{}\n", sha) }
    }
}
