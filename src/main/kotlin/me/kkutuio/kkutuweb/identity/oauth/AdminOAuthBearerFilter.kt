package me.kkutuio.kkutuweb.identity.oauth

import me.kkutuio.kkutuweb.identity.IdentityProviderSettings
import me.kkutuio.kkutuweb.login.LoginService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Collections
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/** Lets legacy admin endpoints consume the Admin SPA's OAuth bearer token. */
@Component
class AdminOAuthBearerFilter(
    private val oidc: OidcService,
    private val settings: IdentityProviderSettings,
    private val loginService: LoginService
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.removePrefix(request.contextPath).startsWith("/api/admin/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authorization?.startsWith("Bearer ") != true) {
            chain.doFilter(request, response)
            return
        }
        val token = authorization.removePrefix("Bearer ").trim()
        if (token.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Bearer token이 필요합니다.")
            return
        }
        val principal = try {
            oidc.authenticateAccessToken(token)
        } catch (error: Exception) {
            val status = if (error is me.kkutuio.kkutuweb.identity.IdpException) error.status else HttpStatus.UNAUTHORIZED.value()
            response.sendError(status, error.message ?: "OAuth 인증에 실패했습니다.")
            return
        }
        if (principal.clientId != settings.adminClientId || "admin:access" !in principal.scopes) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "OAuth 권한이 부족합니다.")
            return
        }
        val existingSession = request.getSession(false)
        val session = request.session
        val originalAttributes = Collections.list(session.attributeNames).associateWith(session::getAttribute)
        try {
            loginService.bindOAuthBearerSession(session, principal.account)
        } catch (error: Exception) {
            val status = if (error is me.kkutuio.kkutuweb.identity.IdpException) error.status else HttpStatus.UNAUTHORIZED.value()
            response.sendError(status, error.message ?: "OAuth 인증에 실패했습니다.")
            return
        }
        try {
            chain.doFilter(request, response)
        } finally {
            if (existingSession == null) {
                runCatching { session.invalidate() }
            } else {
                Collections.list(session.attributeNames).forEach(session::removeAttribute)
                originalAttributes.forEach(session::setAttribute)
            }
        }
    }
}
