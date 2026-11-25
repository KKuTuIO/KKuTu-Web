package me.kkutuio.kkutuweb.login

import me.kkutuio.kkutuweb.SessionAttribute
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Duration
import java.time.Instant
import jakarta.servlet.http.HttpSession

/**
 * 로그인한 뒤에 돌아갈 곳. 로그인을 요구한 화면(OAuth 인증 요청, 계정 화면)이나 `/login?redirect=` 가 남기고,
 * 로그인이 끝난 자리에서 한 번 꺼내 쓴다. 이 사이트의 경로와 끄투리오 도메인(`*.kkutu.io`)의 https 주소만 받는다.
 */
@Component
class LoginRedirect(@Value("\${idp.login-redirect-domain:kkutu.io}") domain: String) {
    private val domain = domain.trim().lowercase().trim('.')

    /** 보낼 수 있는 주소면 그대로, 아니면 null. */
    fun validate(raw: String?): String? {
        val value = raw?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH } ?: return null
        // 브라우저는 역슬래시를 `/` 로, 탭·줄바꿈은 없는 것으로 읽는다. 검사한 값과 가는 곳이 달라지지 않게 거절한다.
        if (value.any { it == '\\' || it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        // Location 헤더에는 ASCII 만 실을 수 있어, 한글이 섞인 주소는 퍼센트 인코딩해 돌려준다.
        if (uri.scheme == null) {
            return uri.toASCIIString().takeIf { value.startsWith('/') && !value.startsWith("//") && uri.rawAuthority == null }
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        val host = uri.host?.lowercase() ?: return null
        return uri.toASCIIString().takeIf { host == domain || host.endsWith(".$domain") }
    }

    /** 검사를 통과한 주소만 남긴다. */
    fun remember(session: HttpSession, target: String) {
        val safe = validate(target) ?: return
        session.setAttribute(SessionAttribute.AFTER_LOGIN_URL.attributeName, safe)
        session.setAttribute(SessionAttribute.AFTER_LOGIN_AT.attributeName, Instant.now().epochSecond)
    }

    /** 남겨 둔 곳을 한 번만 돌려준다. 오래된 것은 버린다(그만둔 로그인이 나중의 로그인을 엉뚱한 곳으로 보내지 않게). */
    fun take(session: HttpSession): String? {
        val target = session.getAttribute(SessionAttribute.AFTER_LOGIN_URL.attributeName) as? String
        val since = session.getAttribute(SessionAttribute.AFTER_LOGIN_AT.attributeName) as? Long
        session.removeAttribute(SessionAttribute.AFTER_LOGIN_URL.attributeName)
        session.removeAttribute(SessionAttribute.AFTER_LOGIN_AT.attributeName)
        if (since != null && since < Instant.now().minus(LIFETIME).epochSecond) return null
        return validate(target)
    }

    /** 로그인하면서 세션을 새로 만들 때 [from] 에 남은 것을 [to] 로 옮기는 함수를 돌려준다. */
    fun carry(from: HttpSession): (HttpSession) -> Unit {
        val kept = listOf(SessionAttribute.AFTER_LOGIN_URL, SessionAttribute.AFTER_LOGIN_AT)
            .mapNotNull { attribute -> from.getAttribute(attribute.attributeName)?.let { attribute.attributeName to it } }
        return { to -> kept.forEach { (name, value) -> to.setAttribute(name, value) } }
    }

    private companion object {
        const val MAX_LENGTH = 2048
        val LIFETIME: Duration = Duration.ofMinutes(15)
    }
}
