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

import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.admin.service.ConnectionLogService
import me.kkutuio.kkutuweb.admin.vo.ConnectionLogVO
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/admin/connection-logs")
class ConnectionLogApi(
    @Autowired private val setting: KKuTuSetting,
    @Autowired private val loginService: LoginService,
    @Autowired private val connectionLogService: ConnectionLogService
) {
    private val logger = LoggerFactory.getLogger(ConnectionLogApi::class.java)

    @GetMapping
    fun getConnectionLog(
        @RequestParam(required = true, name = "page") page: Int,
        @RequestParam(required = true, name = "size") pageSize: Int,
        @RequestParam(required = true, name = "sort") sortData: String,
        @RequestParam(required = false, name = "user_id", defaultValue = "") userId: String,
        @RequestParam(required = false, name = "user_name", defaultValue = "") userName: String,
        @RequestParam(required = false, name = "user_ip", defaultValue = "") userIp: String,
        @RequestParam(required = false, name = "channel", defaultValue = "") channel: String,
        @RequestParam(required = false, name = "user_agent", defaultValue = "") userAgent: String,
        @RequestParam(required = false, name = "finger_print_2", defaultValue = "") fingerPrint2: String,
        @RequestParam(required = false, name = "pcid_cookie", defaultValue = "") pcidFromCookie: String,
        @RequestParam(required = false, name = "pcid_localstorage", defaultValue = "") pcidFromLocalStorage: String,
        request: HttpServletRequest, session: HttpSession
    ): ListResponse<ConnectionLogVO> {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 사용자로 부터 접속 로그 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("관리자가 아닌 사용자(${sessionProfile.id})로 부터 접속 로그 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val adminSetting = setting.getAdmins().find { it.id == sessionProfile.id }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.CONNECTION_LOG)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로 부터 접속 로그 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val searchFilters = mapOf(
            "user_id" to userId,
            "user_name" to userName,
            "user_ip" to userIp,
            "channel" to channel,
            "user_agent" to userAgent,
            "finger_print_2" to fingerPrint2,
            "pcid_cookie" to pcidFromCookie,
            "pcid_localstorage" to pcidFromLocalStorage
        )

        val connectionLogRes = connectionLogService.getConnectionLogRes(page, pageSize, sortData, searchFilters)
        logger.info("[${request.getIp()}] ${sessionProfile.id} 님이 접속 로그 목록을 요청했습니다. 총 개수: ${connectionLogRes.totalElements}")

        return connectionLogRes
    }
}