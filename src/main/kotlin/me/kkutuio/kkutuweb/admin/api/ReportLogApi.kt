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
import me.kkutuio.kkutuweb.admin.service.ReportLogService
import me.kkutuio.kkutuweb.admin.vo.ReportLogVO
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
@RequestMapping("/api/admin/report-logs")
class ReportLogApi(
    @Autowired private val setting: KKuTuSetting,
    @Autowired private val loginService: LoginService,
    @Autowired private val reportLogService: ReportLogService
) {
    private val logger = LoggerFactory.getLogger(ReportLogApi::class.java)

    @GetMapping
    fun getReportLog(
        @RequestParam(required = true, name = "page") page: Int,
        @RequestParam(required = true, name = "size") pageSize: Int,
        @RequestParam(required = true, name = "sort") sortData: String,
        @RequestParam(required = false, name = "report_id", defaultValue = "") reportId: String,
        @RequestParam(required = false, name = "reporter_id", defaultValue = "") reporterId: String,
        @RequestParam(required = false, name = "reporter_nick", defaultValue = "") reporterNick: String,
        @RequestParam(required = false, name = "target_id", defaultValue = "") targetId: String,
        @RequestParam(required = false, name = "reason", defaultValue = "") reason: String,
        @RequestParam(required = false, name = "file_name", defaultValue = "") fileName: String,
        request: HttpServletRequest, session: HttpSession
    ): ListResponse<ReportLogVO> {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 회원으로부터 정책 위반 기록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("관리자가 아닌 회원(${sessionProfile.id})으로부터 신고 내역 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val adminSetting = setting.getAdmins().find { it.id == sessionProfile.id }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.REPORT_LOG)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 신고 내역 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val searchFilters = mapOf(
            "report_id" to reportId,
            "reporter_id" to reporterId,
            "reporter_nick" to reporterNick,
            "target_id" to targetId,
            "reason" to reason,
            "file_name" to fileName
        )

        val reportLogRes = reportLogService.getReportLogRes(page, pageSize, sortData, searchFilters)
        logger.info("[${request.getIp()}] ${sessionProfile.id}님이 신고 내역 목록을 요청했습니다. 총 개수: ${reportLogRes.totalElements}")

        return reportLogRes
    }
}