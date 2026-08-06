package me.kkutuio.kkutuweb.moderation

import me.kkutuio.kkutuweb.admin.SortType
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.factory.DateFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ModerationAuditService(
    private val jdbcTemplate: JdbcTemplate,
    private val ipSubjectCodec: IpSubjectCodec
) {
    private val auditEntriesQuery = """
        SELECT 'CASE_ISSUED:' || c.case_id AS id,
               c.issued_at AS audit_time,
               'SANCTION_ISSUED' AS log_type,
               c.subject_type AS target_type,
               c.subject_user_id AS target_id,
               c.subject_ip_encrypted AS target_ip_encrypted,
               c.summary AS detail,
               c.issued_by AS admin,
               c.case_id,
               NULL::BIGINT AS report_id
        FROM moderation_cases c

        UNION ALL

        SELECT 'CASE_REVOKED:' || c.case_id,
               c.revoked_at,
               'SANCTION_REVOKED',
               c.subject_type,
               c.subject_user_id,
               c.subject_ip_encrypted,
               c.revoke_reason,
               c.revoked_by,
               c.case_id,
               NULL::BIGINT
        FROM moderation_cases c
        WHERE c.revoked_at IS NOT NULL

        UNION ALL

        SELECT 'COMMAND:' || cr.request_id,
               cr.created_at,
               'COUNTER_ADJUSTED',
               'USER',
               regexp_replace(cr.target_key, ':[^:]+$', ''),
               NULL::BYTEA,
               concat(
                   '위반 항목 ', cr.response_body ->> 'categoryCode', ' 누적 ',
                   cr.response_body ->> 'previousValue', '회 → ',
                   cr.response_body ->> 'adjustedValue', '회 · ',
                   cr.response_body ->> 'reason'
               ),
               cr.actor_id,
               NULL::BIGINT,
               NULL::BIGINT
        FROM moderation_command_requests cr
        WHERE cr.command_type = 'ADJUST_COUNTER'

        UNION ALL

        SELECT 'COMMAND:' || cr.request_id,
               cr.created_at,
               CASE cr.command_type
                   WHEN 'CHANGE_NICKNAME' THEN 'NICKNAME_CHANGED'
                   WHEN 'DISCONNECT_USER' THEN 'USER_DISCONNECTED'
                   ELSE 'FLAGS_UPDATED'
               END,
               'USER',
               cr.target_key,
               NULL::BYTEA,
               CASE cr.command_type
                   WHEN 'CHANGE_NICKNAME' THEN concat(
                       COALESCE(cr.response_body ->> 'oldNickname', '(없음)'), ' → ',
                       cr.response_body ->> 'nickname', ' · ', cr.response_body ->> 'reason'
                   )
                   WHEN 'DISCONNECT_USER' THEN cr.response_body ->> 'reason'
                   ELSE concat(
                       cr.response_body ->> 'flagCount', '개 플래그 저장 · ',
                       cr.response_body ->> 'reason'
                   )
               END,
               cr.actor_id,
               NULL::BIGINT,
               NULL::BIGINT
        FROM moderation_command_requests cr
        WHERE cr.command_type IN ('CHANGE_NICKNAME', 'DISCONNECT_USER', 'UPDATE_USER_FLAGS')

        UNION ALL

        SELECT 'REPORT_RESOLVED:' || r.report_id,
               r.resolved_at,
               CASE r.status
                   WHEN 'RESOLVED' THEN 'REPORT_RESOLVED'
                   WHEN 'OVERRIDDEN' THEN 'REPORT_OVERRIDDEN'
                   ELSE 'REPORT_REJECTED'
               END,
               CASE
                   WHEN r.target_id LIKE 'guest\_\_%' ESCAPE '\' THEN 'GUEST'
                   WHEN r.target_type IN ('ROOM', 'UGC') THEN r.target_type
                   ELSE 'USER'
               END,
               r.target_id,
               NULL::BYTEA,
               r.resolution_note,
               r.resolved_by,
               mcr.case_id,
               r.report_id
        FROM report_log r
        LEFT JOIN LATERAL (
            SELECT case_id FROM moderation_case_reports
            WHERE report_id = r.report_id
            ORDER BY case_id DESC LIMIT 1
        ) mcr ON TRUE
        WHERE r.resolved_at IS NOT NULL AND r.resolved_by IS NOT NULL

        UNION ALL

        SELECT 'COMMAND:' || cr.request_id,
               cr.created_at,
               'REPORT_GAME_LINKED',
               CASE
                   WHEN r.target_id LIKE 'guest\_\_%' ESCAPE '\' THEN 'GUEST'
                   WHEN r.target_type IN ('ROOM', 'UGC') THEN r.target_type
                   ELSE 'USER'
               END,
               r.target_id,
               NULL::BYTEA,
               concat(
                   '신고 #', cr.target_key, ' · 경기 ', cr.response_body ->> 'gameId',
                   ' 연결 · ', cr.response_body ->> 'reason'
               ),
               cr.actor_id,
               NULL::BIGINT,
               r.report_id
        FROM moderation_command_requests cr
        JOIN report_log r ON r.report_id = CASE
            WHEN cr.command_type = 'LINK_REPORT_GAME' THEN cr.target_key::BIGINT
            ELSE NULL
        END
        WHERE cr.command_type = 'LINK_REPORT_GAME'
    """.trimIndent()

    private val sortableFields = mapOf(
        "id" to "id",
        "log_time" to "audit_time",
        "log_type" to "log_type",
        "target_type" to "target_type",
        "target_id" to "target_id",
        "detail" to "detail",
        "admin" to "admin"
    )

    private val filterableFields = mapOf(
        "log_type" to "log_type",
        "target_id" to "target_id",
        "detail" to "detail",
        "admin" to "admin"
    )

    @Transactional(readOnly = true)
    fun getAudits(
        page: Int,
        pageSize: Int,
        sortData: String,
        filters: Map<String, String>,
        includeIpAddress: Boolean
    ): ListResponse<ModerationAuditEntry> {
        require(page >= 0) { "페이지 번호는 0 이상이어야 합니다." }
        require(pageSize in 1..150) { "페이지 크기는 1에서 150 사이여야 합니다." }

        val sortParts = sortData.split(',', limit = 2)
        require(sortParts.size == 2) { "정렬 조건이 올바르지 않습니다." }
        val sortField = sortableFields[sortParts[0]]
            ?: throw IllegalArgumentException("정렬할 수 없는 항목입니다.")
        val sortType = SortType.valueOf(sortParts[1].uppercase())

        val activeFilters = filters
            .filterValues { it.isNotBlank() }
            .mapNotNull { (key, value) -> filterableFields[key]?.let { it to value.trim() } }
        val whereClause = if (activeFilters.isEmpty()) "" else activeFilters
            .joinToString(prefix = "WHERE ", separator = " AND ") { (field, _) ->
                "CAST($field AS TEXT) ILIKE ?"
            }
        val parameters = activeFilters.map { (_, value) -> "%$value%" }.toTypedArray()

        val total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ($auditEntriesQuery) audit_entries $whereClause",
            Int::class.java,
            *parameters
        )
        val pageParameters = parameters.toMutableList<Any>().apply {
            add(pageSize)
            add(page * pageSize)
        }.toTypedArray()
        val rows = jdbcTemplate.query(
            """
            SELECT * FROM ($auditEntriesQuery) audit_entries
            $whereClause
            ORDER BY $sortField ${sortType.name}, id ${sortType.name}
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                val targetType = rs.getString("target_type")
                val targetId = if (targetType == "IP") {
                    if (includeIpAddress) rs.getBytes("target_ip_encrypted")?.let(ipSubjectCodec::decrypt) else null
                } else {
                    rs.getString("target_id")
                }
                val caseId = rs.getLong("case_id").let { if (rs.wasNull()) null else it }
                val reportId = rs.getLong("report_id").let { if (rs.wasNull()) null else it }
                ModerationAuditEntry(
                    id = rs.getString("id"),
                    logTime = DateFactory.DATABASE_FORMAT.format(rs.getTimestamp("audit_time").toLocalDateTime()),
                    logType = rs.getString("log_type"),
                    targetType = targetType,
                    targetId = targetId,
                    detail = rs.getString("detail") ?: "",
                    admin = rs.getString("admin") ?: "",
                    caseId = caseId,
                    reportId = reportId
                )
            },
            *pageParameters
        )

        val sanctionCaseIds = rows
            .filter { it.logType == "SANCTION_ISSUED" || it.logType == "SANCTION_REVOKED" }
            .mapNotNull { it.caseId }
            .distinct()
        val reportsByCase = loadReportsByCase(sanctionCaseIds)
        val reportsById = loadReportsById(rows.mapNotNull { it.reportId }.distinct())
        val enrichedRows = rows.map { entry ->
            val reports = buildList {
                if (entry.logType == "SANCTION_ISSUED" || entry.logType == "SANCTION_REVOKED") {
                    entry.caseId?.let { addAll(reportsByCase[it].orEmpty()) }
                }
                entry.reportId?.let { reportsById[it]?.let(::add) }
            }.distinctBy { it.reportId }
            entry.copy(reports = reports)
        }

        return ListResponse(total, enrichedRows)
    }

    private fun loadReportsByCase(caseIds: List<Long>): Map<Long, List<ModerationAuditReportReference>> {
        if (caseIds.isEmpty()) return emptyMap()
        val placeholders = caseIds.joinToString(",") { "?" }
        return jdbcTemplate.query(
            """
            SELECT mcr.case_id, r.report_id, r.time, r.status, r.category_code,
                   r.reason, r.reporter_id, r.target_id, r.target_type
            FROM moderation_case_reports mcr
            JOIN report_log r ON r.report_id = mcr.report_id
            WHERE mcr.case_id IN ($placeholders)
            ORDER BY r.report_id
            """.trimIndent(),
            { rs, _ -> rs.getLong("case_id") to mapReportReference(rs) },
            *caseIds.toTypedArray<Any>()
        ).groupBy({ it.first }, { it.second })
    }

    private fun loadReportsById(reportIds: List<Long>): Map<Long, ModerationAuditReportReference> {
        if (reportIds.isEmpty()) return emptyMap()
        val placeholders = reportIds.joinToString(",") { "?" }
        return jdbcTemplate.query(
            """
            SELECT report_id, time, status, category_code, reason,
                   reporter_id, target_id, target_type
            FROM report_log
            WHERE report_id IN ($placeholders)
            """.trimIndent(),
            { rs, _ -> mapReportReference(rs) },
            *reportIds.toTypedArray<Any>()
        ).associateBy { it.reportId }
    }

    private fun mapReportReference(rs: java.sql.ResultSet) = ModerationAuditReportReference(
        reportId = rs.getLong("report_id"),
        time = DateFactory.DATABASE_FORMAT.format(rs.getTimestamp("time").toLocalDateTime()),
        status = rs.getString("status") ?: "PENDING",
        categoryCode = rs.getString("category_code"),
        reason = rs.getString("reason") ?: "",
        reporterId = rs.getString("reporter_id") ?: "",
        targetId = rs.getString("target_id") ?: "",
        targetType = rs.getString("target_type")
    )
}
