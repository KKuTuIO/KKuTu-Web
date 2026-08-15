package me.kkutuio.kkutuweb.admin.api

import me.kkutuio.kkutuweb.admin.api.response.ActionResponse
import me.kkutuio.kkutuweb.admin.api.response.RestResult
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import me.kkutuio.kkutuweb.theme.ThemePresetAdminService
import me.kkutuio.kkutuweb.theme.ThemePresetRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/admin/theme-presets")
class ThemePresetApi(
    private val setting: KKuTuSetting,
    private val loginService: LoginService,
    private val service: ThemePresetAdminService
) {
    @GetMapping
    fun list(session: HttpSession): Any = if (authorized(session)) service.presets() else emptyList<Any>()

    @GetMapping("/available-themes")
    fun availableThemes(session: HttpSession): Any =
        if (authorized(session)) service.availableThemes() else emptyMap<String, Any>()

    @PostMapping
    fun create(@RequestBody request: ThemePresetRequest, session: HttpSession): ActionResponse =
        mutate(session) { service.create(request) }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: ThemePresetRequest,
        session: HttpSession
    ): ActionResponse = mutate(session) { service.update(id, request) }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, session: HttpSession): ActionResponse =
        mutate(session) { service.delete(id) }

    private fun authorized(session: HttpSession): Boolean {
        val profile = loginService.getSessionProfile(session) ?: return false
        val admin = setting.getAdmins().find { it.id == profile.id } ?: return false
        return AdminSetting.Privilege.WORD in admin.privileges
    }

    private fun mutate(session: HttpSession, action: () -> Any?): ActionResponse {
        if (!authorized(session)) return ActionResponse.rest(false, RestResult.UNAUTHORIZED)
        return try {
            ActionResponse.success(action())
        } catch (error: IllegalArgumentException) {
            ActionResponse.rest(false, RestResult.INVALID_DATA, mapOf("message" to (error.message ?: "잘못된 요청입니다.")))
        } catch (error: DataIntegrityViolationException) {
            ActionResponse.rest(false, RestResult.INVALID_DATA, mapOf("message" to "중복되거나 제약 조건에 맞지 않는 데이터입니다."))
        }
    }
}
