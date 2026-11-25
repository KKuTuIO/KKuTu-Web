package me.kkutuio.kkutuweb.analytics

import me.kkutuio.kkutuweb.handler.GlobalExceptionHandler
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.session.SessionProfile
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.GameServerReconnectSetting
import me.kkutuio.kkutuweb.setting.GameServerSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor

class AnalyticsApiTest {
    private val dao = FakeAnalyticsDao().apply {
        items = listOf(AnalyticsTopItem("i", "상품", "BDG1", 10, 2, 20))
        economyRows = listOf(
            EconomyRow(java.time.LocalDate.of(2026, 11, 26), "INFLOW", "GAME_REWARD", 10, 1),
            EconomyRow(java.time.LocalDate.of(2026, 11, 26), "SINK", "SHOP_BUY", 4, 1)
        )
    }

    private fun mvc(loggedIn: Boolean = true, privileges: List<AdminSetting.Privilege>? = listOf(AdminSetting.Privilege.EVENT)): MockMvc {
        val setting = Mockito.mock(KKuTuSetting::class.java)
        Mockito.`when`(setting.getAdmins()).thenReturn(listOfNotNull(privileges?.let { AdminSetting(ADMIN_ID, "관리자", "운영", it) }))
        Mockito.`when`(setting.getGameServers()).thenReturn(
            listOf(GameServerSetting(false, "host", "key", "host", 8080, 0, GameServerReconnectSetting(true, 30), "감자"))
        )
        val login = Mockito.mock(LoginService::class.java) { invocation ->
            when (invocation.method.name) {
                "getSessionProfile" -> if (loggedIn) SessionProfile("local", "admin", "관리자", "관리자", "") else null
                "accountUuid" -> if (loggedIn) ADMIN_ID else null
                else -> null
            }
        }
        val clock = Clock.fixed(Instant.parse("2026-11-25T16:30:00Z"), ZoneOffset.UTC)
        val clients = players(5)
        val service = AnalyticsService(dao, AnalyticsRollupService(dao, clients, clock, Executor { it.run() }), clients, setting, clock)
        return MockMvcBuilders.standaloneSetup(AnalyticsApi(AdminModerationAuthorizer(setting, login), service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun body(mvc: MockMvc, range: String?): JsonNode {
        val request = get(PATH).apply { if (range != null) param("range", range) }
        val response = mvc.perform(request).andExpect(status().isOk).andReturn().response
        return ObjectMapper().readTree(response.getContentAsString(Charsets.UTF_8))
    }

    private fun JsonNode.keys() = propertyNames().toList().toSet()

    @Test
    fun `응답의 필드 이름은 백오피스와 약속한 그대로다`() {
        val body = body(mvc(), null)

        assertEquals(setOf("rangeDays", "kpis", "channels", "dailyMetrics", "economyBreakdown", "topItems"), body.keys())
        assertEquals(7, body["rangeDays"].intValue())
        assertEquals(
            setOf(
                "dau", "dauDeltaPct", "mau", "mauDeltaPct", "stickinessPct", "ccu", "newUsersToday", "returningUsersToday",
                "pingIssuedToday", "pingSunkToday", "shopPurchasesToday", "pendingReports", "openTickets", "urgentTickets", "activeEvents"
            ),
            body["kpis"].keys()
        )
        assertEquals(setOf("channelId", "name", "status", "ccu", "rooms"), body["channels"][0].keys())
        assertEquals(7, body["dailyMetrics"].size())
        assertEquals(
            setOf("date", "dau", "mau", "newUsers", "returningUsers", "peakCcu", "pingIssued", "pingSunk", "shopVolume"),
            body["dailyMetrics"][0].keys()
        )
        assertEquals("2026-11-26", body["dailyMetrics"][6]["date"].asString())
        assertEquals(setOf("inflow", "sink"), body["economyBreakdown"].keys())
        assertEquals(setOf("label", "amount"), body["economyBreakdown"]["inflow"][0].keys())
        assertEquals("게임 보상", body["economyBreakdown"]["inflow"][0]["label"].asString())
        assertEquals(setOf("id", "name", "group", "cost", "hit", "revenue"), body["topItems"][0].keys())
        // 조회가 오늘 지표를 앞당겨 셌다.
        assertEquals(10, body["kpis"]["dau"].intValue())
        assertTrue(body["kpis"]["stickinessPct"].isNumber)
    }

    @Test
    fun `7d 와 30d 만 받는다`() {
        assertEquals(7, body(mvc(), "7d")["rangeDays"].intValue())
        assertEquals(30, body(mvc(), "30d")["dailyMetrics"].size())
        listOf("1d", "30", "90d", "7D").forEach { range ->
            mvc().perform(get(PATH).param("range", range))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("range 는 7d 또는 30d 여야 합니다."))
        }
    }

    @Test
    fun `로그인하지 않았거나 권한이 있는 관리자가 아니면 아무것도 읽지 않는다`() {
        mvc(loggedIn = false).perform(get(PATH)).andExpect(status().isUnauthorized)
        mvc(privileges = null).perform(get(PATH)).andExpect(status().isForbidden)
        mvc(privileges = emptyList()).perform(get(PATH)).andExpect(status().isForbidden)
        assertTrue(dao.activityCalls.isEmpty())
    }

    @Test
    fun `Spring 이 새 빈의 생성자를 고를 수 있다`() {
        AnnotationConfigApplicationContext().use { context ->
            // 완성된 객체로 넣는다(대역에는 주입이나 초기화를 하지 않는다).
            listOf(
                Mockito.mock(JdbcTemplate::class.java), players(), Mockito.mock(KKuTuSetting::class.java),
                Mockito.mock(AdminModerationAuthorizer::class.java)
            ).forEachIndexed { index, dependency -> context.beanFactory.registerSingleton("dependency$index", dependency) }
            context.register(
                AnalyticsDao::class.java, EconomyStatsRecorder::class.java, AnalyticsRollupService::class.java,
                AnalyticsService::class.java, AnalyticsApi::class.java
            )
            context.refresh()
            context.getBean(AnalyticsApi::class.java)
        }
    }

    private companion object {
        const val PATH = "/api/admin/analytics/overview"
        const val ADMIN_ID = "11111111-1111-4111-8111-111111111111"
    }
}
