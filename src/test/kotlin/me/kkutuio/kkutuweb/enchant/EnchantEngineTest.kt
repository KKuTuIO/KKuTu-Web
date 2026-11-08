package me.kkutuio.kkutuweb.enchant

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class EnchantEngineTest {
    @Test
    fun `chance starts at 30 and rises 10 per failure up to 100`() {
        assertEquals(listOf(30, 40, 50, 60, 70, 80, 90, 100, 100), (0..8).map { EnchantRules.chance(it) })
    }

    @Test
    fun `a regular try always succeeds once the chance reaches 100`() {
        repeat(1000) { assertTrue(EnchantEngine.regularSucceeds(7, Random(it))) }
    }

    @Test
    fun `expected regular tries per success is about 2_44`() {
        val random = Random(42)
        val runs = 200_000
        var tries = 0L
        repeat(runs) {
            var pity = 0
            while (true) {
                tries++
                if (EnchantEngine.regularSucceeds(pity, random)) break
                pity++
            }
        }
        assertEquals(2.4447, tries.toDouble() / runs, 0.03)
    }

    @Test
    fun `every pool tier is valid and weights are positive`() {
        for (def in EnchantPool.EFFECTS) {
            assertTrue(def.weight > 0, def.id)
            assertTrue(def.tiers.isNotEmpty() && def.tiers.any { it.premium }, "${def.id} needs a premium tier")
            assertTrue(def.tiers.all { it.weight > 0 }, def.id)
        }
    }

    @Test
    fun `rolled lines are valid and never repeat an effect already on the item`() {
        val random = Random(7)
        repeat(2000) {
            val have = mutableListOf<EnchantLine>()
            repeat(EnchantRules.MAX_LINES) {
                val line = EnchantEngine.rollLine(have, topTierChance = if (it % 2 == 0) 40 else 0, random = random)
                assertTrue(EnchantEngine.isValid(line), line.toString())
                assertTrue(have.none { h -> h.effect == line.effect })
                have += line
            }
        }
    }

    @Test
    fun `a roll with no top-tier chance is never flagged premium`() {
        val random = Random(1)
        repeat(2000) { assertFalse(EnchantEngine.rollLine(emptyList(), 0, random).premium) }
    }

    @Test
    fun `a roll with 100 percent top-tier chance always draws a top tier`() {
        val random = Random(5)
        repeat(2000) {
            val line = EnchantEngine.rollLine(emptyList(), 100, random)
            assertTrue(line.premium)
            assertTrue(EnchantPool.effect(line.effect)!!.tiers.first { t -> t.value == line.value }.premium)
        }
    }

    @Test
    fun `guaranteed tickets always succeed and the normal ticket follows the pity curve`() {
        val rare = EnchantTickets.byId("enchantTicketRare")!!
        val normal = EnchantTickets.byId("enchantTicketNormal")!!
        repeat(500) { assertTrue(EnchantEngine.succeeds(rare, 0, Random(it))) }
        val wins = (1..20_000).count { EnchantEngine.succeeds(normal, 0, Random(it)) }
        assertEquals(0.30, wins / 20_000.0, 0.02)
        assertEquals(listOf("enchantTicketNormal", "enchantTicketAdvanced", "enchantTicketRare"), EnchantTickets.ALL.map { it.id })
    }

    @Test
    fun `about 40 percent of premium rolls draw from premium tiers`() {
        val random = Random(99)
        val n = 50_000
        val premium = (1..n).count { EnchantEngine.rollLine(emptyList(), 40, random).premium }
        assertEquals(0.40, premium.toDouble() / n, 0.02)
    }

    @Test
    fun `mode is set exactly for per-mode effects`() {
        val random = Random(3)
        repeat(500) {
            val line = EnchantEngine.rollLine(emptyList(), 0, random)
            assertEquals(EnchantPool.effect(line.effect)!!.perMode, line.mode != null)
        }
    }

    @Test
    fun `summary adds per effect and mode and clamps to the cap`() {
        val lines = listOf(
            EnchantLine("ping_pct", 30, "KKT"), EnchantLine("ping_pct", 20, "KKT"), EnchantLine("ping_pct", 10, "EKT"),
            EnchantLine("ogg_charge_cut", 60), EnchantLine("ogg_charge_cut", 60), EnchantLine("ogg_charge_cut", 45)
        )
        val s = EnchantEngine.summarize(lines)
        assertEquals(50, s["ping_pct:KKT"])
        assertEquals(10, s["ping_pct:EKT"])
        assertEquals(120, s["ogg_charge_cut"])
    }

    @Test
    fun `isValid rejects unknown effects, off-tier values and bad modes`() {
        assertFalse(EnchantEngine.isValid(EnchantLine("nope", 1)))
        assertFalse(EnchantEngine.isValid(EnchantLine("ping_pct", 35, "KKT")))
        assertFalse(EnchantEngine.isValid(EnchantLine("ping_pct", 30)))
        assertFalse(EnchantEngine.isValid(EnchantLine("ping_pct", 30, "XXX")))
        assertFalse(EnchantEngine.isValid(EnchantLine("ogg_max_stack", 1, "KKT")))
        assertTrue(EnchantEngine.isValid(EnchantLine("ping_pct", 30, "KKT")))
    }
}
