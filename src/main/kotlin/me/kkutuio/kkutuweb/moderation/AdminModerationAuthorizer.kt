package me.kkutuio.kkutuweb.moderation

import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpSession

@Component
class AdminModerationAuthorizer(
    private val setting: KKuTuSetting,
    private val loginService: LoginService
) {
    fun require(session: HttpSession, privilege: AdminSetting.Privilege): String {
        val profile = loginService.getSessionProfile(session)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val admin = setting.getAdmins().firstOrNull { it.id == profile.id }
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (privilege !in admin.privileges) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        return profile.id
    }
}
