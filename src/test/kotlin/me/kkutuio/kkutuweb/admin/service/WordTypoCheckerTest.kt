package me.kkutuio.kkutuweb.admin.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordTypoCheckerTest {
    private val corpus = buildList {
        repeat(8) {
            add("오싹오싹한이야기")
            add("오싹오싹요거트꼬마유령")
            add("딸기크레페맛쿠키")
            add("초코딸기맛쿠키")
            add("장곡사철조비로자나불좌상")
            add("철조약사여래좌상")
            add("석조대좌")
        }
    }

    @Test
    fun `finds a mismatch inside a repeated title fragment`() {
        assertSuggests(
            "오싹오삭요거트꼬마유령딸기크레페맛쿠키",
            "오싹오싹요거트꼬마유령딸기크레페맛쿠키"
        )
    }

    @Test
    fun `finds an unusual syllable inside a compound title`() {
        assertSuggests("꼬마유령달기크레페맛쿠키", "꼬마유령딸기크레페맛쿠키")
    }

    @Test
    fun `finds a duplicated boundary syllable`() {
        assertSuggests(
            "장곡사사철조약사여래좌상부석조대좌",
            "장곡사철조약사여래좌상부석조대좌"
        )
    }

    @Test
    fun `does not report a well supported long title`() {
        val result = WordTypoChecker.check(listOf("오싹오싹요거트꼬마유령"), corpus)
        assertTrue(result.isEmpty())
    }

    private fun assertSuggests(word: String, expected: String) {
        val suggestions = WordTypoChecker.check(listOf(word), corpus)[word].orEmpty().map { it.suggestion }
        assertTrue(expected in suggestions, "Expected $expected in $suggestions")
    }
}
