package me.kkutuio.kkutuweb.admin.api

import me.kkutuio.kkutuweb.RecordingJdbcTemplate
import me.kkutuio.kkutuweb.admin.dao.ConnectionLogDAO
import me.kkutuio.kkutuweb.admin.dao.ReportLogDAO
import me.kkutuio.kkutuweb.admin.dao.SuspicionLogDAO
import me.kkutuio.kkutuweb.admin.dao.TableStatisticsDAO
import me.kkutuio.kkutuweb.admin.mapper.ConnectionLogMapper
import me.kkutuio.kkutuweb.admin.mapper.ReportLogMapper
import me.kkutuio.kkutuweb.admin.mapper.SuspicionLogMapper
import me.kkutuio.kkutuweb.admin.service.ConnectionLogService
import me.kkutuio.kkutuweb.admin.service.ReportLogService
import me.kkutuio.kkutuweb.admin.service.SuspicionLogService
import me.kkutuio.kkutuweb.handler.GlobalExceptionHandler
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.session.SessionProfile
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.mockito.Mockito
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.sql.Timestamp
import java.time.Instant

/** 요청 파라미터부터 SQL까지 한 번에 확인한다. */
class LogApiDateRangeTest {
    private class Target(
        val path: String,
        val table: String,
        val idColumn: String,
        val textFilter: Pair<String, String>,
        val textClause: String,
        val controller: (KKuTuSetting, LoginService, RecordingJdbcTemplate, TableStatisticsDAO) -> Any
    )

    private val targets = listOf(
        Target(
            "/api/admin/connection-logs", "connection_log", "id", "user_id" to "someone", "user_id = ?"
        ) { setting, login, jdbc, statistics ->
            val dao = ConnectionLogDAO(jdbc.mock, ConnectionLogMapper())
            ConnectionLogApi(setting, login, ConnectionLogService(dao, statistics))
        },
        Target(
            "/api/admin/report-logs", "report_log", "report_id", "reporter_id" to "someone", "reporter_id = ?"
        ) { setting, login, jdbc, statistics ->
            val dao = ReportLogDAO(jdbc.mock, ReportLogMapper())
            ReportLogApi(setting, login, ReportLogService(dao, statistics))
        },
        Target(
            "/api/admin/suspicion-logs", "suspicion_log", "case_id", "user_id" to "someone", "user_id = ?"
        ) { setting, login, jdbc, statistics ->
            val dao = SuspicionLogDAO(jdbc.mock, SuspicionLogMapper())
            SuspicionLogApi(setting, login, SuspicionLogService(dao, statistics))
        }
    )

    private class Fixture(val mvc: MockMvc, val jdbc: RecordingJdbcTemplate, val statistics: TableStatisticsDAO)

    private fun fixture(target: Target): Fixture {
        val setting = Mockito.mock(KKuTuSetting::class.java)
        val admin = AdminSetting(
            ADMIN_ID, "관리자", "운영",
            listOf(
                AdminSetting.Privilege.CONNECTION_LOG,
                AdminSetting.Privilege.REPORT_LOG,
                AdminSetting.Privilege.SUSPICION_LOG
            )
        )
        Mockito.`when`(setting.getAdminIds()).thenReturn(listOf(ADMIN_ID))
        Mockito.`when`(setting.getAdmins()).thenReturn(listOf(admin))
        val login = Mockito.mock(LoginService::class.java) { invocation ->
            when (invocation.method.name) {
                "getSessionProfile" -> SessionProfile("local", "admin", "관리자", "관리자", "")
                "accountUuid" -> ADMIN_ID
                else -> null
            }
        }
        val jdbc = RecordingJdbcTemplate()
        val statistics = Mockito.mock(TableStatisticsDAO::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(target.controller(setting, login, jdbc, statistics))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        return Fixture(mvc, jdbc, statistics)
    }

    private fun request(target: Target, vararg params: Pair<String, String>) =
        get(target.path)
            .param("page", "2")
            .param("size", "10")
            .param("sort", "time,DESC")
            .apply { params.forEach { (name, value) -> param(name, value) } }

    private fun at(instant: String): Timestamp = Timestamp.from(Instant.parse(instant))

    private fun cases(name: String, test: (Target) -> Unit) =
        targets.map { DynamicTest.dynamicTest("${it.table}: $name") { test(it) } }

    @TestFactory
    fun `날짜 범위는 건수와 목록 조회에 모두 적용된다`() = cases("from, to") { target ->
        val fixture = fixture(target)

        fixture.mvc.perform(request(target, "from" to "2026-05-01", "to" to "2026-05-02"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalEstimated").value(false))

        val bounds = listOf<Any?>(at("2026-04-30T15:00:00Z"), at("2026-05-02T15:00:00Z"))
        val (count, page) = fixture.jdbc.calls
        assertEquals("queryForObject", count.method)
        assertEquals("SELECT COUNT(*) FROM ${target.table} WHERE time >= ? AND time < ?", count.sql)
        assertEquals(bounds, count.values)
        assertEquals("query", page.method)
        assertTrue(page.sql.contains("WHERE time >= ? AND time < ?"), page.sql)
        assertTrue(page.sql.contains("ORDER BY time DESC, ${target.idColumn} DESC"), page.sql)
        assertEquals(bounds + listOf(10, 20), page.values)
        Mockito.verifyNoInteractions(fixture.statistics)
    }

    @TestFactory
    fun `날짜 범위는 기존 검색 조건과 함께 쓴다`() = cases("filter + range") { target ->
        val fixture = fixture(target)

        // 뒤바뀐 날짜는 바로잡는다.
        fixture.mvc.perform(
            request(target, target.textFilter, "from" to "2026-05-02", "to" to "2026-05-01")
        ).andExpect(status().isOk)

        val expected = listOf<Any?>("someone", at("2026-04-30T15:00:00Z"), at("2026-05-02T15:00:00Z"))
        val (count, page) = fixture.jdbc.calls
        val where = "WHERE ${target.textClause} AND time >= ? AND time < ?"
        assertTrue(count.sql.endsWith(where), count.sql)
        assertEquals(expected, count.values)
        assertTrue(page.sql.contains(where), page.sql)
        assertEquals(expected + listOf(10, 20), page.values)
    }

    @TestFactory
    fun `한쪽 날짜만 지정하면 그 조건만 넣는다`() = cases("one side") { target ->
        val fromOnly = fixture(target)
        fromOnly.mvc.perform(request(target, "from" to "2026-05-01")).andExpect(status().isOk)
        assertEquals("SELECT COUNT(*) FROM ${target.table} WHERE time >= ?", fromOnly.jdbc.calls[0].sql)
        assertEquals(listOf<Any?>(at("2026-04-30T15:00:00Z")), fromOnly.jdbc.calls[0].values)
        assertFalse(fromOnly.jdbc.calls[1].sql.contains("time < ?"))

        val toOnly = fixture(target)
        toOnly.mvc.perform(request(target, "to" to "2026-05-01")).andExpect(status().isOk)
        assertEquals("SELECT COUNT(*) FROM ${target.table} WHERE time < ?", toOnly.jdbc.calls[0].sql)
        assertEquals(listOf<Any?>(at("2026-05-01T15:00:00Z")), toOnly.jdbc.calls[0].values)
        assertFalse(toOnly.jdbc.calls[1].sql.contains("time >= ?"))
    }

    @TestFactory
    fun `날짜가 없으면 추정 건수를 쓰고 조건을 넣지 않는다`() = cases("all") { target ->
        listOf(emptyList(), listOf("from" to "", "to" to "")).forEach { params ->
            val fixture = fixture(target)
            Mockito.`when`(fixture.statistics.getEstimatedRowCount(target.table)).thenReturn(1234)

            fixture.mvc.perform(request(target, *params.toTypedArray()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalElements").value(1234))
                .andExpect(jsonPath("$.totalEstimated").value(true))

            val page = fixture.jdbc.calls.single()
            assertEquals("query", page.method)
            assertFalse(page.sql.contains("WHERE time"), page.sql)
            assertEquals(listOf<Any?>(10, 20), page.values)
        }
    }

    @TestFactory
    fun `잘못된 날짜는 조회하지 않고 400으로 거절한다`() = cases("invalid") { target ->
        listOf("from", "to").forEach { name ->
            val fixture = fixture(target)

            fixture.mvc.perform(request(target, name to "2026-02-30"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("날짜는 YYYY-MM-DD 형식의 올바른 날짜여야 합니다."))

            assertTrue(fixture.jdbc.calls.isEmpty())
            Mockito.verifyNoInteractions(fixture.statistics)
        }
    }

    private companion object {
        const val ADMIN_ID = "11111111-1111-4111-8111-111111111111"
    }
}
