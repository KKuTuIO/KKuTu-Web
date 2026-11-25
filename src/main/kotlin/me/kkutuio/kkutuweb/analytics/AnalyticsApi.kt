package me.kkutuio.kkutuweb.analytics

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** 백오피스 홈의 운영 지표. 토큰 검사는 AdminOAuthBearerFilter, 여기서는 권한이 하나라도 있는 관리자인지 본다. */
@RestController
@RequestMapping("/api/admin/analytics")
class AnalyticsApi(
    private val authorizer: AdminModerationAuthorizer,
    private val service: AnalyticsService
) {
    @GetMapping("/overview")
    fun overview(@RequestParam(defaultValue = "7d") range: String, session: HttpSession): AnalyticsOverview {
        authorizer.requireAny(session, *AdminSetting.Privilege.entries.toTypedArray())
        val days = when (range) {
            "7d" -> 7
            "30d" -> 30
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "range 는 7d 또는 30d 여야 합니다.")
        }
        return service.overview(days)
    }
}
