package me.kkutuio.kkutuweb.identity

import com.fasterxml.jackson.databind.ObjectMapper
import me.kkutuio.kkutuweb.identity.oauth.OidcService
import me.kkutuio.kkutuweb.identity.oauth.TokenSigner
import me.kkutuio.kkutuweb.identity.oauth.AdminOAuthBearerFilter
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.user.UserDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Instant
import java.util.UUID
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest

class IdentitySecurityRegressionTest {
    private val dao = mock(IdentityDao::class.java)
    private val settings = mock(IdentityProviderSettings::class.java)

    @Test
    fun `passkey ceremonies require user verification`() {
        val accountId = UUID.randomUUID()
        val account = account(accountId)
        `when`(dao.listPasskeys(accountId)).thenReturn(emptyList())
        val service = WebAuthnService(dao, settings, ObjectMapper())

        val registration = service.registrationOptions(account)["publicKey"] as Map<*, *>
        val authenticatorSelection = registration["authenticatorSelection"] as Map<*, *>
        val authentication = service.authenticationOptions()["publicKey"] as Map<*, *>

        assertEquals("required", authenticatorSelection["userVerification"])
        assertEquals("required", authentication["userVerification"])
    }

    @Test
    fun `account claims do not advertise unused authentication methods`() {
        val accountId = UUID.randomUUID()
        val account = account(accountId)
        `when`(dao.findActiveAccessToken(anyString())).thenReturn(mapOf(
            "account_id" to accountId,
            "client_id" to "client",
            "scopes" to "[\"account\"]",
            "selected_profile_id" to null
        ))
        `when`(dao.findAccount(accountId)).thenReturn(account)
        `when`(dao.listIdentities(accountId)).thenReturn(emptyList())
        val service = OidcService(
            dao,
            settings,
            mock(TokenSigner::class.java),
            mock(UserDao::class.java),
            ObjectMapper()
        )

        val claims = service.userInfo("access-token")

        assertFalse(claims.containsKey("amr"))
    }

    @Test
    fun `opening an email login link does not authenticate`() {
        val security = mock(AccountSecurityService::class.java)
        val login = mock(LoginService::class.java)
        val controller = AccountPageController(mock(AccountService::class.java), security, login)

        assertEquals("forward:/account/recovery.html", controller.recoveryLogin())
        verifyNoInteractions(security, login)
    }

    @Test
    fun `confirmed email login replaces an existing session`() {
        val security = mock(AccountSecurityService::class.java)
        val login = mock(LoginService::class.java)
        val request = mock(HttpServletRequest::class.java)
        val target = account(UUID.randomUUID())
        `when`(security.consumeEmailRecoveryLogin("token")).thenReturn(target)
        val controller = AccountPageController(mock(AccountService::class.java), security, login)

        assertEquals("redirect:/account", controller.completeRecoveryLogin("token", request))
        verify(login).loginWithAccount(request, target)
    }

    @Test
    fun `admin API rejects legacy cookie-only requests`() {
        val filter = AdminOAuthBearerFilter(
            mock(OidcService::class.java),
            mock(IdentityProviderSettings::class.java),
            mock(LoginService::class.java)
        )
        val request = MockHttpServletRequest("GET", "/api/admin/profile")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
        verifyNoInteractions(chain)
    }

    private fun account(id: UUID) = Account(
        id = id,
        uuid = UUID.randomUUID(),
        legacyUserId = "test-user",
        status = AccountStatus.ACTIVE,
        externalMfaEnabled = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        sessionNotBefore = Instant.EPOCH,
        primaryIdentityId = null,
        originIdentityId = null
    )
}
