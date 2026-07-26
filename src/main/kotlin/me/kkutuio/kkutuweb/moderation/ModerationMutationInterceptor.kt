package me.kkutuio.kkutuweb.moderation

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class ModerationMutationInterceptor : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if (request.method == HttpMethod.GET.name || request.method == HttpMethod.OPTIONS.name) {
            return true
        }
        if (request.getHeader("X-Requested-With") != "XMLHttpRequest") {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "CSRF 검증 헤더가 없습니다.")
        }
        return true
    }
}
