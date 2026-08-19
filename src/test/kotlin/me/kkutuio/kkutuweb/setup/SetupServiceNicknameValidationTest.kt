package me.kkutuio.kkutuweb.setup

import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.user.UserDao
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.StringRedisTemplate

class SetupServiceNicknameValidationTest {
    private val service = SetupService(
        mock(LoginService::class.java),
        mock(UserDao::class.java),
        mock(StringRedisTemplate::class.java)
    )

    @Test
    fun `allows Korean jamo inside a nickname`() {
        assertNull(service.nicknameValidationError("끄투하고싶ㄷㅏㅏㅏ"))
    }
}
