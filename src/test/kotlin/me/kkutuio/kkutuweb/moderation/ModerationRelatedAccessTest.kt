package me.kkutuio.kkutuweb.moderation

import me.kkutuio.kkutuweb.RecordingJdbcTemplate
import me.kkutuio.kkutuweb.admin.KstDateRange
import me.kkutuio.kkutuweb.handler.GlobalExceptionHandler
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.user.User
import me.kkutuio.kkutuweb.user.UserDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class ModerationRelatedAccessTest {
    private val jdbc = RecordingJdbcTemplate { sql ->
        if (!sql.contains("SELECT user_ip, NULLIF")) return@RecordingJdbcTemplate null
        Mockito.mock(ResultSet::class.java).also {
            Mockito.`when`(it.getString("user_ip")).thenReturn("203.0.113.7")
            Mockito.`when`(it.getString("pcid_cookie")).thenReturn("pcid-a")
        }
    }
    private val userDao = Mockito.mock(UserDao::class.java).also {
        Mockito.`when`(it.getUserByIdentifier(USER_ID)).thenReturn(Mockito.mock(User::class.java))
    }
    private val service = ModerationService(
        jdbc.mock, Mockito.mock(), userDao, Mockito.mock(), Mockito.mock(), Mockito.mock(),
        Mockito.mock(), Mockito.mock(), Mockito.mock(), Mockito.mock(), Mockito.mock()
    )

    private fun relatedAccess(
        hours: Int = 72,
        from: String? = null,
        to: String? = null,
        matchType: String = "IP",
        offset: Int = 0
    ) = service.getRelatedAccess(USER_ID, matchType, hours, 10, offset, from, to)

    private fun matchedCall() = jdbc.calls.last { it.sql.contains("WITH matched AS") }

    private fun at(instant: String): Timestamp = Timestamp.from(Instant.parse(instant))

    @Test
    fun `허용한 시간 범위는 현재 시각에서 거꾸로 계산한다`() {
        listOf(24, 48, 72, 168, 336, 720, 2160).forEach { hours ->
            val before = Instant.now()
            val page = relatedAccess(hours = hours)
            val after = Instant.now()

            val call = matchedCall()
            assertTrue(call.sql.contains("WHERE cl.time >= ? AND cl.user_id <> ?"), call.sql)
            assertFalse(call.sql.contains("cl.time < ?"))
            val cutoff = (call.values[0] as Timestamp).toInstant()
            val lookback = Duration.ofHours(hours.toLong())
            assertTrue(cutoff >= before.minus(lookback) && cutoff <= after.minus(lookback), "$hours: $cutoff")
            assertEquals(
                listOf<Any?>(USER_ID, "203.0.113.7", "::ffff:203.0.113.7", 11, 0),
                call.values.drop(1)
            )
            assertEquals(hours, page.hours)
        }
    }

    @Test
    fun `날짜를 지정하지 않으면 목록에 없는 시간 범위를 거절한다`() {
        listOf(-1, 0, 1, 96, 169, 2161, 4320).forEach { hours ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                relatedAccess(hours = hours, from = "", to = " ")
            }
            assertEquals("조회 기간은 24시간, 48시간, 72시간, 7일, 14일, 30일 또는 90일이어야 합니다.", error.message)
        }
        assertTrue(jdbc.calls.isEmpty())
    }

    @Test
    fun `직접 지정한 날짜는 한국 시간 하루 경계로 조회하고 응답 형식을 유지한다`() {
        // 관리자 화면은 직접 지정할 때 시작일부터 지금까지의 시간을 hours로 보낸다.
        val page = relatedAccess(hours = 53, from = "2026-05-02", to = "2026-05-01", offset = 20)

        val call = matchedCall()
        assertTrue(call.sql.contains("WHERE cl.time >= ? AND cl.time < ? AND cl.user_id <> ?"), call.sql)
        assertEquals(
            listOf<Any?>(
                at("2026-04-30T15:00:00Z"), at("2026-05-02T15:00:00Z"), USER_ID,
                "203.0.113.7", "::ffff:203.0.113.7", 11, 20
            ),
            call.values
        )
        assertEquals(
            ModerationRelatedAccessPage("IP", 53, 20, 10, 0, true, emptyList(), false),
            page
        )
    }

    @Test
    fun `PCID 조회에도 같은 날짜 경계를 쓴다`() {
        relatedAccess(matchType = "pcid", from = "2026-05-01", to = "2026-05-01")

        assertEquals(
            listOf<Any?>(
                at("2026-04-30T15:00:00Z"), at("2026-05-01T15:00:00Z"), USER_ID,
                "pcid-a", "pcid-a", 11, 0
            ),
            matchedCall().values
        )
    }

    @Test
    fun `시작일만 지정하면 지금까지 조회한다`() {
        val from = LocalDate.now(KstDateRange.ZONE).minusDays(100)

        relatedAccess(from = from.toString())

        val call = matchedCall()
        assertTrue(call.sql.contains("WHERE cl.time >= ? AND cl.user_id <> ?"), call.sql)
        assertEquals(Timestamp.from(from.atStartOfDay(KstDateRange.ZONE).toInstant()), call.values[0])
        assertEquals(USER_ID, call.values[1])
    }

    @Test
    fun `종료일만 지정하면 그날까지 최대 기간을 조회한다`() {
        relatedAccess(to = "2026-05-01")

        val call = matchedCall()
        assertTrue(call.sql.contains("WHERE cl.time >= ? AND cl.time < ?"), call.sql)
        // 2025-05-01 00:00 KST부터 366일.
        assertEquals(at("2025-04-30T15:00:00Z"), call.values[0])
        assertEquals(at("2026-05-01T15:00:00Z"), call.values[1])
    }

    @Test
    fun `직접 지정하는 기간은 366일까지다`() {
        relatedAccess(from = "2025-01-01", to = "2026-01-01")

        jdbc.calls.clear()
        val today = LocalDate.now(KstDateRange.ZONE)
        listOf(
            "2025-01-01" to "2026-01-02",
            today.minusDays(366).toString() to null
        ).forEach { (from, to) ->
            val error = assertThrows(IllegalArgumentException::class.java) { relatedAccess(from = from, to = to) }
            assertEquals("직접 지정하는 조회 기간은 최대 366일입니다.", error.message)
        }
        assertTrue(jdbc.calls.isEmpty())

        relatedAccess(from = today.minusDays(365).toString())
    }

    @Test
    fun `잘못된 날짜는 조회하지 않는다`() {
        assertThrows(IllegalArgumentException::class.java) { relatedAccess(from = "2026-02-30") }
        assertThrows(IllegalArgumentException::class.java) { relatedAccess(to = "05/01/2026") }
        assertTrue(jdbc.calls.isEmpty())
    }

    @Test
    fun `API는 from과 to를 전달하고 두 권한을 모두 확인한다`() {
        val authorizer = Mockito.mock(AdminModerationAuthorizer::class.java) { "admin" }
        val mvc = MockMvcBuilders
            .standaloneSetup(AdminModerationApi(authorizer, service, Mockito.mock()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        val session = MockHttpSession()
        val path = "/api/admin/moderation/users/$USER_ID/related-access"

        mvc.perform(
            get(path).session(session)
                .param("matchType", "IP").param("hours", "720")
                .param("from", "2026-05-01").param("to", "2026-05-02")
        ).andExpect(status().isOk).andExpect(jsonPath("$.hours").value(720))

        assertEquals(at("2026-04-30T15:00:00Z"), matchedCall().values[0])
        assertEquals(at("2026-05-02T15:00:00Z"), matchedCall().values[1])
        Mockito.verify(authorizer).require(session, AdminSetting.Privilege.USER_MODERATION_READ)
        Mockito.verify(authorizer).require(session, AdminSetting.Privilege.CONNECTION_LOG)

        mvc.perform(get(path).param("matchType", "IP").param("hours", "336"))
            .andExpect(status().isOk).andExpect(jsonPath("$.hours").value(336))
        mvc.perform(get(path).param("matchType", "IP").param("hours", "100"))
            .andExpect(status().isBadRequest)
    }

    private companion object {
        const val USER_ID = "22222222-2222-4222-8222-222222222222"
    }
}
