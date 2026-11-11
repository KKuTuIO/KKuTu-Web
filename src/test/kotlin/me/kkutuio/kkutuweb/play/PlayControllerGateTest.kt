package me.kkutuio.kkutuweb.play

import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession

/** The new client is a preview for game administrators only: everybody else must get a plain 404 from every route it uses. */
class PlayControllerGateTest {
    private val admin = "11111111-1111-1111-1111-111111111111"
    private val player = "22222222-2222-2222-2222-222222222222"

    private val session = MockHttpSession()

    private fun controller(account: String?): PlayController {
        val login = mock(LoginService::class.java)
        `when`(login.accountUuid(session)).thenReturn(account)
        val setting = mock(KKuTuSetting::class.java)
        `when`(setting.getGameAdminIds()).thenReturn(setOf(admin))
        return PlayController(
            setting, login, mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock()
        )
    }

    @Test
    fun `a regular player or a visitor gets 404 everywhere`() {
        for (account in listOf(player, null)) {
            val c = controller(account)
            val req = MockHttpServletRequest("GET", "/_play/immutable/x.js")
            assertEquals(404, c.shell("0", session).statusCode.value())
            assertEquals(404, c.asset(req, session).statusCode.value())
            assertEquals(404, c.locale(req, session).statusCode.value())
            assertEquals(404, c.goodDetails(session).statusCode.value())
            assertEquals(404, c.meta(session).statusCode.value())
            assertEquals(404, c.ticket(0, session, req).statusCode.value())
            assertEquals(404, (c.entry(session) as org.springframework.http.ResponseEntity<*>).statusCode.value())
        }
    }

    @Test
    fun `an administrator is sent to the lobby and cannot read outside the build directory`() {
        val c = controller(admin)
        assertEquals("redirect:/play/0", c.entry(session))
        assertEquals(404, c.asset(MockHttpServletRequest("GET", "/_play/../application.yml"), session).statusCode.value())
        assertEquals(404, c.asset(MockHttpServletRequest("GET", "/_play/missing.js"), session).statusCode.value())
        // what the build does not carry of the CDN comes from the real one, and only from there
        val redirect = c.asset(MockHttpServletRequest("GET", "/_play/cdn/media/ShopBGM.mp3"), session)
        assertEquals(302, redirect.statusCode.value())
        assertEquals("https://cdn.kkutu.io/media/ShopBGM.mp3", redirect.headers.location.toString())
        assertEquals(404, c.asset(MockHttpServletRequest("GET", "/_play/cdn/../x"), session).statusCode.value())
    }
}
