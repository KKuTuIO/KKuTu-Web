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

import me.kkutuio.kkutuweb.admin.api.request.UpdateLogRequest
import me.kkutuio.kkutuweb.admin.api.request.WordEditRequest
import me.kkutuio.kkutuweb.admin.api.response.ActionResponse
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.admin.api.response.RestResult
import me.kkutuio.kkutuweb.admin.service.AdminWordService
import me.kkutuio.kkutuweb.admin.vo.WordVO
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
@RequestMapping("/api/admin/words")
class WordApi(
    @Autowired private val setting: KKuTuSetting,
    @Autowired private val loginService: LoginService,
    @Autowired private val adminWordService: AdminWordService
) {
    private val logger = LoggerFactory.getLogger(WordApi::class.java)

    @GetMapping("/{lang}")
    fun getWordList(
        @PathVariable lang: String,
        @RequestParam(required = true, name = "page") page: Int,
        @RequestParam(required = true, name = "size") pageSize: Int,
        @RequestParam(required = true, name = "sort") sortData: String,
        @RequestParam(required = false, defaultValue = "") word: String,
        @RequestParam(required = false, defaultValue = "") theme: String,
        request: HttpServletRequest, session: HttpSession
    ): ListResponse<WordVO> {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 사용자로부터 단어 목록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("관리자가 아닌 사용자(${sessionProfile.id})로부터 단어 목록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val adminSetting = setting.getAdmins().find { it.id == sessionProfile.id }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 목록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val searchFilters = mapOf(
            "_id" to word,
            "theme" to theme
        )

        val wordListRes = adminWordService.getWordListRes(lang, page, pageSize, sortData, searchFilters)
        logger.info("[${request.getIp()}] ${sessionProfile.id} 님이 단어 목록을 요청했습니다. 언어: $lang / 검색어: $word / 테마: $theme / 총 개수: ${wordListRes.totalElements}")

        return wordListRes
    }

    @GetMapping("/{lang}/{word}")
    fun getWord(
        @PathVariable lang: String,
        @PathVariable word: String,
        request: HttpServletRequest, session: HttpSession
    ): ListResponse<WordVO> {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 사용자로부터 단어 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("관리자가 아닌 사용자(${sessionProfile.id})로부터 단어 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val adminSetting = setting.getAdmins().find { it.id == sessionProfile.id }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        logger.info("[${request.getIp()}] ${sessionProfile.id} 님이 단어 정보를 요청했습니다. 언어: $lang / 단어: $word")
        return adminWordService.getWords(lang, word)
    }

    @PatchMapping("/{lang}/{word}")
    fun editWord(
        @PathVariable lang: String,
        @PathVariable word: String,
        @RequestBody wordEditRequest: WordEditRequest,
        request: HttpServletRequest, session: HttpSession
    ): ActionResponse {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 사용자로부터 단어 수정 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHENTICATED)
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("관리자가 아닌 사용자(${sessionProfile.id})로부터 단어 수정 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val adminSetting = setting.getAdmins().find { it.id == sessionProfile.id }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 수정 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val actionResponse = adminWordService.editWord(sessionProfile.id, lang, word, wordEditRequest)
        logger.info("[${request.getIp()}] ${sessionProfile.id} 님이 단어를 수정했습니다. 언어: $lang / 단어: $word")

        return actionResponse
    }

    @DeleteMapping("/{lang}/{word}")
    fun deleteWord(
        @PathVariable lang: String,
        @PathVariable word: String,
        @RequestParam updateLogIgnore: Boolean,
        @RequestParam updateLogIncludeDetail: Boolean,
        request: HttpServletRequest, session: HttpSession
    ): ActionResponse {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 사용자로부터 단어 삭제 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHENTICATED)
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("관리자가 아닌 사용자(${sessionProfile.id})로부터 단어 삭제 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val adminSetting = setting.getAdmins().find { it.id == sessionProfile.id }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 삭제 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val actionResponse = adminWordService.deleteWord(
            sessionProfile.id,
            lang,
            word,
            UpdateLogRequest(updateLogIgnore, updateLogIncludeDetail)
        )
        logger.info("[${request.getIp()}] ${sessionProfile.id} 님이 단어를 삭제했습니다. 언어: $lang / 단어: $word")

        return actionResponse
    }

    @PutMapping("/{lang}/{word}")
    fun addWord(
        @PathVariable lang: String,
        @PathVariable word: String,
        @RequestBody wordEditRequest: WordEditRequest,
        request: HttpServletRequest, session: HttpSession
    ): ActionResponse {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 사용자로부터 단어 추가 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHENTICATED)
        }

        if (!setting.getAdminIds().contains(sessionProfile.id)) {
            logger.warn("관리자가 아닌 사용자(${sessionProfile.id})로부터 단어 추가 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val adminSetting = setting.getAdmins().find { it.id == sessionProfile.id }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 추가 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val actionResponse = adminWordService.addWord(sessionProfile.id, lang, word, wordEditRequest)
        logger.info("[${request.getIp()}] ${sessionProfile.id} 님이 단어를 추가했습니다. 언어: $lang / 단어: $word")

        return actionResponse
    }
}