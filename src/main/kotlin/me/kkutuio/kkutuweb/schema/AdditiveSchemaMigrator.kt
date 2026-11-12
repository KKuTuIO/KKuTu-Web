package me.kkutuio.kkutuweb.schema

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** What the start-up schema step did on this boot. `state`: NOT_RUN, DISABLED, UP_TO_DATE, APPLIED, SKIPPED_LOCKED, DENIED, LOCK_TIMEOUT, REFUSED, ERROR. */
data class AutoMigrationStatus(val state: String, val at: String?, val applied: List<String>, val message: String?) {
    companion object { val NOT_RUN = AutoMigrationStatus("NOT_RUN", null, emptyList(), null) }
}

/**
 * Creates the tables a new feature needs when the web service starts, so that nobody needs database access to roll a release out.
 *
 * It is deliberately small:
 * - only the files listed in [FILES] are ever run, and only if they contain nothing but `CREATE TABLE/INDEX IF NOT EXISTS` ([violations]);
 *   a migration that changes or removes anything existing never belongs here (those stay manual);
 * - a file runs only when one of the tables it creates is missing, all of them in one transaction (all or nothing), with a short lock timeout and a
 *   database-wide advisory lock so that two instances starting together cannot collide;
 * - it never stops the service from starting: a missing privilege, a timeout or any other failure is recorded in [status] (shown by the schema check
 *   API) and logged, and the service starts anyway.
 */
@Component
class AdditiveSchemaMigrator(
    private val jdbc: JdbcTemplate,
    private val transactionManager: PlatformTransactionManager,
    @Value("\${kkutu.schema.auto-apply:true}") private val enabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(AdditiveSchemaMigrator::class.java)
    private val last = AtomicReference(AutoMigrationStatus.NOT_RUN)

    fun status(): AutoMigrationStatus = last.get()

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        run()
    }

    fun run(): AutoMigrationStatus {
        val result = try {
            attempt()
        } catch (error: Exception) {
            val root = generateSequence<Throwable>(error) { it.cause }.last()
            val sqlState = (generateSequence<Throwable>(error) { it.cause }.filterIsInstance<SQLException>().firstOrNull())?.sqlState
            val state = when (sqlState) { "42501" -> "DENIED"; "55P03" -> "LOCK_TIMEOUT"; else -> "ERROR" }
            logger.warn("스키마 자동 적용을 마치지 못했습니다 ({}). 서비스는 계속 시작합니다.", state, error)
            AutoMigrationStatus(state, Instant.now().toString(), emptyList(), (root.message ?: error.javaClass.simpleName).take(300))
        }
        last.set(result)
        return result
    }

    private fun attempt(): AutoMigrationStatus {
        if (!enabled) return AutoMigrationStatus("DISABLED", Instant.now().toString(), emptyList(), null)
        val files = FILES.associateWith { load(it) }
        val problems = files.flatMap { (name, sql) -> violations(sql).map { "$name: $it" } }
        if (problems.isNotEmpty()) {
            logger.error("스키마 자동 적용 파일이 허용 범위를 벗어나 실행하지 않습니다: {}", problems)
            return AutoMigrationStatus("REFUSED", Instant.now().toString(), emptyList(), problems.joinToString("; ").take(300))
        }
        val existing = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String::class.java
        ).filterNotNull().toSet()
        val pending = files.filter { (_, sql) -> tablesOf(sql).any { it !in existing } }.keys.toList()
        if (pending.isEmpty()) return AutoMigrationStatus("UP_TO_DATE", Instant.now().toString(), emptyList(), null)

        val applied = TransactionTemplate(transactionManager).execute {
            jdbc.execute("SET LOCAL lock_timeout = '5s'")
            val got = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean::class.java, LOCK_KEY) == true
            if (!got) return@execute null
            pending.forEach { jdbc.execute(files.getValue(it)) }
            pending
        }
        return if (applied == null) AutoMigrationStatus("SKIPPED_LOCKED", Instant.now().toString(), emptyList(), "다른 인스턴스가 적용 중입니다.")
        else {
            logger.info("스키마 자동 적용: {}", applied)
            AutoMigrationStatus("APPLIED", Instant.now().toString(), applied, null)
        }
    }

    private fun load(name: String): String = ClassPathResource("db/additive/$name").inputStream.use { String(it.readAllBytes(), Charsets.UTF_8) }

    companion object {
        /** In apply order. A file is added here only after it is checked to create new tables and nothing else. */
        val FILES = listOf(
            "20261107_tile_roulette.sql",
            "20261108_event_enrollment.sql",
            "20261108_event_invite.sql",
            "20261109_family.sql",
            "20261111_wiki_binding.sql",
            "20261112_item_enchant.sql"
        )

        /** Arbitrary constant: the advisory lock the instances take to run this step one at a time. */
        private const val LOCK_KEY = 7_261_112_001L

        private val comment = Regex("--[^\\n]*")
        private val create = Regex("""^CREATE\s+(UNIQUE\s+)?(TABLE|INDEX)\s+IF\s+NOT\s+EXISTS\s+""", RegexOption.IGNORE_CASE)
        private val tableName = Regex("""CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+"?([A-Za-z0-9_]+)"?""", RegexOption.IGNORE_CASE)

        private fun statements(sql: String): List<String> = sql.replace(comment, "").split(';').map { it.trim() }.filter { it.isNotEmpty() }

        /** Every statement that is not a `CREATE TABLE/INDEX IF NOT EXISTS`, and every `$`-quoted block (a hiding place for anything). */
        internal fun violations(sql: String): List<String> {
            val body = sql.replace(comment, "")
            val found = mutableListOf<String>()
            if ('$' in body) found += "contains a \$-quoted block"
            statements(sql).filterNot { create.containsMatchIn(it) }.forEach { found += "not an additive statement: " + it.take(60).replace(Regex("\\s+"), " ") }
            return found
        }

        internal fun tablesOf(sql: String): List<String> = tableName.findAll(sql.replace(comment, "")).map { it.groupValues[1].lowercase() }.toList()
    }
}
