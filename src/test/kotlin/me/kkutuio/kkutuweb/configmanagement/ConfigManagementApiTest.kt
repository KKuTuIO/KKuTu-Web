package me.kkutuio.kkutuweb.configmanagement

import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.web.server.ResponseStatusException

class ConfigManagementApiTest {
    private val service = mock(ConfigManagementService::class.java)
    private val authorizer = mock(AdminModerationAuthorizer::class.java)
    private val api = ConfigManagementApi(service, authorizer)
    private val session = MockHttpSession()

    @Test
    fun `every endpoint requires the source management privilege and nothing weaker`() {
        Mockito.`when`(authorizer.require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)).thenReturn("actor-1")
        Mockito.`when`(service.overview()).thenReturn(ConfigOverview(emptyList()))
        api.overview(session)
        api.view("web", "games.json", session)
        api.save("web", "games.json", ConfigSaveRequest("{}", "h"), session)
        verify(authorizer, Mockito.times(3)).require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        verify(authorizer, never()).require(session, AdminSetting.Privilege.ADMIN_MANAGE)
        verify(authorizer, never()).require(session, AdminSetting.Privilege.GAME_SERVER_RESTART)
        verify(service).save("web", "games.json", ConfigSaveRequest("{}", "h"), "actor-1")
    }

    @Test
    fun `without the privilege nothing reaches the service`() {
        Mockito.`when`(authorizer.require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)).thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN))
        assertEquals(HttpStatus.FORBIDDEN.value(), assertThrows<ResponseStatusException> { api.overview(session) }.statusCode.value())
        assertEquals(HttpStatus.FORBIDDEN.value(), assertThrows<ResponseStatusException> { api.view("web", "kkutu.json", session) }.statusCode.value())
        assertEquals(HttpStatus.FORBIDDEN.value(), assertThrows<ResponseStatusException> { api.save("web", "kkutu.json", ConfigSaveRequest("{}", "h"), session) }.statusCode.value())
        Mockito.verifyNoInteractions(service)
    }
}
