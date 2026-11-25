package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.identity.AccessTokenPrincipal
import me.kkutuio.kkutuweb.identity.IdpException
import me.kkutuio.kkutuweb.identity.oauth.OidcService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse

/**
 * `/api/support/` 는 고객센터 앱의 Bearer 토큰으로만 인증한다. 웹 세션 쿠키는 보지 않고 세션도 만들지 않으므로
 * 이 경로에는 CSRF 토큰이 필요 없다. 컨트롤러는 여기서 넣은 요청 속성이 없으면 거절한다(필터를 비켜 간 요청도 막힌다).
 */
@Component
class SupportOAuthBearerFilter(
    private val oidc: OidcService,
    private val settings: SupportSettings
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method == "OPTIONS" || !request.requestURI.removePrefix(request.contextPath).startsWith(PREFIX)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        // 첨부 파일은 JSON 본문에 실어 보낸다. multipart 는 본문을 읽기 전에 돌려보낸다.
        if (request.contentType?.trim()?.startsWith("multipart/", ignoreCase = true) == true) {
            response.sendError(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "지원하지 않는 요청 형식입니다.")
            return
        }
        if (request.requestURI.removePrefix(request.contextPath) == CONFIG_PATH) {
            chain.doFilter(request, response)
            return
        }
        val token = request.getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
        if (token.isNullOrEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "로그인이 필요합니다.")
            return
        }
        val principal = try {
            oidc.authenticateAccessToken(token)
        } catch (error: IdpException) {
            response.sendError(error.status, error.message)
            return
        }
        if (principal.clientId != settings.clientId || SupportSettings.SCOPE !in principal.scopes) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "OAuth 권한이 부족합니다.")
            return
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
        if (request.method != "POST") {
            chain.doFilter(request, response)
            return
        }
        // 길이를 알리지 않는 요청(chunked)도 있으므로 실제로 읽은 크기로 제한한다. 인증한 뒤에만 읽는다.
        val limit = if (UPLOAD_PATH.matches(request.requestURI.removePrefix(request.contextPath))) MAX_UPLOAD_BYTES else MAX_BODY_BYTES
        val body = if (request.contentLengthLong > limit) null
        else request.inputStream.readNBytes(limit + 1).takeIf { it.size <= limit }
        if (body == null) {
            response.sendError(HttpStatus.CONTENT_TOO_LARGE.value(), "요청이 너무 큽니다.")
            return
        }
        chain.doFilter(BufferedBodyRequest(request, body), response)
    }

    companion object {
        private const val PREFIX = "/api/support/"
        private const val CONFIG_PATH = "/api/support/config"
        private const val MAX_BODY_BYTES = 64 * 1024
        /** 첨부를 실을 수 있는 요청(접수·답변)의 한도: 파일들의 base64 와 본문. */
        private const val MAX_UPLOAD_BYTES = SupportFiles.MAX_PER_MESSAGE * (SupportFiles.MAX_BYTES + 2) / 3 * 4 + MAX_BODY_BYTES
        private val UPLOAD_PATH = Regex("/api/support/tickets(/[^/]+/messages)?")
        private const val PRINCIPAL_ATTRIBUTE = "kkutu.support.principal"

        fun principal(request: HttpServletRequest): AccessTokenPrincipal =
            request.getAttribute(PRINCIPAL_ATTRIBUTE) as? AccessTokenPrincipal
                ?: throw IdpException("login_required", "로그인이 필요합니다.", 401)
    }
}

/** 이미 읽어 둔 본문을 다시 읽게 해 주는 요청. */
private class BufferedBodyRequest(request: HttpServletRequest, private val body: ByteArray) : HttpServletRequestWrapper(request) {
    override fun getInputStream(): ServletInputStream {
        val source = ByteArrayInputStream(body)
        return object : ServletInputStream() {
            override fun read(): Int = source.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = source.read(buffer, offset, length)
            override fun isFinished(): Boolean = source.available() == 0
            override fun isReady(): Boolean = true
            override fun setReadListener(listener: ReadListener) = throw IllegalStateException("비동기 읽기는 지원하지 않습니다.")
        }
    }

    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream, characterEncoding ?: "UTF-8"))
}
