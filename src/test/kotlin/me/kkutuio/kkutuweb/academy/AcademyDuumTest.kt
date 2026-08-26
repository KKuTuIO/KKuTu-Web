package me.kkutuio.kkutuweb.academy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AcademyDuumTest {
    @Test
    fun `transforms rieul and nieun initials like the game server`() {
        assertEquals("여", AcademyDuum.transform("려"))
        assertEquals("나", AcademyDuum.transform("라"))
        assertEquals("여", AcademyDuum.transform("녀"))
        assertEquals(null, AcademyDuum.transform("가"))
    }

    @Test
    fun `accepts transformed source only for Korean duum rules`() {
        assertTrue(AcademyDuum.connects("려", "여", true, "ko"))
        assertTrue(AcademyDuum.connects("려", "려", true, "ko"))
        assertFalse(AcademyDuum.connects("려", "여", false, "ko"))
        assertFalse(AcademyDuum.connects("려", "여", true, "en"))
    }

    @Test
    fun `snapshot follows forward and reverse connection direction`() {
        val words = listOf(
            AcademyCorpusWord("여름", 10, 0, "", false),
            AcademyCorpusWord("름새", 5, 0, "", false),
            AcademyCorpusWord("새벽", 3, 0, "", false)
        )
        val forward = snapshot(words, AcademyRuleConfig(direction = "FORWARD", duum = true))
        assertEquals(listOf("여름"), forward.connectionWords("려").map { it.word })
        assertEquals("름", forward.destination(words[0]))

        val reverse = snapshot(words, AcademyRuleConfig(direction = "REVERSE", duum = false))
        assertEquals(listOf("여름"), reverse.connectionWords("름").map { it.word })
        assertEquals("여", reverse.destination(words[0]))
    }

    private fun snapshot(words: List<AcademyCorpusWord>, config: AcademyRuleConfig) = AcademyCorpusSnapshot(
        config = config,
        words = words,
        byId = words.associateBy { it.word },
        byStart = words.groupBy { it.startChar },
        byEnd = words.groupBy { it.endChar },
        version = 1
    )
}
