package me.kkutuio.kkutuweb.analytics

import me.kkutuio.kkutuweb.RecordingJdbcTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

class AnalyticsDaoTest {
    private fun at(instant: String): Timestamp = Timestamp.from(Instant.parse(instant))

    @Test
    fun `접속 기록은 한국 날짜 30일 범위만 읽고 손님은 뺀다`() {
        val jdbc = RecordingJdbcTemplate()
        AnalyticsDao(jdbc.mock).activity(LocalDate.of(2026, 11, 26))

        val call = jdbc.calls.single()
        assertTrue(call.sql.contains("WHERE time >= ? AND time < ? AND user_id NOT LIKE 'guest\\_\\_%' ESCAPE '\\'"), call.sql)
        val dayStart = at("2026-11-25T15:00:00Z")
        assertEquals(listOf<Any?>(dayStart, dayStart, at("2026-10-27T15:00:00Z"), at("2026-11-26T15:00:00Z")), call.values)
    }

    @Test
    fun `인기 상품은 구매 횟수 순이고 값이 없는 상품의 매출은 0 이다`() {
        fun row(cost: Long, name: String?) = RecordingJdbcTemplate {
            Mockito.mock(ResultSet::class.java).also { rs ->
                Mockito.`when`(rs.getString("_id")).thenReturn("item")
                Mockito.`when`(rs.getString("group")).thenReturn("BDG1")
                Mockito.`when`(rs.getString("name")).thenReturn(name)
                Mockito.`when`(rs.getLong("cost")).thenReturn(cost)
                Mockito.`when`(rs.getLong("hit")).thenReturn(3_000_000L)
            }
        }
        val paid = row(1000, "반짝 이름표")
        assertEquals(
            listOf(AnalyticsTopItem("item", "반짝 이름표", "BDG1", 1000, 3_000_000, 3_000_000_000)),
            AnalyticsDao(paid.mock).topItems(6)
        )
        assertTrue(paid.calls.single().sql.contains("ORDER BY s.hit DESC"))
        assertEquals(listOf<Any?>(6), paid.calls.single().values)
        // 판매하지 않는 상품(음수 값)과 이름이 없는 상품
        assertEquals(
            listOf(AnalyticsTopItem("item", "item", "BDG1", -1, 3_000_000, 0)),
            AnalyticsDao(row(-1, null).mock).topItems(6)
        )
    }
}
