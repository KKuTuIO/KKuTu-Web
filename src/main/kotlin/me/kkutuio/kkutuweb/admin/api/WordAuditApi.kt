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
import me.kkutuio.kkutuweb.admin.domain.WordAuditLog
import me.kkutuio.kkutuweb.admin.service.AdminWordAuditService
import me.kkutuio.kkutuweb.admin.vo.WordAuditLogVO
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/admin/word-audits")
class WordAuditApi(
    @Autowired private val setting: KKuTuSetting,
    @Autowired private val loginService: LoginService,
    @Autowired private val adminWordAuditService: AdminWordAuditService
) {
    private val logger = LoggerFactory.getLogger(WordAuditApi::class.java)

    @GetMapping("/{lang}")
    fun getWordAuditList(
        @PathVariable lang: String,
        @RequestParam(required = true, name = "page") page: Int,
        @RequestParam(required = true, name = "size") pageSize: Int,
        @RequestParam(required = true, name = "sort") sortData: String,
        @RequestParam(required = false, name = "id", defaultValue = "") id: String,
        @RequestParam(required = false, name = "log_type", defaultValue = "") type: String,
        @RequestParam(required = false, name = "word", defaultValue = "") word: String,
        @RequestParam(required = false, name = "old_type", defaultValue = "") oldType: String,
        @RequestParam(required = false, name = "old_mean", defaultValue = "") oldMean: String,
        // @RequestParam(required = false, name = "old_flag", defaultValue = "") oldFlag: String,
        @RequestParam(required = false, name = "old_theme", defaultValue = "") oldTheme: String,
        @RequestParam(required = false, name = "new_type", defaultValue = "") newType: String,
        @RequestParam(required = false, name = "new_mean", defaultValue = "") newMean: String,
        // @RequestParam(required = false, name = "new_flag", defaultValue = "") newFlag: String,
        @RequestParam(required = false, name = "new_theme", defaultValue = "") newTheme: String,
        // @RequestParam(required = false, name = "update_log_ignore", defaultValue = "") updateLogIgnore: String,
        // @RequestParam(required = false, name = "update_log_include_detail", defaultValue = "") updateLogIncludeDetail: String,
        @RequestParam(required = false, name = "admin", defaultValue = "") admin: String,
        request: HttpServletRequest, session: HttpSession
    ): ListResponse<WordAuditLogVO> {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 회원으로부터 단어 관리 로그 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("관리자가 아닌 회원(${sessionProfile.id})으로부터 단어 관리 로그 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val adminSetting = setting.getAdmins().find { it.id == sessionProfile.id }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 관리 로그 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }
        val searchFilters = mapOf(
                "id" to id,
                "type" to type,
                "word" to word,
                "old_type" to oldType,
                "new_type" to newType,
                "old_theme" to oldTheme,
                "new_theme" to newTheme,
                "old_mean" to oldMean,
                "new_mean" to newMean
        )

        val wordListRes = adminWordAuditService.getWordAuditListRes(lang, page, pageSize, sortData, searchFilters)
        logger.info("[${request.getIp()}] ${sessionProfile.id} 님이 단어 관리 로그를 요청했습니다. 언어: $lang / 총 개수: ${wordListRes.totalElements}")

        return wordListRes
    }
}