package me.kkutuio.kkutuweb.support

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class Ticket(
    val id: UUID,
    val code: String,
    val accountId: UUID,
    val accountUuid: UUID,
    val gameUserId: String?,
    val nickname: String?,
    val category: String,
    val subject: String,
    val status: TicketStatus,
    val assigneeUuid: UUID?,
    val notifyEmail: Boolean,
    val details: JsonNode,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val dueAt: Instant?,
    val userReadAt: Instant?,
    val lastStaffMessageAt: Instant?,
    val resolvedAt: Instant?,
    val closedAt: Instant?
) {
    /** 사용자가 아직 열어 보지 않은 운영자 답변이 있다. */
    val unread: Boolean get() = lastStaffMessageAt != null && (userReadAt == null || userReadAt.isBefore(lastStaffMessageAt))
}

data class TicketMessage(
    val id: Long,
    val kind: String,
    val actorUuid: UUID?,
    val staffName: String?,
    val staffTitle: String?,
    val body: String,
    val event: JsonNode?,
    val createdAt: Instant
)

data class StaffProfile(
    val accountUuid: String,
    val publicName: String,
    val title: String,
    val role: StaffRole,
    val active: Boolean
)

data class NewTicket(
    val id: UUID,
    val code: String,
    val accountId: UUID,
    val accountUuid: UUID,
    val gameUserId: String?,
    val nickname: String?,
    val category: String,
    val subject: String,
    val notifyEmail: Boolean,
    val details: Map<String, Any?>,
    val consentVersion: String,
    val requestKey: UUID,
    val dueAt: Instant?
)

/** 운영자 목록의 검색 조건. [assignee] 의 빈 문자열은 "담당 없음"이다. */
data class TicketFilter(
    val statuses: Set<TicketStatus> = emptySet(),
    val category: String? = null,
    val assignee: String? = null,
    val query: String? = null
)

@Repository
class SupportDao(private val jdbc: JdbcTemplate, private val objectMapper: ObjectMapper) {
    private val ticketMapper = RowMapper { rs, _ ->
        Ticket(
            id = rs.uuid("id")!!,
            code = rs.getString("code"),
            accountId = rs.uuid("account_id")!!,
            accountUuid = rs.uuid("account_uuid")!!,
            gameUserId = rs.getString("game_user_id"),
            nickname = rs.getString("nickname"),
            category = rs.getString("category"),
            subject = rs.getString("subject"),
            status = TicketStatus.valueOf(rs.getString("status")),
            assigneeUuid = rs.uuid("assignee_uuid"),
            notifyEmail = rs.getBoolean("notify_email"),
            details = objectMapper.readTree(rs.getString("details")),
            version = rs.getInt("version"),
            createdAt = rs.instant("created_at")!!,
            updatedAt = rs.instant("updated_at")!!,
            dueAt = rs.instant("due_at"),
            userReadAt = rs.instant("user_read_at"),
            lastStaffMessageAt = rs.instant("last_staff_message_at"),
            resolvedAt = rs.instant("resolved_at"),
            closedAt = rs.instant("closed_at")
        )
    }

    private val messageMapper = RowMapper { rs, _ ->
        TicketMessage(
            id = rs.getLong("id"),
            kind = rs.getString("kind"),
            actorUuid = rs.uuid("actor_uuid"),
            staffName = rs.getString("staff_name"),
            staffTitle = rs.getString("staff_title"),
            body = rs.getString("body"),
            event = rs.getString("event")?.let(objectMapper::readTree),
            createdAt = rs.instant("created_at")!!
        )
    }

    private val staffMapper = RowMapper { rs, _ ->
        StaffProfile(
            accountUuid = rs.getString("account_uuid"),
            publicName = rs.getString("public_name"),
            title = rs.getString("title"),
            role = StaffRole.valueOf(rs.getString("role")),
            active = rs.getBoolean("active")
        )
    }

    /** 트랜잭션이 끝날 때까지 같은 계정의 접수를 한 줄로 세운다(중복 접수·미종료 상한 검사용). */
    fun lockAccount(accountId: UUID) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", RowCallbackHandler { }, "support:$accountId")
    }

    fun insertTicket(ticket: NewTicket) {
        jdbc.update(
            "INSERT INTO support_ticket(id, code, account_id, account_uuid, game_user_id, nickname, category, subject, status, " +
                "notify_email, details, consent_version, consented_at, request_key, due_at, user_read_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?, CAST(? AS jsonb), ?, now(), ?, ?, now())",
            ticket.id, ticket.code, ticket.accountId, ticket.accountUuid, ticket.gameUserId, ticket.nickname, ticket.category,
            ticket.subject, ticket.notifyEmail, objectMapper.writeValueAsString(ticket.details), ticket.consentVersion,
            ticket.requestKey, ticket.dueAt?.let(Timestamp::from)
        )
    }

    fun findCodeByRequestKey(accountId: UUID, requestKey: UUID): String? = jdbc.queryForList(
        "SELECT code FROM support_ticket WHERE account_id = ? AND request_key = ?", String::class.java, accountId, requestKey
    ).firstOrNull()

    fun codeExists(code: String): Boolean = jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM support_ticket WHERE code = ?)", Boolean::class.java, code
    ) == true

    fun countOpen(accountId: UUID): Int = jdbc.queryForObject(
        "SELECT count(*) FROM support_ticket WHERE account_id = ? AND status NOT IN ('RESOLVED', 'CLOSED')", Int::class.java, accountId
    ) ?: 0

    /** 소유자 조건이 붙은 조회. 남의 번호는 없는 번호와 같다. */
    fun findOwned(accountId: UUID, code: String, lock: Boolean = false): Ticket? = jdbc.query(
        "SELECT * FROM support_ticket WHERE account_id = ? AND code = ?" + if (lock) " FOR UPDATE" else "",
        ticketMapper, accountId, code
    ).firstOrNull()

    fun find(id: UUID, lock: Boolean = false): Ticket? = jdbc.query(
        "SELECT * FROM support_ticket WHERE id = ?" + if (lock) " FOR UPDATE" else "", ticketMapper, id
    ).firstOrNull()

    fun listOwned(accountId: UUID, statuses: Set<TicketStatus>, limit: Int, offset: Int): Pair<Int, List<Ticket>> {
        val where = StringBuilder("account_id = ?")
        val parameters = mutableListOf<Any>(accountId)
        addStatuses(statuses, where, parameters)
        return page(where.toString(), parameters, "updated_at DESC, code", limit, offset)
    }

    fun countUnread(accountId: UUID): Int = jdbc.queryForObject(
        "SELECT count(*) FROM support_ticket WHERE account_id = ? AND last_staff_message_at IS NOT NULL " +
            "AND (user_read_at IS NULL OR user_read_at < last_staff_message_at)",
        Int::class.java, accountId
    ) ?: 0

    fun search(filter: TicketFilter, limit: Int, offset: Int): Pair<Int, List<Ticket>> {
        val where = StringBuilder("TRUE")
        val parameters = mutableListOf<Any>()
        addStatuses(filter.statuses, where, parameters)
        filter.category?.takeIf { it.isNotBlank() }?.let { where.append(" AND category = ?"); parameters += it }
        when (val assignee = filter.assignee) {
            null -> Unit
            "" -> where.append(" AND assignee_uuid IS NULL")
            else -> { where.append(" AND assignee_uuid = ?"); parameters += UUID.fromString(assignee) }
        }
        filter.query?.trim()?.takeIf { it.isNotEmpty() }?.let { query ->
            // LIKE 의 특수 문자를 글자 그대로 찾는다.
            val like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            where.append(" AND (code ILIKE ? OR subject ILIKE ? OR nickname ILIKE ? OR game_user_id = ? OR account_uuid::text = ?)")
            parameters.addAll(listOf(like, like, like, query, query.lowercase()))
        }
        // 답이 필요한 것부터: 기한이 있는 미답변 문의, 그다음 오래 기다린 순서.
        val order = "CASE WHEN status IN ('RECEIVED', 'IN_PROGRESS') THEN 0 ELSE 1 END, " +
            "CASE WHEN last_staff_message_at IS NULL THEN due_at END NULLS LAST, " +
            "CASE WHEN status IN ('RECEIVED', 'IN_PROGRESS') THEN updated_at END ASC, updated_at DESC, code"
        return page(where.toString(), parameters, order, limit, offset)
    }

    private fun addStatuses(statuses: Set<TicketStatus>, where: StringBuilder, parameters: MutableList<Any>) {
        if (statuses.isEmpty()) return
        where.append(" AND status IN (").append(statuses.joinToString(", ") { "?" }).append(')')
        parameters.addAll(statuses.map { it.name })
    }

    private fun page(where: String, parameters: List<Any>, order: String, limit: Int, offset: Int): Pair<Int, List<Ticket>> {
        val total = jdbc.queryForObject("SELECT count(*) FROM support_ticket WHERE $where", Int::class.java, *parameters.toTypedArray()) ?: 0
        val rows = jdbc.query(
            "SELECT * FROM support_ticket WHERE $where ORDER BY $order LIMIT ? OFFSET ?",
            ticketMapper, *(parameters + listOf(limit, offset)).toTypedArray()
        )
        return total to rows
    }

    fun insertMessage(
        ticketId: UUID, kind: String, actorUuid: UUID?, body: String,
        staff: StaffProfile? = null, event: Map<String, Any?>? = null
    ) {
        jdbc.update(
            "INSERT INTO support_message(ticket_id, kind, actor_uuid, staff_name, staff_title, body, event) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
            ticketId, kind, actorUuid, staff?.publicName, staff?.title, body, event?.let(objectMapper::writeValueAsString)
        )
    }

    fun messages(ticketId: UUID): List<TicketMessage> =
        jdbc.query("SELECT * FROM support_message WHERE ticket_id = ? ORDER BY id", messageMapper, ticketId)

    /** 사용자가 열어 본 시각. 목록 순서(updated_at)는 바꾸지 않는다. */
    fun markRead(ticketId: UUID) {
        jdbc.update("UPDATE support_ticket SET user_read_at = now() WHERE id = ?", ticketId)
    }

    /** 상태·담당·분류 중 바뀐 것을 저장하고 버전을 올린다. 해결·종료 시각은 상태를 따른다. */
    fun saveProgress(ticket: Ticket, status: TicketStatus, assignee: UUID?, category: String, dueAt: Instant?, staffReplied: Boolean = false) {
        jdbc.update(
            "UPDATE support_ticket SET status = ?, assignee_uuid = ?, category = ?, due_at = ?, version = version + 1, updated_at = now(), " +
                "resolved_at = CASE WHEN ? = 'RESOLVED' THEN COALESCE(CASE WHEN status = 'RESOLVED' THEN resolved_at END, now()) " +
                "WHEN ? = 'CLOSED' THEN resolved_at END, " +
                "closed_at = CASE WHEN ? = 'CLOSED' THEN COALESCE(closed_at, now()) END, " +
                "last_staff_message_at = CASE WHEN ? THEN now() ELSE last_staff_message_at END " +
                "WHERE id = ?",
            status.name, assignee, category, dueAt?.let(Timestamp::from),
            status.name, status.name, status.name, staffReplied, ticket.id
        )
    }

    fun answeredIdleSince(cutoff: Instant, limit: Int): List<UUID> = jdbc.queryForList(
        "SELECT id FROM support_ticket WHERE status = 'ANSWERED' AND last_staff_message_at < ? ORDER BY last_staff_message_at LIMIT ?",
        UUID::class.java, Timestamp.from(cutoff), limit
    ).filterNotNull()

    fun resolvedSince(cutoff: Instant, limit: Int): List<UUID> = jdbc.queryForList(
        "SELECT id FROM support_ticket WHERE status = 'RESOLVED' AND resolved_at < ? ORDER BY resolved_at LIMIT ?",
        UUID::class.java, Timestamp.from(cutoff), limit
    ).filterNotNull()

    /** 탈퇴가 끝난 계정의 종료되지 않은 문의. 그대로 두면 보유 기간이 시작되지 않는다. */
    fun openOfDeletedAccounts(limit: Int): List<UUID> = jdbc.queryForList(
        "SELECT ticket.id FROM support_ticket ticket JOIN account ON account.id = ticket.account_id " +
            "WHERE account.status = 'DELETED' AND ticket.status <> 'CLOSED' ORDER BY ticket.created_at LIMIT ?",
        UUID::class.java, limit
    ).filterNotNull()

    /** 보유 기간이 지난 문의를 지운다. 메시지와 첨부는 외래 키로 함께 지워진다. */
    fun purgeClosedBefore(years: Int): Int =
        jdbc.update("DELETE FROM support_ticket WHERE status = 'CLOSED' AND closed_at < now() - make_interval(years => ?)", years)

    /** 식별 번호(계정 또는 게임 프로필)가 가리키는 계정의 보안 코드. 읽기만 한다. */
    fun securityCodesOf(userId: String): List<String> = jdbc.queryForList(
        "SELECT account.flags -> 'uid' ->> 'value' FROM account WHERE account.legacy_user_id = ? OR account.id IN (" +
            "SELECT profile.account_id FROM game_profile profile WHERE profile.legacy_user_id = ? OR profile.id::text = ?) LIMIT 5",
        String::class.java, userId, userId, userId
    ).filterNotNull()

    fun findStaff(accountUuid: String): StaffProfile? =
        jdbc.query("SELECT * FROM support_staff WHERE account_uuid = CAST(? AS uuid)", staffMapper, accountUuid).firstOrNull()

    fun listStaff(): List<StaffProfile> = jdbc.query("SELECT * FROM support_staff ORDER BY public_name, account_uuid", staffMapper)

    fun saveStaff(profile: StaffProfile, actorUuid: String) {
        jdbc.update(
            "INSERT INTO support_staff(account_uuid, public_name, title, role, active, updated_by) VALUES (CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS uuid)) " +
                "ON CONFLICT (account_uuid) DO UPDATE SET public_name = EXCLUDED.public_name, title = EXCLUDED.title, role = EXCLUDED.role, " +
                "active = EXCLUDED.active, updated_by = EXCLUDED.updated_by, updated_at = now()",
            profile.accountUuid, profile.publicName, profile.title, profile.role.name, profile.active, actorUuid
        )
    }

    fun setting(key: String): JsonNode? = jdbc.queryForList(
        "SELECT value::text FROM support_setting WHERE key = ?", String::class.java, key
    ).firstOrNull()?.let(objectMapper::readTree)

    fun saveSetting(key: String, value: Any, actorUuid: String) {
        jdbc.update(
            "INSERT INTO support_setting(key, value, updated_by) VALUES (?, CAST(? AS jsonb), CAST(? AS uuid)) " +
                "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_by = EXCLUDED.updated_by, updated_at = now()",
            key, objectMapper.writeValueAsString(value), actorUuid
        )
    }

    private fun ResultSet.uuid(column: String): UUID? = getObject(column, UUID::class.java)
    private fun ResultSet.instant(column: String): Instant? = getTimestamp(column)?.toInstant()
}
