package me.kkutuio.kkutuweb.enchant

import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.shop.ShopDao
import me.kkutuio.kkutuweb.shop.ShopService
import me.kkutuio.kkutuweb.user.UserDao
import jakarta.servlet.http.HttpSession
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import kotlin.random.Random

/**
 * What the panel shows for one copy. [instance] 0 is a plain copy (nothing on it yet). [pity] and [chance] belong to the kind of item:
 * its copies share the failures in a row.
 */
data class CopyState(
    val instance: Int,
    val level: Int,
    val lines: List<EnchantLine>,
    val pending: EnchantLine?,
    val worn: Boolean,
    val pity: Int,
    val chance: Int
)

/** An answer the client shows: [error] is a key of `play.enchant.error.*`; otherwise the fields of the new state ([box] after the ticket was spent). */
data class EnchantReply(
    val error: String? = null,
    val item: String? = null,
    val success: Boolean? = null,
    val line: EnchantLine? = null,
    val state: CopyState? = null,
    val box: JsonNode? = null
)

@Service
class EnchantService(
    @Autowired private val enchantDao: EnchantDao,
    @Autowired private val userDao: UserDao,
    @Autowired private val shopDao: ShopDao,
    @Autowired private val shopService: ShopService,
    @Autowired private val loginService: LoginService
) {
    private val random = Random.Default

    private fun stateOf(inst: EnchantInstance, pity: Int) =
        CopyState(inst.instance, inst.level, inst.lines, inst.pending, inst.worn, pity, EnchantRules.chance(pity))

    private fun plain(itemId: String) = EnchantInstance(itemId, 0, 0, emptyList(), null, false)

    private fun userIdOf(session: HttpSession): String? =
        if (session.isGuest()) null else loginService.gameUserId(session)

    private fun countIn(box: JsonNode, id: String): Int = box.get(id)?.let { it.get("value")?.intValue() ?: it.intValue() } ?: 0

    /**
     * Everything this user has enchanted, and the rules the panel shows. Per item: the failures in a row and its enchanted copies (no
     * more than the box holds). Ticket prices are the shop's, so the client reads them there.
     */
    fun overview(session: HttpSession): Map<String, Any?> {
        val userId = userIdOf(session) ?: return mapOf("error" to "guest")
        val box = userDao.getUser(userId)?.box
        val pities = enchantDao.pityOf(userId)
        val items = enchantDao.instancesOf(userId).groupBy { it.itemId }.mapValues { (id, list) ->
            val pity = pities[id] ?: 0
            mapOf(
                "pity" to pity, "chance" to EnchantRules.chance(pity),
                "copies" to list.take(if (box == null) list.size else countIn(box, id)).map { stateOf(it, pity) }
            )
        }.toMutableMap()
        for ((id, pity) in pities) if (pity > 0 && id !in items) items[id] = mapOf("pity" to pity, "chance" to EnchantRules.chance(pity), "copies" to emptyList<CopyState>())
        return mapOf(
            "rules" to mapOf(
                "baseChance" to EnchantRules.BASE_CHANCE,
                "pityStep" to EnchantRules.PITY_STEP,
                "maxLines" to EnchantRules.MAX_LINES,
                "groups" to EnchantPool.ENCHANTABLE_GROUPS,
                "tickets" to EnchantTickets.ALL
            ),
            "effects" to EnchantPool.EFFECTS.associate { it.id to it.unit.name },
            "items" to items
        )
    }

    /** The totals the game uses: the lines of the copy worn for each item worn now (per effect, per game type), capped. */
    fun summary(session: HttpSession): Map<String, Any?> {
        val userId = userIdOf(session) ?: return mapOf("error" to "guest")
        return mapOf("effects" to summaryOf(userId))
    }

    fun summaryOf(userId: String): Map<String, Int> {
        val user = userDao.getUser(userId) ?: return emptyMap()
        val worn = user.equip.properties().map { it.value.asString() }.filter { it.isNotEmpty() }.toSet()
        return EnchantEngine.summarize(enchantDao.instancesOf(userId).filter { it.worn && it.itemId in worn }.flatMap { it.lines })
    }

    /** One try on a copy of [itemId] ([instance] 0: a plain copy), spending one [ticketId]. A success on a plain copy makes it an enchanted one. */
    @Transactional
    fun enchant(session: HttpSession, itemId: String, instance: Int, ticketId: String): EnchantReply {
        val userId = userIdOf(session) ?: return EnchantReply(error = "guest")
        val ticket = EnchantTickets.byId(ticketId) ?: return EnchantReply(error = "bad")
        if (instance < 0) return EnchantReply(error = "bad")
        if (shopDao.getGood(itemId)?.group !in EnchantPool.ENCHANTABLE_GROUPS) return EnchantReply(error = "notEnchantable")

        val box = enchantDao.lockBox(userId) ?: return EnchantReply(error = "bad")
        val owned = countIn(box, itemId)
        if (owned <= 0) return EnchantReply(error = "notOwned")
        if (countIn(box, ticketId) <= 0) return EnchantReply(error = "noTicket")

        val pity = enchantDao.lockPity(userId, itemId) // also serialises this user's tries on this item
        val target: EnchantInstance = if (instance > 0) {
            enchantDao.lockInstance(userId, itemId, instance) ?: return EnchantReply(error = "bad")
        } else {
            if (owned - enchantDao.instanceCount(userId, itemId) < 1) return EnchantReply(error = "notOwned")
            plain(itemId)
        }
        if (target.pending != null) return EnchantReply(error = "pending")

        shopService.consumeGood(box, ticketId, 1, true)
        enchantDao.saveBox(userId, box)

        val chance = if (ticket.guaranteed) 100 else EnchantRules.chance(pity)
        val success = EnchantEngine.succeeds(ticket, pity, random)
        val line = if (success) EnchantEngine.rollLine(target.lines, ticket.topTierChance, random) else null

        val nextPity = if (success) 0 else pity + 1
        enchantDao.savePity(userId, itemId, nextPity)
        val next: EnchantInstance = when {
            line == null -> target
            target.instance == 0 -> enchantDao.createInstance(userId, itemId, 1, listOf(line))
            target.lines.size < EnchantRules.MAX_LINES -> target.copy(level = target.level + 1, lines = target.lines + line).also { enchantDao.saveInstance(userId, it) }
            else -> target.copy(level = target.level + 1, pending = line).also { enchantDao.saveInstance(userId, it) }
        }
        enchantDao.log(userId, itemId, ticketId, pity, chance, success, shopDao.getGood(ticketId)?.cost?.toLong() ?: 0L, line)
        return EnchantReply(item = itemId, success = success, line = line, state = stateOf(next, nextPity), box = box)
    }

    /** Settles a success that found the copy full: [slot] takes its place in the lines, null throws it away. */
    @Transactional
    fun resolve(session: HttpSession, itemId: String, instance: Int, slot: Int?): EnchantReply {
        val userId = userIdOf(session) ?: return EnchantReply(error = "guest")
        val pity = enchantDao.lockPity(userId, itemId)
        val inst = (if (instance > 0) enchantDao.lockInstance(userId, itemId, instance) else null) ?: return EnchantReply(error = "bad")
        val pending = inst.pending ?: return EnchantReply(error = "nothingPending")
        if (slot != null && slot !in inst.lines.indices) return EnchantReply(error = "bad")
        val lines = if (slot == null) inst.lines else inst.lines.toMutableList().also { it[slot] = pending }
        val next = inst.copy(lines = lines, pending = null)
        enchantDao.saveInstance(userId, next)
        return EnchantReply(item = itemId, state = stateOf(next, pity))
    }

    /** The copy of [itemId] worn from now on ([instance] 0: a plain copy); its lines are the ones that count. */
    @Transactional
    fun wear(session: HttpSession, itemId: String, instance: Int): EnchantReply {
        val userId = userIdOf(session) ?: return EnchantReply(error = "guest")
        val pity = enchantDao.lockPity(userId, itemId)
        if (instance < 0) return EnchantReply(error = "bad")
        val inst = if (instance == 0) plain(itemId) else enchantDao.lockInstance(userId, itemId, instance) ?: return EnchantReply(error = "bad")
        enchantDao.setWorn(userId, itemId, instance)
        return EnchantReply(item = itemId, state = stateOf(inst.copy(worn = instance > 0), pity))
    }
}
