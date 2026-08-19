package me.kkutuio.kkutuweb.identity

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
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
    fun account(session: HttpSession): String = if (accounts.currentAccount(session) == null) "redirect:/login" else "forward:/account.html"
    @GetMapping("/account/recovery") fun recovery(): String = "forward:/account/recovery.html"
    @GetMapping("/account/recovery/login")
    fun recoveryLogin(@RequestParam token: String, request: HttpServletRequest): String {
        loginService.loginWithAccount(request, security.consumeEmailRecoveryLogin(token))
        return "redirect:/account"
    }
}
