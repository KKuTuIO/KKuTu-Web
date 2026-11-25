package me.kkutuio.kkutuweb.analytics

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 운영 지표의 날짜 기준. */
internal val ANALYTICS_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

internal fun startOfKstDay(date: LocalDate): Instant = date.atStartOfDay(ANALYTICS_ZONE).toInstant()

/** 활동 지표를 셀 때 함께 보는 기간(오늘 포함). */
internal const val MAU_WINDOW_DAYS = 30L

data class DailyMetricRow(
    val date: LocalDate,
    val dau: Long,
    val mau: Long,
    val newUsers: Long,
    val returningUsers: Long,
    val peakCcu: Long,
    val updatedAt: Instant
)

data class EconomyRow(val date: LocalDate, val direction: String, val source: String, val amount: Long, val txCount: Long)

/** 하루의 접속 기록 집계. [returningUsers] 는 그날 접속했고 그 전 29일 안에도 접속한 적이 있는 수다. */
data class ActivityCount(val dau: Long, val mau: Long, val returningUsers: Long)

@Repository
class AnalyticsDao(private val jdbc: JdbcTemplate) {
    fun dailyMetrics(from: LocalDate, to: LocalDate): List<DailyMetricRow> = jdbc.query(
        "SELECT stat_date, dau, mau, new_users, returning_users, peak_ccu, updated_at FROM analytics_daily_metric " +
            "WHERE stat_date >= ? AND stat_date <= ? ORDER BY stat_date",
        { rs, _ ->
            DailyMetricRow(
                rs.getObject("stat_date", LocalDate::class.java),
                rs.getLong("dau"), rs.getLong("mau"), rs.getLong("new_users"), rs.getLong("returning_users"), rs.getLong("peak_ccu"),
                rs.getTimestamp("updated_at").toInstant()
            )
        },
        from, to
    )

    /**
     * [date] 하루의 활동. 접속 기록은 [date] 를 포함한 30일 범위만 읽는다(`time` 은 변환하지 않고 경계 값만 넣는다).
     * 손님(`guest__`)은 세지 않는다.
     */
    fun activity(date: LocalDate): ActivityCount {
        val dayStart = Timestamp.from(startOfKstDay(date))
        val dayEnd = Timestamp.from(startOfKstDay(date.plusDays(1)))
        val windowStart = Timestamp.from(startOfKstDay(date.minusDays(MAU_WINDOW_DAYS - 1)))
        return jdbc.query(
            """
            SELECT COUNT(*) FILTER (WHERE seen_today) AS dau,
                   COUNT(*) AS mau,
                   COUNT(*) FILTER (WHERE seen_today AND seen_before) AS returning_users
            FROM (
                SELECT user_id, BOOL_OR(time >= ?) AS seen_today, BOOL_OR(time < ?) AS seen_before
                FROM connection_log
                WHERE time >= ? AND time < ? AND user_id NOT LIKE 'guest\_\_%' ESCAPE '\'
                GROUP BY user_id
            ) visitors
            """.trimIndent(),
            { rs, _ -> ActivityCount(rs.getLong("dau"), rs.getLong("mau"), rs.getLong("returning_users")) },
            dayStart, dayStart, windowStart, dayEnd
        ).firstOrNull() ?: ActivityCount(0, 0, 0)
    }

    /** [date] 하루에 만든 계정 수. */
    fun newAccounts(date: LocalDate): Long = jdbc.queryForObject(
        "SELECT COUNT(*) FROM account WHERE created_at >= ? AND created_at < ?", Long::class.java,
        Timestamp.from(startOfKstDay(date)), Timestamp.from(startOfKstDay(date.plusDays(1)))
    ) ?: 0L

    /** 접속 기록 집계를 저장한다. `peak_ccu` 는 건드리지 않는다. [computedAt] 은 집계를 시작한 시각이다. */
    fun saveActivity(date: LocalDate, activity: ActivityCount, newUsers: Long, computedAt: Instant) {
        jdbc.update(
            "INSERT INTO analytics_daily_metric (stat_date, dau, mau, new_users, returning_users, updated_at) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (stat_date) DO UPDATE SET dau = EXCLUDED.dau, mau = EXCLUDED.mau, new_users = EXCLUDED.new_users, " +
                "returning_users = EXCLUDED.returning_users, updated_at = EXCLUDED.updated_at",
            date, activity.dau, activity.mau, newUsers, activity.returningUsers, Timestamp.from(computedAt)
        )
    }

    /**
     * 접속자 표본을 그날의 최고치에 반영한다. `updated_at` 은 접속 기록 집계 시각이라 여기서는 바꾸지 않고,
     * 표본이 행을 처음 만들 때는 '아직 집계하지 않음'(epoch)으로 둔다.
     */
    fun savePeak(date: LocalDate, ccu: Int) {
        jdbc.update(
            "INSERT INTO analytics_daily_metric (stat_date, peak_ccu, updated_at) VALUES (?, ?, to_timestamp(0)) " +
                "ON CONFLICT (stat_date) DO UPDATE SET peak_ccu = GREATEST(analytics_daily_metric.peak_ccu, EXCLUDED.peak_ccu)",
            date, ccu
        )
    }

    fun economy(from: LocalDate, to: LocalDate): List<EconomyRow> = jdbc.query(
        "SELECT stat_date, direction, source, total_amount, tx_count FROM economy_daily_stat WHERE stat_date >= ? AND stat_date <= ?",
        { rs, _ ->
            EconomyRow(
                rs.getObject("stat_date", LocalDate::class.java), rs.getString("direction"), rs.getString("source"),
                rs.getLong("total_amount"), rs.getLong("tx_count")
            )
        },
        from, to
    )

    fun topItems(limit: Int): List<AnalyticsTopItem> = jdbc.query(
        """
        SELECT s._id, s."group", s.cost, s.hit, d."name_ko_KR" AS name
        FROM kkutu_shop s
        LEFT JOIN kkutu_shop_desc d ON d._id = s._id
        ORDER BY s.hit DESC NULLS LAST, s._id
        LIMIT ?
        """.trimIndent(),
        { rs, _ ->
            val id = rs.getString("_id")
            val cost = rs.getLong("cost")
            val hit = rs.getLong("hit")
            AnalyticsTopItem(
                id = id,
                name = rs.getString("name")?.takeIf { it.isNotBlank() } ?: id,
                group = rs.getString("group").orEmpty(),
                cost = cost,
                hit = hit,
                revenue = if (cost > 0) cost * hit else 0L
            )
        },
        limit
    )

    fun pendingReports(): Long = count("SELECT COUNT(*) FROM report_log WHERE status = 'PENDING'")

    /** 답변이 필요한(접수·처리 중) 문의. 보관한 것은 뺀다. */
    fun openTickets(): Long = count("SELECT COUNT(*) FROM support_ticket ticket WHERE $OPEN_TICKET")

    /** [openTickets] 가운데 아직 답하지 않았고 답변 기한이 [dueBefore] 이전(지난 것 포함)인 문의. */
    fun urgentTickets(dueBefore: Instant): Long = count(
        "SELECT COUNT(*) FROM support_ticket ticket WHERE $OPEN_TICKET AND ticket.last_staff_message_at IS NULL AND ticket.due_at <= ?",
        Timestamp.from(dueBefore)
    )

    fun activeEvents(now: Instant): Long = count(
        "SELECT COUNT(*) FROM game_events WHERE active = TRUE AND starts_at <= ? AND ends_at > ?", now.toEpochMilli(), now.toEpochMilli()
    )

    private fun count(sql: String, vararg values: Any): Long = jdbc.queryForObject(sql, Long::class.java, *values) ?: 0L

    private companion object {
        const val OPEN_TICKET = "ticket.status IN ('RECEIVED', 'IN_PROGRESS') " +
            "AND NOT EXISTS (SELECT 1 FROM support_ticket_archive archive WHERE archive.ticket_id = ticket.id)"
    }
}
