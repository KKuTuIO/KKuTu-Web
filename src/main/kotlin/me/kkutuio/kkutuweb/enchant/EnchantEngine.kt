package me.kkutuio.kkutuweb.enchant

import kotlin.random.Random

/**
 * 강화 (item enchant) rules, kept free of Spring and the database so they can be tested and tuned on their own.
 * The numbers here are the economy knobs of the test: see [EnchantRules].
 */
object EnchantRules {
    /** Success chance of the first try of a ticket that can fail, in percent; each failure in a row adds [PITY_STEP], capped at 100. */
    const val BASE_CHANCE = 30
    const val PITY_STEP = 10

    /** Lines one item can hold. A success on a full item is held as a pending line the player keeps or discards. */
    const val MAX_LINES = 3

    fun chance(pity: Int): Int = (BASE_CHANCE + PITY_STEP * pity.coerceAtLeast(0)).coerceAtMost(100)

    /** A failed try on a copy of at least this level (the next success would be +4) can damage one of its lines. */
    const val DAMAGE_FROM_LEVEL = 3

    /** Percent chance that a failed try damages, by the copy's level: 20 at level 3, +20 per level, at most [DAMAGE_MAX_CHANCE]. */
    const val DAMAGE_STEP = 20
    const val DAMAGE_MAX_CHANCE = 80
    fun damageChance(level: Int): Int =
        if (level < DAMAGE_FROM_LEVEL) 0 else (DAMAGE_STEP * (level - DAMAGE_FROM_LEVEL + 1)).coerceAtMost(DAMAGE_MAX_CHANCE)

    /** Damage is counted in these steps of a line's value (0.05%): [DAMAGE_FULL] steps take the whole line away. */
    const val DAMAGE_FULL = 2000

    /** The damage one hit takes: from one step up to the chance of the hit, in percent of the value (so 20% can take 0.05% to 20%). */
    fun maxHit(level: Int): Int = damageChance(level) * (DAMAGE_FULL / 100)

    /** Ping a try spends to be sure it cannot damage (only asked when it could). */
    const val PROTECT_COST = 300

    /** Ping to repair one [REPAIR_STEP] of damage (10 percentage points, a started step counts whole). */
    const val REPAIR_STEP = DAMAGE_FULL / 10
    const val REPAIR_COST = 500
    fun repairCost(damage: Int): Long = ((damage.coerceAtLeast(0) + REPAIR_STEP - 1) / REPAIR_STEP).toLong() * REPAIR_COST
}

/**
 * A kind of 강화 티켓. Each is an ordinary shop item (group CNS), so its price is the shop price and is changed where shop prices are.
 * [guaranteed]: the try always succeeds. [topTierChance]: percent chance that the line is drawn only from the effect's top tiers.
 */
data class EnchantTicket(val id: String, val tier: String, val guaranteed: Boolean, val topTierChance: Int)

object EnchantTickets {
    val ALL = listOf(
        EnchantTicket("enchantTicketNormal", "normal", guaranteed = false, topTierChance = 0),
        EnchantTicket("enchantTicketAdvanced", "advanced", guaranteed = true, topTierChance = 40),
        EnchantTicket("enchantTicketRare", "rare", guaranteed = true, topTierChance = 100)
    )
    private val byId = ALL.associateBy { it.id }
    fun byId(id: String): EnchantTicket? = byId[id]
}

enum class EffectUnit { PERMILLE, FLAT, SECONDS }

data class Tier(val value: Int, val weight: Int, val premium: Boolean = false)

/**
 * One kind of line. Percent values are stored in permille (0.1% = 1) so sums stay exact.
 * [weight] is how often the effect is picked; [perMode] effects are rolled for one game type.
 */
data class EffectDef(
    val id: String,
    val unit: EffectUnit,
    val weight: Int,
    val tiers: List<Tier>,
    val perMode: Boolean = false,
    /** Cap of the total over everything worn (per game type for [perMode] effects), null for none. */
    val cap: Int? = null
)

data class EnchantLine(
    val effect: String,
    val value: Int,
    val mode: String? = null,
    val premium: Boolean = false,
    /** Damage taken, in steps of 0.05% of the value ([EnchantRules.DAMAGE_FULL] = all of it). Added up by every hit; the value counts less. */
    val damage: Int = 0
) {
    /** The value that counts: [value] less the damage, in permille (can be fractional). */
    fun effective(): Double = value * (EnchantRules.DAMAGE_FULL - damage.coerceIn(0, EnchantRules.DAMAGE_FULL)) / EnchantRules.DAMAGE_FULL.toDouble()
}

object EnchantPool {
    /** The game types a [EffectDef.perMode] line can be rolled for: the `RULE` keys of games.default.json. */
    val MODES = listOf(
        "EKT", "ESH", "KKT", "KSH", "CSQ", "KCW", "KTY", "ETY", "KAP", "HUN", "KDA", "EDA",
        "KSS", "ESS", "EAP", "KWS", "EWS", "KDG", "EDG", "KFT", "KMT", "ECW", "KLT", "ELT"
    )

    /** Item groups (shop `group`) that can carry enchants: the avatar equipment. */
    val ENCHANTABLE_GROUPS = setOf("Mhead", "Meye", "Mmouth", "Mhand", "Mclothes", "Mshoes", "Mback", "Mbody")

    private fun pm(vararg values: Int) = values.toList()

    private fun tiers(values: List<Int>, weights: List<Int>, premiumFrom: Int): List<Tier> {
        require(values.size == weights.size)
        return values.indices.map { Tier(values[it], weights[it], it >= premiumFrom) }
    }

    // The pool of the design note, de-duplicated: a repeated line in the note is a higher weight here, not a second entry.
    // Weights and caps are first guesses to be tuned from the enchant log.
    val EFFECTS: List<EffectDef> = listOf(
        EffectDef("ping_pct", EffectUnit.PERMILLE, 12, tiers(pm(10, 20, 30, 40, 50, 60), listOf(30, 25, 20, 14, 8, 3), 4), perMode = true),
        EffectDef("exp_pct", EffectUnit.PERMILLE, 12, tiers(pm(10, 20, 30, 40, 50, 60), listOf(30, 25, 20, 14, 8, 3), 4), perMode = true),
        EffectDef("ping_per_player", EffectUnit.PERMILLE, 8, tiers(pm(2, 4, 6, 8), listOf(40, 30, 20, 10), 2), cap = 40),
        EffectDef("exp_per_player", EffectUnit.PERMILLE, 8, tiers(pm(2, 4, 6, 8), listOf(40, 30, 20, 10), 2), cap = 40),
        EffectDef("time_penalty_reduce", EffectUnit.PERMILLE, 8, tiers(pm(10, 15, 20, 25, 30), listOf(35, 28, 20, 12, 5), 3), cap = 100),
        EffectDef("overtime_penalty_reduce", EffectUnit.PERMILLE, 6, tiers(pm(10, 15, 20, 25, 30), listOf(35, 28, 20, 12, 5), 3), cap = 100),
        EffectDef("rare_shard_chance", EffectUnit.PERMILLE, 5, tiers((10..20).toList(), listOf(14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 5), 8), cap = 50),
        EffectDef("item_mode_uses", EffectUnit.FLAT, 3, listOf(Tier(1, 1, premium = true)), cap = 3),
        EffectDef("ogg_max_stack", EffectUnit.FLAT, 4, tiers(pm(1, 2, 3), listOf(12, 6, 2), 1), cap = 6),
        EffectDef("ogg_charge_cut", EffectUnit.SECONDS, 4, tiers(pm(15, 30, 45, 60), listOf(14, 8, 4, 1), 2), cap = 120)
    )

    private val byId = EFFECTS.associateBy { it.id }
    fun effect(id: String): EffectDef? = byId[id]
}

object EnchantEngine {
    /** Does a try of a ticket that can fail succeed? [pity] is the number of failures in a row on this item. */
    fun regularSucceeds(pity: Int, random: Random): Boolean = random.nextInt(100) < EnchantRules.chance(pity)

    /** Does a try with [ticket] succeed? */
    fun succeeds(ticket: EnchantTicket, pity: Int, random: Random): Boolean = ticket.guaranteed || regularSucceeds(pity, random)

    /**
     * Draws a line for an item that already holds [have]: an effect that is not on it yet, then a tier of it. With
     * [topTierChance] percent the tier is drawn only from the effect's top (premium) tiers.
     */
    fun rollLine(have: List<EnchantLine>, topTierChance: Int, random: Random): EnchantLine {
        val used = have.map { it.effect }.toSet()
        val candidates = EnchantPool.EFFECTS.filter { it.id !in used }.ifEmpty { EnchantPool.EFFECTS }
        val effect = pickWeighted(candidates, random) { it.weight }
        val premiumRoll = random.nextInt(100) < topTierChance
        val tiers = if (premiumRoll) effect.tiers.filter { it.premium }.ifEmpty { effect.tiers } else effect.tiers
        val tier = pickWeighted(tiers, random) { it.weight }
        val mode = if (effect.perMode) EnchantPool.MODES[random.nextInt(EnchantPool.MODES.size)] else null
        return EnchantLine(effect.id, tier.value, mode, premiumRoll && tier.premium)
    }

    /**
     * A failed try on a copy at [level] with [lines]: does it damage, and which line by how much? Only a line that still has value is hit.
     * Returns the new lines (the same when nothing was hit).
     */
    fun damage(lines: List<EnchantLine>, level: Int, random: Random): List<EnchantLine> {
        val chance = EnchantRules.damageChance(level)
        if (chance == 0 || random.nextInt(100) >= chance) return lines
        val hittable = lines.indices.filter { lines[it].damage < EnchantRules.DAMAGE_FULL }
        if (hittable.isEmpty()) return lines
        val at = hittable[random.nextInt(hittable.size)]
        val hit = 1 + random.nextInt(EnchantRules.maxHit(level))
        return lines.toMutableList().also { it[at] = lines[at].copy(damage = (lines[at].damage + hit).coerceAtMost(EnchantRules.DAMAGE_FULL)) }
    }

    /** Totals per effect (and game type) over [lines] of everything worn, each clamped to its cap. Key: `effect` or `effect:MODE`. Damaged lines count less. */
    fun summarize(lines: Iterable<EnchantLine>): Map<String, Int> {
        val sums = LinkedHashMap<String, Double>()
        for (line in lines) {
            val def = EnchantPool.effect(line.effect) ?: continue
            val key = if (def.perMode && line.mode != null) "${line.effect}:${line.mode}" else line.effect
            sums[key] = (sums[key] ?: 0.0) + line.effective()
        }
        val totals = LinkedHashMap<String, Int>()
        for ((key, sum) in sums) {
            val cap = EnchantPool.effect(key.substringBefore(':'))?.cap
            totals[key] = Math.round(if (cap != null) minOf(sum, cap.toDouble()) else sum).toInt()
        }
        return totals
    }

    /** A line the server may keep: a known effect, a value of one of its tiers, a mode exactly when the effect wants one. */
    fun isValid(line: EnchantLine): Boolean {
        val def = EnchantPool.effect(line.effect) ?: return false
        if (def.tiers.none { it.value == line.value }) return false
        return if (def.perMode) line.mode in EnchantPool.MODES else line.mode == null
    }

    private fun <T> pickWeighted(items: List<T>, random: Random, weight: (T) -> Int): T {
        val total = items.sumOf(weight)
        var roll = random.nextInt(total)
        for (item in items) {
            roll -= weight(item)
            if (roll < 0) return item
        }
        return items.last()
    }
}
