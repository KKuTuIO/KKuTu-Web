package me.kkutuio.kkutuweb.dict

import me.kkutuio.kkutuweb.word.Word
import me.kkutuio.kkutuweb.word.WordDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class DictServiceTest {
    @Test
    fun `exact lookup reads the complete Korean dictionary and preserves the legacy body`() {
        val wordDao = mock(WordDao::class.java)
        `when`(wordDao.getWords("kkutu_ko", "가나다")).thenReturn(
            listOf(
                Word(
                    id = "가나다",
                    type = "1,2",
                    mean = "첫째 뜻\n둘째 \\\"뜻\\\"",
                    hit = 17,
                    flag = 2,
                    theme = "GAME,ODW"
                )
            )
        )

        val result = DictService(wordDao).getWord("가나다", "ko")

        assertEquals(
            "{\"word\":\"가나다\",\"mean\":\"첫째 뜻\\n둘째 \\\\\\\"뜻\\\\\\\"\",\"theme\":\"GAME,ODW\",\"type\":\"1,2\"}",
            result
        )
        verify(wordDao).getWords("kkutu_ko", "가나다")
    }

    @Test
    fun `exact lookup keeps legacy error objects instead of changing HTTP response semantics`() {
        val wordDao = mock(WordDao::class.java)
        `when`(wordDao.getWords("kkutu_en", "missing")).thenReturn(emptyList())

        val service = DictService(wordDao)

        assertEquals("{\"error\":404}", service.getWord("missing", "en"))
        assertEquals("{\"error\":400}", service.getWord("anything", "jp"))
        verify(wordDao).getWords("kkutu_en", "missing")
    }

    @Test
    fun `prefix lookup preserves the legacy JSON array contract`() {
        val wordDao = mock(WordDao::class.java)
        `when`(wordDao.getWordsFromChar("kkutu_ko", "가", null)).thenReturn(
            listOf(
                Word("가방", "1", "물건을 넣는 도구", 1, 0, ""),
                Word("가위", "1", "자르는 도구", 2, 0, "")
            )
        )

        val result = DictService(wordDao).getWords("가", "ko", null)

        assertEquals(
            "[{\"word\":\"가방\",\"mean\":\"물건을 넣는 도구\",\"theme\":\"\",\"type\":\"1\"}, {\"word\":\"가위\",\"mean\":\"자르는 도구\",\"theme\":\"\",\"type\":\"1\"}]",
            result
        )
        verify(wordDao).getWordsFromChar("kkutu_ko", "가", null)
    }

    @Test
    fun `invalid prefix parameters do not query the database`() {
        val wordDao = mock(WordDao::class.java)
        val service = DictService(wordDao)

        assertEquals("{\"error\":400}", service.getWords("가나", "ko", null))
        assertEquals("{\"error\":400}", service.getWords("가", "ko", "나다"))
        assertEquals("{\"error\":400}", service.getWords("a", "jp", null))
        verifyNoInteractions(wordDao)
    }
}
