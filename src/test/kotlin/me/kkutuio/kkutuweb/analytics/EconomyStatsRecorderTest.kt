package me.kkutuio.kkutuweb.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class EconomyStatsRecorderTest {
    /** UTC 로는 25일, 한국 시간으로는 26일. */
    private val clock = Clock.fixed(Instant.parse("2026-11-25T16:30:00Z"), ZoneOffset.UTC)
    private val writes = mutableListOf<List<Any?>>()
    private var failure: RuntimeException? = null
    private val jdbc = Mockito.mock(JdbcTemplate::class.java) { invocation ->
        failure?.let { throw it }
        writes.add(invocation.arguments.toList())
        1
    }
    private val recorder = EconomyStatsRecorder(jdbc, clock, Executor { it.run() })

    @AfterEach
    fun clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization()
    }

    private fun row(direction: String, source: String, amount: Long) =
        listOf<Any?>(EconomyStatsRecorder.UPSERT, LocalDate.of(2026, 11, 26), direction, source, amount)

    @Test
    fun `늘면 유입, 줄면 소각이고 금액은 양수로 한국 날짜에 더한다`() {
        recorder.record(EconomySource.SANCTION_REVOKE, 120)
        recorder.record(EconomySource.ENCHANT, -300)

        assertEquals(listOf(row("INFLOW", "SANCTION_REVOKE", 120), row("SINK", "ENCHANT", 300)), writes)
    }

    @Test
    fun `변화가 없으면 기록하지 않는다`() {
        recorder.record(EconomySource.NICK_CHANGE, 0)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `저장 문장은 매개 변수만 쓰고 금액과 건수를 더한다`() {
        val sql = EconomyStatsRecorder.UPSERT
        assertEquals(4, sql.count { it == '?' })
        assertTrue(sql.contains("total_amount = economy_daily_stat.total_amount + EXCLUDED.total_amount"), sql)
        assertTrue(sql.contains("tx_count = economy_daily_stat.tx_count + EXCLUDED.tx_count"), sql)
    }

    @Test
    fun `트랜잭션 안에서는 커밋한 뒤에만 기록한다`() {
        TransactionSynchronizationManager.initSynchronization()
        recorder.record(EconomySource.ENCHANT, -10)
        assertTrue(writes.isEmpty(), "커밋 전에는 쓰지 않는다")

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        assertEquals(listOf(row("SINK", "ENCHANT", 10)), writes)
    }

    @Test
    fun `되돌린 트랜잭션은 세지 않는다`() {
        TransactionSynchronizationManager.initSynchronization()
        recorder.record(EconomySource.ENCHANT, -10)
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(1) } // rolled back
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `DB 오류는 밖으로 나가지 않는다`() {
        failure = org.springframework.dao.DataAccessResourceFailureException("relation \"economy_daily_stat\" does not exist")
        assertDoesNotThrow { recorder.record(EconomySource.SHOP_BUY, -5) }

        TransactionSynchronizationManager.initSynchronization()
        recorder.record(EconomySource.SHOP_BUY, -5)
        assertDoesNotThrow { TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() } }
    }

    @Test
    fun `대기열이 거절해도 밖으로 나가지 않는다`() {
        val rejecting = EconomyStatsRecorder(jdbc, clock, Executor { throw RejectedExecutionException("full") })
        assertDoesNotThrow { rejecting.record(EconomySource.SHOP_BUY, -5) }
        assertDoesNotThrow { rejecting.record(EconomySource.SHOP_BUY, Long.MIN_VALUE) } // 절댓값을 낼 수 없는 값
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `실제 쓰기 스레드는 요청 스레드와 따로 돌고 종료할 때 남은 것을 비운다`() {
        val real = EconomyStatsRecorder(jdbc)
        real.record(EconomySource.ENCHANT, -1)
        real.close()
        assertEquals(1, writes.size)
        assertDoesNotThrow { real.record(EconomySource.ENCHANT, -1) } // 종료한 뒤
        assertEquals(1, writes.size)
    }

    @Test
    fun `출처 코드의 화면 이름`() {
        assertEquals(
            mapOf(
                "GAME_REWARD" to "게임 보상", "LEVEL_REWARD" to "레벨 보상", "EVENT_REWARD" to "행사 보상", "MAILBOX" to "우편함",
                "COUPON" to "쿠폰", "TUTORIAL" to "튜토리얼", "SHOP_SELL" to "상점 판매", "SHOP_BUY" to "상점 구매",
                "ENCHANT" to "아이템 강화", "NICK_CHANGE" to "별명 변경", "FAMILY_CREATE" to "패밀리 생성",
                "SANCTION_REVOKE" to "제재 복구", "SANCTION_DEDUCT" to "제재 회수", "OTHER" to "기타"
            ),
            EconomySource.entries.associate { it.name to it.label }
        )
        assertEquals("기타", EconomySource.labelOf("SOMETHING_NEW"))
    }
}
