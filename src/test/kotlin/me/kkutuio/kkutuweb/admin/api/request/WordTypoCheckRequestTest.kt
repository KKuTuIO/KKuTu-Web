package me.kkutuio.kkutuweb.admin.api.request

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import me.kkutuio.kkutuweb.word.WordFlag

class WordTypoCheckRequestTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `accepts the empty theme value sent by an older admin bundle`() {
        val request = objectMapper.readValue(
            """{"themes":"","word":"오싹오삭"}""",
            WordTypoCheckRequest::class.java
        )

        assertEquals(listOf(""), request.themes)
        assertFalse(request.isValid())
    }

    @Test
    fun `accepts the theme array sent by the current admin bundle`() {
        val request = objectMapper.readValue(
            """{"scope":"THEME","themes":["COK"]}""",
            WordTypoCheckRequest::class.java
        )

        assertEquals(listOf("COK"), request.themes)
        assertEquals(8, request.toSearchFilter().minLength)
        assertEquals(listOf(WordFlag.INJEONG.flag), request.toSearchFilter().flags)
    }
}
