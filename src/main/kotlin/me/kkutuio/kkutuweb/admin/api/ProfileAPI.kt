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

package me.kkutuio.kkutuweb.admin.api

import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/admin")
class ProfileAPI(
    @Autowired private val setting: KKuTuSetting,
    @Autowired private val loginService: LoginService
) {
    private val logger = LoggerFactory.getLogger(ProfileAPI::class.java)

    @GetMapping("/profile")
    fun getProfile(request: HttpServletRequest, session: HttpSession): AdminSetting? {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("[${request.getIp()}] 인증되지 않은 회원으로부터 본인 프로필 정보 조회 요청이 차단되었습니다.")
            return null
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("[${request.getIp()}] 권한이 없는 회원(${sessionProfile.id})으로부터 본인 프로필 정보 조회 요청이 차단되었습니다.")
            return null
        }

        return setting.getAdmins().find { it.id == sessionProfile.id }
    }
}