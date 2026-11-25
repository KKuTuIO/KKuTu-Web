package me.kkutuio.kkutuweb.admin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

class KstDateRangeTest {
    private fun at(instant: String): Timestamp = Timestamp.from(Instant.parse(instant))

    @Test
    fun `양 끝 날짜를 한국 시간 기준으로 모두 포함한다`() {
        val range = KstDateRange.parse("2026-05-01", "2026-05-31")

        assertEquals(at("2026-04-30T15:00:00Z"), range.fromInclusive)
        assertEquals(at("2026-05-31T15:00:00Z"), range.toExclusive)
    }

    @Test
    fun `같은 날짜는 하루 전체다`() {
        val range = KstDateRange.parse("2026-12-31", "2026-12-31")

        assertEquals(at("2026-12-30T15:00:00Z"), range.fromInclusive)
        assertEquals(at("2026-12-31T15:00:00Z"), range.toExclusive)
    }

    @Test
    fun `값이 없거나 공백이면 제한을 두지 않는다`() {
        listOf(KstDateRange.parse(null, null), KstDateRange.parse("", "   ")).forEach { range ->
            assertTrue(range.isEmpty)
            assertNull(range.fromInclusive)
            assertNull(range.toExclusive)
        }
    }

    @Test
    fun `한쪽만 지정할 수 있다`() {
        val fromOnly = KstDateRange.parse(" 2026-05-01 ", "")
        val toOnly = KstDateRange.parse(null, "2026-05-01")

        assertEquals(LocalDate.of(2026, 5, 1), fromOnly.from)
        assertNull(fromOnly.toExclusive)
        assertNull(toOnly.fromInclusive)
        assertEquals(at("2026-05-01T15:00:00Z"), toOnly.toExclusive)
    }

    @Test
    fun `시작일이 종료일보다 늦으면 서로 바꾼다`() {
        assertEquals(
            KstDateRange.parse("2026-05-01", "2026-05-10"),
            KstDateRange.parse("2026-05-10", "2026-05-01")
        )
    }

    @Test
    fun `형식이 틀리거나 없는 날짜는 거절한다`() {
        listOf(
            "2026-5-1", "20260501", "2026/05/01", "2026-05-01T00:00:00", "+12026-05-01",
            "2026-02-29", "2026-13-01", "2026-04-31", "today"
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java, { KstDateRange.parse(value, null) }, value)
            assertThrows(IllegalArgumentException::class.java, { KstDateRange.parse(null, value) }, value)
        }
    }

    @Test
    fun `조건과 값을 순서대로 덧붙인다`() {
        val clauses = mutableListOf("user_id = ?")
        val values = mutableListOf<Any>("user")

        KstDateRange.parse("2026-05-01", "2026-05-01").appendConditions("cl.time", clauses, values)

        assertEquals(listOf("user_id = ?", "cl.time >= ?", "cl.time < ?"), clauses)
        assertEquals(listOf<Any>("user", at("2026-04-30T15:00:00Z"), at("2026-05-01T15:00:00Z")), values)
    }
}
