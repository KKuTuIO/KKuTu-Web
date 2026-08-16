/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.admin.api.response.WordTypoCandidate
import me.kkutuio.kkutuweb.admin.api.response.WordTypoSuggestion
import me.kkutuio.kkutuweb.word.WordSpellingData
import java.text.Normalizer

object WordTypoChecker {
    private const val MAX_SUGGESTIONS = 5

    fun check(
        scope: List<WordSpellingData>,
        corpus: List<WordSpellingData>,
        allowInternalWhitespace: Boolean = false
    ): List<WordTypoCandidate> {
        if (scope.isEmpty()) return emptyList()

        val corpusByWord = corpus.associateBy { it.word }
        val scopeByWord = scope.associateBy { it.word }
        val suggestionMap = scope.associate { it.word to linkedMapOf<String, WordTypoSuggestion>() }.toMutableMap()
        val substitutionPatterns = HashMap<String, MutableList<WordSpellingData>>()

        scope.forEach { candidate ->
            candidate.word.indices.forEach { index ->
                substitutionPatterns.getOrPut(pattern(candidate.word, index)) { arrayListOf() }.add(candidate)
            }

            candidate.word.indices.forEach { index ->
                corpusByWord[candidate.word.removeRange(index, index + 1)]?.let { suggestion ->
                    addSuggestion(candidate, suggestion, "EXTRA_CHARACTER", suggestionMap)
                }
            }

            for (index in 0 until candidate.word.length - 1) {
                if (candidate.word[index] == candidate.word[index + 1]) continue
                val swapped = candidate.word.toCharArray().also {
                    val current = it[index]
                    it[index] = it[index + 1]
                    it[index + 1] = current
                }.concatToString()
                corpusByWord[swapped]?.let { suggestion ->
                    addSuggestion(candidate, suggestion, "TRANSPOSED_CHARACTERS", suggestionMap, allowEqualHit = true)
                }
            }
        }

        corpus.forEach { suggestion ->
            suggestion.word.indices.forEach { index ->
                substitutionPatterns[pattern(suggestion.word, index)].orEmpty().forEach { candidate ->
                    if (candidate.word != suggestion.word) {
                        addSuggestion(candidate, suggestion, "DIFFERENT_CHARACTER", suggestionMap)
                    }
                }

                scopeByWord[suggestion.word.removeRange(index, index + 1)]?.let { candidate ->
                    addSuggestion(candidate, suggestion, "MISSING_CHARACTER", suggestionMap)
                }
            }
        }

        return scope.mapNotNull { candidate ->
            val issues = formattingIssues(candidate.word, allowInternalWhitespace)
            val suggestions = suggestionMap[candidate.word].orEmpty().values
                .sortedWith(compareByDescending<WordTypoSuggestion> { it.hit }.thenBy { it.word })
                .take(MAX_SUGGESTIONS)
            if (issues.isEmpty() && suggestions.isEmpty()) null else WordTypoCandidate(
                word = candidate.word,
                hit = candidate.hit,
                issues = issues,
                suggestions = suggestions
            )
        }
    }

    private fun addSuggestion(
        candidate: WordSpellingData,
        suggestion: WordSpellingData,
        reason: String,
        result: MutableMap<String, LinkedHashMap<String, WordTypoSuggestion>>,
        allowEqualHit: Boolean = false
    ) {
        if (candidate.word == suggestion.word) return
        val sufficientlyEstablished = suggestion.hit > candidate.hit &&
            (candidate.hit == 0 || suggestion.hit >= maxOf(5, candidate.hit * 3))
        if (!sufficientlyEstablished && !(allowEqualHit && suggestion.hit >= candidate.hit)) return

        val suggestions = result[candidate.word] ?: return
        val existing = suggestions[suggestion.word]
        if (existing == null || reasonPriority(reason) < reasonPriority(existing.reason)) {
            suggestions[suggestion.word] = WordTypoSuggestion(suggestion.word, suggestion.hit, reason)
        }
    }

    private fun formattingIssues(word: String, allowInternalWhitespace: Boolean): List<String> = buildList {
        if (word != word.trim()) add("LEADING_OR_TRAILING_SPACE")
        if (!allowInternalWhitespace && word.any { it.isWhitespace() }) add("CONTAINS_WHITESPACE")
        if (word.any { Character.isISOControl(it) }) add("CONTROL_CHARACTER")
        if (!Normalizer.isNormalized(word, Normalizer.Form.NFC)) add("NOT_NFC_NORMALIZED")
    }.distinct()

    private fun pattern(word: String, index: Int): String =
        "${word.length}:$index:${word.substring(0, index)}\u0000${word.substring(index + 1)}"

    private fun reasonPriority(reason: String): Int = when (reason) {
        "TRANSPOSED_CHARACTERS" -> 0
        "EXTRA_CHARACTER", "MISSING_CHARACTER" -> 1
        else -> 2
    }
}
