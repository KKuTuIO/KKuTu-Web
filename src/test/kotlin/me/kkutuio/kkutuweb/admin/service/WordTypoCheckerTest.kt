package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.word.WordSpellingData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordTypoCheckerTest {
    @Test
    fun `finds a transposed spelling in the existing dictionary`() {
        val result = WordTypoChecker.check(
            scope = listOf(WordSpellingData("삼전성자", 0)),
            corpus = listOf(
                WordSpellingData("삼전성자", 0),
                WordSpellingData("삼성전자", 80)
            )
        )

        assertEquals("삼전성자", result.single().word)
        assertEquals("삼성전자", result.single().suggestions.single().word)
        assertEquals("TRANSPOSED_CHARACTERS", result.single().suggestions.single().reason)
    }

    @Test
    fun `does not report equally unused one character neighbours`() {
        val result = WordTypoChecker.check(
            scope = listOf(WordSpellingData("가다", 0)),
            corpus = listOf(WordSpellingData("가다", 0), WordSpellingData("나다", 0))
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `reports formatting problems without a suggestion`() {
        val result = WordTypoChecker.check(
            scope = listOf(WordSpellingData(" 잘못된단어 ", 0)),
            corpus = emptyList()
        )

        assertTrue(result.single().issues.contains("LEADING_OR_TRAILING_SPACE"))
        assertTrue(result.single().issues.contains("CONTAINS_WHITESPACE"))
    }

    @Test
    fun `allows internal spaces in languages that support phrases`() {
        val result = WordTypoChecker.check(
            scope = listOf(WordSpellingData("ice cream", 0)),
            corpus = emptyList(),
            allowInternalWhitespace = true
        )

        assertTrue(result.isEmpty())
    }
}
