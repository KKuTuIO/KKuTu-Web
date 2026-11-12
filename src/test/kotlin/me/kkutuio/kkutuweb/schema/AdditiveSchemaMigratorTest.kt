package me.kkutuio.kkutuweb.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.sql.SQLException

class AdditiveSchemaMigratorTest {
    private fun file(name: String) = ClassPathResource("db/additive/$name").inputStream.use { String(it.readAllBytes(), Charsets.UTF_8) }

    @Test
    fun `every file on the list only creates new tables and indexes`() {
        AdditiveSchemaMigrator.FILES.forEach { name ->
            assertEquals(emptyList<String>(), AdditiveSchemaMigrator.violations(file(name)), name)
            assertTrue(AdditiveSchemaMigrator.tablesOf(file(name)).isNotEmpty(), "$name creates a table")
        }
    }

    @Test
    fun `no file in the folder is left off the list`() {
        val dir = java.io.File(javaClass.getResource("/db/additive")!!.toURI()).list()!!.filter { it.endsWith(".sql") }.sorted()
        assertEquals(dir, AdditiveSchemaMigrator.FILES.sorted())
    }

    @Test
    fun `anything that changes or removes something existing is refused`() {
        listOf(
            "ALTER TABLE users ADD COLUMN x int;",
            "DROP TABLE block_user;",
            "UPDATE users SET money = 0;",
            "DELETE FROM users;",
            "INSERT INTO users VALUES (1);",
            "TRUNCATE users;",
            "CREATE TABLE users (id int);",                          // no IF NOT EXISTS: would fail or hide a difference
            "CREATE TABLE IF NOT EXISTS t (id int); DROP TABLE t;",  // an additive statement does not excuse the one after it
            "DO \$\$ BEGIN DROP TABLE t; END \$\$;"
        ).forEach { sql -> assertTrue(AdditiveSchemaMigrator.violations(sql).isNotEmpty(), sql) }
        // comments and the additive statements themselves are fine
        assertEquals(emptyList<String>(), AdditiveSchemaMigrator.violations("-- DROP TABLE x;\nCREATE TABLE IF NOT EXISTS t (id int);\nCREATE UNIQUE INDEX IF NOT EXISTS i ON t (id);"))
    }

    @Test
    fun `table names are read from the file`() {
        assertEquals(listOf("family_group", "family_group_member", "family_group_application"), AdditiveSchemaMigrator.tablesOf(file("20261109_family.sql")))
        assertEquals(listOf("item_enchant", "item_enchant_log", "item_enchant_instance"), AdditiveSchemaMigrator.tablesOf(file("20261112_item_enchant.sql")))
    }

    private fun migrator(jdbc: JdbcTemplate, enabled: Boolean = true) = AdditiveSchemaMigrator(jdbc, mock(PlatformTransactionManager::class.java), enabled)

    @Test
    fun `switched off, it does nothing`() {
        val jdbc = mock(JdbcTemplate::class.java)
        assertEquals("DISABLED", migrator(jdbc, enabled = false).run().state)
        org.mockito.Mockito.verifyNoInteractions(jdbc)
    }

    @Test
    fun `when everything is already there it changes nothing`() {
        val jdbc = mock(JdbcTemplate::class.java)
        val all = AdditiveSchemaMigrator.FILES.flatMap { AdditiveSchemaMigrator.tablesOf(file(it)) }
        `when`(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String::class.java)).thenReturn(all)
        assertEquals("UP_TO_DATE", migrator(jdbc).run().state)
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).execute(org.mockito.ArgumentMatchers.anyString())
    }

    @Test
    fun `a failure never escapes, a missing privilege or a timeout is only recorded`() {
        val denied = mock(JdbcTemplate::class.java)
        `when`(denied.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String::class.java))
            .thenThrow(org.springframework.jdbc.UncategorizedSQLException("t", "sql", SQLException("permission denied for schema public", "42501")))
        val d = migrator(denied).run()
        assertEquals("DENIED", d.state)
        assertTrue(d.message!!.contains("permission denied"))

        val timeout = mock(JdbcTemplate::class.java)
        `when`(timeout.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String::class.java))
            .thenThrow(org.springframework.jdbc.UncategorizedSQLException("t", "sql", SQLException("canceling statement due to lock timeout", "55P03")))
        assertEquals("LOCK_TIMEOUT", migrator(timeout).run().state)

        val broken = mock(JdbcTemplate::class.java)
        `when`(broken.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String::class.java)).thenThrow(RuntimeException("boom"))
        val b = migrator(broken)
        assertEquals("ERROR", b.run().state)
        assertEquals("ERROR", b.status().state) // and the schema check API shows it
    }
}
