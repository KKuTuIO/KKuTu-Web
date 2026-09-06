/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package me.kkutuio.kkutuweb.academy

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/admin/academy")
class AcademyAdminApi(
    private val academyService: AcademyService,
    private val setting: KKuTuSetting,
    private val loginService: LoginService
) {
    private val logger = LoggerFactory.getLogger(AcademyAdminApi::class.java)

    @GetMapping("/public/{lang}")
    fun list(
        @PathVariable lang: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        session: HttpSession
    ): AcademyPublishedListResponse {
        authorizedAdmin(session)
        validateLang(lang)
        return academyService.listPublished(lang, page, size)
    }

    @PutMapping("/public/{lang}/{word}")
    fun publish(
        @PathVariable lang: String,
        @PathVariable word: String,
        @RequestBody(required = false) body: AcademyPublishRequest?,
        request: HttpServletRequest,
        session: HttpSession
    ): AcademyAdminActionResponse {
        val adminId = authorizedAdmin(session)
        validateLang(lang)
        academyService.publish(lang, word, body ?: AcademyPublishRequest(), adminId)
        logger.info("[${request.getIp()}] $adminId 님이 어인정 단어를 학습 사전에 공개했습니다. $lang/$word")
        return AcademyAdminActionResponse(true, 1)
    }

    @PostMapping("/public/{lang}/bulk")
    fun bulkPublish(
        @PathVariable lang: String,
        @RequestBody body: AcademyBulkPublishRequest,
        request: HttpServletRequest,
        session: HttpSession
    ): AcademyAdminActionResponse {
        val adminId = authorizedAdmin(session)
        validateLang(lang)
        require(body.words.size <= 1_000) { "한 번에 최대 1,000개까지 공개할 수 있습니다." }
        val affected = academyService.bulkPublish(lang, body, adminId)
        logger.info("[${request.getIp()}] $adminId 님이 어인정 단어 $affected 개를 학습 사전에 공개했습니다. 언어=$lang")
        return AcademyAdminActionResponse(true, affected)
    }

    @DeleteMapping("/public/{lang}/{word}")
    fun unpublish(
        @PathVariable lang: String,
        @PathVariable word: String,
        request: HttpServletRequest,
        session: HttpSession
    ): AcademyAdminActionResponse {
        val adminId = authorizedAdmin(session)
        validateLang(lang)
        val deleted = academyService.unpublish(lang, word)
        logger.info("[${request.getIp()}] $adminId 님이 어인정 단어의 학습 사전 공개를 해제했습니다. $lang/$word")
        return AcademyAdminActionResponse(deleted, if (deleted) 1 else 0)
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestParam(required = false) lang: String?,
        request: HttpServletRequest,
        session: HttpSession
    ): AcademyAdminActionResponse {
        val adminId = authorizedAdmin(session)
        lang?.let(::validateLang)
        academyService.refresh(lang)
        logger.info("[${request.getIp()}] $adminId 님이 단어 아카데미 캐시를 갱신했습니다. 언어=${lang ?: "all"}")
        return AcademyAdminActionResponse(true, 0)
    }

    private fun authorizedAdmin(session: HttpSession): String {
        val profile = loginService.getSessionProfile(session)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.")
        val accountUuid = loginService.accountUuid(session)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "통합계정 로그인이 필요합니다.")
        if (accountUuid !in setting.getAdminIds()) {
            logger.warn("관리자가 아닌 회원(${profile.id})의 단어 공개 관리 요청을 차단했습니다.")
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.")
        }
        val admin = setting.getAdmins().find { it.id == accountUuid }
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 설정을 찾을 수 없습니다.")
        if (AdminSetting.Privilege.WORD !in admin.privileges) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "단어 관리 권한이 필요합니다.")
        }
        return accountUuid
    }

    private fun validateLang(lang: String) {
        require(lang == "ko" || lang == "en") { "지원하지 않는 언어입니다." }
    }
}

data class AcademyAdminActionResponse(val success: Boolean, val affected: Int)
