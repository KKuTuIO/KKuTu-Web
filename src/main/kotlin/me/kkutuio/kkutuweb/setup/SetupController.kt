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

package me.kkutuio.kkutuweb.setup

import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.extension.getOAuthUser
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.oauth.AuthVendor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession

@Controller
@RequestMapping("/setup")
class SetupController(
    @Autowired private val loginService: LoginService,
    @Autowired private val setupService: SetupService,
    @Autowired private val loginRedirect: me.kkutuio.kkutuweb.login.LoginRedirect
) {
    private val logger = LoggerFactory.getLogger(SetupController::class.java)

    @GetMapping
    fun setup(
        request: HttpServletRequest,
        session: HttpSession
    ): String {
        val isGuest = session.isGuest()
        val sessionProfile = loginService.getSessionProfile(session)

        if (isGuest || !setupService.needSetup(sessionProfile!!)) {
            return "redirect:/"
        }

        if (session.getOAuthUser().authVendor == AuthVendor.LOCAL) {
            return "redirect:/account"
        }

        if (!loginService.getOAuthServiceFromSession(session).allowRegister) {
            try {
                session.invalidate()
            } catch (e: Exception) {
            }
            return "redirect:/regFail"
        }

        logger.info("[${request.getIp()}] 초기 설정 화면에 접속했습니다.")
        return "forward:/setup.html"
    }

    /** 신규 설정을 마친 화면이 오는 곳. 로그인 전에 가려던 곳이 있으면 거기로, 없으면 게임으로 보낸다. */
    @GetMapping("/continue")
    fun continueAfterSetup(session: HttpSession): String = "redirect:" + (loginRedirect.take(session) ?: "/game/server/0")
}
