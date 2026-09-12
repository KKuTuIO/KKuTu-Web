package me.kkutuio.kkutuweb.servermanagement

import me.kkutuio.kkutuweb.setting.GameServerManagementSetting
import me.kkutuio.kkutuweb.setting.GameServerReconnectSetting
import me.kkutuio.kkutuweb.setting.GameServerSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CopyOnWriteArrayList

class GameServerManagementServiceTest {
    @Test
    fun `accepts ordinary git branch names`() {
        assertTrue(GameServerManagementService.isSafeBranch("production"))
        assertTrue(GameServerManagementService.isSafeBranch("release/4.2.2"))
        assertTrue(GameServerManagementService.isSafeBranch("fix/server_status-1"))
    }

    @Test
    fun `rejects shell input and ambiguous git refs`() {
        listOf("--upload-pack=evil", "feature;reboot", "feature/../main", "feature//main", "main.lock", "feature/.hidden", "HEAD", "@{upstream}")
            .forEach { assertFalse(GameServerManagementService.isSafeBranch(it), it) }
    }

    @Test
    fun `quotes single quotes for a POSIX shell`() {
        assertTrue(GameServerManagementService.shellQuote("a'b").contains("'\\''"))
    }

    @Test
    fun `management settings reject command-shaped process names`() {
        assertThrows(IllegalArgumentException::class.java) {
            GameServerManagementSetting("game-host", "/srv/game", "game; reboot")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameServerManagementSetting("-oProxyCommand=evil", "/srv/game", "game")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameServerManagementSetting("game-host", "/srv/game", "game", sshKeyPath = "keys/game-server")
        }
    }

    @Test
    fun `source update builds a fixed SSH command around the validated branch`() {
        val setting = Mockito.mock(KKuTuSetting::class.java)
        val server = GameServerSetting(
            true, "game.example", "key", "127.0.0.1", 8080, 1,
            GameServerReconnectSetting(true, 30), "감자",
            GameServerManagementSetting(
                "game-host", "/srv/game dir", "kkutu-potato",
                sshKeyPath = "/opt/kkutu-web/ssh/game-server-ed25519"
            )
        )
        Mockito.`when`(setting.getGameServers()).thenReturn(listOf(server))
        val commands = CopyOnWriteArrayList<List<String>>()
        val service = GameServerManagementService(
            setting,
            RemoteCommandRunner { command, _ ->
                commands += command
                CommandResult(0, "compiled", false)
            },
            ObjectMapper()
        )

        val started = service.updateSource(0, "release/4.2.2", "actor")
        var result = started
        repeat(50) {
            result = service.operation(started.id)
            if (result.status != ServerOperationStatus.RUNNING) return@repeat
            Thread.sleep(10)
        }

        assertEquals(ServerOperationStatus.SUCCEEDED, result.status)
        assertEquals("ssh", commands.single().first())
        assertTrue(commands.single().containsAll(listOf(
            "PreferredAuthentications=publickey", "IdentitiesOnly=yes", "-i",
            "/opt/kkutu-web/ssh/game-server-ed25519"
        )))
        assertTrue(commands.single().last().contains("git pull --ff-only origin"))
        assertTrue(commands.single().last().contains("release/4.2.2"))
        assertTrue(commands.single().last().contains("pnpm --dir server exec tsc --noEmitOnError -p tsconfig.json"))
        service.close()
    }

    @Test
    fun `status failure exposes the SSH exit code and diagnostic output`() {
        val setting = Mockito.mock(KKuTuSetting::class.java)
        Mockito.`when`(setting.getGameServers()).thenReturn(listOf(GameServerSetting(
            true, "game.example", "key", "127.0.0.1", 8080, 1,
            GameServerReconnectSetting(true, 30), "감자",
            GameServerManagementSetting("opc@s0", "/srv/game", "game-0")
        )))
        val service = GameServerManagementService(
            setting,
            RemoteCommandRunner { _, _ -> CommandResult(255, "Permission denied (publickey).", false) },
            ObjectMapper()
        )

        val status = service.processStatus(0)
        val diagnostic = requireNotNull(status.error)

        assertEquals("unavailable", status.status)
        assertTrue(diagnostic.contains("종료 코드 255"))
        assertTrue(diagnostic.contains("Permission denied (publickey)."))
        service.close()
    }
}
