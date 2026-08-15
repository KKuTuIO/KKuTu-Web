package me.kkutuio.kkutuweb.crossword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CrosswordGeneratorTest {
    @Test
    fun `generated crossword is connected and uses legacy direction format`() {
        val words = listOf("가나다", "다라마", "마바사", "사아자", "자차카", "카타파", "파하가", "나다라", "라마바")
            .mapIndexed { index, word -> CrosswordCandidate(word, 100 - index) }
        val request = CrosswordGenerateRequest(count = 1, width = 8, height = 8, minWords = 4, maxWords = 7, maxWordLength = 3)
        val generated = CrosswordGenerator(Random(7)).generate(words, request)

        assertNotNull(generated)
        generated!!
        assertTrue(generated.entries.size >= 4)
        assertTrue(generated.entries.all { it.dir == 0 || it.dir == 1 })
        assertEquals(generated.entries.size, generated.serialize().split('|').size)
    }

    @Test
    fun `candidate policy applies theme weights exclusions meaning types flags and hit`() {
        val rows = listOf(
            CrosswordCandidate("가나다", 10, setOf("FOOD"), setOf("1"), flag = 0, hasMeaning = true),
            CrosswordCandidate("다라마", 20, setOf("SPORT"), setOf("1"), flag = 2, hasMeaning = true),
            CrosswordCandidate("마바사", 30, setOf("FOOD"), setOf("5"), flag = 8, hasMeaning = true),
            CrosswordCandidate("사아자", 40, setOf("FOOD"), setOf("1"), flag = 0, hasMeaning = false)
        )
        val request = CrosswordGenerateRequest(
            themeWeights = listOf(CrosswordThemeWeight("FOOD", 1.0), CrosswordThemeWeight("SPORT", 3.0)),
            includeTypes = listOf("1"), requireMeaning = true, minHit = 5,
            allowedFlags = listOf(1, 2, 4), popularityBias = 0.0
        )

        val filtered = CrosswordCandidatePolicy.apply(rows, request)
        assertEquals(listOf("가나다", "다라마"), filtered.map { it.word })
        assertEquals(1.0, filtered[0].selectionWeight)
        assertEquals(3.0, filtered[1].selectionWeight)
    }

    @Test
    fun `theme weight is normalized so large themes do not gain probability from candidate count`() {
        val rows = listOf(
            CrosswordCandidate("가나다", 1, setOf("FOOD")),
            CrosswordCandidate("나다라", 1, setOf("FOOD")),
            CrosswordCandidate("다라마", 1, setOf("SPORT"))
        )
        val request = CrosswordGenerateRequest(
            themeWeights = listOf(CrosswordThemeWeight("FOOD", 1.0), CrosswordThemeWeight("SPORT", 1.0)),
            popularityBias = 0.0
        )

        val filtered = CrosswordCandidatePolicy.apply(rows, request)
        assertEquals(1.0, filtered.filter { "FOOD" in it.themes }.sumOf { it.selectionWeight })
        assertEquals(1.0, filtered.filter { "SPORT" in it.themes }.sumOf { it.selectionWeight })
    }
}
