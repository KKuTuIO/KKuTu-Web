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
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@Controller
@RequestMapping("/login")
class LoginController(
    @Autowired private val loginService: LoginService
) {
    private val logger = LoggerFactory.getLogger(LoginController::class.java)

    @GetMapping
    fun login(
        model: Model,
        request: HttpServletRequest
    ): String {
        model.addAttribute("viewName", request.getView(View.REACT))
        return request.getView(View.LAYOUT)
    }

    @GetMapping("/fail")
    fun loginFailed(
        model: Model,
        request: HttpServletRequest
    ): String {
        model.addAttribute("viewName", "view/loginFailed")
        return request.getView(View.LAYOUT)
    }

    @GetMapping("/logout")
    fun logout(session: HttpSession): String {
        try {
            session.invalidate()
        } catch (e: Exception) {
        }
        return "redirect:/"
    }

    @GetMapping("/{vendorName}")
    fun loginRequest(
        @PathVariable vendorName: String,
        session: HttpSession
    ): String {
        val vendorType = AuthVendor.fromName(vendorName) ?: return "redirect:/login/fail"

        return "redirect:" + loginService.getAuthorizationUrl(session, vendorType)
    }

    @GetMapping("/{vendorName}/callback")
    fun loginCallback(
        @PathVariable vendorName: String,
        @RequestParam("code", required = false, defaultValue = "") code: String,
        @RequestParam("state", required = false, defaultValue = "") state: String,
        request: HttpServletRequest
    ): String {
        if (code.isEmpty() || state.isEmpty()) return "redirect:/login/fail"
        val vendorType = AuthVendor.fromName(vendorName) ?: return "redirect:/login/fail"

        val loginSuccess = loginService.login(request, vendorType, code, state)
        if (loginSuccess) {
            val session = request.session
            val oAuthUser = session.getOAuthUser()

            logger.info("[${request.getIp()}] ${oAuthUser.name}(${oAuthUser.vendorId}) 님이 ${vendorType.name} 로그인에 성공했습니다.")
        } else {
            logger.info("[${request.getIp()}] ${request.session.id} 세션에서 ${vendorType.name} 로그인에 실패했습니다.")
        }

        return if (!loginSuccess) "redirect:/login/fail"
        else "redirect:/"
    }
}