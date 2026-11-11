package me.kkutuio.kkutuweb.wiki

import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class WikiBinding(val accountId: UUID, val wikiUserId: Long, val wikiUsername: String, val boundAt: Instant)
data class WikiChallenge(val accountId: UUID, val wikiUsername: String, val code: String, val createdAt: Instant, val expiresAt: Instant)
data class PendingEdit(val revisionId: Long, val accountId: UUID)

/** What the binding service and the sync need from storage (the DAO below; tests use an in-memory one). */
interface WikiStore {
    fun findBinding(accountId: UUID): WikiBinding?
    fun findBindingByWikiUser(wikiUserId: Long): WikiBinding?
    fun allBindings(): List<WikiBinding>
    fun bind(accountId: UUID, wikiUserId: Long, wikiUsername: String, now: Instant): Boolean
    fun unbind(accountId: UUID): Boolean
    fun saveChallenge(c: WikiChallenge)
    fun findChallenge(accountId: UUID): WikiChallenge?
    fun deleteChallenge(accountId: UUID)
    fun latestEditAt(accountId: UUID): Instant?
    fun addEdits(accountId: UUID, wikiUserId: Long, edits: List<WikiEdit>): Int
    fun pendingOlderThan(cutoff: Instant): List<PendingEdit>
    fun settle(revisionIds: Collection<Long>, now: Instant)
    fun discard(revisionIds: Collection<Long>)
    fun counts(accountId: UUID): Pair<Int, Int>
    fun settledTimes(accountId: UUID, limit: Int): List<Long>
    fun writeFlag(accountId: UUID, settled: Int, edits: List<Long>, nowMillis: Long): Boolean
}

/** Storage of the 리오위키 binding. The tables come from the repo migrations; they are also created (IF NOT EXISTS) the first time they are used. */
@Repository
class WikiDao(private val jdbc: JdbcTemplate) : WikiStore {
    private val logger = LoggerFactory.getLogger(javaClass)
    @Volatile private var ready = false

    @Synchronized
    private fun ensure() {
        if (ready) return
        jdbc.execute("CREATE TABLE IF NOT EXISTS wiki_binding (account_id UUID PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE, wiki_user_id BIGINT NOT NULL UNIQUE, wiki_username TEXT NOT NULL, bound_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        jdbc.execute("CREATE TABLE IF NOT EXISTS wiki_bind_challenge (account_id UUID PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE, wiki_username TEXT NOT NULL, code TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMPTZ NOT NULL)")
        jdbc.execute("CREATE TABLE IF NOT EXISTS wiki_contribution (revision_id BIGINT PRIMARY KEY, account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE, wiki_user_id BIGINT NOT NULL, page_title TEXT NOT NULL, edited_at TIMESTAMPTZ NOT NULL, settled_at TIMESTAMPTZ)")
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_wiki_contribution_account ON wiki_contribution (account_id, settled_at)")
        ready = true
    }

    private fun binding(rs: java.sql.ResultSet) = WikiBinding(UUID.fromString(rs.getString("account_id")), rs.getLong("wiki_user_id"), rs.getString("wiki_username"), rs.getTimestamp("bound_at").toInstant())

    override fun findBinding(accountId: UUID): WikiBinding? { ensure(); return jdbc.query("SELECT * FROM wiki_binding WHERE account_id = ?", { rs, _ -> binding(rs) }, accountId).firstOrNull() }
    override fun findBindingByWikiUser(wikiUserId: Long): WikiBinding? { ensure(); return jdbc.query("SELECT * FROM wiki_binding WHERE wiki_user_id = ?", { rs, _ -> binding(rs) }, wikiUserId).firstOrNull() }
    override fun allBindings(): List<WikiBinding> { ensure(); return jdbc.query("SELECT * FROM wiki_binding ORDER BY bound_at", { rs, _ -> binding(rs) }) }

    /** @return false when that wiki user (or the account) is already bound: the unique keys decide, so two requests at once cannot both win. */
    override fun bind(accountId: UUID, wikiUserId: Long, wikiUsername: String, now: Instant): Boolean {
        ensure()
        return try {
            jdbc.update("INSERT INTO wiki_binding(account_id, wiki_user_id, wiki_username, bound_at) VALUES (?,?,?,?)", accountId, wikiUserId, wikiUsername, Timestamp.from(now)) == 1
        } catch (_: DuplicateKeyException) {
            false
        }
    }

    override fun unbind(accountId: UUID): Boolean { ensure(); return jdbc.update("DELETE FROM wiki_binding WHERE account_id = ?", accountId) == 1 }

    override fun saveChallenge(c: WikiChallenge) {
        ensure()
        jdbc.update(
            "INSERT INTO wiki_bind_challenge(account_id, wiki_username, code, created_at, expires_at) VALUES (?,?,?,?,?) " +
                "ON CONFLICT (account_id) DO UPDATE SET wiki_username = EXCLUDED.wiki_username, code = EXCLUDED.code, created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at",
            c.accountId, c.wikiUsername, c.code, Timestamp.from(c.createdAt), Timestamp.from(c.expiresAt)
        )
    }
    override fun findChallenge(accountId: UUID): WikiChallenge? {
        ensure()
        return jdbc.query("SELECT * FROM wiki_bind_challenge WHERE account_id = ?", { rs, _ ->
            WikiChallenge(UUID.fromString(rs.getString("account_id")), rs.getString("wiki_username"), rs.getString("code"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant())
        }, accountId).firstOrNull()
    }
    override fun deleteChallenge(accountId: UUID) { ensure(); jdbc.update("DELETE FROM wiki_bind_challenge WHERE account_id = ?", accountId) }

    /** The newest edit time already stored for the account (where the next read of the wiki starts), or null. */
    override fun latestEditAt(accountId: UUID): Instant? { ensure(); return jdbc.queryForObject("SELECT MAX(edited_at) FROM wiki_contribution WHERE account_id = ?", Timestamp::class.java, accountId)?.toInstant() }

    /** Stores edits not seen before; an edit already stored (its revision id is the key) is left alone. @return how many were new */
    override fun addEdits(accountId: UUID, wikiUserId: Long, edits: List<WikiEdit>): Int {
        ensure()
        var added = 0
        for (e in edits) added += jdbc.update(
            "INSERT INTO wiki_contribution(revision_id, account_id, wiki_user_id, page_title, edited_at) VALUES (?,?,?,?,?) ON CONFLICT (revision_id) DO NOTHING",
            e.revisionId, accountId, wikiUserId, e.title, Timestamp.from(e.editedAt)
        )
        return added
    }

    override fun pendingOlderThan(cutoff: Instant): List<PendingEdit> {
        ensure()
        return jdbc.query("SELECT revision_id, account_id FROM wiki_contribution WHERE settled_at IS NULL AND edited_at <= ? ORDER BY edited_at", { rs, _ ->
            PendingEdit(rs.getLong("revision_id"), UUID.fromString(rs.getString("account_id")))
        }, Timestamp.from(cutoff))
    }
    override fun settle(revisionIds: Collection<Long>, now: Instant) {
        ensure()
        revisionIds.forEach { jdbc.update("UPDATE wiki_contribution SET settled_at = ? WHERE revision_id = ? AND settled_at IS NULL", Timestamp.from(now), it) }
    }
    override fun discard(revisionIds: Collection<Long>) {
        ensure()
        revisionIds.forEach { jdbc.update("DELETE FROM wiki_contribution WHERE revision_id = ? AND settled_at IS NULL", it) }
    }

    override fun counts(accountId: UUID): Pair<Int, Int> {
        ensure()
        val row = jdbc.queryForMap("SELECT COUNT(*) FILTER (WHERE settled_at IS NOT NULL) AS settled, COUNT(*) FILTER (WHERE settled_at IS NULL) AS pending FROM wiki_contribution WHERE account_id = ?", accountId)
        return (row["settled"] as Number).toInt() to (row["pending"] as Number).toInt()
    }

    /** Newest settled edit times (epoch ms, oldest first), so a mission can count the edits made since its event began. */
    override fun settledTimes(accountId: UUID, limit: Int): List<Long> {
        ensure()
        return jdbc.queryForList("SELECT edited_at FROM (SELECT edited_at FROM wiki_contribution WHERE account_id = ? AND settled_at IS NOT NULL ORDER BY edited_at DESC LIMIT ?) t ORDER BY edited_at", Timestamp::class.java, accountId, limit)
            .mapNotNull { it?.time }
    }

    /**
     * Writes `account.flags.wikiContributions = {value, time, edits}` the way every account flag looks (the game server reads it for the `wiki.contribution`
     * mission signal). `value` never goes down; `edits` are the newest settled edit times. One statement under the row lock, so it can't lose a concurrent
     * change to another flag.
     */
    override fun writeFlag(accountId: UUID, settled: Int, edits: List<Long>, nowMillis: Long): Boolean {
        ensure()
        return jdbc.update(
            """
            UPDATE account SET updated_at = CURRENT_TIMESTAMP, flags = jsonb_set(
                COALESCE(flags, '{}'::jsonb), ARRAY['wikiContributions'], jsonb_build_object(
                    'value', GREATEST(COALESCE((flags #>> ARRAY['wikiContributions','value'])::int, 0), ?::int),
                    'time', ?::bigint,
                    'edits', ?::jsonb), true)
            WHERE id = ?
            """.trimIndent(),
            settled, nowMillis, edits.joinToString(",", "[", "]"), accountId
        ) == 1
    }
}
