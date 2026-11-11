package me.kkutuio.kkutuweb.login

import me.kkutuio.kkutuweb.oauth.AuthVendor
import me.kkutuio.kkutuweb.setting.OAuthVendorSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoginProvidersTest {
    private fun setting(order: Short, allowRegister: Boolean) =
        OAuthVendorSetting(order, "id", "secret", "https://example.test/cb", allowRegister)

    @Test
    fun `providers follow the configured order and expose the registration flag`() {
        val result = loginProviderList(
            mapOf(
                AuthVendor.DALDALSO to setting(3, false),
                AuthVendor.NAVER to setting(1, true),
                AuthVendor.DISCORD to setting(2, true)
            )
        )

        assertEquals(
            listOf(
                mapOf("id" to "naver", "allow_register" to true),
                mapOf("id" to "discord", "allow_register" to true),
                mapOf("id" to "daldalso", "allow_register" to false)
            ),
            result
        )
    }

    @Test
    fun `no enabled providers gives an empty list`() {
        assertEquals(emptyList<Map<String, Any>>(), loginProviderList(emptyMap()))
    }
}
