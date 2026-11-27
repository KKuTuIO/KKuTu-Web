package me.kkutuio.kkutuweb.malmoi

import me.kkutuio.kkutuweb.analytics.EconomySource
import me.kkutuio.kkutuweb.analytics.EconomyStatsRecorder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class Book(
    val id: Long, val kind: String, val baseTheme: String?, val lang: String, val title: String, val description: String,
    val criteria: String, val cover: String, val creator: UUID?, val creatorName: String?, val owner: UUID?, val ownerName: String?,
    val state: String, val inviteToken: String?, val locked: Boolean, val createdAt: Instant, val updatedAt: Instant
)

data class WordRow(
    val id: Long, val bookId: Long, val word: String, val meanings: String, val source: String, val author: UUID?, val authorName: String?,
    val appliesAt: Instant, val removedAt: Instant?, val removeAt: Instant?, val pending: String?, val locked: Boolean, val promotion: String?
)

data class LicenseRow(val id: Long, val account: UUID, val number: String, val issuedAt: Instant, val expiresAt: Instant, val revokedAt: Instant?, val reissueAfter: Instant?)

/** 말모이의 저장소. 테이블은 `db/additive/20261127_malmoi.sql`(웹이 시작할 때 만든다). 게임 서버는 같은 테이블을 읽기만 한다. */
@Component
class MalmoiDao(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val mapper: ObjectMapper,
    @Autowired private val economy: EconomyStatsRecorder
) {
    private fun ts(rs: ResultSet, c: String): Instant? = rs.getTimestamp(c)?.toInstant()
    private fun uuid(rs: ResultSet, c: String): UUID? = rs.getString(c)?.let(UUID::fromString)

    private fun book(rs: ResultSet, @Suppress("UNUSED_PARAMETER") n: Int) = Book(
        rs.getLong("id"), rs.getString("kind"), rs.getString("base_theme"), rs.getString("lang"), rs.getString("title"), rs.getString("description"),
        rs.getString("criteria"), rs.getString("cover"), uuid(rs, "creator_uuid"), rs.getString("creator_name"), uuid(rs, "owner_uuid"), rs.getString("owner_name"),
        rs.getString("state"), rs.getString("invite_token"), rs.getBoolean("locked"), ts(rs, "created_at")!!, ts(rs, "updated_at")!!
    )

    private fun word(rs: ResultSet, @Suppress("UNUSED_PARAMETER") n: Int) = WordRow(
        rs.getLong("id"), rs.getLong("malmoi_id"), rs.getString("word"), rs.getString("meanings"), rs.getString("source"), uuid(rs, "author_uuid"), rs.getString("author_name"),
        ts(rs, "applies_at")!!, ts(rs, "removed_at"), ts(rs, "remove_at"), rs.getString("pending"), rs.getBoolean("locked"), rs.getString("promotion")
    )

    // ---- books ----

    fun findBook(id: Long): Book? = jdbc.query("SELECT * FROM malmoi WHERE id = ?", ::book, id).firstOrNull()
    fun findByToken(token: String): Book? = jdbc.query("SELECT * FROM malmoi WHERE invite_token = ?", ::book, token).firstOrNull()
    fun extFor(theme: String): Book? = jdbc.query("SELECT * FROM malmoi WHERE kind = 'EXT' AND base_theme = ?", ::book, theme).firstOrNull()

    /** 목록: 공개(와 내가 만든 것, 등록한 것)만 보인다. 일부 공개는 링크로만 알 수 있어 남의 것은 나오지 않는다. */
    fun list(me: UUID, scope: String, query: String, limit: Int): List<Book> {
        val like = "%${query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        val sql = when (scope) {
            "registered" -> "SELECT m.* FROM malmoi m JOIN malmoi_registration r ON r.malmoi_id = m.id AND r.account_uuid = ? WHERE m.state <> 'HIDDEN' AND (m.title ILIKE ? ESCAPE '\\' OR ? = '%%') ORDER BY m.updated_at DESC LIMIT ?"
            "mine" -> "SELECT m.* FROM malmoi m WHERE (m.owner_uuid = ? OR m.creator_uuid = ? OR EXISTS (SELECT 1 FROM malmoi_editor e WHERE e.malmoi_id = m.id AND e.account_uuid = ? AND e.accepted)) AND (m.title ILIKE ? ESCAPE '\\' OR ? = '%%') ORDER BY m.updated_at DESC LIMIT ?"
            else -> "SELECT m.* FROM malmoi m WHERE m.state = 'PUBLIC' AND (m.title ILIKE ? ESCAPE '\\' OR ? = '%%') ORDER BY (SELECT count(*) FROM malmoi_registration r WHERE r.malmoi_id = m.id) DESC, m.updated_at DESC LIMIT ?"
        }
        return when (scope) {
            "registered" -> jdbc.query(sql, ::book, me, like, like, limit)
            "mine" -> jdbc.query(sql, ::book, me, me, me, like, like, limit)
            else -> jdbc.query(sql, ::book, like, like, limit)
        }
    }

    fun createBook(kind: String, baseTheme: String?, title: String, description: String, criteriaJson: String, coverJson: String, creator: UUID?, creatorName: String?, state: String, token: String?): Long =
        jdbc.queryForObject(
            """INSERT INTO malmoi(kind, base_theme, title, description, criteria, cover, creator_uuid, creator_name, owner_uuid, owner_name, state, invite_token)
               VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, ?, ?) RETURNING id""",
            Long::class.java, kind, baseTheme, title, description, criteriaJson, coverJson, creator, creatorName, if (kind == "NEW") creator else null, if (kind == "NEW") creatorName else null, state, token
        )!!

    fun updateBook(id: Long, title: String, description: String, criteriaJson: String, coverJson: String) {
        jdbc.update("UPDATE malmoi SET title = ?, description = ?, criteria = CAST(? AS jsonb), cover = CAST(? AS jsonb), updated_at = now() WHERE id = ?", title, description, criteriaJson, coverJson, id)
    }

    fun setState(id: Long, state: String) { jdbc.update("UPDATE malmoi SET state = ?, updated_at = now() WHERE id = ?", state, id) }
    fun setOwner(id: Long, owner: UUID, name: String) { jdbc.update("UPDATE malmoi SET owner_uuid = ?, owner_name = ?, updated_at = now() WHERE id = ?", owner, name, id) }
    fun touch(id: Long) { jdbc.update("UPDATE malmoi SET updated_at = now(), revision = revision + 1 WHERE id = ?", id) }
    fun unlistedCount(creator: UUID): Int = jdbc.queryForObject("SELECT count(*) FROM malmoi WHERE creator_uuid = ? AND state = 'UNLISTED'", Int::class.java, creator) ?: 0
    fun titleTaken(title: String): Boolean = (jdbc.queryForObject("SELECT count(*) FROM malmoi WHERE lower(title) = lower(?)", Int::class.java, title) ?: 0) > 0

    // ---- counts for the list and the detail ----

    fun registrants(id: Long): Int = jdbc.queryForObject("SELECT count(*) FROM malmoi_registration WHERE malmoi_id = ?", Int::class.java, id) ?: 0
    fun wordCounts(id: Long): Pair<Int, Int> {
        val r = jdbc.queryForMap("SELECT count(*) FILTER (WHERE removed_at IS NULL) AS total, count(*) FILTER (WHERE removed_at IS NULL AND applies_at <= now()) AS live FROM malmoi_word WHERE malmoi_id = ?", id)
        return (r["total"] as Number).toInt() to (r["live"] as Number).toInt()
    }
    fun previewWords(id: Long, n: Int): List<String> = jdbc.queryForList("SELECT word FROM malmoi_word WHERE malmoi_id = ? AND removed_at IS NULL ORDER BY created_at DESC LIMIT ?", String::class.java, id, n).filterNotNull()

    // ---- registration ----

    fun isRegistered(me: UUID, id: Long): Boolean = (jdbc.queryForObject("SELECT count(*) FROM malmoi_registration WHERE account_uuid = ? AND malmoi_id = ?", Int::class.java, me, id) ?: 0) > 0
    fun registeredIds(me: UUID): Set<Long> = jdbc.queryForList("SELECT malmoi_id FROM malmoi_registration WHERE account_uuid = ?", Long::class.java, me).filterNotNull().toSet()
    fun register(me: UUID, id: Long) { jdbc.update("INSERT INTO malmoi_registration(account_uuid, malmoi_id) VALUES (?, ?) ON CONFLICT DO NOTHING", me, id) }
    fun unregister(me: UUID, id: Long) { jdbc.update("DELETE FROM malmoi_registration WHERE account_uuid = ? AND malmoi_id = ?", me, id) }

    // ---- editors and blocks ----

    fun isEditor(me: UUID, id: Long): Boolean = (jdbc.queryForObject("SELECT count(*) FROM malmoi_editor WHERE malmoi_id = ? AND account_uuid = ? AND accepted", Int::class.java, id, me) ?: 0) > 0
    fun blockedUntil(me: UUID, id: Long): Instant? =
        jdbc.query("SELECT until_at FROM malmoi_block WHERE malmoi_id = ? AND account_uuid = ? AND until_at > now()", { rs, _ -> rs.getTimestamp(1).toInstant() }, id, me).firstOrNull()

    // ---- words ----

    fun findWord(id: Long, wordId: Long): WordRow? = jdbc.query("SELECT * FROM malmoi_word WHERE malmoi_id = ? AND id = ?", ::word, id, wordId).firstOrNull()
    fun findWordText(id: Long, text: String): WordRow? = jdbc.query("SELECT * FROM malmoi_word WHERE malmoi_id = ? AND word = ?", ::word, id, text).firstOrNull()

    fun words(id: Long, query: String, limit: Int, offset: Int): List<WordRow> {
        val like = "%${query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        return jdbc.query(
            "SELECT * FROM malmoi_word WHERE malmoi_id = ? AND removed_at IS NULL AND (word ILIKE ? ESCAPE '\\' OR ? = '%%') ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            ::word, id, like, like, limit, offset
        )
    }

    /** 이미 지운 낱말을 다시 올리면 그 줄을 되살린다(낱말은 말모이마다 하나). */
    fun addWord(id: Long, text: String, meaningsJson: String, source: String, author: UUID, authorName: String, appliesAt: Instant): Long =
        jdbc.queryForObject(
            """INSERT INTO malmoi_word(malmoi_id, word, meanings, source, author_uuid, author_name, applies_at)
               VALUES (?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
               ON CONFLICT (malmoi_id, word) DO UPDATE SET meanings = EXCLUDED.meanings, source = EXCLUDED.source, author_uuid = EXCLUDED.author_uuid, author_name = EXCLUDED.author_name,
                 applies_at = EXCLUDED.applies_at, removed_at = NULL, remove_at = NULL, pending = NULL, created_at = now()
               WHERE malmoi_word.removed_at IS NOT NULL RETURNING id""",
            Long::class.java, id, text, meaningsJson, source, author, authorName, Timestamp.from(appliesAt)
        ) ?: 0L

    fun setWord(wordId: Long, meaningsJson: String, source: String) { jdbc.update("UPDATE malmoi_word SET meanings = CAST(? AS jsonb), source = ?, pending = NULL WHERE id = ?", meaningsJson, source, wordId) }
    fun setPending(wordId: Long, pendingJson: String) { jdbc.update("UPDATE malmoi_word SET pending = CAST(? AS jsonb) WHERE id = ?", pendingJson, wordId) }
    fun removeNow(wordId: Long) { jdbc.update("UPDATE malmoi_word SET removed_at = now(), remove_at = NULL WHERE id = ?", wordId) }
    fun removeLater(wordId: Long, at: Instant) { jdbc.update("UPDATE malmoi_word SET remove_at = ? WHERE id = ?", Timestamp.from(at), wordId) }

    /** 반영 시각이 지난 수정 대기분을 낱말에 적용하고, 삭제 예약이 지난 낱말을 지운다(읽을 때마다 가볍게 처리한다). */
    fun settle(id: Long) {
        jdbc.update("UPDATE malmoi_word SET removed_at = remove_at, remove_at = NULL WHERE malmoi_id = ? AND removed_at IS NULL AND remove_at IS NOT NULL AND remove_at <= now()", id)
        jdbc.update(
            """UPDATE malmoi_word SET meanings = COALESCE(pending->'meanings', meanings), source = COALESCE(pending->>'source', source), pending = NULL
               WHERE malmoi_id = ? AND pending IS NOT NULL AND (pending->>'applies_at')::timestamptz <= now()""", id
        )
    }

    /** 공식 사전의 원 주제에 이미 있는 낱말인가(확장팩은 이런 낱말을 받지 않는다). */
    fun inOfficialTheme(lang: String, word: String, theme: String): Boolean {
        val table = if (lang == "en") "kkutu_en" else "kkutu_ko"
        return (jdbc.queryForObject("SELECT count(*) FROM $table WHERE _id = ? AND (',' || theme || ',') LIKE ?", Int::class.java, word, "%,$theme,%") ?: 0) > 0
    }

    // ---- editors, delegation, invitations ----

    /** 별명으로 계정을 찾는다(별명 뒤의 #번호까지 적어도 되고, 앞부분만 적어도 한 명으로 정해지면 찾는다). */
    fun accountByNickname(nickname: String): Pair<UUID, String>? = jdbc.query(
        "SELECT a.uuid AS uuid, u.nickname AS nick FROM users u JOIN game_profile gp ON gp.legacy_user_id = u._id JOIN account a ON a.id = gp.account_id WHERE u.nickname = ? OR gp.nickname = ? LIMIT 1",
        { rs, _ -> UUID.fromString(rs.getString("uuid")) to rs.getString("nick") }, nickname, nickname
    ).firstOrNull()

    fun editors(id: Long): List<Map<String, Any?>> = jdbc.queryForList("SELECT name, accepted FROM malmoi_editor WHERE malmoi_id = ? ORDER BY created_at", id)
    fun invite(id: Long, account: UUID, name: String) { jdbc.update("INSERT INTO malmoi_editor(malmoi_id, account_uuid, name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", id, account, name) }
    fun accept(id: Long, account: UUID): Int = jdbc.update("UPDATE malmoi_editor SET accepted = TRUE WHERE malmoi_id = ? AND account_uuid = ?", id, account)
    fun leaveEditor(id: Long, account: UUID): Int = jdbc.update("DELETE FROM malmoi_editor WHERE malmoi_id = ? AND account_uuid = ?", id, account)
    fun invitations(account: UUID): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT m.id, m.title, m.owner_name FROM malmoi_editor e JOIN malmoi m ON m.id = e.malmoi_id WHERE e.account_uuid = ? AND NOT e.accepted AND m.state = 'UNLISTED' ORDER BY e.created_at DESC", account
    )
    fun newToken(id: Long, token: String) { jdbc.update("UPDATE malmoi SET invite_token = ?, updated_at = now() WHERE id = ?", token, id) }

    // ---- review requests ----

    fun pendingReview(id: Long): Boolean = (jdbc.queryForObject("SELECT count(*) FROM malmoi_publish_review WHERE malmoi_id = ? AND status = 'PENDING'", Int::class.java, id) ?: 0) > 0
    fun lastReview(id: Long): Map<String, Any?>? = jdbc.queryForList("SELECT status, reason FROM malmoi_publish_review WHERE malmoi_id = ? ORDER BY created_at DESC LIMIT 1", id).firstOrNull()
    fun requestReview(id: Long, revision: Long, by: UUID) { jdbc.update("INSERT INTO malmoi_publish_review(malmoi_id, revision, requested_by) VALUES (?, ?, ?)", id, revision, by) }
    fun revisionOf(id: Long): Long = jdbc.queryForObject("SELECT revision FROM malmoi WHERE id = ?", Long::class.java, id) ?: 0L

    // ---- revert and block ----

    fun findRevision(id: Long, revisionId: Long): Map<String, Any?>? =
        jdbc.queryForList("SELECT id, word, kind, actor_uuid, actor_name, reverted_at FROM malmoi_revision WHERE malmoi_id = ? AND id = ?", id, revisionId).firstOrNull()
    fun markReverted(revisionId: Long) { jdbc.update("UPDATE malmoi_revision SET reverted_at = now() WHERE id = ?", revisionId) }
    fun restoreWord(id: Long, text: String): Int = jdbc.update("UPDATE malmoi_word SET removed_at = NULL, remove_at = NULL WHERE malmoi_id = ? AND word = ? AND removed_at IS NOT NULL", id, text)
    fun removeWordNow(id: Long, text: String): Int = jdbc.update("UPDATE malmoi_word SET removed_at = now(), remove_at = NULL WHERE malmoi_id = ? AND word = ? AND removed_at IS NULL", id, text)
    fun block(id: Long, account: UUID, name: String, reason: String, by: UUID, days: Long) {
        jdbc.update(
            "INSERT INTO malmoi_block(malmoi_id, account_uuid, name, reason, blocked_by, until_at) VALUES (?, ?, ?, ?, ?, now() + make_interval(days => ?)) ON CONFLICT (malmoi_id, account_uuid) DO UPDATE SET reason = EXCLUDED.reason, blocked_by = EXCLUDED.blocked_by, until_at = EXCLUDED.until_at, created_at = now()",
            id, account, name, reason, by, days.toInt()
        )
    }
    fun unblock(id: Long, name: String): Int = jdbc.update("DELETE FROM malmoi_block WHERE malmoi_id = ? AND name = ?", id, name)
    fun blocks(id: Long): List<Map<String, Any?>> = jdbc.queryForList("SELECT name, reason, until_at FROM malmoi_block WHERE malmoi_id = ? AND until_at > now() ORDER BY until_at", id)

    // ---- revisions ----

    fun log(id: Long, word: String?, kind: String, reason: String, actor: UUID?, actorName: String?) {
        jdbc.update("INSERT INTO malmoi_revision(malmoi_id, word, kind, reason, actor_uuid, actor_name) VALUES (?, ?, ?, ?, ?, ?)", id, word, kind, reason, actor, actorName)
    }

    fun revisions(id: Long, limit: Int): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT id, word, kind, reason, actor_name, reverted_at, created_at FROM malmoi_revision WHERE malmoi_id = ? ORDER BY created_at DESC, id DESC LIMIT ?", id, limit
    )

    /** 오늘(24시간) 공개 말모이에서 한 편집 수. */
    fun editsToday(me: UUID): Int = jdbc.queryForObject(
        "SELECT count(*) FROM malmoi_revision r JOIN malmoi m ON m.id = r.malmoi_id WHERE r.actor_uuid = ? AND m.state = 'PUBLIC' AND r.kind IN ('add', 'edit', 'remove') AND r.created_at > now() - interval '24 hours'",
        Int::class.java, me
    ) ?: 0

    // ---- contributions ----

    /** 날짜별 활동(최근 [days]일): 날짜, 사람, 건수. */
    fun activity(id: Long, days: Int): List<Triple<String, String, Int>> = jdbc.query(
        """SELECT to_char(created_at, 'YYYY-MM-DD') AS d, COALESCE(author_name, '') AS who, count(*) AS n FROM malmoi_word
           WHERE malmoi_id = ? AND removed_at IS NULL AND created_at > now() - make_interval(days => ?) GROUP BY 1, 2 ORDER BY 1""",
        { rs, _ -> Triple(rs.getString("d"), rs.getString("who"), rs.getInt("n")) }, id, days
    )

    /** 지분의 기준: 낱말이 [MalmoiRules.TOP_DAYS]일 넘게 남은 것. 작성 계정별. */
    fun survivors(id: Long): List<Triple<UUID?, String, Int>> = jdbc.query(
        """SELECT author_uuid, COALESCE(max(author_name), '') AS who, count(*) AS n FROM malmoi_word
           WHERE malmoi_id = ? AND removed_at IS NULL AND created_at <= now() - make_interval(days => ?) GROUP BY author_uuid ORDER BY n DESC, min(created_at), author_uuid""",
        { rs, _ -> Triple(uuid(rs, "author_uuid"), rs.getString("who"), rs.getInt("n")) }, id, MalmoiRules.TOP_DAYS.toInt()
    )

    // ---- license ----

    private fun license(rs: ResultSet, @Suppress("UNUSED_PARAMETER") n: Int) =
        LicenseRow(rs.getLong("id"), UUID.fromString(rs.getString("account_uuid")), rs.getString("number"), ts(rs, "issued_at")!!, ts(rs, "expires_at")!!, ts(rs, "revoked_at"), ts(rs, "reissue_after"))

    fun latestLicense(me: UUID): LicenseRow? = jdbc.query("SELECT * FROM malmoi_license WHERE account_uuid = ? ORDER BY issued_at DESC, id DESC LIMIT 1", ::license, me).firstOrNull()
    fun activeLicense(me: UUID): LicenseRow? = jdbc.query("SELECT * FROM malmoi_license WHERE account_uuid = ? AND revoked_at IS NULL AND expires_at > now() ORDER BY issued_at DESC LIMIT 1", ::license, me).firstOrNull()

    fun issueLicense(me: UUID, profileId: String, fee: Int, days: Long, oathVersion: Int): LicenseRow {
        val id = jdbc.queryForObject(
            "INSERT INTO malmoi_license(account_uuid, profile_id, number, oath_version, fee, expires_at) VALUES (?, ?, '', ?, ?, now() + make_interval(days => ?)) RETURNING id",
            Long::class.java, me, profileId, oathVersion, fee, days.toInt()
        )!!
        val issued = jdbc.queryForObject("SELECT issued_at FROM malmoi_license WHERE id = ?", Timestamp::class.java, id)!!.toInstant()
        val zoned = issued.atZone(java.time.ZoneId.of("Asia/Seoul"))
        jdbc.update("UPDATE malmoi_license SET number = ? WHERE id = ?", MalmoiRules.licenseNumber(zoned.year, zoned.monthValue, id), id)
        return jdbc.query("SELECT * FROM malmoi_license WHERE id = ?", ::license, id).first()
    }

    fun revokeLicense(me: UUID, reason: String, reissueAfterDays: Long) {
        jdbc.update(
            "UPDATE malmoi_license SET revoked_at = now(), revoke_reason = ?, reissue_after = now() + make_interval(days => ?) WHERE account_uuid = ? AND revoked_at IS NULL",
            reason, reissueAfterDays.toInt(), me
        )
    }

    // ---- the profile in use (level, Ping) ----

    /** 레벨 계산용 점수. 프로필 행을 잠그지 않는다(읽기). */
    fun scoreOf(profileId: String): Long = jdbc.query("SELECT kkutu FROM users WHERE _id = ?", { rs, _ ->
        runCatching { mapper.readTree(rs.getString(1) ?: "{}").path("score").asLong(0) }.getOrDefault(0L)
    }, profileId).firstOrNull() ?: 0L

    fun moneyOf(profileId: String): Long = jdbc.query("SELECT money FROM users WHERE _id = ?", { rs, _ -> rs.getLong(1) }, profileId).firstOrNull() ?: 0L

    fun nicknameOf(profileId: String): String = jdbc.query("SELECT nickname FROM users WHERE _id = ?", { rs, _ -> rs.getString(1) ?: "" }, profileId).firstOrNull() ?: ""

    /** 핑을 내는 쪽: 프로필 행을 잠그고 모자라면 false. 트랜잭션 안에서 부른다. */
    fun spend(profileId: String, amount: Int): Boolean {
        val money = jdbc.query("SELECT money FROM users WHERE _id = ? FOR UPDATE", { rs, _ -> rs.getLong(1) }, profileId).firstOrNull() ?: return false
        if (money < amount) return false
        jdbc.update("UPDATE users SET money = money - ? WHERE _id = ?", amount, profileId)
        economy.record(EconomySource.MALMOI_LICENSE, -amount.toLong())
        return true
    }

    fun json(value: Any?): String = mapper.writeValueAsString(value)
    fun tree(text: String?) = mapper.readTree(text ?: "null")
    fun strings(text: String?): List<String> = tree(text ?: "[]").let { n -> (0 until n.size()).map { n.get(it).asString() } }
}
