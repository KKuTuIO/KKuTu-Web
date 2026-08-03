package me.kkutuio.kkutuweb.setting

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GameServerReconnectSettingTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `explicit reconnect configuration is parsed in seconds`() {
        val node = objectMapper.readTree(
            """{"enabled":false,"retryInterval":86400}"""
        )

        assertEquals(
            GameServerReconnectSetting(enabled = false, retryInterval = 86400),
            parseGameServerReconnectSetting(node)
        )
    }

    @Test
    fun `missing reconnect configuration keeps automatic reconnect enabled`() {
        assertEquals(
            GameServerReconnectSetting(
                enabled = true,
                retryInterval = DEFAULT_GAME_SERVER_RECONNECT_INTERVAL_SECONDS
            ),
            parseGameServerReconnectSetting(null)
        )
    }

    @Test
    fun `non-positive reconnect interval is rejected`() {
        val node = objectMapper.readTree(
            """{"enabled":true,"retryInterval":0}"""
        )

        assertThrows(IllegalArgumentException::class.java) {
            parseGameServerReconnectSetting(node)
        }
    }
}
