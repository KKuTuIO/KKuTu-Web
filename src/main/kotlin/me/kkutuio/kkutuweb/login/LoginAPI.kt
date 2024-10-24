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

import me.kkutuio.kkutuweb.setting.OAuthSetting
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/login")
class LoginAPI(
    @Autowired private val oAuthSetting: OAuthSetting
) {
    @GetMapping("/vendor")
    fun vendorList(): List<String> {
        return oAuthSetting.getSetting().entries
            .sortedBy { it.value.order }
            .map { it.key.name.lowercase() }
    }

    @GetMapping("/reason")
    fun getLoginReason(session: HttpSession): String {
        val reason = session.getAttribute("loginReason")?.toString() ?: ""
        session.removeAttribute("loginReason")

        return reason
    }
}