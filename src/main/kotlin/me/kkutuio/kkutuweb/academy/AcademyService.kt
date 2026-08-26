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

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.shop.ShopService
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.random.Random

class AcademyRequestException(
    val status: Int,
    val code: String,
    override val message: String
) : RuntimeException(message)

@Service
class AcademyService(
    private val academyDao: AcademyDao,
    private val corpusService: AcademyCorpusService,
    private val graphAnalyzer: AcademyGraphAnalyzer,
    private val shopService: ShopService
) {
    companion object {
        const val PUBLIC_SEARCH_MAX_SIZE = 100
        const val ANALYSIS_EXCLUDED_WORD_LIMIT = 1_000
        const val ANALYSIS_DEPTH_LIMIT = 30
        const val RESTRICTED_DAILY_LIMIT = 25
        const val RESTRICTED_RESULT_LIMIT = 20
    }

    fun search(
        rawConfig: AcademyRuleConfig,
        text: String,
        match: String,
        startChar: String,
        endChar: String,
        mission: String,
        sort: String,
        page: Int,
        size: Int
    ): AcademySearchResponse {
        val config = corpusService.normalize(rawConfig)
        val safeSize = size.coerceIn(1, PUBLIC_SEARCH_MAX_SIZE)
        validateSingleCharacter(startChar, "시작 글자")
        validateSingleCharacter(endChar, "끝 글자")
        validateSingleCharacter(mission, "미션 글자")
        val rows = academyDao.search(
            config,
            AcademySearchQuery(
                text = text.take(100),
                match = match,
                startChar = startChar,
                endChar = endChar,
                mission = mission,
                sort = sort,
                page = page,
                size = safeSize
            )
        )
        val hasNext = rows.size > safeSize
        return AcademySearchResponse(
            items = rows.take(safeSize).map { toView(it, config) },
            page = page.coerceAtLeast(0),
            size = safeSize,
            hasNext = hasNext
        )
    }

    fun getWord(rawConfig: AcademyRuleConfig, word: String): AcademyWordView {
        val config = corpusService.normalize(rawConfig)
        val normalizedWord = word.trim()
        require(normalizedWord.isNotEmpty() && normalizedWord.length <= 128) { "단어가 올바르지 않습니다." }
        val record = academyDao.getVisibleWord(config, normalizedWord)
            ?: throw AcademyRequestException(404, "WORD_NOT_PUBLIC", "공개 단어장에서 확인할 수 없는 단어입니다.")
        return toView(record, config)
    }

    fun analyze(request: AcademyAnalysisRequest): AcademyAnalysisResponse {
        require(request.config.excludedWords.size <= ANALYSIS_EXCLUDED_WORD_LIMIT) {
            "제외 단어는 최대 ${ANALYSIS_EXCLUDED_WORD_LIMIT}개까지 지정할 수 있습니다."
        }
        return graphAnalyzer.analysis(request.copy(config = corpusService.normalize(request.config)))
    }

    fun compare(request: AcademyCompareRequest): AcademyCompareResponse = graphAnalyzer.compare(
        request.copy(
            base = corpusService.normalize(request.base),
            compared = corpusService.normalize(request.compared)
        )
    )

    fun strategy(request: AcademyStrategyRequest): AcademyStrategyResponse {
        require(request.startChar.trim().length == 1) { "시작 음절은 한 글자여야 합니다." }
        return graphAnalyzer.strategy(
            request.copy(
                config = corpusService.normalize(request.config),
                startChar = request.startChar.trim(),
                usedWords = request.usedWords.distinct().take(ANALYSIS_EXCLUDED_WORD_LIMIT),
                depth = request.depth.coerceIn(1, ANALYSIS_DEPTH_LIMIT)
            )
        )
    }

    fun simulator(request: AcademySimulatorRequest): AcademySimulatorResponse {
        val config = corpusService.normalize(request.config)
        val snapshot = corpusService.snapshot(config)
        val candidateText = request.word.trim()
        val chain = request.chain.map(String::trim).filter(String::isNotEmpty).take(2_000)
        val used = chain.toHashSet()
        val previous = chain.lastOrNull()?.let(snapshot.byId::get)
        val required = previous?.let(snapshot::requiredAfter)
        val candidate = snapshot.byId[candidateText]

        fun rejected(code: String, message: String): AcademySimulatorResponse = AcademySimulatorResponse(
            accepted = false,
            code = if (request.shields > 0) "SHIELD_USED" else code,
            message = if (request.shields > 0) "보호막으로 이번 실패를 방어했습니다. $message" else message,
            chain = chain,
            requiredChar = required,
            nextChar = required,
            shieldUsed = request.shields > 0
        )

        if (candidateText.isEmpty()) return rejected("EMPTY_WORD", "단어를 입력해 주세요.")
        if (candidate == null) return rejected(
            "WORD_NOT_PUBLIC",
            "현재 공개 학습 사전에 포함되지 않은 단어입니다."
        )
        if (candidate.word in used) return rejected("DUPLICATED_WORD", "이미 사용한 단어입니다.")
        if (required != null && !snapshot.connects(required, candidate)) {
            return rejected("NOT_CHAINABLE", "‘$required’에서 이어지는 단어가 아닙니다.")
        }

        val graph = graphAnalyzer.graph(config)
        val source = required ?: snapshot.source(candidate)
        val move = graphAnalyzer.moveViews(graph, source, used).firstOrNull { it.word == candidate.word }
        val next = snapshot.destination(candidate)
        val newChain = chain + candidate.word
        val alternatives = graphAnalyzer.moveViews(graph, source, used).filter { it.word != candidate.word }.take(12)
        val botLevel = request.botLevel?.uppercase()?.let {
            runCatching { AcademyBotLevel.valueOf(it) }.getOrNull()
        }
        val botMove = botLevel?.let { graphAnalyzer.pickBotMove(graph, next, newChain.toSet(), it) }
        return AcademySimulatorResponse(
            accepted = true,
            code = "ACCEPTED",
            message = if (botMove == null) "정상적으로 이어졌습니다." else "정상적으로 이어졌습니다. 상대 수를 계산했습니다.",
            chain = newChain,
            requiredChar = required,
            nextChar = next,
            shieldUsed = false,
            analysis = move,
            alternatives = alternatives,
            botMove = botMove
        )
    }

    fun practice(request: AcademyPracticeRequest): AcademyPracticeChallenge {
        val config = corpusService.normalize(request.config)
        val graph = graphAnalyzer.graph(config)
        val difficulty = runCatching { AcademyPracticeDifficulty.valueOf(request.difficulty.uppercase()) }
            .getOrDefault(AcademyPracticeDifficulty.STANDARD)
        val used = request.usedWords.toSet()
        val requested = request.startChar?.trim()?.takeIf { it.length == 1 }
        val candidates = graph.states.keys.asSequence()
            .filter { graph.adjacency[it].orEmpty().any { word -> word.word !in used } }
            .filter { syllable ->
                val moves = graph.adjacency[syllable].orEmpty().count { it.word !in used }
                when (difficulty) {
                    AcademyPracticeDifficulty.BEGINNER -> moves >= 8
                    AcademyPracticeDifficulty.STANDARD -> moves in 3..20
                    AcademyPracticeDifficulty.EXPERT -> moves <= 7 || graph.states[syllable]?.state == AcademyPositionState.WIN
                }
            }
            .toList()
        val required = requested?.takeIf { it in graph.states && graph.adjacency[it].orEmpty().isNotEmpty() }
            ?: candidates.randomOrNull()
            ?: throw AcademyRequestException(404, "NO_CHALLENGE", "이 설정에서 연습 문제를 만들 수 없습니다.")
        val moves = graphAnalyzer.moveViews(graph, required, used)
        val best = moves.firstOrNull()
        val sample = best?.word
        val hint = when (difficulty) {
            AcademyPracticeDifficulty.BEGINNER -> AcademyPracticeHint(
                firstLetter = sample?.take(1),
                length = sample?.length,
                theme = sample?.let { graph.snapshot.byId[it]?.themes?.firstOrNull() },
                sample = sample
            )
            AcademyPracticeDifficulty.STANDARD -> AcademyPracticeHint(
                firstLetter = sample?.take(1),
                length = sample?.length
            )
            AcademyPracticeDifficulty.EXPERT -> AcademyPracticeHint()
        }
        val state = graph.states[required]?.state
        return AcademyPracticeChallenge(
            requiredChar = required,
            difficulty = difficulty.name,
            timeLimitSeconds = when (difficulty) {
                AcademyPracticeDifficulty.BEGINNER -> 0
                AcademyPracticeDifficulty.STANDARD -> 15
                AcademyPracticeDifficulty.EXPERT -> 8
            },
            shieldCount = when (difficulty) {
                AcademyPracticeDifficulty.BEGINNER -> 3
                AcademyPracticeDifficulty.STANDARD -> 1
                AcademyPracticeDifficulty.EXPERT -> 0
            },
            availableMoveCount = moves.size,
            hint = hint,
            objective = when (state) {
                AcademyPositionState.WIN -> "상대가 불리해지는 수를 찾아보세요."
                AcademyPositionState.LOSS -> "가능한 한 오래 버티는 방어 수를 찾아보세요."
                AcademyPositionState.ROUTE -> "루트를 유지하면서 선택지를 줄여보세요."
                null -> "이어지는 단어를 찾아보세요."
            }
        )
    }

    fun dailyQuiz(index: Int): AcademyQuizQuestion = createQuiz(LocalDate.now(), index.coerceIn(0, 9)).first

    fun answerQuiz(request: AcademyQuizAnswerRequest): AcademyQuizAnswerResponse {
        val parts = request.questionId.split(':')
        if (parts.size != 2) throw AcademyRequestException(400, "INVALID_QUIZ", "퀴즈 식별자가 올바르지 않습니다.")
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
            ?: throw AcademyRequestException(400, "INVALID_QUIZ", "퀴즈 날짜가 올바르지 않습니다.")
        val index = parts[1].toIntOrNull()?.coerceIn(0, 9)
            ?: throw AcademyRequestException(400, "INVALID_QUIZ", "퀴즈 번호가 올바르지 않습니다.")
        val (_, expected) = createQuiz(date, index)
        val correct = request.answer.trim() == expected.answer
        return AcademyQuizAnswerResponse(correct, expected.answer, expected.explanation)
    }

    fun restrictedSearch(
        request: AcademyRestrictedSearchRequest,
        session: HttpSession,
        remainingDailyQueries: Int
    ): AcademyRestrictedSearchResponse {
        val lang = request.lang.lowercase().takeIf { it == "ko" || it == "en" }
            ?: throw AcademyRequestException(400, "INVALID_LANG", "지원하지 않는 언어입니다.")
        val start = request.startChar.trim()
        val mission = request.mission?.trim()?.takeIf(String::isNotEmpty)
        require(start.length == 1) { "시작 글자는 한 글자여야 합니다." }
        require(mission == null || mission.length == 1) { "미션 글자는 한 글자여야 합니다." }
        val tokenCost = if (mission == null) 1 else 2
        val payment = shopService.consumeToken(session, tokenCost)
        if (payment.contains("\"error\"")) {
            throw AcademyRequestException(402, "WORD_TOKEN_REQUIRED", "단어 토큰이 부족하거나 사용할 수 없습니다.")
        }
        val records = academyDao.restrictedSearch(lang, start, mission, RESTRICTED_RESULT_LIMIT)
        val config = corpusService.normalize(AcademyRuleConfig(lang = lang, dictionary = "COMBINED"))
        return AcademyRestrictedSearchResponse(
            items = records.map { toView(it, config) },
            consumedTokens = tokenCost,
            remainingDailyQueries = remainingDailyQueries
        )
    }

    fun publish(lang: String, word: String, request: AcademyPublishRequest, adminId: String) {
        academyDao.publish(lang, word, request.reason.ifBlank { "관리자 공개" }, adminId)
        refresh(lang)
    }

    fun bulkPublish(lang: String, request: AcademyBulkPublishRequest, adminId: String): Int {
        val words = request.words.map(String::trim).filter(String::isNotEmpty).distinct().take(1_000)
        words.forEach { academyDao.publish(lang, it, request.reason.ifBlank { "관리자 일괄 공개" }, adminId) }
        refresh(lang)
        return words.size
    }

    fun unpublish(lang: String, word: String): Boolean {
        val deleted = academyDao.unpublish(lang, word)
        if (deleted) refresh(lang)
        return deleted
    }

    fun listPublished(lang: String, page: Int, size: Int): AcademyPublishedListResponse {
        val safeSize = size.coerceIn(1, 200)
        val rows = academyDao.listPublished(lang, page, safeSize)
        return AcademyPublishedListResponse(
            items = rows.take(safeSize),
            page = page.coerceAtLeast(0),
            size = safeSize,
            hasNext = rows.size > safeSize
        )
    }

    fun refresh(lang: String? = null) {
        academyDao.refreshSchemaState()
        corpusService.refresh(lang)
        graphAnalyzer.invalidate()
    }

    private fun toView(record: AcademyWordRecord, config: AcademyRuleConfig): AcademyWordView {
        val snapshot = corpusService.snapshot(config)
        val direction = runCatching { AcademyDirection.valueOf(config.direction) }
            .getOrDefault(AcademyDirection.FORWARD)
        val start = record.word.firstOrNull()?.toString().orEmpty()
        val end = record.word.lastOrNull()?.toString().orEmpty()
        val next = if (direction == AcademyDirection.FORWARD) end else start
        val defenseCount = snapshot.connectionWords(next).size
        return AcademyWordView(
            word = record.word,
            mean = record.mean,
            themes = record.theme.split(',').filter { it.isNotBlank() && it != "0" },
            types = record.type.split(',').filter(String::isNotBlank),
            hit = record.hit,
            flags = record.flags,
            length = record.word.length,
            startChar = start,
            endChar = end,
            nextChar = next,
            defenseCount = defenseCount,
            attackGrade = graphAnalyzer.attackGrade(defenseCount, null),
            publishedOverride = record.publishedOverride
        )
    }

    private data class QuizExpected(val answer: String, val explanation: String)

    private fun createQuiz(date: LocalDate, index: Int): Pair<AcademyQuizQuestion, QuizExpected> {
        val config = corpusService.normalize(AcademyRuleConfig(lang = "ko", dictionary = "STANDARD"))
        val snapshot = corpusService.snapshot(config)
        if (snapshot.words.size < 20) throw AcademyRequestException(503, "QUIZ_UNAVAILABLE", "퀴즈 사전을 준비하지 못했습니다.")
        val random = Random((date.toEpochDay() * 31 + index).toInt())
        val id = "$date:$index"
        return when (index % 3) {
            0 -> {
                val word = snapshot.words[random.nextInt(snapshot.words.size)]
                val correct = snapshot.destination(word)
                val pool = snapshot.words.asSequence()
                    .map(snapshot::destination)
                    .filter { it != correct }
                    .distinct()
                    .toList()
                    .shuffled(random)
                    .take(3)
                AcademyQuizQuestion(
                    questionId = id,
                    date = date.toString(),
                    index = index,
                    type = "NEXT_CHAR",
                    prompt = "‘${word.word}’ 다음에 이어야 하는 글자는 무엇일까요?",
                    options = (pool + correct).shuffled(random),
                    explanationHint = "끝말잇기는 마지막 글자를 이어갑니다."
                ) to QuizExpected(correct, "‘${word.word}’의 마지막 글자는 ‘$correct’입니다.")
            }
            1 -> {
                val word = snapshot.words[random.nextInt(snapshot.words.size)].word
                val fake = linkedSetOf<String>()
                while (fake.size < 3) {
                    val pivot = random.nextInt(word.length)
                    val replacement = ('가'.code + random.nextInt(11172)).toChar()
                    val candidate = word.replaceRange(pivot, pivot + 1, replacement.toString())
                    if (candidate !in snapshot.byId) fake += candidate
                }
                AcademyQuizQuestion(
                    questionId = id,
                    date = date.toString(),
                    index = index,
                    type = "VALID_WORD",
                    prompt = "다음 중 공개 학습 사전에 등록된 단어를 고르세요.",
                    options = (fake + word).shuffled(random),
                    explanationHint = "표준 공개 사전을 기준으로 판정합니다."
                ) to QuizExpected(word, "‘$word’이(가) 공개 학습 사전에 등록된 단어입니다.")
            }
            else -> {
                val graph = graphAnalyzer.graph(config)
                val required = graph.adjacency.entries.filter { it.value.size >= 4 }.random(random).key
                val moves = graphAnalyzer.moveViews(graph, required).take(4)
                val correct = moves.first().word
                AcademyQuizQuestion(
                    questionId = id,
                    date = date.toString(),
                    index = index,
                    type = "BEST_ATTACK",
                    prompt = "‘$required’에서 상대 선택지를 가장 강하게 줄이는 수를 고르세요.",
                    options = moves.map(AcademyMoveView::word).shuffled(random),
                    explanationHint = "상대가 받는 글자의 방어 단어 수를 비교해 보세요."
                ) to QuizExpected(correct, "‘$correct’은(는) 후보 중 가장 유리한 상태로 연결됩니다.")
            }
        }
    }

    private fun validateSingleCharacter(value: String, label: String) {
        require(value.isBlank() || value.trim().length == 1) { "${label}은 한 글자여야 합니다." }
    }
}