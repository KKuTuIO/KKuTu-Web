package me.kkutuio.kkutuweb.identity

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import me.kkutuio.kkutuweb.login.LoginService
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@Controller
class AccountPageController(
    private val accounts: AccountService,
    private val security: AccountSecurityService,
    private val loginService: LoginService
) {
    @GetMapping("/account", "/account/security", "/account/services")
    fun account(session: HttpSession): String {
        if (accounts.currentAccount(session) == null) return "redirect:/login"
        return if (loginService.needsSetup(session)) "redirect:/setup" else "forward:/account.html"
    }
    @GetMapping("/account/apps")
    fun connectedApplications(session: HttpSession): String = if (accounts.currentAccount(session) == null) "redirect:/login" else "forward:/account/apps.html"
    @GetMapping("/account/sanctions")
    fun sanctions(session: HttpSession): String = if (accounts.currentAccount(session) == null) "redirect:/login" else "forward:/account/sanctions.html"
    @GetMapping("/account/recovery") fun recovery(): String = "forward:/account/recovery.html"
    @GetMapping("/account/recovery/login")
    fun recoveryLogin(): String = "forward:/account/recovery.html"

    @PostMapping("/account/recovery/login")
    fun completeRecoveryLogin(@RequestParam token: String, request: HttpServletRequest): String {
        loginService.loginWithAccount(request, security.consumeEmailRecoveryLogin(token))
        return "redirect:/account"
    }
}
