package me.kkutuio.kkutuweb.setting

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class EnglishCrosswordModeTest {
    @Test
    fun `English crossword keeps existing mode indices stable`() {
        val rules = jacksonObjectMapper().readTree(File("src/main/resources/games.default.json"))["RULE"]
        val modeIds = rules.fieldNames().asSequence().toList()

        assertEquals(21, modeIds.indexOf("ECW"))
        assertEquals("en", rules["ECW"]["lang"].textValue())
        assertEquals("Crossword", rules["ECW"]["rule"].textValue())
    }
}
