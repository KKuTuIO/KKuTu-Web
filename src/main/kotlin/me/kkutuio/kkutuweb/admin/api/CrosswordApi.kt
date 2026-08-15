package me.kkutuio.kkutuweb.admin.api

import me.kkutuio.kkutuweb.admin.api.response.ActionResponse
import me.kkutuio.kkutuweb.admin.api.response.RestResult
import me.kkutuio.kkutuweb.crossword.CrosswordAdminService
import me.kkutuio.kkutuweb.crossword.CrosswordGenerateRequest
import me.kkutuio.kkutuweb.crossword.CrosswordPackRequest
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/admin/crosswords")
class CrosswordApi(
    private val setting: KKuTuSetting,
    private val loginService: LoginService,
    private val service: CrosswordAdminService
) {
    @GetMapping("/packs")
    fun packs(session: HttpSession): Any = if (authorized(session)) service.packs() else emptyList<Any>()

    @PostMapping("/packs")
    fun create(@RequestBody request: CrosswordPackRequest, session: HttpSession): ActionResponse =
        mutate(session) { service.createPack(request) }

    @PatchMapping("/packs/{id}")
    fun update(@PathVariable id: String, @RequestBody request: CrosswordPackRequest, session: HttpSession): ActionResponse =
        mutate(session) { service.updatePack(id, request) }

    @DeleteMapping("/packs/{id}")
    fun delete(@PathVariable id: String, session: HttpSession): ActionResponse =
        mutate(session) { service.deletePack(id) }

    @GetMapping("/packs/{id}/puzzles")
    fun puzzles(
        @PathVariable id: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        session: HttpSession
    ): Any = if (authorized(session)) service.puzzles(id, page, size) else emptyList<Any>()

    @PostMapping("/packs/{id}/generate")
    fun generate(
        @PathVariable id: String,
        @RequestBody request: CrosswordGenerateRequest,
        session: HttpSession
    ): ActionResponse = mutate(session) { service.generate(id, request) }

    @DeleteMapping("/packs/{id}/puzzles/{puzzleId}")
    fun deletePuzzle(@PathVariable id: String, @PathVariable puzzleId: Long, session: HttpSession): ActionResponse =
        mutate(session) { service.deletePuzzle(id, puzzleId) }

    private fun authorized(session: HttpSession): Boolean {
        val profile = loginService.getSessionProfile(session) ?: return false
        val admin = setting.getAdmins().find { it.id == profile.id } ?: return false
        return AdminSetting.Privilege.CROSSWORD in admin.privileges || AdminSetting.Privilege.WORD in admin.privileges
    }

    private fun mutate(session: HttpSession, action: () -> Any?): ActionResponse {
        if (!authorized(session)) return ActionResponse.rest(false, RestResult.UNAUTHORIZED)
        return try {
            ActionResponse.success(action())
        } catch (error: IllegalArgumentException) {
            ActionResponse.rest(false, RestResult.INVALID_DATA, mapOf("message" to (error.message ?: "잘못된 요청입니다.")))
        } catch (error: org.springframework.dao.DataIntegrityViolationException) {
            ActionResponse.rest(false, RestResult.INVALID_DATA, mapOf("message" to "중복되거나 참조 중인 데이터입니다."))
        }
    }
}
