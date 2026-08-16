/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package me.kkutuio.kkutuweb.admin.service

import kotlin.math.ln

object WordTypoChecker {
    private const val MAX_SUGGESTIONS = 3
    private const val MIN_IMPROVEMENT = 0.055

    fun check(words: List<String>, corpus: List<String>): Map<String, List<WordTypoCorrection>> {
        if (words.isEmpty() || corpus.isEmpty()) return emptyMap()
        val model = SyllableLanguageModel(corpus)
        return words.mapNotNull { word ->
            val corrections = corrections(word, model)
            if (corrections.isEmpty()) null else word to corrections
        }.toMap(LinkedHashMap())
    }

    private fun corrections(word: String, model: SyllableLanguageModel): List<WordTypoCorrection> {
        if (word.length < 4) return emptyList()
        val originalScore = model.score(word)
        val candidates = LinkedHashMap<String, WordTypoCorrection>()

        fun offer(candidate: String, start: Int, removed: String, added: String, reason: String, relaxed: Boolean = false) {
            if (candidate == word || candidate.length < 2) return
            val improvement = model.score(candidate) - originalScore
            val threshold = if (relaxed) 0.012 else MIN_IMPROVEMENT
            if (improvement < threshold) return
            val correction = WordTypoCorrection(candidate, start, removed, added, reason, improvement)
            val previous = candidates[candidate]
            if (previous == null || correction.improvement > previous.improvement) candidates[candidate] = correction
        }

        // Repeated title fragments such as "오싹오삭" are especially strong typo signals.
        for (start in word.indices) {
            for (length in 2..6) {
                val secondStart = start + length
                if (secondStart + length > word.length) break
                val first = word.substring(start, secondStart)
                val second = word.substring(secondStart, secondStart + length)
                val differences = first.indices.filter { first[it] != second[it] }
                if (differences.size != 1) continue
                val offset = differences[0]
                offer(
                    word.replaceRange(secondStart, secondStart + length, first),
                    secondStart + offset,
                    second[offset].toString(),
                    first[offset].toString(),
                    "REPEATED_PATTERN_MISMATCH",
                    relaxed = true
                )
                offer(
                    word.replaceRange(start, secondStart, second),
                    start + offset,
                    first[offset].toString(),
                    second[offset].toString(),
                    "REPEATED_PATTERN_MISMATCH",
                    relaxed = true
                )
            }
        }

        word.indices.forEach { index ->
            val removed = word[index].toString()
            val duplicate = (index > 0 && word[index - 1] == word[index]) ||
                (index + 1 < word.length && word[index + 1] == word[index])
            offer(
                word.removeRange(index, index + 1),
                index,
                removed,
                "",
                if (duplicate) "DUPLICATED_SYLLABLE" else "EXTRA_SYLLABLE",
                relaxed = duplicate
            )

            val replacementCharacters = LinkedHashSet<Char>()
            if (index > 0) replacementCharacters.addAll(model.after(word[index - 1]))
            if (index + 1 < word.length) replacementCharacters.addAll(model.before(word[index + 1]))
            if (index > 0 && index + 1 < word.length) {
                replacementCharacters.addAll(model.between(word[index - 1], word[index + 1]))
            }
            replacementCharacters.filter { it != word[index] && hangulComponentDistance(it, word[index]) <= 1 }
                .take(12)
                .forEach { replacement ->
                    offer(
                        word.replaceRange(index, index + 1, replacement.toString()),
                        index,
                        removed,
                        replacement.toString(),
                        "UNUSUAL_SYLLABLE"
                    )
                }

            if (index + 1 < word.length && word[index] != word[index + 1]) {
                val chars = word.toCharArray()
                chars[index] = word[index + 1]
                chars[index + 1] = word[index]
                offer(
                    chars.concatToString(),
                    index,
                    word.substring(index, index + 2),
                    "${word[index + 1]}${word[index]}",
                    "SWAPPED_SYLLABLES"
                )
            }
        }

        for (gap in 1 until word.length) {
            model.between(word[gap - 1], word[gap]).take(8).forEach { inserted ->
                offer(
                    word.substring(0, gap) + inserted + word.substring(gap),
                    gap,
                    "",
                    inserted.toString(),
                    "MISSING_SYLLABLE"
                )
            }
        }

        return candidates.values
            .sortedWith(compareByDescending<WordTypoCorrection> { it.improvement }.thenBy { it.suggestion })
            .take(MAX_SUGGESTIONS)
    }

    private fun hangulComponentDistance(left: Char, right: Char): Int {
        if (left == right) return 0
        val leftCode = left.code - 0xAC00
        val rightCode = right.code - 0xAC00
        if (leftCode !in 0 until 11172 || rightCode !in 0 until 11172) return Int.MAX_VALUE
        val leftParts = intArrayOf(leftCode / 588, leftCode % 588 / 28, leftCode % 28)
        val rightParts = intArrayOf(rightCode / 588, rightCode % 588 / 28, rightCode % 28)
        return leftParts.indices.count { leftParts[it] != rightParts[it] }
    }

    private class SyllableLanguageModel(corpus: List<String>) {
        private val bigrams = HashMap<String, Int>()
        private val trigrams = HashMap<String, Int>()
        private val before = HashMap<Char, HashMap<Char, Int>>()
        private val after = HashMap<Char, HashMap<Char, Int>>()
        private val between = HashMap<String, HashMap<Char, Int>>()

        init {
            corpus.asSequence().filter { it.length >= 2 }.forEach { word ->
                for (index in 0 until word.length - 1) {
                    val left = word[index]
                    val right = word[index + 1]
                    bigrams.merge(word.substring(index, index + 2), 1, Int::plus)
                    after.getOrPut(left) { HashMap() }.merge(right, 1, Int::plus)
                    before.getOrPut(right) { HashMap() }.merge(left, 1, Int::plus)
                }
                for (index in 0 until word.length - 2) {
                    val left = word[index]
                    val middle = word[index + 1]
                    val right = word[index + 2]
                    trigrams.merge(word.substring(index, index + 3), 1, Int::plus)
                    between.getOrPut("$left$right") { HashMap() }.merge(middle, 1, Int::plus)
                }
            }
        }

        fun score(word: String): Double {
            var score = 0.0
            var weight = 0.0
            for (index in 0 until word.length - 1) {
                score += ln(1.0 + (bigrams[word.substring(index, index + 2)] ?: 0))
                weight += 1.0
            }
            for (index in 0 until word.length - 2) {
                score += 1.7 * ln(1.0 + (trigrams[word.substring(index, index + 3)] ?: 0))
                weight += 1.7
            }
            return if (weight == 0.0) 0.0 else score / weight
        }

        fun before(right: Char): List<Char> = top(before[right])
        fun after(left: Char): List<Char> = top(after[left])
        fun between(left: Char, right: Char): List<Char> = top(between["$left$right"])

        private fun top(values: Map<Char, Int>?): List<Char> = values.orEmpty().entries
            .sortedByDescending { it.value }
            .take(16)
            .map { it.key }
    }
}

data class WordTypoCorrection(
    val suggestion: String,
    val start: Int,
    val removed: String,
    val added: String,
    val reason: String,
    val improvement: Double
)
