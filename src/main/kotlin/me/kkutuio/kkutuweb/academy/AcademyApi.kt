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
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.locale.LocalePropertyLoader
import me.kkutuio.kkutuweb.login.LoginService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.Locale

@RestController
@RequestMapping("/api/academy", produces = [MediaType.APPLICATION_JSON_VALUE])
class AcademyApi(
    private val academyService: AcademyService,
    private val rateLimitService: AcademyRateLimitService,
    private val loginService: LoginService,
    private val localePropertyLoader: LocalePropertyLoader
) {
    @GetMapping("/meta")
    fun metadata(): AcademyMetaResponse {
        val messages = localePropertyLoader.getMessages(Locale.KOREAN)
        return AcademyMetaResponse(
            dictionaries = listOf(
                AcademyOption("BASIC", "기초 사전", "일반 속성의 기본 단어로 연습합니다."),
                AcademyOption("STANDARD", "표준 사전", "비어인정 단어 전체를 사용합니다."),
                AcademyOption("COMBINED", "통합 공개 사전", "비어인정과 관리자가 공개한 어인정을 사용합니다.")
            ),
            directions = listOf(
                AcademyOption("FORWARD", "끝말잇기", "앞 단어의 마지막 글자로 시작합니다."),
                AcademyOption("REVERSE", "앞말잇기", "앞 단어의 첫 글자로 끝나는 단어를 잇습니다.")
            ),
            botLevels = listOf(
                AcademyOption("RANDOM", "무작위", "가능한 단어 중 무작위로 선택합니다."),
                AcademyOption("BALANCED", "표준", "사용 빈도와 공격력을 함께 고려합니다."),
                AcademyOption("EXPERT", "고수 선택", "승패 상태와 수읽기를 우선합니다.")
            ),
            difficulties = listOf(
                AcademyOption("BEGINNER", "입문", "정답과 충분한 힌트를 제공합니다."),
                AcademyOption("STANDARD", "표준", "15초 제한과 한 번의 보호막을 사용합니다."),
                AcademyOption("EXPERT", "실전", "짧은 제한 시간에 공격 루트를 찾아야 합니다.")
            ),
            themes = messages.filterKeys { it.startsWith("word.theme.") }
                .mapKeys { it.key.removePrefix("word.theme.") },
            parts = messages.filterKeys { it.startsWith("word.class.") }
                .mapKeys { it.key.removePrefix("word.class.") },
            limits = AcademyLimits(
                publicSearchPageSize = AcademyService.PUBLIC_SEARCH_MAX_SIZE,
                analysisExcludedWordLimit = AcademyService.ANALYSIS_EXCLUDED_WORD_LIMIT,
                analysisDepthLimit = AcademyService.ANALYSIS_DEPTH_LIMIT,
                restrictedDailyLimit = AcademyService.RESTRICTED_DAILY_LIMIT,
                restrictedResultLimit = AcademyService.RESTRICTED_RESULT_LIMIT
            )
        )
    }

    @GetMapping("/search")
    fun search(
        @RequestParam(defaultValue = "ko") lang: String,
        @RequestParam(defaultValue = "COMBINED") dictionary: String,
        @RequestParam(defaultValue = "FORWARD") direction: String,
        @RequestParam(defaultValue = "true") duum: Boolean,
        @RequestParam(defaultValue = "2") minLength: Int,
        @RequestParam(defaultValue = "64") maxLength: Int,
        @RequestParam(defaultValue = "true") includeLoanword: Boolean,
        @RequestParam(defaultValue = "true") includeSpaced: Boolean,
        @RequestParam(defaultValue = "true") includeDialect: Boolean,
        @RequestParam(defaultValue = "true") includeOld: Boolean,
        @RequestParam(defaultValue = "true") includeCultural: Boolean,
        @RequestParam(defaultValue = "true") includeKung: Boolean,
        @RequestParam(defaultValue = "") themes: String,
        @RequestParam(defaultValue = "") excludedThemes: String,
        @RequestParam(defaultValue = "") text: String,
        @RequestParam(defaultValue = "CONTAINS") match: String,
        @RequestParam(defaultValue = "") start: String,
        @RequestParam(defaultValue = "") end: String,
        @RequestParam(defaultValue = "") mission: String,
        @RequestParam(defaultValue = "HIT_DESC") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int,
        request: HttpServletRequest
    ): AcademySearchResponse {
        enforcePublicRate("search", request, 120, 60)
        return academyService.search(
            AcademyRuleConfig(
                lang = lang,
                dictionary = dictionary,
                direction = direction,
                duum = duum,
                minLength = minLength,
                maxLength = maxLength,
                includeLoanword = includeLoanword,
                includeSpaced = includeSpaced,
                includeDialect = includeDialect,
                includeOld = includeOld,
                includeCultural = includeCultural,
                includeKung = includeKung,
                themes = csv(themes),
                excludedThemes = csv(excludedThemes)
            ),
            text,
            match,
            start,
            end,
            mission,
            sort,
            page,
            size
        )
    }

    @GetMapping("/word/{lang}/{word}")
    fun word(
        @PathVariable lang: String,
        @PathVariable word: String,
        @RequestParam(defaultValue = "COMBINED") dictionary: String,
        @RequestParam(defaultValue = "FORWARD") direction: String,
        @RequestParam(defaultValue = "true") duum: Boolean,
        request: HttpServletRequest
    ): AcademyWordView {
        enforcePublicRate("word", request, 180, 60)
        return academyService.getWord(
            AcademyRuleConfig(lang = lang, dictionary = dictionary, direction = direction, duum = duum),
            word
        )
    }

    @PostMapping("/analyze")
    fun analyze(@RequestBody body: AcademyAnalysisRequest, request: HttpServletRequest): AcademyAnalysisResponse {
        enforcePublicRate("analyze", request, 20, 60)
        return academyService.analyze(body)
    }

    @PostMapping("/compare")
    fun compare(@RequestBody body: AcademyCompareRequest, request: HttpServletRequest): AcademyCompareResponse {
        enforcePublicRate("compare", request, 10, 60)
        return academyService.compare(body)
    }

    @PostMapping("/strategy")
    fun strategy(@RequestBody body: AcademyStrategyRequest, request: HttpServletRequest): AcademyStrategyResponse {
        enforcePublicRate("strategy", request, 30, 60)
        return academyService.strategy(body)
    }

    @PostMapping("/simulator/step")
    fun simulator(
        @RequestBody body: AcademySimulatorRequest,
        request: HttpServletRequest
    ): AcademySimulatorResponse {
        enforcePublicRate("simulator", request, 120, 60)
        return academyService.simulator(body)
    }

    @PostMapping("/practice/challenge")
    fun practice(
        @RequestBody body: AcademyPracticeRequest,
        request: HttpServletRequest
    ): AcademyPracticeChallenge {
        enforcePublicRate("practice", request, 60, 60)
        return academyService.practice(body)
    }

    @GetMapping("/quiz/daily")
    fun quiz(
        @RequestParam(defaultValue = "0") index: Int,
        request: HttpServletRequest
    ): AcademyQuizQuestion {
        enforcePublicRate("quiz", request, 60, 60)
        return academyService.dailyQuiz(index)
    }

    @PostMapping("/quiz/answer")
    fun answerQuiz(
        @RequestBody body: AcademyQuizAnswerRequest,
        request: HttpServletRequest
    ): AcademyQuizAnswerResponse {
        enforcePublicRate("quiz-answer", request, 120, 60)
        return academyService.answerQuiz(body)
    }

    @PostMapping("/restricted/search")
    fun restricted(
        @RequestBody body: AcademyRestrictedSearchRequest,
        request: HttpServletRequest,
        session: HttpSession
    ): AcademyRestrictedSearchResponse {
        if (session.isGuest()) throw AcademyRequestException(401, "LOGIN_REQUIRED", "로그인 후 이용할 수 있습니다.")
        val accountUuid = loginService.accountUuid(session)
            ?: throw AcademyRequestException(401, "LOGIN_REQUIRED", "통합계정 로그인이 필요합니다.")
        val remaining = rateLimitService.consumeRestricted(
            accountUuid,
            request.getIp(),
            AcademyService.RESTRICTED_DAILY_LIMIT
        ) ?: throw AcademyRequestException(429, "RESTRICTED_LIMIT", "어인정 제한 조회 한도를 초과했거나 보안 저장소를 사용할 수 없습니다.")
        return academyService.restrictedSearch(body, session, remaining)
    }

    private fun enforcePublicRate(
        scope: String,
        request: HttpServletRequest,
        maximum: Int,
        windowSeconds: Long
    ) {
        if (!rateLimitService.allowPublic(scope, request.getIp(), maximum, windowSeconds)) {
            throw AcademyRequestException(429, "RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.")
        }
    }

    private fun csv(value: String): List<String> = value.split(',').map(String::trim).filter(String::isNotEmpty)
}

data class AcademyErrorResponse(val code: String, val message: String)

@RestControllerAdvice(assignableTypes = [AcademyApi::class, AcademyAdminApi::class])
class AcademyExceptionHandler {
    @ExceptionHandler(AcademyRequestException::class)
    fun academyError(error: AcademyRequestException, response: HttpServletResponse): AcademyErrorResponse {
        response.status = error.status
        return AcademyErrorResponse(error.code, error.message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(error: IllegalArgumentException, response: HttpServletResponse): AcademyErrorResponse {
        response.status = 400
        return AcademyErrorResponse("INVALID_REQUEST", error.message ?: "잘못된 요청입니다.")
    }
}
