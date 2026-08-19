package me.kkutuio.kkutuweb.login

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import javax.servlet.http.HttpSession

@Controller
class LogoutController {
    @GetMapping("/logout")
    fun logout(session: HttpSession): String {
        runCatching { session.invalidate() }
        return "redirect:/"
    }
}
