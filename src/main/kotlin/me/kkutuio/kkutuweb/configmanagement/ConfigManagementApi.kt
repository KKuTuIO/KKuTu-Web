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

/** 설정 파일 보기·수정. 서버 전체를 좌우하므로 ADMIN_MANAGE 권한자만 쓴다. */
@RestController
@RequestMapping("/api/admin/config-management")
class ConfigManagementApi(
    private val service: ConfigManagementService,
    private val authorizer: AdminModerationAuthorizer
) {
    @GetMapping
    fun overview(session: HttpSession): ConfigOverview {
        authorizer.require(session, AdminSetting.Privilege.ADMIN_MANAGE)
        return service.overview()
    }

    @GetMapping("/{target}/{file:.+}")
    fun view(@PathVariable target: String, @PathVariable file: String, session: HttpSession): ConfigFileView {
        authorizer.require(session, AdminSetting.Privilege.ADMIN_MANAGE)
        return service.view(target, file)
    }

    @PutMapping("/{target}/{file:.+}")
    fun save(@PathVariable target: String, @PathVariable file: String, @RequestBody request: ConfigSaveRequest, session: HttpSession): ConfigSaveResult {
        val actor = authorizer.require(session, AdminSetting.Privilege.ADMIN_MANAGE)
        return service.save(target, file, request, actor)
    }
}
