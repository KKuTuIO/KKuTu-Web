package me.kkutuio.kkutuweb.identity

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import me.kkutuio.kkutuweb.login.LoginService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession

@Controller
class AccountPageController(
    private val accounts: AccountService,
    private val security: AccountSecurityService,
    private val loginService: LoginService
) {
    /** 로그인하고 나면 보려던 화면으로 돌아온다. */
    private fun loginThen(path: String) = "redirect:/login?redirect=$path"

    @GetMapping("/account", "/account/security", "/account/services")
    fun account(request: HttpServletRequest, session: HttpSession): String {
        if (accounts.currentAccount(session) == null) return loginThen(request.requestURI.removePrefix(request.contextPath))
        return if (loginService.needsSetup(session)) "redirect:/setup" else "forward:/account.html"
    }
    @GetMapping("/account/apps")
    fun connectedApplications(session: HttpSession): String = if (accounts.currentAccount(session) == null) loginThen("/account/apps") else "forward:/account/apps.html"
    @GetMapping("/account/sanctions")
    fun sanctions(session: HttpSession): String = if (accounts.currentAccount(session) == null) loginThen("/account/sanctions") else "forward:/account/sanctions.html"
    @GetMapping("/account/profile-delete")
    fun profileDelete(session: HttpSession): String = if (accounts.currentAccount(session) == null) loginThen("/account/profile-delete") else "forward:/account/profile-delete.html"
    @GetMapping("/account/delete")
    fun accountDelete(session: HttpSession): String = if (accounts.currentAccount(session) == null) loginThen("/account/delete") else "forward:/account/delete.html"
    @GetMapping("/account/recovery") fun recovery(): String = "forward:/account/recovery.html"
    @GetMapping("/account/recovery/login")
    fun recoveryLogin(): String = "forward:/account/recovery.html"

    @PostMapping("/account/recovery/login")
    fun completeRecoveryLogin(@RequestParam token: String, request: HttpServletRequest): String {
        loginService.loginWithAccount(request, security.consumeEmailRecoveryLogin(token))
        return "redirect:/account"
    }
}
