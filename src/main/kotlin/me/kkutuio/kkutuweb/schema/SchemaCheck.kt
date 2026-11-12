package me.kkutuio.kkutuweb.schema

import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import jakarta.servlet.http.HttpSession

/** One object a database migration leaves behind (or, when [want] is false, removes). */
data class ExpectedObject(val ord: Int, val migration: String, val kind: String, val `object`: String, val want: Boolean)

/** What the database has now, read from the system catalogs. Column keys are `table.column`; the value is the declared max length, or null. */
data class SchemaSnapshot(
    val tables: Set<String>,
    val indexes: Set<String>,
    val columns: Map<String, Int?>,
    val functions: Set<String>,
    val constraints: Set<String>
)

data class MigrationState(val migration: String, val objects: Int, val asExpected: Int, val state: String)
data class SchemaProblem(val migration: String, val kind: String, val `object`: String, val problem: String)
data class SchemaCheckResult(val checkedAt: String, val summary: List<MigrationState>, val problems: List<SchemaProblem>)

/** `varchar` columns the new clients rely on being at least this wide (see the `column_len500` kind). */
private const val WIDE_COLUMN = 500

internal fun isPresent(e: ExpectedObject, db: SchemaSnapshot): Boolean = when (e.kind) {
    // a bare name is in `public`; a `schema.name` is taken as written
    "table" -> if ('.' in e.`object`) e.`object` in db.tables else "public.${e.`object`}" in db.tables
    "index" -> e.`object` in db.indexes
    "column" -> e.`object` in db.columns
    "column_len500" -> db.columns.containsKey(e.`object`) && (db.columns[e.`object`] == null || db.columns[e.`object`]!! >= WIDE_COLUMN)
    "function" -> e.`object` in db.functions
    "constraint" -> e.`object` in db.constraints
    else -> throw IllegalArgumentException("unknown kind ${e.kind}")
}

/** Pure: which migrations are in the database. A migration is APPLIED when every object is as it leaves it, MISSING when none is, PARTIAL otherwise. */
internal fun evaluate(expected: List<ExpectedObject>, db: SchemaSnapshot, now: Instant = Instant.now()): SchemaCheckResult {
    val checked = expected.sortedBy { it.ord }.map { it to (isPresent(it, db) == it.want) }
    val summary = checked.groupBy { it.first.migration }.map { (migration, items) ->
        val ok = items.count { it.second }
        MigrationState(migration, items.size, ok, when (ok) { items.size -> "APPLIED"; 0 -> "MISSING"; else -> "PARTIAL" })
    }
    val problems = checked.filterNot { it.second }.map { (e, _) ->
        SchemaProblem(e.migration, e.kind, e.`object`, if (e.want) "should exist, is missing" else "should be gone, still exists")
    }
    return SchemaCheckResult(now.toString(), summary, problems)
}

/**
 * Tells which database migrations are already in the production database, without anyone needing database access. It only reads the system
 * catalogs (nothing is created, changed or dropped). For administrators who may read the API docs (`API_ACCESS`); call it from the back office's API docs page.
 */
@RestController
@RequestMapping("/api/admin/schema")
class SchemaCheckApi(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val authorizer: AdminModerationAuthorizer
) {
    private val expected: List<ExpectedObject> by lazy {
        ClassPathResource("schema-expectations.json").inputStream.use { stream ->
            objectMapper.readTree(stream).toList().map {
                ExpectedObject(it["ord"].asInt(), it["migration"].asString(""), it["kind"].asString(""), it["object"].asString(""), it["want"].asBoolean())
            }
        }
    }

    @GetMapping("/check")
    fun check(session: HttpSession): SchemaCheckResult {
        authorizer.require(session, AdminSetting.Privilege.API_ACCESS)
        return try {
            evaluate(expected, snapshot())
        } catch (error: DataAccessException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "스키마를 읽을 수 없습니다.")
        }
    }

    private fun snapshot() = SchemaSnapshot(
        tables = jdbc.queryForList(
            "SELECT table_schema || '.' || table_name FROM information_schema.tables WHERE table_schema IN ('public', 'privacy_retention')", String::class.java
        ).filterNotNull().toSet(),
        indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String::class.java).filterNotNull().toSet(),
        columns = jdbc.query(
            "SELECT table_name || '.' || column_name AS k, character_maximum_length AS len FROM information_schema.columns WHERE table_schema = 'public'"
        ) { rs, _ -> rs.getString("k") to rs.getObject("len")?.let { (it as Number).toInt() } }.toMap(),
        functions = jdbc.queryForList(
            "SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public'", String::class.java
        ).filterNotNull().toSet(),
        constraints = jdbc.queryForList("SELECT conname FROM pg_constraint", String::class.java).filterNotNull().toSet()
    )
}
