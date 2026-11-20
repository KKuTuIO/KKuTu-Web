package me.kkutuio.kkutuweb.record

import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.game.GameClientManager
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * 제출 처리 진단(읽기 전용): 동시 입력 모드(자음퀴즈, 워드스택 등)에서 정답이 무시되는 이유를 서버 로그 없이 확인한다.
 * 채널마다, 워커마다 결과 코드별 건수와 방 대기열에서 기다린 시간(waitMs), 최근에 받아들여지지 않았거나 오래 기다린 제출을 준다.
 * 제출한 글자는 담지 않는다. 값은 서버가 마지막으로 시작한 뒤의 누적이며 서버를 다시 시작하면 비워진다.
 */
@RestController
@RequestMapping("/api/admin/gameplay-diagnostics")
class GameplayDiagnosticsAdminApi(
    private val gameClientManager: GameClientManager,
    private val authorizer: AdminModerationAuthorizer
) {
    private val objectMapper = jacksonObjectMapper()

    @GetMapping
    fun diagnostics(session: HttpSession, response: HttpServletResponse): Map<String, Any?> {
        authorizer.requireAny(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE, AdminSetting.Privilege.ADMIN_MANAGE)
        response.setHeader("Cache-Control", "private, no-store")
        val channels = gameClientManager.requestGameplayDiagnostics().map { (channel, raw) ->
            val node = objectMapper.readTree(raw)
            mapOf(
                "channel" to channel.toInt(),
                "ok" to (node["ok"]?.asBoolean() ?: false),
                "now" to node["now"],
                "workers" to node["workers"]
            )
        }
        return mapOf("channels" to channels)
    }
}
