package me.kkutuio.kkutuweb.configmanagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConfigJsonTest {
    private val sentinel = ConfigJson.SECRET_SENTINEL

    private val kkutu = """
        {
          "version": "3.0.0",
          "maxPlayers": 400,
          "gameServers": [
            { "name": "a", "isSecure": true, "publicHost": "h", "key": "SERVER-KEY", "host": "127.0.0.1", "port": 8080, "cid": 1,
              "management": { "sshTarget": "t", "workingDirectory": "/srv", "pm2ProcessName": "p" } }
          ],
          "serviceMode": "NORMAL",
          "adminMasters": ["uuid-1"],
          "cdnHost": "cdn", "apiKey": "API-KEY", "cryptoKey": "CRYPTO-KEY",
          "mainPage": { "status": false, "title": "t" }
        }
    """.trimIndent()

    @Test
    fun `secret-looking keys are detected and ordinary keys are not`() {
        listOf("PG_PASS", "CAPTCHA_SECRET_KEY", "apiKey", "cryptoKey", "PRIVKEY", "DISCORD_WEBHOOK", "accessToken", "WEB_KEY", "apiKeys").forEach { assertTrue(ConfigJson.isSecretKey(it), it) }
        listOf("PG_USER", "PG_PORT", "name", "keyword", "maxPlayers", "serviceMode", "title").forEach { assertFalse(ConfigJson.isSecretKey(it), it) }
    }

    @Test
    fun `masking hides secrets but keeps protected values visible and leaves the original untouched`() {
        val original = ConfigJson.parse(kkutu)
        val masked = ConfigJson.masked("kkutu.json", original)
        assertEquals(sentinel, masked.get("apiKey").stringValue())
        assertEquals(sentinel, masked.get("gameServers").get(0).get("key").stringValue())
        assertEquals("uuid-1", masked.get("adminMasters").get(0).stringValue())
        assertEquals("t", masked.get("gameServers").get(0).get("management").get("sshTarget").stringValue())
        assertEquals("API-KEY", original.get("apiKey").stringValue())
        assertFalse(ConfigJson.pretty(masked).contains("API-KEY"))
    }

    @Test
    fun `an untouched masked document restores every secret and accepts ordinary edits`() {
        val original = ConfigJson.parse(kkutu)
        val edited = ConfigJson.masked("kkutu.json", original).also { (it as tools.jackson.databind.node.ObjectNode).put("maxPlayers", 500) }
        val merged = ConfigJson.verifyAndRestore("kkutu.json", original, edited)
        assertEquals("API-KEY", merged.get("apiKey").stringValue())
        assertEquals("SERVER-KEY", merged.get("gameServers").get(0).get("key").stringValue())
        assertEquals(500, merged.get("maxPlayers").intValue())
        assertEquals(listOf("/maxPlayers"), ConfigJson.changedPointers(original, merged))
    }

    private fun rejected(file: String, original: String, submittedText: String): List<String> =
        assertThrows<ConfigRejectedException> { ConfigJson.verifyAndRestore(file, ConfigJson.parse(original), ConfigJson.parse(submittedText)) }.problems

    private fun maskedText(original: String, file: String = "kkutu.json") = ConfigJson.pretty(ConfigJson.masked(file, ConfigJson.parse(original)))

    @Test
    fun `changing deleting or adding a secret is rejected`() {
        val masked = maskedText(kkutu)
        assertTrue(rejected("kkutu.json", kkutu, masked.replaceFirst("\"$sentinel\"", "\"NEW\"")).single().contains("비밀 값은 수정할 수 없습니다"))
        assertTrue(rejected("kkutu.json", kkutu, masked.replaceFirst("\"apiKey\": \"$sentinel\",", "")).single().contains("지울 수 없습니다: /apiKey"))
        assertTrue(rejected("kkutu.json", kkutu, masked.replaceFirst("\"version\"", "\"newToken\": \"x\",\n  \"version\"")).single().contains("추가할 수 없습니다: /newToken"))
    }

    @Test
    fun `protected kkutu entries cannot be edited`() {
        val masked = maskedText(kkutu)
        assertTrue(rejected("kkutu.json", kkutu, masked.replace("uuid-1", "uuid-evil")).single().contains("보호된 항목은 수정할 수 없습니다: /adminMasters"))
        assertTrue(rejected("kkutu.json", kkutu, masked.replace("/srv", "/tmp")).single().contains("/gameServers/0/management"))
    }

    @Test
    fun `the same key outside kkutu json is not treated as protected`() {
        val doc = """{"adminMasters": ["x"], "name": "n"}"""
        val merged = ConfigJson.verifyAndRestore("games.json", ConfigJson.parse(doc), ConfigJson.parse("""{"adminMasters": ["y"], "name": "n"}"""))
        assertEquals("y", merged.get("adminMasters").get(0).stringValue())
    }

    @Test
    fun `type changes are reported but null may become a value`() {
        val original = ConfigJson.parse("""{"PORT": 5432, "ON": true, "NAME": "x", "LATER": null, "OBJ": {"A": 1}, "LIST": [1]}""")
        val edited = ConfigJson.parse("""{"PORT": "5432", "ON": "true", "NAME": "y", "LATER": 3, "OBJ": [], "LIST": ["a"]}""")
        val problems = ConfigJson.typeChanges(original, edited)
        assertEquals(3, problems.size, problems.toString())
        assertTrue(problems.any { it.startsWith("/PORT") } && problems.any { it.startsWith("/ON") } && problems.any { it.startsWith("/OBJ") })
    }

    @Test
    fun `web structure validation mirrors what the web server reads at startup`() {
        assertEquals(emptyList<String>(), ConfigJson.validateWebStructure("kkutu.json", ConfigJson.parse(kkutu)))
        assertTrue(ConfigJson.validateWebStructure("kkutu.json", ConfigJson.parse(kkutu.replace("\"maxPlayers\": 400,", "\"maxPlayers\": \"400\","))).isNotEmpty())
        assertTrue(ConfigJson.validateWebStructure("kkutu.json", ConfigJson.parse(kkutu.replace("\"port\": 8080", "\"port\": \"x\""))).any { it.contains("gameServers[0].port") })
        assertTrue(ConfigJson.validateWebStructure("kkutu.json", ConfigJson.parse("[]")).isNotEmpty())
        for (name in listOf("games", "moremi", "themes")) {
            val text = javaClass.getResource("/$name.default.json")!!.readText()
            assertEquals(emptyList<String>(), ConfigJson.validateWebStructure("$name.json", ConfigJson.parse(text)), name)
        }
        assertTrue(ConfigJson.validateWebStructure("games.json", ConfigJson.parse("""{"RULE": {}, "OPTIONS": {"a": {}}}""")).isNotEmpty())
        assertTrue(ConfigJson.validateWebStructure("moremi.json", ConfigJson.parse("""{"moremi": {"parts": [1], "categories": [], "equips": [], "groups": {}}}""")).isNotEmpty())
        assertTrue(ConfigJson.validateWebStructure("themes.json", ConfigJson.parse("""{"word": {"themes": {}}}""")).isNotEmpty())
    }

    @Test
    fun `pretty output matches JSON stringify with two spaces and round trips`() {
        val text = """{"a":[1,2],"b":{},"c":[],"d":"한글 \"q\"","e":null,"f":1.50,"g":{"h":true}}"""
        val node = ConfigJson.parse(text)
        val pretty = ConfigJson.pretty(node)
        assertEquals(
            "{\n  \"a\": [\n    1,\n    2\n  ],\n  \"b\": {},\n  \"c\": [],\n  \"d\": \"한글 \\\"q\\\"\",\n  \"e\": null,\n  \"f\": 1.50,\n  \"g\": {\n    \"h\": true\n  }\n}\n",
            pretty
        )
        assertEquals(node, ConfigJson.parse(pretty))
    }

    @Test
    fun `parsing is strict`() {
        assertThrows<ConfigJsonException> { ConfigJson.parse("") }
        assertThrows<ConfigJsonException> { ConfigJson.parse("{} extra") }
        assertThrows<ConfigJsonException> { ConfigJson.parse("""{"a": 1, "a": 2}""") }
        assertThrows<ConfigJsonException> { ConfigJson.parse("""{"a": 1,}""") }
    }
}
