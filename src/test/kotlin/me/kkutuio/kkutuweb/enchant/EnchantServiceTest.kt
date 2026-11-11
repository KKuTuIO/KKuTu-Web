package me.kkutuio.kkutuweb.enchant

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.shop.Good
import me.kkutuio.kkutuweb.shop.ShopDao
import me.kkutuio.kkutuweb.shop.ShopService
import me.kkutuio.kkutuweb.user.UserDao
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import tools.jackson.databind.node.JsonNodeFactory

class EnchantServiceTest {
    private val dao = mock(EnchantDao::class.java)
    private val userDao = mock(UserDao::class.java)
    private val shopDao = mock(ShopDao::class.java)
    private val shopService = mock(ShopService::class.java)
    private val login = mock(LoginService::class.java)
    private val session = mock(HttpSession::class.java)
    private val service = EnchantService(dao, userDao, shopDao, shopService, login)
    private val json = JsonNodeFactory.instance

    private fun line(effect: String, value: Int) = EnchantLine(effect, value)
    private val full = listOf(line("ping_per_player", 2), line("exp_per_player", 2), line("ogg_max_stack", 1))

    /** [owned] copies of the hat in the box, [tickets] of each of two ticket kinds, [made] enchanted copies already, [pity] failures in a row. */
    private fun setup(group: String = "Mhead", owned: Int = 1, tickets: Int = 1, made: List<EnchantInstance> = emptyList(), pity: Int = 0) {
        `when`(session.getAttribute("isGuest")).thenReturn(false)
        `when`(login.gameUserId(session)).thenReturn("u1")
        val box = json.objectNode()
        if (owned > 0) box.set("hat", json.objectNode().put("value", owned))
        if (tickets > 0) {
            box.set("enchantTicketNormal", json.objectNode().put("value", tickets))
            box.set("enchantTicketAdvanced", json.objectNode().put("value", tickets))
        }
        `when`(dao.lockBox("u1")).thenReturn(box)
        `when`(shopDao.getGood("hat")).thenReturn(Good("hat", 0, 0, 0, group, null, json.objectNode()))
        `when`(shopDao.getGood("enchantTicketAdvanced")).thenReturn(Good("enchantTicketAdvanced", 1000, 0, 0, "CNS", null, json.objectNode()))
        `when`(dao.lockPity("u1", "hat")).thenReturn(pity)
        `when`(dao.instanceCount("u1", "hat")).thenReturn(made.size)
        made.forEach { `when`(dao.lockInstance("u1", "hat", it.instance)).thenReturn(it) }
    }

    private fun made(n: Int, lines: List<EnchantLine> = listOf(line("ping_per_player", 2)), pending: EnchantLine? = null, level: Int = lines.size) =
        EnchantInstance("hat", n, level, lines, pending, false)

    @Test
    fun `a guaranteed ticket on a plain copy makes it an enchanted copy, spends the ticket, logs the shop price and resets the pity`() {
        setup(pity = 5)
        `when`(dao.createInstance(anyString(), anyString(), anyInt(), anyList())).thenAnswer { EnchantInstance("hat", 1, 1, it.getArgument(3), null, false) }
        val reply = service.enchant(session, "hat", 0, "enchantTicketAdvanced")
        assertEquals(true, reply.success)
        assertEquals(1, reply.state!!.instance)
        assertEquals(1, reply.state!!.level)
        assertEquals(listOf(reply.line), reply.state!!.lines)
        assertEquals(0, reply.state!!.pity)
        verify(shopService).consumeGood(reply.box!!, "enchantTicketAdvanced", 1, true)
        verify(dao).saveBox("u1", reply.box!!)
        verify(dao).savePity("u1", "hat", 0)
        verify(dao).log("u1", "hat", "enchantTicketAdvanced", 5, 100, true, 1000L, reply.line)
    }

    @Test
    fun `a success on an enchanted copy adds a line and a level`() {
        val copy = made(2)
        setup(owned = 2, made = listOf(copy))
        val reply = service.enchant(session, "hat", 2, "enchantTicketAdvanced")
        assertEquals(2, reply.state!!.level)
        assertEquals(2, reply.state!!.lines.size)
        verify(dao).saveInstance("u1", copy.copy(level = 2, lines = copy.lines + reply.line!!))
        verify(dao, never()).createInstance(anyString(), anyString(), anyInt(), anyList())
    }

    @Test
    fun `a success on a full copy is held as pending`() {
        val copy = made(1, full)
        setup(made = listOf(copy))
        val reply = service.enchant(session, "hat", 1, "enchantTicketAdvanced")
        assertEquals(full, reply.state!!.lines)
        assertEquals(reply.line, reply.state!!.pending)
        assertEquals(4, reply.state!!.level)
    }

    @Test
    fun `no ticket stops the try before anything is spent or saved`() {
        setup(tickets = 0)
        assertEquals("noTicket", service.enchant(session, "hat", 0, "enchantTicketNormal").error)
        verify(dao, never()).log(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean(), anyLong(), any())
        verifyNoInteractions(shopService)
    }

    @Test
    fun `an unknown ticket is refused`() {
        setup()
        assertEquals("bad", service.enchant(session, "hat", 0, "wordToken").error)
    }

    @Test
    fun `items that are not owned or not enchantable are refused without charging`() {
        setup(owned = 0)
        assertEquals("notOwned", service.enchant(session, "hat", 0, "enchantTicketNormal").error)
        setup(group = "BON")
        assertEquals("notEnchantable", service.enchant(session, "hat", 0, "enchantTicketNormal").error)
        verifyNoInteractions(shopService)
    }

    @Test
    fun `a plain copy needs one the enchanted ones do not already account for`() {
        setup(owned = 1, made = listOf(made(1)))
        assertEquals("notOwned", service.enchant(session, "hat", 0, "enchantTicketNormal").error)
        verifyNoInteractions(shopService)
    }

    @Test
    fun `an unknown copy is refused`() {
        setup()
        assertEquals("bad", service.enchant(session, "hat", 7, "enchantTicketNormal").error)
    }

    @Test
    fun `a pending success blocks the next try on that copy`() {
        setup(made = listOf(made(1, full, pending = line("ogg_charge_cut", 60))))
        assertEquals("pending", service.enchant(session, "hat", 1, "enchantTicketAdvanced").error)
        verifyNoInteractions(shopService)
    }

    @Test
    fun `resolve replaces the chosen slot or discards`() {
        val pending = line("ogg_charge_cut", 60)
        val copy = made(1, full, pending)
        setup(made = listOf(copy))
        val replaced = service.resolve(session, "hat", 1, 1)
        assertEquals(pending, replaced.state!!.lines[1])
        assertNull(replaced.state!!.pending)
        assertEquals(full, service.resolve(session, "hat", 1, null).state!!.lines)
        assertEquals("bad", service.resolve(session, "hat", 1, 3).error)
        assertEquals("bad", service.resolve(session, "hat", 0, null).error)
    }

    @Test
    fun `resolve with nothing pending is refused`() {
        setup(made = listOf(made(1)))
        assertEquals("nothingPending", service.resolve(session, "hat", 1, null).error)
    }

    @Test
    fun `wearing a copy marks it, and a plain copy clears the mark`() {
        setup(made = listOf(made(1)))
        assertTrue(service.wear(session, "hat", 1).state!!.worn)
        verify(dao).setWorn("u1", "hat", 1)
        assertFalse(service.wear(session, "hat", 0).state!!.worn)
        verify(dao).setWorn("u1", "hat", 0)
        assertEquals("bad", service.wear(session, "hat", 9).error)
    }

    @Test
    fun `guests cannot enchant`() {
        `when`(session.getAttribute("isGuest")).thenReturn(true)
        assertEquals("guest", service.enchant(session, "hat", 0, "enchantTicketNormal").error)
    }

    @Test
    fun `protecting a risky try without the Ping stops it before anything is spent`() {
        val copy = made(1, lines = full, level = 3)
        setup(made = listOf(copy))
        `when`(dao.moneyOf("u1")).thenReturn(100L)
        assertEquals("noMoney", service.enchant(session, "hat", 1, "enchantTicketNormal", protect = true).error)
        verify(dao, never()).addMoney(anyString(), anyLong())
        verify(shopService, never()).consumeGood(dao.lockBox("u1")!!, "enchantTicketNormal", 1, true)
    }

    @Test
    fun `a guaranteed ticket is never charged for protection`() {
        val copy = made(1, lines = full, level = 3)
        setup(made = listOf(copy))
        val reply = service.enchant(session, "hat", 1, "enchantTicketAdvanced", protect = true)
        assertEquals(true, reply.success)
        assertNull(reply.protectedTry)
        verify(dao, never()).addMoney(anyString(), anyLong())
    }

    @Test
    fun `repair takes the Ping and clears the damage of one line`() {
        val hurt = listOf(line("ping_per_player", 2).copy(damage = 300), line("exp_per_player", 2), line("ogg_max_stack", 1))
        val copy = made(1, lines = hurt)
        setup(made = listOf(copy))
        `when`(dao.moneyOf("u1")).thenReturn(5000L, 4000L)
        val reply = service.repair(session, "hat", 1, 0)
        assertNull(reply.error)
        assertEquals(0, reply.state!!.lines[0].damage)
        assertEquals(4000L, reply.money)
        verify(dao).addMoney("u1", -1000L) // 300 steps = 15 points = two started 10-point steps
    }

    @Test
    fun `repair without the Ping or without damage is refused`() {
        val hurt = listOf(line("ping_per_player", 2).copy(damage = 300), line("exp_per_player", 2))
        setup(made = listOf(made(1, lines = hurt)))
        `when`(dao.moneyOf("u1")).thenReturn(10L)
        assertEquals("noMoney", service.repair(session, "hat", 1, 0).error)
        assertEquals("notDamaged", service.repair(session, "hat", 1, 1).error)
        assertEquals("bad", service.repair(session, "hat", 1, 9).error)
        verify(dao, never()).addMoney(anyString(), anyLong())
    }
}
