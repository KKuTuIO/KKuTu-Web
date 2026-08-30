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

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

data class AcademyCorpusSnapshot(
    val config: AcademyRuleConfig,
    val words: List<AcademyCorpusWord>,
    val byId: Map<String, AcademyCorpusWord>,
    val byStart: Map<String, List<AcademyCorpusWord>>,
    val byEnd: Map<String, List<AcademyCorpusWord>>,
    val version: Long
) {
    val direction: AcademyDirection = runCatching { AcademyDirection.valueOf(config.direction) }
        .getOrDefault(AcademyDirection.FORWARD)

    fun source(word: AcademyCorpusWord): String =
        if (direction == AcademyDirection.FORWARD) word.startChar else word.endChar

    fun destination(word: AcademyCorpusWord): String =
        if (direction == AcademyDirection.FORWARD) word.endChar else word.startChar

    fun requiredAfter(word: AcademyCorpusWord): String = destination(word)

    fun connectionWords(required: String): List<AcademyCorpusWord> {
        if (required.isBlank()) return emptyList()
        val index = if (direction == AcademyDirection.FORWARD) byStart else byEnd
        val sources = AcademyDuum.acceptedSources(required, config.duum, config.lang)
        if (sources.size == 1) return index[sources.first()].orEmpty()
        val seen = HashSet<String>()
        return buildList {
            sources.forEach { source ->
                index[source].orEmpty().forEach { word ->
                    if (seen.add(word.word)) add(word)
                }
            }
        }
    }

    fun connects(required: String, word: AcademyCorpusWord): Boolean =
        AcademyDuum.connects(required, source(word), config.duum, config.lang)
}

data class AcademyClientCounterIndex(
    val version: Long,
    val counts: Map<String, Int>
)

private data class AcademyCounterCacheKey(val config: AcademyRuleConfig, val version: Long)

@Service
class AcademyCorpusService(private val academyDao: AcademyDao) {
    private val version = AtomicLong(1)
    private val baseCache = Caffeine.newBuilder()
        .maximumSize(2)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build<String, List<AcademyCorpusWord>>()
    private val snapshotCache = Caffeine.newBuilder()
        .maximumSize(24)
        .expireAfterAccess(Duration.ofMinutes(5))
        .build<AcademyRuleConfig, AcademyCorpusSnapshot>()
    private val counterCache = Caffeine.newBuilder()
        .maximumSize(24)
        .expireAfterAccess(Duration.ofMinutes(10))
        .build<AcademyCounterCacheKey, AcademyClientCounterIndex>()

    fun normalize(raw: AcademyRuleConfig): AcademyRuleConfig {
        val lang = raw.lang.lowercase().takeIf { it == "ko" || it == "en" } ?: "ko"
        val dictionary = runCatching { AcademyDictionaryPreset.valueOf(raw.dictionary.uppercase()) }
            .getOrDefault(AcademyDictionaryPreset.COMBINED).name
        val direction = runCatching { AcademyDirection.valueOf(raw.direction.uppercase()) }
            .getOrDefault(AcademyDirection.FORWARD).name
        val minLength = raw.minLength.coerceIn(1, 64)
        val maxLength = raw.maxLength.coerceIn(minLength, 128)
        return raw.copy(
            lang = lang,
            dictionary = dictionary,
            direction = direction,
            duum = raw.duum && lang == "ko",
            minLength = minLength,
            maxLength = maxLength,
            themes = raw.themes.map(String::trim).filter(String::isNotEmpty).distinct().sorted().take(30),
            excludedThemes = raw.excludedThemes.map(String::trim).filter(String::isNotEmpty).distinct().sorted().take(30),
            excludedWords = raw.excludedWords.map(String::trim).filter(String::isNotEmpty).distinct().sorted().take(1_000)
        )
    }

    fun snapshot(raw: AcademyRuleConfig): AcademyCorpusSnapshot {
        val config = normalize(raw)
        return snapshotCache.get(config) { createSnapshot(config) }!!
    }

    /**
     * Static, cacheable baseline counts for browser-side chain accounting.
     * The browser subtracts already-used words locally; no per-turn count query is needed.
     */
    fun clientCounterIndex(raw: AcademyRuleConfig): AcademyClientCounterIndex {
        val snapshot = snapshot(raw)
        val key = AcademyCounterCacheKey(snapshot.config, snapshot.version)
        return counterCache.get(key) { createClientCounterIndex(snapshot) }!!
    }

    fun refresh(lang: String? = null) {
        if (lang == null) baseCache.invalidateAll() else baseCache.invalidate(lang.lowercase())
        snapshotCache.invalidateAll()
        counterCache.invalidateAll()
        version.incrementAndGet()
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    fun scheduledRefresh() {
        refresh()
    }

    private fun createSnapshot(config: AcademyRuleConfig): AcademyCorpusSnapshot {
        val excludedWords = config.excludedWords.toHashSet()
        val requiredThemes = config.themes.toHashSet()
        val excludedThemes = config.excludedThemes.toHashSet()
        val words = baseWords(config.lang).asSequence()
            .filter { word ->
                when (config.dictionary) {
                    AcademyDictionaryPreset.BASIC.name -> word.flags == 0
                    AcademyDictionaryPreset.STANDARD.name -> word.flags and INJEONG_FLAG == 0
                    else -> word.flags and INJEONG_FLAG == 0 || word.publishedOverride
                }
            }
            .filter { it.word.length in config.minLength..config.maxLength }
            .filter { config.includeLoanword || it.flags and 1 == 0 }
            .filter { config.includeSpaced || it.flags and 4 == 0 }
            .filter { config.includeDialect || it.flags and 8 == 0 }
            .filter { config.includeOld || it.flags and 16 == 0 }
            .filter { config.includeCultural || it.flags and 32 == 0 }
            .filter { config.includeKung || it.flags and 64 == 0 }
            .filter { it.word !in excludedWords }
            .filter { requiredThemes.isEmpty() || it.themes.any(requiredThemes::contains) }
            .filter { excludedThemes.isEmpty() || it.themes.none(excludedThemes::contains) }
            .toList()

        return AcademyCorpusSnapshot(
            config = config,
            words = words,
            byId = words.associateBy(AcademyCorpusWord::word),
            byStart = words.groupBy(AcademyCorpusWord::startChar),
            byEnd = words.groupBy(AcademyCorpusWord::endChar),
            version = version.get()
        )
    }

    private fun createClientCounterIndex(snapshot: AcademyCorpusSnapshot): AcademyClientCounterIndex {
        val sourceIndex = if (snapshot.direction == AcademyDirection.FORWARD) snapshot.byStart else snapshot.byEnd
        val required = linkedSetOf<String>()
        sourceIndex.keys.forEach { actual ->
            required += AcademyDuum.requiredForms(actual, snapshot.config.duum, snapshot.config.lang)
        }
        return AcademyClientCounterIndex(
            version = snapshot.version,
            counts = required.associateWith { snapshot.connectionWords(it).size }
        )
    }

    private fun baseWords(lang: String): List<AcademyCorpusWord> =
        baseCache.get(lang) { academyDao.loadPublicCorpus(lang) }!!
}
