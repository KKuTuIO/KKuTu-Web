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
import me.kkutuio.kkutuweb.admin.api.request.BulkWordAddRequest
import me.kkutuio.kkutuweb.admin.api.request.BulkWordDeleteRequest
import me.kkutuio.kkutuweb.admin.api.request.BulkWordModifyRequest
import me.kkutuio.kkutuweb.admin.api.request.WordEditRequest
import me.kkutuio.kkutuweb.admin.api.request.WordTypoCheckRequest
import me.kkutuio.kkutuweb.admin.api.response.ActionResponse
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.admin.api.response.RestResult
import me.kkutuio.kkutuweb.admin.service.AdminWordService
import me.kkutuio.kkutuweb.admin.vo.WordVO
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import me.kkutuio.kkutuweb.word.WordMatch
import me.kkutuio.kkutuweb.word.WordSearchFilter
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
        @RequestParam(required = false, defaultValue = "LEGACY") wordMatch: String,
        @RequestParam(required = false, defaultValue = "") themes: String,
        @RequestParam(required = false, defaultValue = "false") themeMatchAll: Boolean,
        @RequestParam(required = false, defaultValue = "") types: String,
        @RequestParam(required = false, defaultValue = "") flags: String,
        @RequestParam(required = false, defaultValue = "false") flagMatchAll: Boolean,
        @RequestParam(required = false) minHit: Int?,
        @RequestParam(required = false) maxHit: Int?,
        @RequestParam(required = false) minLength: Int?,
        @RequestParam(required = false) maxLength: Int?,
        @RequestParam(required = false) hasTheme: Boolean?,
        @RequestParam(required = false) hasMeaning: Boolean?,
        @RequestParam(required = false, defaultValue = "false") onlyInjeongWithMeaning: Boolean,
        @RequestParam(required = false, defaultValue = "") createdBy: String,
        @RequestParam(required = false) createdWithinDays: Int?,
        request: HttpServletRequest, session: HttpSession
    ): ListResponse<WordVO> {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 회원으로부터 단어 목록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val accountUuid = loginService.accountUuid(session)
        if (accountUuid == null || !setting.getAdminIds().contains(accountUuid)) {
            logger.warn("관리자가 아닌 회원(${sessionProfile.id})으로부터 단어 목록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val adminSetting = setting.getAdmins().find { it.id == accountUuid }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 목록 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val selectedThemes = csv(themes.ifBlank { theme })
        val searchFilter = WordSearchFilter(
            word = word,
            wordMatch = WordMatch.parse(wordMatch),
            themes = selectedThemes,
            themeMatchAll = themeMatchAll,
            types = csv(types),
            flags = csv(flags).mapNotNull { it.toIntOrNull() },
            flagMatchAll = flagMatchAll,
            minHit = minHit,
            maxHit = maxHit,
            minLength = minLength,
            maxLength = maxLength,
            hasTheme = hasTheme,
            hasMeaning = hasMeaning,
            onlyInjeongWithMeaning = onlyInjeongWithMeaning,
            createdBy = createdBy,
            createdWithinDays = createdWithinDays?.coerceIn(1, 3650)
        )

        val wordListRes = adminWordService.getWordListRes(lang, page, pageSize, sortData, searchFilter)
        logger.info("[${request.getIp()}] ${sessionProfile.id} 님이 단어 목록을 요청했습니다. 언어: $lang / 검색어: $word / 테마: ${selectedThemes.joinToString()} / 총 개수: ${wordListRes.totalElements}")

        return wordListRes
    }

    @PostMapping("/{lang}/typo-check")
    fun checkTypos(
        @PathVariable lang: String,
        @RequestBody typoCheckRequest: WordTypoCheckRequest,
        request: HttpServletRequest,
        session: HttpSession
    ): ActionResponse {
        val adminId = authorizedAdminId(session, "단어 오타 후보 검사")
            ?: return unauthorizedResponse(session)

        val actionResponse = adminWordService.checkTypos(lang, typoCheckRequest)
        logger.info("[${request.getIp()}] $adminId 님이 단어 오타 후보 검사를 요청했습니다. 언어: $lang")
        return actionResponse
    }

    @GetMapping("/{lang}/{word}")
    fun getWord(
        @PathVariable lang: String,
        @PathVariable word: String,
        request: HttpServletRequest, session: HttpSession
    ): ListResponse<WordVO> {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 회원으로부터 단어 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val accountUuid = loginService.accountUuid(session)
        if (accountUuid == null || !setting.getAdminIds().contains(accountUuid)) {
            logger.warn("관리자가 아닌 회원(${sessionProfile.id})으로부터 단어 조회 요청이 차단되었습니다.")
            return ListResponse(0, emptyList())
        }

        val adminSetting = setting.getAdmins().find { it.id == accountUuid }!!
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
            logger.warn("인증되지 않은 회원으로부터 단어 수정 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHENTICATED)
        }

        val accountUuid = loginService.accountUuid(session)
        if (accountUuid == null || !setting.getAdminIds().contains(accountUuid)) {
            logger.warn("관리자가 아닌 회원(${sessionProfile.id})으로부터 단어 수정 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val adminSetting = setting.getAdmins().find { it.id == accountUuid }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 수정 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val actionResponse = adminWordService.editWord(accountUuid, lang, word, wordEditRequest)
        logger.info("[${request.getIp()}] ${accountUuid} 님이 단어를 수정했습니다. 언어: $lang / 단어: $word")

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
            logger.warn("인증되지 않은 회원으로부터 단어 삭제 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHENTICATED)
        }

        val accountUuid = loginService.accountUuid(session)
        if (accountUuid == null || !setting.getAdminIds().contains(accountUuid)) {
            logger.warn("관리자가 아닌 회원(${sessionProfile.id})으로부터 단어 삭제 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val adminSetting = setting.getAdmins().find { it.id == accountUuid }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 삭제 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val actionResponse = adminWordService.deleteWord(
            accountUuid,
            lang,
            word,
            UpdateLogRequest(updateLogIgnore, updateLogIncludeDetail)
        )
        logger.info("[${request.getIp()}] ${accountUuid} 님이 단어를 삭제했습니다. 언어: $lang / 단어: $word")

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
            logger.warn("인증되지 않은 회원으로부터 단어 추가 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHENTICATED)
        }

        val accountUuid = loginService.accountUuid(session)
        if (accountUuid == null || !setting.getAdminIds().contains(accountUuid)) {
            logger.warn("관리자가 아닌 회원(${sessionProfile.id})으로부터 단어 추가 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val adminSetting = setting.getAdmins().find { it.id == accountUuid }!!
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 단어 추가 요청이 차단되었습니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        val actionResponse = adminWordService.addWord(accountUuid, lang, word, wordEditRequest)
        logger.info("[${request.getIp()}] ${accountUuid} 님이 단어를 추가했습니다. 언어: $lang / 단어: $word")

        return actionResponse
    }

    @PostMapping("/{lang}/bulk/preview")
    fun previewBulkAdd(
        @PathVariable lang: String,
        @RequestBody bulkWordAddRequest: BulkWordAddRequest,
        request: HttpServletRequest, session: HttpSession
    ): ActionResponse {
        val adminId = authorizedAdminId(session, "단어 대량 추가 사전 확인")
            ?: return unauthorizedResponse(session)

        val actionResponse = adminWordService.previewBulkAdd(lang, bulkWordAddRequest)
        logger.info("[${request.getIp()}] $adminId 님이 단어 대량 추가 사전 확인을 요청했습니다. 언어: $lang / 입력 개수: ${bulkWordAddRequest.words.size}")
        return actionResponse
    }

    @PostMapping("/{lang}/bulk")
    fun bulkAdd(
        @PathVariable lang: String,
        @RequestBody bulkWordAddRequest: BulkWordAddRequest,
        request: HttpServletRequest, session: HttpSession
    ): ActionResponse {
        val adminId = authorizedAdminId(session, "단어 대량 추가")
            ?: return unauthorizedResponse(session)

        val actionResponse = adminWordService.bulkAdd(adminId, lang, bulkWordAddRequest)
        logger.info("[${request.getIp()}] $adminId 님이 단어 대량 추가를 요청했습니다. 언어: $lang / 입력 개수: ${bulkWordAddRequest.words.size}")
        return actionResponse
    }

    @PostMapping("/{lang}/bulk-delete/preview")
    fun previewBulkDelete(
        @PathVariable lang: String,
        @RequestBody bulkWordDeleteRequest: BulkWordDeleteRequest,
        request: HttpServletRequest, session: HttpSession
    ): ActionResponse {
        val adminId = authorizedAdminId(session, "단어 대량 삭제 사전 확인")
            ?: return unauthorizedResponse(session)

        val actionResponse = adminWordService.previewBulkDelete(lang, bulkWordDeleteRequest)
        logger.info("[${request.getIp()}] $adminId 님이 단어 대량 삭제 사전 확인을 요청했습니다. 언어: $lang / 입력 개수: ${bulkWordDeleteRequest.words.size}")
        return actionResponse
    }

    @PostMapping("/{lang}/bulk-delete")
    fun bulkDelete(
        @PathVariable lang: String,
        @RequestBody bulkWordDeleteRequest: BulkWordDeleteRequest,
        request: HttpServletRequest, session: HttpSession
    ): ActionResponse {
        val adminId = authorizedAdminId(session, "단어 대량 삭제")
            ?: return unauthorizedResponse(session)

        val actionResponse = adminWordService.bulkDelete(adminId, lang, bulkWordDeleteRequest)
        logger.info("[${request.getIp()}] $adminId 님이 단어 대량 삭제를 요청했습니다. 언어: $lang / 입력 개수: ${bulkWordDeleteRequest.words.size}")
        return actionResponse
    }

    @PostMapping("/{lang}/bulk-modify/preview")
    fun previewBulkModify(
        @PathVariable lang: String,
        @RequestBody bulkWordModifyRequest: BulkWordModifyRequest,
        request: HttpServletRequest, session: HttpSession
    ): ActionResponse {
        val adminId = authorizedAdminId(session, "단어 대량 수정 사전 확인")
            ?: return unauthorizedResponse(session)

        val actionResponse = adminWordService.previewBulkModify(lang, bulkWordModifyRequest)
        logger.info("[${request.getIp()}] $adminId 님이 단어 대량 수정 사전 확인을 요청했습니다. 언어: $lang / 입력 개수: ${bulkWordModifyRequest.words.size}")
        return actionResponse
    }

    @PostMapping("/{lang}/bulk-modify")
    fun bulkModify(
        @PathVariable lang: String,
        @RequestBody bulkWordModifyRequest: BulkWordModifyRequest,
        request: HttpServletRequest, session: HttpSession
    ): ActionResponse {
        val adminId = authorizedAdminId(session, "단어 대량 수정")
            ?: return unauthorizedResponse(session)

        val actionResponse = adminWordService.bulkModify(adminId, lang, bulkWordModifyRequest)
        logger.info("[${request.getIp()}] $adminId 님이 단어 대량 수정을 요청했습니다. 언어: $lang / 입력 개수: ${bulkWordModifyRequest.words.size}")
        return actionResponse
    }

    private fun csv(value: String): List<String> = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun authorizedAdminId(session: HttpSession, action: String): String? {
        val sessionProfile = loginService.getSessionProfile(session)
        if (sessionProfile == null) {
            logger.warn("인증되지 않은 회원으로부터 $action 요청이 차단되었습니다.")
            return null
        }
        val accountUuid = loginService.accountUuid(session)
        if (accountUuid == null || !setting.getAdminIds().contains(accountUuid)) {
            logger.warn("관리자가 아닌 회원(${sessionProfile.id})으로부터 $action 요청이 차단되었습니다.")
            return null
        }
        val adminSetting = setting.getAdmins().find { it.id == accountUuid } ?: return null
        if (!adminSetting.privileges.contains(AdminSetting.Privilege.WORD)) {
            logger.warn("기능 권한이 없는 관리자(${sessionProfile.id})로부터 $action 요청이 차단되었습니다.")
            return null
        }
        return accountUuid
    }

    private fun unauthorizedResponse(session: HttpSession): ActionResponse {
        return if (loginService.getSessionProfile(session) == null) {
            ActionResponse.rest(success = false, restResult = RestResult.UNAUTHENTICATED)
        } else {
            ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }
    }
}
