package me.kkutuio.kkutuweb.analytics

import me.kkutuio.kkutuweb.SessionAttribute
import me.kkutuio.kkutuweb.enchant.EnchantDao
import me.kkutuio.kkutuweb.identity.Account
import me.kkutuio.kkutuweb.identity.AdvancedBadWordFilter
import me.kkutuio.kkutuweb.identity.IdentityDao
import me.kkutuio.kkutuweb.identity.NicknameService
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.moderation.ModerationService
import me.kkutuio.kkutuweb.moderation.policy.ResolvedPolicyEffect
import me.kkutuio.kkutuweb.setup.SetupService
import me.kkutuio.kkutuweb.shop.Good
import me.kkutuio.kkutuweb.shop.ShopDao
import me.kkutuio.kkutuweb.shop.ShopService
import me.kkutuio.kkutuweb.user.User
import me.kkutuio.kkutuweb.user.UserDao
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor

/** 웹에서 핑이 바뀌는 곳마다 통계에 남는지, 통계가 실패해도 본래 작업이 끝나는지 확인한다. */
class EconomyHookTest {
    private data class Stat(val direction: String, val source: String, val amount: Long)

    private val stats = mutableListOf<Stat>()
    private var statsDown = false
    private val statsJdbc = Mockito.mock(JdbcTemplate::class.java) { invocation ->
        check(!statsDown) { "economy_daily_stat is missing" }
        val values = invocation.arguments.drop(1)
        stats.add(Stat(values[1] as String, values[2] as String, values[3] as Long))
        1
    }
    private val recorder = EconomyStatsRecorder(statsJdbc, Clock.systemUTC(), Executor { it.run() })

    @AfterEach
    fun clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization()
    }

    private fun updatingJdbc(rows: Int) = Mockito.mock(JdbcTemplate::class.java) { invocation ->
        if (invocation.method.name == "update") rows else Mockito.RETURNS_DEFAULTS.answer(invocation)
    }

    @Test
    fun `강화의 핑 변화는 방향에 따라 남는다`() {
        val dao = EnchantDao(updatingJdbc(1), ObjectMapper(), recorder)
        dao.addMoney("user", -500)
        dao.addMoney("user", 70)
        dao.addMoney("user", 0)
        assertEquals(listOf(Stat("SINK", "ENCHANT", 500), Stat("INFLOW", "ENCHANT", 70)), stats)
    }

    @Test
    fun `바뀐 행이 없으면 세지 않고, 통계가 실패해도 강화는 계속된다`() {
        EnchantDao(updatingJdbc(0), ObjectMapper(), recorder).addMoney("ghost", -500)
        assertTrue(stats.isEmpty())

        statsDown = true
        EnchantDao(updatingJdbc(1), ObjectMapper(), recorder).addMoney("user", -500) // 예외 없음
    }

    private fun moderation(jdbc: JdbcTemplate) = ModerationService(
        jdbc, ObjectMapper(), Mockito.mock(UserDao::class.java), Mockito.mock(), Mockito.mock(), Mockito.mock(),
        Mockito.mock(), Mockito.mock(), Mockito.mock(), Mockito.mock(), Mockito.mock(), recorder
    )

    private fun commit() = TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

    @Test
    fun `Spring 은 제재 서비스에도 기록기를 넣는다`() {
        AnnotationConfigApplicationContext().use { context ->
            val constructor = ModerationService::class.java.constructors.single { !it.isSynthetic }
            constructor.parameterTypes.forEachIndexed { index, type ->
                val dependency: Any = if (type == EconomyStatsRecorder::class.java) recorder else Mockito.mock(type)
                context.beanFactory.registerSingleton("dependency$index", dependency)
            }
            context.register(ModerationService::class.java)
            context.refresh()
            val field = ModerationService::class.java.getDeclaredField("economyStats").apply { isAccessible = true }
            assertSame(recorder, field.get(context.getBean(ModerationService::class.java)))
        }
    }

    @Test
    fun `제재의 핑 회수는 SANCTION_DEDUCT 로 남는다`() {
        val jdbc = Mockito.mock(JdbcTemplate::class.java) { invocation ->
            val sql = invocation.arguments[0] as String
            when (invocation.method.name) {
                "update" -> 1
                "query" -> if (sql.contains("FROM game_profile")) listOf("p1") else listOf(1000L to 500L)
                else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
            }
        }
        val apply = ModerationService::class.java.getDeclaredMethod(
            "applyEffect", Long::class.javaPrimitiveType, String::class.java, ResolvedPolicyEffect::class.java
        ).apply { isAccessible = true }
        TransactionSynchronizationManager.initSynchronization()

        apply.invoke(
            moderation(jdbc), 1L, "account",
            ResolvedPolicyEffect(
                "RESOURCE_ADJUSTMENT", Instant.now(), null, false,
                mapOf("mode" to "DEDUCT_PERCENT", "percent" to 30, "targets" to listOf("CYBER_POINT"))
            )
        )
        assertTrue(stats.isEmpty(), "커밋 전")
        commit()

        assertEquals(listOf(Stat("SINK", "SANCTION_DEDUCT", 300)), stats)
    }

    private fun reverse(moneyDelta: Long) {
        val reverse = ModerationService::class.java.getDeclaredMethod(
            "reverseEffect", Long::class.javaPrimitiveType, String::class.java, String::class.java, JsonNode::class.java
        ).apply { isAccessible = true }
        val parameters = ObjectMapper().readTree("""{"_rollback":[{"profileId":"p1","moneyDelta":$moneyDelta,"scoreDelta":0}]}""")
        reverse.invoke(moderation(updatingJdbc(1)), 1L, "RESOURCE_ADJUSTMENT", "account", parameters)
    }

    @Test
    fun `제재 철회로 돌려준 핑은 SANCTION_REVOKE, 줄어드는 철회는 SANCTION_DEDUCT 다`() {
        TransactionSynchronizationManager.initSynchronization()
        reverse(300)
        reverse(-40)
        reverse(0)
        commit()
        assertEquals(listOf(Stat("INFLOW", "SANCTION_REVOKE", 300), Stat("SINK", "SANCTION_DEDUCT", 40)), stats)
    }

    private fun user(money: Long) = ObjectMapper().let {
        User("u1", "별명", money, it.createObjectNode(), null, it.createObjectNode(), it.createObjectNode(), null, null, null, null, it.createObjectNode(), it.createObjectNode(), null)
    }

    private fun buy(cost: Long, count: Int, money: Long): String {
        val mapper = ObjectMapper()
        val shopDao = Mockito.mock(ShopDao::class.java)
        Mockito.`when`(shopDao.getGood("item")).thenReturn(Good("item", cost, 0, 0, "BDG1", null, mapper.createObjectNode()))
        val users = Mockito.mock(UserDao::class.java)
        Mockito.`when`(users.getUser("u1")).thenReturn(user(money))
        val login = Mockito.mock(LoginService::class.java) { invocation ->
            if (invocation.method.name == "gameUserId") "u1" else null
        }
        val session = MockHttpSession().apply { setAttribute(SessionAttribute.IS_GUEST.attributeName, false) }
        val shop = ShopService(mapper, shopDao, Mockito.mock(), users, login, Mockito.mock(), recorder)
        return shop.buyGood("item", session, count)
    }

    @Test
    fun `상점 구매는 한 번에 한 건의 SHOP_BUY 소각이다`() {
        assertTrue(buy(cost = 100, count = 3, money = 1000).contains("\"result\": 200"))
        assertEquals(listOf(Stat("SINK", "SHOP_BUY", 300)), stats)
    }

    @Test
    fun `살 수 없었거나 무료인 상품은 세지 않는다`() {
        assertTrue(buy(cost = 100, count = 3, money = 10).contains("error"))
        assertTrue(buy(cost = 0, count = 1, money = 10).contains("\"result\": 200"))
        assertTrue(stats.isEmpty())
    }

    private fun changeNickname(fixed: Boolean) {
        val users = Mockito.mock(UserDao::class.java) { invocation ->
            if (invocation.method.name == "lockNicknameState") UserDao.NicknameState("u1", "예전#1", 500, null, null, false, null)
            else Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        val identity = Mockito.mock(IdentityDao::class.java) { invocation ->
            if (invocation.method.name == "nicknameSuffix") "12345" else Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        val account = Mockito.mock(Account::class.java)
        Mockito.`when`(account.id).thenReturn(UUID.randomUUID())
        Mockito.`when`(account.legacyUserId).thenReturn("u1")
        NicknameService(users, Mockito.mock(SetupService::class.java), identity, Mockito.mock(AdvancedBadWordFilter::class.java), recorder)
            .change(account, "새별명", fixed)
    }

    @Test
    fun `별명 고정 비용만 NICK_CHANGE 로 남는다`() {
        changeNickname(fixed = false)
        assertTrue(stats.isEmpty())
        changeNickname(fixed = true)
        assertEquals(listOf(Stat("SINK", "NICK_CHANGE", 100)), stats)
    }
}
