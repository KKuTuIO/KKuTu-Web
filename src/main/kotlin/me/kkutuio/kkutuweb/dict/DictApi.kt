/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package me.kkutuio.kkutuweb.dict

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.academy.AcademyRateLimitService
import me.kkutuio.kkutuweb.academy.AcademyRequestException
import me.kkutuio.kkutuweb.academy.AcademyRestrictedSearchRequest
import me.kkutuio.kkutuweb.academy.AcademyRuleConfig
import me.kkutuio.kkutuweb.academy.AcademyService
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.locale.LocalePropertyLoader
import me.kkutuio.kkutuweb.login.LoginService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

@RestController
class DictApi(
    private val academyService: AcademyService,
    private val academyRateLimitService: AcademyRateLimitService,
    private val loginService: LoginService,
    private val localePropertyLoader: LocalePropertyLoader
) {
    @GetMapping("/dictionary/meta", "/api/dictionary/meta", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getMetadata(): DictionaryMetadata {
        val messages = localePropertyLoader.getMessages(Locale.KOREAN)
        return DictionaryMetadata(
            themes = messages.filterKeys { it.startsWith("word.theme.") }
                .mapKeys { it.key.removePrefix("word.theme.") },
            parts = messages.filterKeys { it.startsWith("word.class.") }
                .mapKeys { it.key.removePrefix("word.class.") }
        )
    }

    /** Legacy exact lookup. It intentionally exposes only the public academy corpus. */
    @GetMapping("/dictionary/{lang}/{word}", "/api/dictionary/{lang}/{word}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getWord(
        @PathVariable word: String,
        @PathVariable lang: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): LegacyDictionaryWord? {
        if (!academyRateLimitService.allowPublic("legacy-word", request.getIp(), 180, 60)) {
            response.status = HttpServletResponse.SC_TOO_MANY_REQUESTS
            return null
        }
        return try {
            val result = academyService.getWord(AcademyRuleConfig(lang = lang, dictionary = "COMBINED"), word)
            LegacyDictionaryWord(result.word, result.mean, result.themes.joinToString(","), result.types.joinToString(","))
        } catch (_: AcademyRequestException) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            null
        }
    }

    /**
     * Legacy wordsheet lookup now means a restricted, token-backed injeong lookup.
     * The public searchable dictionary lives under /api/academy/search.
     */
    @GetMapping("/wordsheet/{lang}/{startChar}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getWords(
        @PathVariable startChar: String,
        @PathVariable lang: String,
        @RequestParam(required = false) mission: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession
    ): List<LegacyDictionaryWord> {
        if (session.isGuest()) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return emptyList()
        }
        val accountUuid = loginService.accountUuid(session)
        if (accountUuid == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return emptyList()
        }
        val remaining = academyRateLimitService.consumeRestricted(
            accountUuid,
            request.getIp(),
            AcademyService.RESTRICTED_DAILY_LIMIT
        )
        if (remaining == null) {
            response.status = HttpServletResponse.SC_TOO_MANY_REQUESTS
            return emptyList()
        }
        return try {
            academyService.restrictedSearch(
                AcademyRestrictedSearchRequest(lang, startChar, mission),
                session,
                remaining
            ).items.map {
                LegacyDictionaryWord(it.word, it.mean, it.themes.joinToString(","), it.types.joinToString(","))
            }
        } catch (error: AcademyRequestException) {
            response.status = error.status
            emptyList()
        } catch (_: IllegalArgumentException) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            emptyList()
        }
    }
}

data class DictionaryMetadata(
    val themes: Map<String, String>,
    val parts: Map<String, String>
)

data class LegacyDictionaryWord(
    val word: String,
    val mean: String,
    val theme: String,
    val type: String
)
