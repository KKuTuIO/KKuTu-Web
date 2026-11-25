package me.kkutuio.kkutuweb.analytics

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * `economy_daily_stat.source` 에 쓰는 코드와 화면 이름. 코드는 게임 서버와 함께 쓰는 약속이므로 이름을 바꾸지 않는다.
 * 목록에 없는 코드는 `기타` 로 보인다.
 */
enum class EconomySource(val label: String) {
    GAME_REWARD("게임 보상"),
    LEVEL_REWARD("레벨 보상"),
    EVENT_REWARD("행사 보상"),
    MAILBOX("우편함"),
    COUPON("쿠폰"),
    TUTORIAL("튜토리얼"),
    SHOP_SELL("상점 판매"),
    SHOP_BUY("상점 구매"),
    ENCHANT("아이템 강화"),
    NICK_CHANGE("별명 변경"),
    FAMILY_CREATE("패밀리 생성"),
    SANCTION_REVOKE("제재 복구"),
    SANCTION_DEDUCT("제재 회수"),
    OTHER("기타");

    companion object {
        fun labelOf(code: String): String = entries.firstOrNull { it.name == code }?.label ?: OTHER.label
    }
}

enum class EconomyDirection { INFLOW, SINK }

/**
 * 웹에서 핑이 바뀐 만큼을 날짜별 통계에 더한다. 통계는 부가 기능이라 **본래 작업을 절대 방해하지 않는다.**
 *
 * - 트랜잭션 안에서 부르면 커밋된 뒤에만 기록한다. 되돌린 변경은 세지 않고, 통계 행의 잠금을 본래 트랜잭션이 쥐지 않는다.
 * - 기록은 전용 스레드 하나가 자기 연결로 한다. PostgreSQL 은 문장 하나가 실패하면 트랜잭션 전체가 못 쓰게 되므로,
 *   같은 트랜잭션에서 예외만 삼키는 방식으로는 강화나 제재를 지킬 수 없다.
 * - 실패하면 경고만 남긴다. 다시 시도하지 않는다.
 */
@Component
class EconomyStatsRecorder(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    private val executor: Executor
) {
    @Autowired
    constructor(jdbc: JdbcTemplate) : this(jdbc, Clock.systemUTC(), newWriter())

    private val logger = LoggerFactory.getLogger(EconomyStatsRecorder::class.java)

    /** [delta] 는 보유 핑의 변화량이다. 양수면 유입, 음수면 소각, 0 이면 기록하지 않는다. */
    fun record(source: EconomySource, delta: Long) {
        if (delta == 0L) return
        try {
            val direction = if (delta > 0) EconomyDirection.INFLOW else EconomyDirection.SINK
            val amount = Math.absExact(delta)
            val date = LocalDate.now(clock.withZone(ANALYTICS_ZONE)) // 늦게 쓰더라도 바뀐 시각의 날짜
            val write = { submit(date, direction, source, amount) }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() = write()
                })
            } else {
                write()
            }
        } catch (error: Exception) {
            logger.warn("핑 통계를 기록하지 못했습니다 (source={}, delta={}): {}", source, delta, error.message)
        }
    }

    private fun submit(date: LocalDate, direction: EconomyDirection, source: EconomySource, amount: Long) {
        try {
            executor.execute {
                try {
                    jdbc.update(UPSERT, date, direction.name, source.name, amount)
                } catch (error: Exception) {
                    logger.warn("핑 통계를 저장하지 못했습니다 (date={}, source={}, amount={}): {}", date, source, amount, error.message)
                }
            }
        } catch (error: Exception) { // 대기열이 가득 찼거나 종료 중
            logger.warn("핑 통계를 대기열에 넣지 못했습니다 (date={}, source={}, amount={}): {}", date, source, amount, error.message)
        }
    }

    @PreDestroy
    fun close() {
        val service = executor as? ExecutorService ?: return
        service.shutdown()
        if (!service.awaitTermination(5, TimeUnit.SECONDS)) logger.warn("저장하지 못한 핑 통계가 남은 채 종료합니다.")
    }

    internal companion object {
        const val UPSERT = "INSERT INTO economy_daily_stat (stat_date, direction, source, total_amount, tx_count) VALUES (?, ?, ?, ?, 1) " +
            "ON CONFLICT (stat_date, direction, source) DO UPDATE SET " +
            "total_amount = economy_daily_stat.total_amount + EXCLUDED.total_amount, " +
            "tx_count = economy_daily_stat.tx_count + EXCLUDED.tx_count, updated_at = now()"

        private const val QUEUE_LIMIT = 10_000

        private fun newWriter(): ExecutorService = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE_LIMIT)
        ) { task -> Thread(task, "economy-stats").apply { isDaemon = true } }
    }
}
