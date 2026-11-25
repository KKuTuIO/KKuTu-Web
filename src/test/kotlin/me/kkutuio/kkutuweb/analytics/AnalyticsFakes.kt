package me.kkutuio.kkutuweb.analytics

import me.kkutuio.kkutuweb.game.GameClientManager
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate

/** 메모리 위의 지표 저장소. [failing] 에 이름이 있는 조회는 테이블이 없을 때처럼 실패한다. */
internal class FakeAnalyticsDao : AnalyticsDao(Mockito.mock(JdbcTemplate::class.java)) {
    val metrics = mutableMapOf<LocalDate, DailyMetricRow>()
    var economyRows = emptyList<EconomyRow>()
    var items = emptyList<AnalyticsTopItem>()
    var counts = mapOf<String, Long>()
    val failing = mutableMapOf<String, RuntimeException>()

    val activityCalls = mutableListOf<LocalDate>()
    val peaks = mutableListOf<Pair<LocalDate, Int>>()
    val saved = mutableListOf<DailyMetricRow>()
    var urgentBefore: Instant? = null
    var activityOf: (LocalDate) -> ActivityCount = { ActivityCount(10, 40, 4) }
    var newAccountsOf: (LocalDate) -> Long = { 3 }

    private fun check(name: String) {
        failing[name]?.let { throw it }
    }

    override fun dailyMetrics(from: LocalDate, to: LocalDate): List<DailyMetricRow> {
        check("dailyMetrics")
        return metrics.values.filter { it.date in from..to }.sortedBy { it.date }
    }

    override fun activity(date: LocalDate): ActivityCount {
        check("activity")
        activityCalls.add(date)
        return activityOf(date)
    }

    override fun newAccounts(date: LocalDate): Long {
        check("newAccounts")
        return newAccountsOf(date)
    }

    override fun saveActivity(date: LocalDate, activity: ActivityCount, newUsers: Long, computedAt: Instant) {
        check("saveActivity")
        val row = DailyMetricRow(date, activity.dau, activity.mau, newUsers, activity.returningUsers, metrics[date]?.peakCcu ?: 0, computedAt)
        metrics[date] = row
        saved.add(row)
    }

    override fun savePeak(date: LocalDate, ccu: Int) {
        check("savePeak")
        peaks.add(date to ccu)
    }

    override fun economy(from: LocalDate, to: LocalDate): List<EconomyRow> {
        check("economy")
        return economyRows.filter { it.date in from..to }
    }

    override fun topItems(limit: Int): List<AnalyticsTopItem> {
        check("topItems")
        return items.take(limit)
    }

    private fun counted(name: String): Long {
        check(name)
        return counts[name] ?: 0
    }

    override fun pendingReports() = counted("pendingReports")
    override fun openTickets() = counted("openTickets")
    override fun urgentTickets(dueBefore: Instant): Long {
        urgentBefore = dueBefore
        return counted("urgentTickets")
    }
    override fun activeEvents(now: Instant) = counted("activeEvents")
}

internal fun players(vararg values: Int?): GameClientManager = Mockito.mock(GameClientManager::class.java).also {
    Mockito.`when`(it.getPlayers()).thenReturn(values.toList())
}
