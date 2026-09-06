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

class AcademyRequestException(
    val status: Int,
    val code: String,
    override val message: String
) : RuntimeException(message)

@Service
class AcademyService(
    private val academyDao: AcademyDao,
    private val injeongDao: AcademyInjeongDao,
    private val corpusService: AcademyCorpusService,
    private val shopService: ShopService
) {
    companion object {
        const val ANALYSIS_EXCLUDED_WORD_LIMIT = 1_000
        const val ANALYSIS_DEPTH_LIMIT = 30
        const val INJEONG_DAILY_LIMIT = 25
        const val INJEONG_RESULT_LIMIT = 20
    }

    fun injeongSearch(
        request: AcademyInjeongSearchRequest,
        session: HttpSession,
        remainingDailyQueries: Int
    ): AcademyInjeongSearchResponse {
        val lang = request.lang.lowercase().takeIf { it == "ko" || it == "en" }
            ?: throw AcademyRequestException(400, "INVALID_LANG", "지원하지 않는 언어입니다.")
        val start = request.startChar?.trim().orEmpty()
        val end = request.endChar?.trim().orEmpty()
        val mission = request.mission?.trim()?.takeIf(String::isNotEmpty)
        require((start.isNotEmpty()) xor (end.isNotEmpty())) { "시작 글자 또는 끝 글자 중 하나를 선택해 주세요." }
        require(start.isEmpty() || start.length == 1) { "시작 글자는 한 글자여야 합니다." }
        require(end.isEmpty() || end.length == 1) { "끝 글자는 한 글자여야 합니다." }
        require(mission == null || mission.length == 1) { "미션 글자는 한 글자여야 합니다." }

        val tokenCost = if (mission == null) 1 else 2
        val payment = shopService.consumeToken(session, tokenCost)
        if (payment.contains("\"error\"")) {
            throw AcademyRequestException(402, "WORD_TOKEN_REQUIRED", "단어 토큰이 부족하거나 사용할 수 없습니다.")
        }

        val records = injeongDao.search(
            lang = lang,
            startChar = start.takeIf(String::isNotEmpty),
            endChar = end.takeIf(String::isNotEmpty),
            mission = mission,
            limit = INJEONG_RESULT_LIMIT
        )
        val config = corpusService.normalize(AcademyRuleConfig(lang = lang, dictionary = "COMBINED"))
        return AcademyInjeongSearchResponse(
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
            attackGrade = attackGrade(defenseCount),
            publishedOverride = record.publishedOverride
        )
    }

    private fun attackGrade(defenseCount: Int): String = when {
        defenseCount == 0 -> "FINISH"
        defenseCount <= 2 -> "VERY_HIGH"
        defenseCount <= 5 -> "HIGH"
        defenseCount <= 15 -> "MEDIUM"
        else -> "LOW"
    }

}
