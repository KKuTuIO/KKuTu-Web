package me.kkutuio.kkutuweb.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class WebSecurityConfigTest {
    @Test
    fun `cookie authenticated account mutations require csrf`() {
        assertTrue(request("POST", "/api/account/password/change").requiresCsrf())
        assertTrue(request("DELETE", "/api/account/profiles/7").requiresCsrf())
        assertTrue(request("POST", "/oauth/authorize/consent").requiresCsrf())
    }

    @Test
    fun `public login and recovery endpoints remain callable without csrf`() {
        assertFalse(request("POST", "/api/account/password/login").requiresCsrf())
        assertFalse(request("POST", "/api/account/recovery/request").requiresCsrf())
        assertFalse(request("GET", "/api/account").requiresCsrf())
    }

    @Test
    fun `bearer authenticated admin mutations do not depend on browser csrf cookies`() {
        val request = request("DELETE", "/api/admin/oauth-clients/test").apply {
            addHeader("Authorization", "Bearer token")
        }

        assertFalse(request.requiresCsrf())
    }

    @Test
    fun `untrusted bearer text cannot disable csrf on cookie account mutations`() {
        val request = request("POST", "/api/account/password/change").apply {
            addHeader("Authorization", "Bearer attacker-controlled")
        }

        assertTrue(request.requiresCsrf())
    }

    @Test
    fun `context path is excluded when matching protected routes`() {
        val request = MockHttpServletRequest("POST", "/web/api/account/email/change").apply {
            contextPath = "/web"
        }

        assertTrue(request.requiresCsrf())
    }

    private fun request(method: String, path: String) = MockHttpServletRequest(method, path)
    private fun MockHttpServletRequest.requiresCsrf() = IdpCsrfRequestMatcher.matches(this)
}
