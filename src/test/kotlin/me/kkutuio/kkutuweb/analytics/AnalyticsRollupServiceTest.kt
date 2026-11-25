package me.kkutuio.kkutuweb.analytics

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.BadSqlGrammarException
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.Executor

class AnalyticsRollupServiceTest {
    private class MovingClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(now, zone)
        override fun instant() = now
    }

    /** UTC 로는 25일, 한국 시간으로는 26일 01:30. */
    private val clock = MovingClock(Instant.parse("2026-11-25T16:30:00Z"))
    private val today = LocalDate.of(2026, 11, 26)
    private val dao = FakeAnalyticsDao()
    private fun service(vararg ccu: Int?) = AnalyticsRollupService(dao, players(*ccu), clock, Executor { it.run() })

    private fun computed(date: LocalDate, at: Instant) {
        dao.metrics[date] = DailyMetricRow(date, 1, 1, 0, 0, 0, at)
    }

    @Test
    fun `표본은 연결된 채널의 합을 한국 날짜에 반영한다`() {
        service(12, null, 30).samplePeakCcu()
        service(null, null).samplePeakCcu()
        assertEquals(listOf(today to 42, today to 0), dao.peaks)
    }

    @Test
    fun `표본 저장 실패는 밖으로 나가지 않는다`() {
        dao.failing["savePeak"] = DataAccessResourceFailureException("no table")
        val service = service(1)
        assertDoesNotThrow { service.samplePeakCcu(); service.samplePeakCcu() }
    }

    @Test
    fun `비어 있는 날을 오늘부터 정해진 수만큼 채우고 다음 번에 이어서 채운다`() {
        val service = service()
        assertEquals((0L..4L).map { today.minusDays(it) }, service.rollup(30, 5))
        clock.now = clock.now.plus(Duration.ofMinutes(10))
        // 오늘은 다시 세고, 채운 날은 건너뛴다.
        assertEquals(listOf(today) + (5L..8L).map { today.minusDays(it) }, service.rollup(30, 5))
    }

    @Test
    fun `최근 30일 밖은 세지 않는다`() {
        val service = service()
        val dates = service.rollup(30, 100)
        assertEquals(30, dates.size)
        assertEquals(today.minusDays(29), dates.last())
    }

    @Test
    fun `그날이 끝나기 전에 센 날만 다시 센다`() {
        val yesterday = today.minusDays(1)
        val before = today.minusDays(2)
        computed(today, clock.now) // 방금 셈
        computed(yesterday, startOfKstDay(today).minusSeconds(60)) // 하루가 끝나기 전
        computed(before, startOfKstDay(yesterday)) // 끝난 뒤
        assertEquals(listOf(yesterday), service().rollup(3, 5))
    }

    @Test
    fun `표본만 있는 날은 아직 세지 않은 날이다`() {
        computed(today, Instant.EPOCH)
        assertEquals(listOf(today), service().rollup(1, 1))
    }

    @Test
    fun `조회가 앞당기는 집계는 오늘과 어제뿐이고 5분에 한 번이다`() {
        val service = service()
        service.refreshOnDemand()
        assertEquals(listOf(today, today.minusDays(1)), dao.activityCalls)

        dao.metrics.clear()
        service.refreshOnDemand()
        clock.now = clock.now.plus(Duration.ofMinutes(4))
        service.refreshOnDemand()
        assertEquals(2, dao.activityCalls.size)

        clock.now = clock.now.plus(Duration.ofMinutes(1))
        service.refreshOnDemand()
        assertEquals(4, dao.activityCalls.size)
    }

    @Test
    fun `지표 테이블이 없으면 접속 기록을 읽지 않는다`() {
        dao.failing["dailyMetrics"] = BadSqlGrammarException("read", "SELECT", SQLException("missing", "42P01"))
        assertEquals(emptyList<LocalDate>(), service().rollup(30, 5))
        assertTrue(dao.activityCalls.isEmpty())
    }

    @Test
    fun `접속 기록 조회가 실패하면 거기서 멈추고 예외를 던지지 않는다`() {
        dao.activityOf = { if (it == today) ActivityCount(1, 1, 0) else throw DataAccessResourceFailureException("timeout") }
        val service = service()
        assertEquals(listOf(today), service.rollup(30, 5))
        assertEquals(listOf(today, today.minusDays(1)), dao.activityCalls)
        assertDoesNotThrow { service.scheduleRollup() } // 잠금도 풀려 있다
    }

    @Test
    fun `새 계정 수를 저장하고, 가입 시각이 없는 DB 에서는 첫 접속 수를 쓴다`() {
        service().rollup(1, 1)
        assertEquals(3, dao.saved.last().newUsers)
        assertEquals(4, dao.saved.last().returningUsers)

        dao.metrics.clear()
        dao.failing["newAccounts"] = BadSqlGrammarException("count", "SELECT", SQLException("no column", "42703"))
        service().rollup(1, 1)
        assertEquals(6, dao.saved.last().newUsers) // dau 10 - 재방문 4
    }

    @Test
    fun `일시적인 계정 조회 실패는 대신하는 값으로 저장하지 않는다`() {
        dao.failing["newAccounts"] = DataAccessResourceFailureException("connection reset")
        assertEquals(emptyList<LocalDate>(), service().rollup(1, 1))
        assertTrue(dao.saved.isEmpty())
    }
}
