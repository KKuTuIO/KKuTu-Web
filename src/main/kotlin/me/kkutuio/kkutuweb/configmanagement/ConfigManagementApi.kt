package me.kkutuio.kkutuweb.configmanagement

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 설정 파일 보기·수정. 소스 갱신 권한(GAME_SOURCE_MANAGE)이 있는 사람의 일이다.
 * 서버 재시작(GAME_SERVER_RESTART)이나 관리자 위임(ADMIN_MANAGE)과는 별개이며, 이 권한자는 SSH 접근이 없을 수 있어
 * 관리자 계정·서버 접속 정보 같은 보호 항목은 이 화면에서 바꾸지 못하게 막아 둔다.
 */
@RestController
@RequestMapping("/api/admin/config-management")
class ConfigManagementApi(
    private val service: ConfigManagementService,
    private val authorizer: AdminModerationAuthorizer
) {
    @GetMapping
    fun overview(session: HttpSession): ConfigOverview {
        authorizer.require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        return service.overview()
    }

    @GetMapping("/{target}/{file:.+}")
    fun view(@PathVariable target: String, @PathVariable file: String, session: HttpSession): ConfigFileView {
        authorizer.require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        return service.view(target, file)
    }

    @PutMapping("/{target}/{file:.+}")
    fun save(@PathVariable target: String, @PathVariable file: String, @RequestBody request: ConfigSaveRequest, session: HttpSession): ConfigSaveResult {
        val actor = authorizer.require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        return service.save(target, file, request, actor)
    }
}
