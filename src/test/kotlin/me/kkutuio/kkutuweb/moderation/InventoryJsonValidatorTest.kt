package me.kkutuio.kkutuweb.moderation

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class InventoryJsonValidatorTest {
    private val mapper = JsonMapper.builder().build()
    private fun json(text: String) = mapper.readTree(text)

    @Test
    fun `accepts real-world box and equip shapes`() {
        InventoryJsonValidator.validateBox(json("""{"8th_name":{"value":1},"blue_name":{"value":3,"expire":1790000000},"legacy":2}"""))
        InventoryJsonValidator.validateBox(json("{}"))
        InventoryJsonValidator.validateEquip(json("""{"NIK":"rainbow_name","Mhead":"8th_conical_hat","BON":"bonusEXPMNY_15"}"""))
        InventoryJsonValidator.validateEquip(json("{}"))
    }

    @Test
    fun `rejects malformed box`() {
        listOf(
            "[]", "\"x\"", """{"a":"text"}""", """{"a":null}""", """{"a":{"value":-1}}""", """{"a":{"value":1.5}}""",
            """{"a":{"expire":10}}""", """{"a":{"value":1,"extra":true}}""", """{"a":{"value":1,"expire":-5}}""",
            """{"a":-1}""", """{"a":2000000000}""", """{"":{"value":1}}""", """{"${"k".repeat(101)}":{"value":1}}""",
        ).forEach { text ->
            assertThrows(IllegalArgumentException::class.java, { InventoryJsonValidator.validateBox(json(text)) }, text)
        }
    }

    @Test
    fun `rejects box with too many items`() {
        val big = (0..InventoryJsonValidator.MAX_BOX_ITEMS).joinToString(",", "{", "}") { "\"i$it\":{\"value\":1}" }
        assertThrows(IllegalArgumentException::class.java) { InventoryJsonValidator.validateBox(json(big)) }
    }

    @Test
    fun `rejects malformed equip`() {
        listOf("[]", """{"NIK":null}""", """{"NIK":5}""", """{"NIK":{"id":"a"}}""", """{"NIK":""}""", """{"":"a"}""", """{"NIK":"a\u0000b"}""").forEach { text ->
            assertThrows(IllegalArgumentException::class.java, { InventoryJsonValidator.validateEquip(json(text)) }, text)
        }
    }
}
