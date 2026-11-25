/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.kkutuio.kkutuweb.login

import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.extension.getOAuthUser
import me.kkutuio.kkutuweb.oauth.AuthVendor
import me.kkutuio.kkutuweb.SessionAttribute
import me.kkutuio.kkutuweb.view.View
import me.kkutuio.kkutuweb.view.Views.getView
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession

@Controller
@RequestMapping("/login")
class LoginController(
    @Autowired private val loginService: LoginService,
    @Autowired private val loginRedirect: LoginRedirect
) {
    private val logger = LoggerFactory.getLogger(LoginController::class.java)

    /**
     * `redirect` 는 로그인한 뒤에 돌아갈 곳이다(이 사이트의 경로 또는 `*.kkutu.io`). 이미 로그인했으면 바로 보낸다.
     * 받을 수 없는 주소는 무시하고 로그인 화면만 보여 준다.
     */
    @GetMapping
    fun login(
        @RequestParam("redirect", required = false) redirect: String?,
        session: HttpSession
    ): String {
        val target = loginRedirect.validate(redirect)
        if (target != null) {
            if (loginService.accountUuid(session) != null) return "redirect:$target"
            loginRedirect.remember(session, target)
        }
        return "forward:/login.html"
    }

    @GetMapping("/fail")
    fun loginFailed(
        model: Model,
        request: HttpServletRequest
    ): String {
        return "forward:/login/fail.html"
    }

    @GetMapping("/link-account")
    fun linkAccount(session: HttpSession): String {
        return if (session.getAttribute(SessionAttribute.LOGIN_LINK_REQUIRED.attributeName) == true) "forward:/login/link-account.html" else "redirect:/login"
    }

    @GetMapping("/logout")
    fun logout(session: HttpSession): String {
        try {
            session.invalidate()
        } catch (e: Exception) {
        }
        return "redirect:/"
    }

    // Do not let this catch the internal forwards to /login/*.html.  Those
    // forwards must reach the Svelte static bundle, not be interpreted as an
    // unknown OAuth provider and redirected back to /login/fail.
    @GetMapping("/{vendorName:naver|google|kakao|facebook|discord|daldalso|github}")
    fun loginRequest(
        @PathVariable vendorName: String,
        session: HttpSession
    ): String {
        val vendorType = AuthVendor.fromName(vendorName) ?: return "redirect:/login/fail"

        val authorizationUrl = loginService.getAuthorizationUrl(session, vendorType)
            ?: return "redirect:/login/fail"

        return "redirect:$authorizationUrl"
    }

    @GetMapping("/{vendorName:naver|google|kakao|facebook|discord|daldalso|github}/callback")
    fun loginCallback(
        @PathVariable vendorName: String,
        @RequestParam("code", required = false, defaultValue = "") code: String,
        @RequestParam("state", required = false, defaultValue = "") state: String,
        request: HttpServletRequest
    ): String {
        if (code.isEmpty() || state.isEmpty()) return "redirect:/login/fail"
        val vendorType = AuthVendor.fromName(vendorName) ?: return "redirect:/login/fail"

        val loginSuccess = loginService.login(request, vendorType, code, state)
        if (loginSuccess && request.session.getAttribute(SessionAttribute.LOGIN_LINK_REQUIRED.attributeName) == true) {
            return "redirect:/login/link-account"
        }
        if (loginSuccess && loginService.hasPendingSecondFactor(request.session)) {
            return "redirect:/login/mfa"
        }
        if (loginSuccess && loginService.needsSetup(request.session)) {
            return "redirect:/setup"
        }
        if (loginSuccess) {
            val session = request.session
            val oAuthUser = session.getOAuthUser()

            logger.info("[${request.getIp()}] ${oAuthUser.name}(${oAuthUser.vendorId}) 님이 ${vendorType.name} 로그인에 성공했습니다.")
        } else {
            logger.info("[${request.getIp()}] ${request.session.id} 세션에서 ${vendorType.name} 로그인에 실패했습니다.")
        }

        if (!loginSuccess) return "redirect:/login/fail"
        return "redirect:" + (loginRedirect.take(request.session) ?: "/")
    }

    @GetMapping("/mfa")
    fun mfa(): String = "forward:/login/mfa.html"

    /** 비밀번호·패스키 로그인을 마친 화면이 오는 곳. 돌아갈 곳이 있으면 보낸다. */
    @GetMapping("/continue")
    fun continueAfterLogin(session: HttpSession): String = "redirect:" + (loginRedirect.take(session) ?: "/")
}
