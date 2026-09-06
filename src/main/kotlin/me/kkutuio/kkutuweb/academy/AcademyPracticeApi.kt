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
import me.kkutuio.kkutuweb.extension.getIp
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/academy/practice")
class AcademyPracticeApi(
    private val corpusService: AcademyCorpusService,
    private val graphAnalyzer: AcademyGraphAnalyzer,
    private val rateLimitService: AcademyRateLimitService
) {
    @PostMapping("/answer")
    fun answer(
        @RequestBody body: AcademyPracticeAnswerRequest,
        request: HttpServletRequest
    ): AcademyPracticeAnswerResponse {
        if (!rateLimitService.allowPublic("practice-answer", request.getIp(), 120, 60)) {
            throw AcademyRequestException(429, "RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.")
        }
        val config = corpusService.normalize(body.config)
        val required = body.requiredChar.trim()
        require(required.length == 1) { "현재 글자는 한 글자여야 합니다." }
        val snapshot = corpusService.snapshot(config)
        val graph = graphAnalyzer.graph(config)
        val used = body.usedWords.toSet()
        val candidate = snapshot.byId[body.word.trim()]
        val valid = candidate != null && candidate.word !in used && snapshot.connects(required, candidate)
        if (!valid) {
            val shieldUsed = body.shields > 0
            return AcademyPracticeAnswerResponse(
                accepted = false,
                shieldUsed = shieldUsed,
                message = if (shieldUsed) "보호막으로 실패를 방어했습니다." else "현재 글자에서 사용할 수 없는 단어입니다.",
                move = null,
                nextChallenge = null,
                bestMoves = graphAnalyzer.moveViews(graph, required, used).take(5)
            )
        }
        val move = graphAnalyzer.moveViews(graph, required, used).firstOrNull { it.word == candidate.word }
        val next = snapshot.destination(candidate)
        return AcademyPracticeAnswerResponse(
            accepted = true,
            shieldUsed = false,
            message = if (move?.resultingState == AcademyPositionState.LOSS.name) {
                "좋은 공격입니다. 상대를 패배 상태로 보냈습니다."
            } else "정상적으로 방어했습니다.",
            move = move,
            nextChallenge = next,
            bestMoves = graphAnalyzer.moveViews(graph, required, used).take(5)
        )
    }
}

data class AcademyPracticeAnswerRequest(
    val config: AcademyRuleConfig = AcademyRuleConfig(),
    val requiredChar: String,
    val usedWords: List<String> = emptyList(),
    val word: String,
    val shields: Int = 0
)

data class AcademyPracticeAnswerResponse(
    val accepted: Boolean,
    val shieldUsed: Boolean,
    val message: String,
    val move: AcademyMoveView?,
    val nextChallenge: String?,
    val bestMoves: List<AcademyMoveView>
)
