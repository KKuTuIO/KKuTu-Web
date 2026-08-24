package me.kkutuio.kkutuweb.mailbox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.time.Instant

@Service
class MailboxAdminService(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val operators = setOf("eq", "neq", "gte", "gt", "lte", "lt", "exists", "in")
    private val conditionTypes = setOf("userId", "flag", "level", "experience", "lastLoginDays", "item")

    fun list(): List<MailboxAdminMail> = jdbcTemplate.query(
        """
        SELECT _id, name, content, distribution_start_at, distribution_end_at, claim_end_at,
               max_distribution_count, distributed_count, eligibility, per_user_claim_limit,
               rewards, active, created_at, updated_at
        FROM mailbox
        ORDER BY created_at DESC, _id ASC
        """.trimIndent(),
        ::mapMail
    )

    @Transactional
    fun create(request: MailboxAdminMailRequest, adminId: String): MailboxAdminMail {
        val normalized = validate(request)
        try {
            jdbcTemplate.update(
                """
                INSERT INTO mailbox (
                    _id, name, content, distribution_start_at, distribution_end_at, claim_end_at,
                    max_distribution_count, eligibility, per_user_claim_limit, rewards, active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb), ?)
                """.trimIndent(),
                normalized.id, normalized.name, normalized.content,
                normalized.distributionStartAt, normalized.distributionEndAt, normalized.claimEndAt,
                normalized.maxDistributionCount, objectMapper.writeValueAsString(normalized.eligibility),
                normalized.perUserClaimLimit, objectMapper.writeValueAsString(normalized.rewards), normalized.active
            )
        } catch (error: DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 우편 ID입니다.", error)
        }
        val created = find(normalized.id)
        audit(normalized.id, "CREATE", null, created, adminId)
        return created
    }

    @Transactional
    fun update(mailId: String, request: MailboxAdminMailRequest, adminId: String): MailboxAdminMail {
        require(request.id == mailId) { "우편 ID는 수정할 수 없습니다." }
        val normalized = validate(request)
        val before = find(mailId)
        jdbcTemplate.update(
            """
            UPDATE mailbox
            SET name = ?, content = ?, distribution_start_at = ?, distribution_end_at = ?,
                claim_end_at = ?, max_distribution_count = ?, eligibility = CAST(? AS jsonb),
                per_user_claim_limit = ?, rewards = CAST(? AS jsonb), active = ?, updated_at = NOW()
            WHERE _id = ?
            """.trimIndent(),
            normalized.name, normalized.content,
            normalized.distributionStartAt, normalized.distributionEndAt, normalized.claimEndAt,
            normalized.maxDistributionCount, objectMapper.writeValueAsString(normalized.eligibility),
            normalized.perUserClaimLimit, objectMapper.writeValueAsString(normalized.rewards),
            normalized.active, mailId
        )
        val after = find(mailId)
        audit(mailId, "UPDATE", before, after, adminId)
        return after
    }

    @Transactional
    fun delete(mailId: String, adminId: String) {
        val before = find(mailId)
        jdbcTemplate.update("DELETE FROM mailbox WHERE _id = ?", mailId)
        audit(mailId, "DELETE", before, null, adminId)
    }

    fun audits(
        page: Int,
        size: Int,
        sort: String,
        mailId: String,
        action: String,
        admin: String
    ): ListResponse<MailboxAuditEntry> {
        require(page >= 0) { "페이지는 0 이상이어야 합니다." }
        require(size in 1..150) { "페이지 크기는 1~150이어야 합니다." }
        val (sortColumn, sortDirection) = parseSort(sort)
        val where = mutableListOf<String>()
        val parameters = mutableListOf<Any>()
        if (mailId.isNotBlank()) {
            where += "mail_id ILIKE ?"
            parameters += "%${mailId.trim()}%"
        }
        if (action.isNotBlank()) {
            where += "action = ?"
            parameters += action.trim().uppercase()
        }
        if (admin.isNotBlank()) {
            where += "admin_id ILIKE ?"
            parameters += "%${admin.trim()}%"
        }
        val whereSql = if (where.isEmpty()) "" else " WHERE ${where.joinToString(" AND ")}"
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mailbox_audit_log$whereSql",
            Int::class.java,
            *parameters.toTypedArray()
        )
        val content = jdbcTemplate.query(
            """
            SELECT id, mail_id, action, before_data, after_data, admin_id, created_at
            FROM mailbox_audit_log$whereSql
            ORDER BY $sortColumn $sortDirection
            LIMIT ? OFFSET ?
            """.trimIndent(),
            ::mapAudit,
            *(parameters + listOf(size, page * size)).toTypedArray()
        )
        return ListResponse(count ?: 0, content)
    }

    private fun validate(request: MailboxAdminMailRequest): MailboxAdminMailRequest {
        val id = request.id.trim()
        val name = request.name.trim()
        val content = request.content.trim()
        require(idPattern.matches(id)) { "우편 ID는 영문, 숫자, 점, 밑줄, 콜론, 하이픈만 사용할 수 있습니다." }
        require(name.isNotEmpty() && name.length <= 100) { "우편 이름은 1~100자여야 합니다." }
        require(content.length <= 4_000 && content.none { it == '\u0000' }) { "우편 내용은 4,000자 이하여야 합니다." }
        require(request.distributionStartAt > 0) { "배포 시작 일시가 필요합니다." }
        require(request.distributionEndAt > request.distributionStartAt) { "배포 종료 일시는 시작 일시보다 늦어야 합니다." }
        require(request.claimEndAt >= request.distributionEndAt) { "수령 종료 일시는 배포 종료 일시보다 빠를 수 없습니다." }
        require(request.maxDistributionCount == -1 || request.maxDistributionCount >= 0) { "최대 배포 횟수는 -1 또는 0 이상이어야 합니다." }
        require(request.perUserClaimLimit in 1..10) { "사용자별 지급 횟수 제한은 1~10이어야 합니다." }
        validateEligibility(request.eligibility, 0, intArrayOf(0))
        validateRewards(request.rewards)
        return request.copy(id = id, name = name, content = content)
    }

    private fun validateEligibility(node: JsonNode, depth: Int, count: IntArray) {
        require(node.isObject) { "지급 조건은 JSON 객체여야 합니다." }
        require(depth <= 10) { "지급 조건 중첩은 10단계를 넘을 수 없습니다." }
        if (node.size() == 0) return
        count[0] += 1
        require(count[0] <= 50) { "지급 조건은 최대 50개까지 설정할 수 있습니다." }
        val combinators = listOf("all", "any", "not").filter(node::has)
        if (combinators.isNotEmpty()) {
            require(combinators.size == 1 && node.size() == 1) { "조건 결합자는 all, any, not 중 하나만 사용할 수 있습니다." }
            when (val key = combinators.single()) {
                "all", "any" -> {
                    val children = node[key]
                    require(children.isArray && children.size() > 0) { "$key 조건에는 하나 이상의 하위 조건이 필요합니다." }
                    children.forEach { validateEligibility(it, depth + 1, count) }
                }
                "not" -> validateEligibility(node[key], depth + 1, count)
            }
            return
        }
        val type = node.path("type").asText()
        val operator = node.path("operator").asText("eq")
        require(type in conditionTypes) { "지원하지 않는 지급 조건입니다: $type" }
        require(operator in operators) { "지원하지 않는 조건 연산자입니다: $operator" }
        if (operator == "in") require(node.path("value").isArray) { "in 연산자의 값은 배열이어야 합니다." }
        when (type) {
            "userId" -> {
                if (operator == "in") {
                    require(node.path("value").size() > 0 && node.path("value").all { it.isTextual && it.asText().isNotBlank() }) {
                        "사용자 ID 목록에는 하나 이상의 문자열 ID가 필요합니다."
                    }
                } else require(node.path("value").isTextual && node.path("value").asText().isNotBlank()) { "사용자 ID가 필요합니다." }
            }
            "flag" -> {
                require(node.path("key").asText().isNotBlank()) { "플래그 키가 필요합니다." }
                require(node.has("value")) { "플래그 비교 값이 필요합니다." }
            }
            "level", "experience" -> require(node.path("value").isNumber) { "레벨/경험치 조건 값은 숫자여야 합니다." }
            "item" -> {
                require(node.path("itemId").asText().isNotBlank()) { "아이템 ID가 필요합니다." }
                require(node.path("value").isNumber) { "아이템 수량 조건 값은 숫자여야 합니다." }
            }
            "lastLoginDays" -> require(node.path("days").canConvertToInt() && node.path("days").asInt() >= 0) { "최근 접속 일수는 0 이상이어야 합니다." }
        }
    }

    private fun validateRewards(node: JsonNode) {
        require(node.isArray) { "보상은 JSON 배열이어야 합니다." }
        require(node.size() <= 20) { "보상은 최대 20개까지 설정할 수 있습니다." }
        node.forEach { reward ->
            require(reward.isObject) { "각 보상은 JSON 객체여야 합니다." }
            val type = reward.path("type").asText()
            require(type == "ping" || type == "item") { "보상 종류는 ping 또는 item이어야 합니다." }
            require(reward.path("q").canConvertToInt() && reward.path("q").asInt() > 0) { "보상 수량은 1 이상이어야 합니다." }
            if (type == "item") require(reward.path("name").asText().isNotBlank()) { "아이템 보상에는 아이템 ID가 필요합니다." }
            if (reward.has("expire")) require(reward.path("expire").asLong() >= 0) { "아이템 만료 시간은 0 이상이어야 합니다." }
        }
    }

    private fun find(mailId: String): MailboxAdminMail = jdbcTemplate.query(
        """
        SELECT _id, name, content, distribution_start_at, distribution_end_at, claim_end_at,
               max_distribution_count, distributed_count, eligibility, per_user_claim_limit,
               rewards, active, created_at, updated_at
        FROM mailbox WHERE _id = ?
        """.trimIndent(),
        ::mapMail,
        mailId
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "우편을 찾을 수 없습니다.")

    private fun audit(mailId: String, action: String, before: MailboxAdminMail?, after: MailboxAdminMail?, adminId: String) {
        jdbcTemplate.update(
            """
            INSERT INTO mailbox_audit_log (mail_id, action, before_data, after_data, admin_id)
            VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
            """.trimIndent(),
            mailId,
            action,
            before?.let(objectMapper::writeValueAsString),
            after?.let(objectMapper::writeValueAsString),
            adminId
        )
    }

    private fun parseSort(sort: String): Pair<String, String> {
        val values = sort.split(',', limit = 2)
        val column = when (values.firstOrNull()?.trim()) {
            "id" -> "id"
            "mail_id" -> "mail_id"
            "action" -> "action"
            "admin_id" -> "admin_id"
            else -> "created_at"
        }
        val direction = if (values.getOrNull(1)?.trim()?.uppercase() == "ASC") "ASC" else "DESC"
        return column to direction
    }

    private fun mapMail(rs: ResultSet, ignored: Int) = MailboxAdminMail(
        id = rs.getString("_id"),
        name = rs.getString("name"),
        content = rs.getString("content"),
        distributionStartAt = rs.getLong("distribution_start_at"),
        distributionEndAt = rs.getLong("distribution_end_at"),
        claimEndAt = rs.getLong("claim_end_at"),
        maxDistributionCount = rs.getInt("max_distribution_count"),
        distributedCount = rs.getInt("distributed_count"),
        eligibility = objectMapper.readTree(rs.getString("eligibility")),
        perUserClaimLimit = rs.getInt("per_user_claim_limit"),
        rewards = objectMapper.readTree(rs.getString("rewards")),
        active = rs.getBoolean("active"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )

    private fun mapAudit(rs: ResultSet, ignored: Int) = MailboxAuditEntry(
        id = rs.getLong("id"),
        mailId = rs.getString("mail_id"),
        action = rs.getString("action"),
        beforeData = rs.getString("before_data")?.let(objectMapper::readTree),
        afterData = rs.getString("after_data")?.let(objectMapper::readTree),
        adminId = rs.getString("admin_id"),
        createdAt = rs.getTimestamp("created_at").toInstant()
    )
}
