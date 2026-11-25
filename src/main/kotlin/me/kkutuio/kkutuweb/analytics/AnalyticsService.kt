package me.kkutuio.kkutuweb.analytics

import me.kkutuio.kkutuweb.game.GameClientManager
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * 백오피스 홈의 운영 지표. 각 항목은 따로 읽으며, 테이블이 아직 없거나 조회가 실패한 항목은 0 이나 빈 목록으로 채운다.
 * 접속 기록은 여기서 직접 읽지 않는다([AnalyticsRollupService]).
 */
@Service
class AnalyticsService(
    private val dao: AnalyticsDao,
    private val rollup: AnalyticsRollupService,
    private val gameClientManager: GameClientManager,
    private val setting: KKuTuSetting,
    private val clock: Clock
) {
    @Autowired
    constructor(
        dao: AnalyticsDao, rollup: AnalyticsRollupService, gameClientManager: GameClientManager, setting: KKuTuSetting
    ) : this(dao, rollup, gameClientManager, setting, Clock.systemUTC())

    private val logger = LoggerFactory.getLogger(AnalyticsService::class.java)

    fun overview(rangeDays: Int): AnalyticsOverview {
        safe("접속 지표 갱신", Unit) { rollup.refreshOnDemand() }

        val now = clock.instant()
        val today = LocalDate.ofInstant(now, ANALYTICS_ZONE)
        val dates = (rangeDays - 1 downTo 0).map { today.minusDays(it.toLong()) }

        val metrics = safe("하루 지표", emptyList()) { dao.dailyMetrics(dates.first(), today) }.associateBy { it.date }
        val economy = safe("핑 통계", emptyList()) { dao.economy(dates.first(), today) }
        val economyByDate = economy.groupBy { it.date }
        val channels = safe("채널 상태", emptyList()) { channels() }

        val current = metrics[today]
        val previous = metrics[today.minusDays(1)]
        val todayEconomy = economyByDate[today].orEmpty()
        val dau = current?.dau ?: 0
        val mau = current?.mau ?: 0

        return AnalyticsOverview(
            rangeDays = rangeDays,
            kpis = AnalyticsKpis(
                dau = dau,
                dauDeltaPct = deltaPct(current?.dau, previous?.dau),
                mau = mau,
                mauDeltaPct = deltaPct(current?.mau, previous?.mau),
                stickinessPct = if (mau > 0) round1(dau * 100.0 / mau).coerceIn(0.0, 100.0) else 0.0,
                ccu = channels.sumOf { it.ccu.toLong() },
                newUsersToday = current?.newUsers ?: 0,
                returningUsersToday = current?.returningUsers ?: 0,
                pingIssuedToday = todayEconomy.total(EconomyDirection.INFLOW),
                pingSunkToday = todayEconomy.total(EconomyDirection.SINK),
                shopPurchasesToday = todayEconomy.filter { it.isShopPurchase() }.sumOf { it.txCount },
                pendingReports = safe("미처리 신고", 0L) { dao.pendingReports() },
                openTickets = safe("열린 문의", 0L) { dao.openTickets() },
                urgentTickets = safe("긴급 문의", 0L) { dao.urgentTickets(now.plus(URGENT_WITHIN)) },
                activeEvents = safe("진행 중 행사", 0L) { dao.activeEvents(now) }
            ),
            channels = channels,
            dailyMetrics = dates.map { date ->
                val row = metrics[date]
                val stats = economyByDate[date].orEmpty()
                AnalyticsDailyMetric(
                    date = date.toString(),
                    dau = row?.dau ?: 0,
                    mau = row?.mau ?: 0,
                    newUsers = row?.newUsers ?: 0,
                    returningUsers = row?.returningUsers ?: 0,
                    peakCcu = row?.peakCcu ?: 0,
                    pingIssued = stats.total(EconomyDirection.INFLOW),
                    pingSunk = stats.total(EconomyDirection.SINK),
                    shopVolume = stats.filter { it.isShopPurchase() }.sumOf { it.amount }
                )
            },
            economyBreakdown = AnalyticsEconomyBreakdown(
                inflow = breakdown(economy, EconomyDirection.INFLOW),
                sink = breakdown(economy, EconomyDirection.SINK)
            ),
            topItems = safe("인기 상품", emptyList()) { dao.topItems(TOP_ITEMS) }
        )
    }

    /** 채널 번호는 설정(`gameServers`)의 순서다. 접속자 목록도 같은 순서이며, 연결이 끊긴 채널은 값이 없다. */
    private fun channels(): List<AnalyticsChannel> {
        val players = gameClientManager.getPlayers()
        return setting.getGameServers().mapIndexed { index, server ->
            val ccu = players.getOrNull(index)
            AnalyticsChannel(index, server.name, if (ccu != null) "online" else "offline", ccu ?: 0, 0)
        }
    }

    private fun breakdown(rows: List<EconomyRow>, direction: EconomyDirection): List<AnalyticsEconomyEntry> =
        rows.filter { it.direction == direction.name }
            .groupBy { EconomySource.labelOf(it.source) }
            .map { (label, entries) -> AnalyticsEconomyEntry(label, entries.sumOf { it.amount }) }
            .filter { it.amount != 0L }
            .sortedWith(compareByDescending<AnalyticsEconomyEntry> { it.amount }.thenBy { it.label })

    private fun List<EconomyRow>.total(direction: EconomyDirection): Long = filter { it.direction == direction.name }.sumOf { it.amount }

    private fun EconomyRow.isShopPurchase() = direction == EconomyDirection.SINK.name && source == EconomySource.SHOP_BUY.name

    private fun deltaPct(current: Long?, previous: Long?): Double =
        if (current == null || previous == null || previous == 0L) 0.0 else round1((current - previous) * 100.0 / previous)

    /** 소수 한 자리. 증가와 감소를 같은 크기로 반올림한다. */
    private fun round1(value: Double): Double = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toDouble()

    private fun <T> safe(name: String, fallback: T, read: () -> T): T = try {
        read()
    } catch (error: Exception) {
        logger.warn("운영 지표의 '{}' 항목을 읽지 못했습니다: {}", name, error.message)
        fallback
    }

    private companion object {
        const val TOP_ITEMS = 6
        /** 답변 기한이 이만큼 남았으면 긴급이다. */
        val URGENT_WITHIN: Duration = Duration.ofDays(3)
    }
}
