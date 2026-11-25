package me.kkutuio.kkutuweb.analytics

import jakarta.annotation.PreDestroy
import me.kkutuio.kkutuweb.game.GameClientManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * 하루 지표(`analytics_daily_metric`)를 채운다.
 *
 * - 접속자 표본: 1분마다 모든 채널의 접속자 합으로 그날의 최고치를 올린다.
 * - 접속 기록 집계: 10분마다 오늘을 다시 세고, 최근 30일 중 비었거나 그날이 끝나기 전에 센 날짜를 몇 개씩 채운다.
 *   접속 기록이 크므로 스케줄러 스레드(게임 서버 접속자 조회 등과 함께 쓴다)가 아닌 전용 스레드에서 돈다.
 * - 조회 요청은 [refreshOnDemand] 로 오늘과 어제만, 5분에 한 번까지 앞당겨 셀 수 있다.
 *
 * 어느 것도 실패를 밖으로 던지지 않는다.
 */
@Service
class AnalyticsRollupService(
    private val dao: AnalyticsDao,
    private val gameClientManager: GameClientManager,
    private val clock: Clock,
    private val background: Executor
) {
    @Autowired
    constructor(dao: AnalyticsDao, gameClientManager: GameClientManager) : this(
        dao, gameClientManager, Clock.systemUTC(),
        Executors.newSingleThreadExecutor { task -> Thread(task, "analytics-rollup").apply { isDaemon = true } }
    )

    private val logger = LoggerFactory.getLogger(AnalyticsRollupService::class.java)
    private val running = ReentrantLock()
    private val lastOnDemand = AtomicReference<Instant?>(null)
    private val sampleFailing = AtomicBoolean(false)

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    fun samplePeakCcu() {
        try {
            val total = gameClientManager.getPlayers().sumOf { it ?: 0 }
            dao.savePeak(today(), total)
            sampleFailing.set(false)
        } catch (error: Exception) {
            // 1분마다 도는 작업이라, 계속 실패하는 동안에는 한 번만 경고한다.
            if (sampleFailing.compareAndSet(false, true)) logger.warn("최고 접속자 표본을 저장하지 못했습니다: {}", error.message)
        }
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 600_000)
    fun scheduleRollup() {
        try {
            background.execute { rollup(SCHEDULED_LOOKBACK_DAYS, SCHEDULED_MAX_DAYS) }
        } catch (error: Exception) {
            logger.warn("접속 지표 집계를 시작하지 못했습니다: {}", error.message)
        }
    }

    /** 조회 전에 오늘·어제의 지표가 낡았으면 센다. 5분 안에 다시 부르거나 다른 집계가 도는 중이면 바로 돌아온다. */
    fun refreshOnDemand() {
        val now = clock.instant()
        val previous = lastOnDemand.get()
        if (previous != null && Duration.between(previous, now) < ON_DEMAND_INTERVAL) return
        if (!lastOnDemand.compareAndSet(previous, now)) return
        rollup(ON_DEMAND_DAYS, ON_DEMAND_DAYS)
    }

    /**
     * 오늘부터 거꾸로 [lookbackDays] 일 가운데 다시 세야 하는 날을 최대 [maxDays] 개 센다. 센 날짜를 돌려준다.
     * 하나라도 실패하면 거기서 멈춘다(실패하는 DB를 계속 두드리지 않는다).
     */
    internal fun rollup(lookbackDays: Int, maxDays: Int): List<LocalDate> {
        if (!running.tryLock()) return emptyList()
        val done = mutableListOf<LocalDate>()
        try {
            val today = today()
            val from = today.minusDays(lookbackDays - 1L)
            // 저장할 테이블이 없으면 여기서 실패해 접속 기록은 읽지 않는다.
            val computedAt = dao.dailyMetrics(from, today).associate { it.date to it.updatedAt }
            val stale = generateSequence(today) { it.minusDays(1) }
                .takeWhile { it >= from }
                .filter { isStale(it, today, computedAt[it]) }
                .take(maxDays)
                .toList()
            for (date in stale) {
                compute(date)
                done += date
            }
        } catch (error: Exception) {
            logger.warn("접속 지표 집계를 마치지 못했습니다: {}", error.message)
        } finally {
            running.unlock()
        }
        return done
    }

    private fun isStale(date: LocalDate, today: LocalDate, computedAt: Instant?): Boolean = when {
        computedAt == null -> true
        date == today -> Duration.between(computedAt, clock.instant()) >= ON_DEMAND_INTERVAL
        else -> computedAt < startOfKstDay(date.plusDays(1))
    }

    private fun compute(date: LocalDate) {
        val startedAt = clock.instant()
        val activity = dao.activity(date)
        val newUsers = try {
            dao.newAccounts(date)
        } catch (error: BadSqlGrammarException) {
            // 계정 테이블이나 가입 시각이 없는 DB: 최근 30일 안에 처음 접속한 수로 대신한다.
            logger.warn("계정 생성 시각을 읽을 수 없어 첫 접속 수를 씁니다: {}", error.message)
            (activity.dau - activity.returningUsers).coerceAtLeast(0)
        }
        dao.saveActivity(date, activity, newUsers, startedAt)
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ANALYTICS_ZONE))

    @PreDestroy
    fun close() {
        (background as? ExecutorService)?.shutdownNow()
    }

    private companion object {
        val ON_DEMAND_INTERVAL: Duration = Duration.ofMinutes(5)
        const val ON_DEMAND_DAYS = 2
        const val SCHEDULED_LOOKBACK_DAYS = 30
        const val SCHEDULED_MAX_DAYS = 5
    }
}
