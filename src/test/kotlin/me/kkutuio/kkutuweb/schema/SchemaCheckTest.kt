package me.kkutuio.kkutuweb.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant

class SchemaCheckTest {
    private fun e(ord: Int, m: String, kind: String, obj: String, want: Boolean = true) = ExpectedObject(ord, m, kind, obj, want)
    private val now = Instant.EPOCH

    private val db = SchemaSnapshot(
        tables = setOf("public.family_group", "public.users", "privacy_retention.hold"),
        indexes = setOf("family_idx"),
        columns = mapOf("users.exordial" to 500, "users.nick" to 255, "account.subject" to null),
        functions = setOf("tag_fn"),
        constraints = setOf("account_status_check")
    )

    @Test
    fun `a migration whose objects are all there is applied, none is missing, a mix is partial`() {
        val r = evaluate(
            listOf(
                e(0, "a.sql", "table", "family_group"), e(1, "a.sql", "index", "family_idx"),
                e(2, "b.sql", "table", "wiki_binding"),
                e(3, "c.sql", "table", "users"), e(4, "c.sql", "table", "nope")
            ), db, now
        )
        assertEquals(listOf("APPLIED", "MISSING", "PARTIAL"), r.summary.map { it.state })
        assertEquals(listOf("a.sql", "b.sql", "c.sql"), r.summary.map { it.migration })
        assertEquals(listOf("wiki_binding", "nope"), r.problems.map { it.`object` })
    }

    @Test
    fun `an object a migration drops must be gone`() {
        val r = evaluate(listOf(e(0, "d.sql", "table", "users", want = false), e(1, "d.sql", "table", "block_user", want = false)), db, now)
        assertEquals("PARTIAL", r.summary.single().state)
        assertEquals("should be gone, still exists", r.problems.single().problem)
        assertEquals("users", r.problems.single().`object`)
    }

    @Test
    fun `schema-qualified tables are taken as written and bare ones are in public`() {
        val r = evaluate(listOf(e(0, "x", "table", "privacy_retention.hold"), e(1, "x", "table", "privacy_retention.absent"), e(2, "x", "table", "hold")), db, now)
        assertEquals(listOf("privacy_retention.absent", "hold"), r.problems.map { it.`object` })
    }

    @Test
    fun `a column that must be wide is checked by its declared length`() {
        val r = evaluate(
            listOf(e(0, "w", "column_len500", "users.exordial"), e(1, "w", "column_len500", "users.nick"), e(2, "w", "column_len500", "account.subject"), e(3, "w", "column_len500", "users.gone")),
            db, now
        )
        assertEquals(listOf("users.nick", "users.gone"), r.problems.map { it.`object` }) // an unbounded column (no length) is wide enough
    }

    @Test
    fun `the shipped expectation list parses and uses only kinds the check understands`() {
        val list = ObjectMapper().readTree(javaClass.getResourceAsStream("/schema-expectations.json")).toList().map {
            ExpectedObject(it["ord"].asInt(), it["migration"].asString(""), it["kind"].asString(""), it["object"].asString(""), it["want"].asBoolean())
        }
        assertEquals(true, list.size > 100)
        assertEquals(emptySet<String>(), list.map { it.kind }.toSet() - setOf("table", "index", "column", "column_len500", "function", "constraint"))
        evaluate(list, db, now) // must not throw
    }
}
