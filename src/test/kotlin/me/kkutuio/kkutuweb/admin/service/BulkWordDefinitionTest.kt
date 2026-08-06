package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.admin.vo.WordDetailVO
import me.kkutuio.kkutuweb.word.Word
import me.kkutuio.kkutuweb.word.WordFlag
import me.kkutuio.kkutuweb.word.WordTheme
import me.kkutuio.kkutuweb.word.WordType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BulkWordDefinitionTest {
    private val samsungMean =
        "＂1＂ 사람의 세 가지 성품.  ＂2＂ 매일 세 번 자신을 반성함.  ＂3＂ 고려 시대의 세 기관.  " +
            "＂4＂ 삼성추국.  ＂5＂ 중국 당나라의 세 기관.  ＂6＂ 발해의 세 기관.  ＂7＂   "

    private val samsung = Word(
        id = "삼성",
        type = "1,1,1,1,1,1,INJEONG",
        mean = samsungMean,
        hit = 5761,
        flag = 0,
        theme = "210,0,0,0,0,0,BRD"
    )

    @Test
    fun `existing definition is detected as a duplicate`() {
        val detail = WordDetailVO(WordType.INJEONG, "", WordTheme.THEME_BRD)

        assertTrue(BulkWordDefinition.missingDetails(samsung, listOf(detail)).isEmpty())
    }

    @Test
    fun `new definition is appended without changing existing serialized data`() {
        val detail = WordDetailVO(WordType.INJEONG, "특수촬영물 속 삼성.", WordTheme.THEME_SFX)

        val updated = BulkWordDefinition.appendDetails(samsung, listOf(WordFlag.INJEONG), listOf(detail))

        assertEquals("1,1,1,1,1,1,INJEONG,INJEONG", updated.type)
        assertEquals("210,0,0,0,0,0,BRD,SFX", updated.theme)
        assertTrue(updated.mean.startsWith(samsungMean))
        assertTrue(updated.mean.endsWith("＂8＂ 특수촬영물 속 삼성.  "))
        assertEquals(5761, updated.hit)
        assertEquals(WordFlag.INJEONG.flag, updated.flag)
    }
}
