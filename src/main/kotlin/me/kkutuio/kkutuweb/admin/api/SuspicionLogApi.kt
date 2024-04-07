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
import me.kkutuio.kkutuweb.admin.service.SuspicionLogService
import me.kkutuio.kkutuweb.admin.vo.SuspicionLogVO
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
@RequestMapping("/api/admin/suspicion-logs")
class SuspicionLogApi(
    @Autowired private val setting: KKuTuSetting,
    @Autowired private val loginService: LoginService,
    @Autowired private val suspicionLogService: SuspicionLogService
) {
    private val logger = LoggerFactory.getLogger(SuspicionLogApi::class.java)

    @GetMapping
    fun getSuspicionLog(
        @RequestParam(required = true, name = "page") page: Int,
        @RequestParam(required = true, name = "size") pageSize: Int,
        @RequestParam(required = true, name = "sort") sortData: String,
        @RequestParam(required = false, name = "case_id", defaultValue = "") caseId: String,
        @RequestParam(required = false, name = "action", defaultValue = "") action: String,
        @RequestParam(required = false, name = "doubt", defaultValue = "") doubt: String,
        @RequestParam(required = false, name = "user_name", defaultValue = "") userName: String,
        @RequestParam(required = false, name = "user_id", defaultValue = "") userId: String,
        @RequestParam(required = false, name = "user_ip", defaultValue = "") userIp: String,
        @RequestParam(required = false, name = "extra_info", defaultValue = "") extraInfo: String,
        @RequestParam(required = false, name = "reference", defaultValue = "") reference: String,
        request: HttpServletRequest, session: HttpSession
    ): ListResponse<SuspicionLogVO> {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 회원으로부터 정책 위반 기록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("관리자가 아닌 회원(${sessionProfile.id})으로부터 정책 위반 기록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val adminSetting = setting.getAdmins().find { it.id == sessionProfile.id }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.SUSPICION_LOG)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 정책 위반 기록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val searchFilters = mapOf(
            "case_id" to caseId,
            "action" to action,
            "doubt" to doubt,
            "user_id" to userId,
            "user_ip" to userIp,
            "extra_info" to extraInfo,
            "reference" to reference
        )

        val suspicionLogRes = suspicionLogService.getSuspicionLogRes(page, pageSize, sortData, searchFilters)
        logger.info("[${request.getIp()}] ${sessionProfile.id}님이 정책 위반 기록 목록을 요청했습니다. 총 개수: ${suspicionLogRes.totalElements}")

        return suspicionLogRes
    }
}