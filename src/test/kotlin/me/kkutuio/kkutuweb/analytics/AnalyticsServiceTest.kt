package me.kkutuio.kkutuweb.analytics

import me.kkutuio.kkutuweb.setting.GameServerReconnectSetting
import me.kkutuio.kkutuweb.setting.GameServerSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Executor

class AnalyticsServiceTest {
    /** UTC 로는 25일, 한국 시간으로는 26일. */
    private val now = Instant.parse("2026-11-25T16:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 26)
    private val dao = FakeAnalyticsDao()
    private val setting = Mockito.mock(KKuTuSetting::class.java).also { setting ->
        Mockito.`when`(setting.getGameServers()).thenReturn(listOf("감자", "냉이", "다래").mapIndexed { index, name ->
            GameServerSetting(false, "host", "key", "host", 8080, index.toShort(), GameServerReconnectSetting(true, 30), name)
        })
    }

    private fun service(): AnalyticsService {
        val clients = players(120, null, 7)
        val rollup = AnalyticsRollupService(dao, clients, clock, Executor { it.run() })
        return AnalyticsService(dao, rollup, clients, setting, clock)
    }

    private fun metric(daysAgo: Long, dau: Long, mau: Long, newUsers: Long = 0, returning: Long = 0, peak: Long = 0) {
        val date = today.minusDays(daysAgo)
        // 이미 다 센 날로 둔다(조회가 다시 세지 않게).
        dao.metrics[date] = DailyMetricRow(date, dau, mau, newUsers, returning, peak, now)
    }

    private fun stat(daysAgo: Long, direction: String, source: String, amount: Long, tx: Long = 1) =
        EconomyRow(today.minusDays(daysAgo), direction, source, amount, tx)

    @Test
    fun `7일과 30일은 오늘로 끝나는 날짜를 빠짐없이 순서대로 0 으로 채운다`() {
        metric(0, 100, 900)
        metric(1, 80, 800)
        metric(3, 55, 700, newUsers = 5, returning = 40, peak = 33)

        listOf(7, 30).forEach { days ->
            val overview = service().overview(days)
            assertEquals(days, overview.rangeDays)
            assertEquals((days - 1L downTo 0L).map { today.minusDays(it).toString() }, overview.dailyMetrics.map { it.date })
            val threeAgo = overview.dailyMetrics[days - 4]
            assertEquals(AnalyticsDailyMetric("2026-11-23", 55, 700, 5, 40, 33, 0, 0, 0), threeAgo)
            assertEquals(AnalyticsDailyMetric("2026-11-24", 0, 0, 0, 0, 0, 0, 0, 0), overview.dailyMetrics[days - 3])
        }
    }

    @Test
    fun `핵심 지표는 오늘 값과 어제 대비 증감, 소수 한 자리의 고착도다`() {
        metric(0, 100, 900, newUsers = 12, returning = 70)
        metric(1, 80, 960)
        dao.economyRows = listOf(
            stat(0, "INFLOW", "GAME_REWARD", 500), stat(0, "INFLOW", "MAILBOX", 20),
            stat(0, "SINK", "SHOP_BUY", 300, tx = 4), stat(0, "SINK", "ENCHANT", 50, tx = 2),
            stat(1, "SINK", "SHOP_BUY", 9999, tx = 99)
        )
        dao.counts = mapOf("pendingReports" to 3, "openTickets" to 5, "urgentTickets" to 2, "activeEvents" to 1)

        val kpis = service().overview(7).kpis

        assertEquals(
            AnalyticsKpis(
                dau = 100, dauDeltaPct = 25.0, mau = 900, mauDeltaPct = -6.3, stickinessPct = 11.1, ccu = 127,
                newUsersToday = 12, returningUsersToday = 70, pingIssuedToday = 520, pingSunkToday = 350, shopPurchasesToday = 4,
                pendingReports = 3, openTickets = 5, urgentTickets = 2, activeEvents = 1
            ),
            kpis
        )
        assertEquals(now.plus(Duration.ofDays(3)), dao.urgentBefore)
    }

    @Test
    fun `비교하거나 나눌 값이 없으면 0 이고 고착도는 100 을 넘지 않는다`() {
        metric(0, 5, 0)
        metric(1, 0, 0)
        val empty = service().overview(7).kpis
        assertEquals(listOf(0.0, 0.0, 0.0), listOf(empty.dauDeltaPct, empty.mauDeltaPct, empty.stickinessPct))

        metric(0, 11, 10)
        assertEquals(100.0, service().overview(7).kpis.stickinessPct)
    }

    @Test
    fun `채널은 설정 순서이고 연결이 끊기면 offline 이다`() {
        assertEquals(
            listOf(
                AnalyticsChannel(0, "감자", "online", 120, 0),
                AnalyticsChannel(1, "냉이", "offline", 0, 0),
                AnalyticsChannel(2, "다래", "online", 7, 0)
            ),
            service().overview(7).channels
        )
    }

    @Test
    fun `경제 내역은 기간 합계를 한국어 이름으로 큰 순서로 보여 준다`() {
        dao.economyRows = listOf(
            stat(0, "INFLOW", "GAME_REWARD", 100), stat(6, "INFLOW", "GAME_REWARD", 50), stat(2, "INFLOW", "LEVEL_REWARD", 400),
            stat(1, "INFLOW", "OTHER", 5), stat(1, "INFLOW", "NEW_CODE", 7),
            stat(7, "INFLOW", "COUPON", 100000), // 7일 범위 밖
            stat(0, "SINK", "SHOP_BUY", 30), stat(5, "SINK", "SHOP_BUY", 45), stat(3, "SINK", "SANCTION_DEDUCT", 80),
            stat(3, "SINK", "FAMILY_CREATE", 0)
        )

        val overview = service().overview(7)

        assertEquals(
            listOf(AnalyticsEconomyEntry("레벨 보상", 400), AnalyticsEconomyEntry("게임 보상", 150), AnalyticsEconomyEntry("기타", 12)),
            overview.economyBreakdown.inflow
        )
        assertEquals(
            listOf(AnalyticsEconomyEntry("제재 회수", 80), AnalyticsEconomyEntry("상점 구매", 75)),
            overview.economyBreakdown.sink
        )
        val fiveAgo = overview.dailyMetrics.first { it.date == today.minusDays(5).toString() }
        assertEquals(listOf(0L, 45L, 45L), listOf(fiveAgo.pingIssued, fiveAgo.pingSunk, fiveAgo.shopVolume))
        assertEquals(100000, service().overview(30).economyBreakdown.inflow.first().amount)
    }

    @Test
    fun `인기 상품은 여섯 개까지다`() {
        dao.items = (1..9).map { AnalyticsTopItem("i$it", "상품$it", "BDG1", 10, 100L - it, 10 * (100L - it)) }
        assertEquals(dao.items.take(6), service().overview(7).topItems)
    }

    @Test
    fun `어느 테이블이 없어도 나머지는 보여 주고 예외를 던지지 않는다`() {
        metric(0, 100, 900)
        dao.economyRows = listOf(stat(0, "SINK", "SHOP_BUY", 30))
        dao.counts = mapOf("openTickets" to 5, "pendingReports" to 2)
        listOf("economy", "topItems", "openTickets", "urgentTickets", "activeEvents").forEach {
            dao.failing[it] = DataAccessResourceFailureException("missing $it")
        }

        val partial = service().overview(7)
        assertEquals(100, partial.kpis.dau)
        assertEquals(2, partial.kpis.pendingReports)
        assertEquals(0, partial.kpis.openTickets)
        assertEquals(0, partial.kpis.pingSunkToday)
        assertTrue(partial.economyBreakdown.sink.isEmpty() && partial.topItems.isEmpty())

        listOf("dailyMetrics", "pendingReports").forEach { dao.failing[it] = DataAccessResourceFailureException("missing $it") }
        Mockito.`when`(setting.getGameServers()).thenThrow(IllegalStateException("bad setting"))
        val nothing = service().overview(30)
        assertEquals(30, nothing.dailyMetrics.size)
        assertTrue(nothing.dailyMetrics.all { it.dau == 0L && it.peakCcu == 0L })
        assertTrue(nothing.channels.isEmpty())
        assertEquals(0, nothing.kpis.ccu)
    }
}
