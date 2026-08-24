package me.kkutuio.kkutuweb.moderation

import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import jakarta.servlet.http.HttpSession

@Component
class AdminModerationAuthorizer(
    private val setting: KKuTuSetting,
    private val loginService: LoginService
) {
    fun require(session: HttpSession, privilege: AdminSetting.Privilege): String {
        loginService.getSessionProfile(session)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val accountUuid = loginService.accountUuid(session)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val admin = setting.getAdmins().firstOrNull { it.id == accountUuid }
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (privilege !in admin.privileges) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        return accountUuid
    }

    fun hasPrivilege(session: HttpSession, privilege: AdminSetting.Privilege): Boolean {
        loginService.getSessionProfile(session) ?: return false
        val accountUuid = loginService.accountUuid(session) ?: return false
        val admin = setting.getAdmins().firstOrNull { it.id == accountUuid } ?: return false
        return privilege in admin.privileges
    }

    fun requireMaster(session: HttpSession): String {
        loginService.getSessionProfile(session)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val accountUuid = loginService.accountUuid(session)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        if (!setting.isAdminMaster(accountUuid)) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        return accountUuid
    }
}
