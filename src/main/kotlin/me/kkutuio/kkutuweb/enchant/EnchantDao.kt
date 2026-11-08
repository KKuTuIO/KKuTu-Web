package me.kkutuio.kkutuweb.enchant

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.postgresql.util.PGobject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component

/**
 * One enchanted copy of an item. The box only counts copies, so a copy that has been enchanted is told apart here: [instance] is its
 * number among the user's copies of that item (1, 2, ...), [level] how many enchants succeeded on it, [worn] whether it is the copy
 * whose lines count while the item is worn. The copies without a row are the plain ones.
 */
data class EnchantInstance(
    val itemId: String,
    val instance: Int,
    val level: Int,
    val lines: List<EnchantLine>,
    val pending: EnchantLine?,
    val worn: Boolean
)

@Component
class EnchantDao(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val objectMapper: ObjectMapper
) {
    private val instanceMapper = RowMapper<EnchantInstance> { rs, _ ->
        EnchantInstance(
            itemId = rs.getString("item_id"),
            instance = rs.getInt("instance"),
            level = rs.getInt("level"),
            lines = readLines(rs.getString("lines")),
            pending = rs.getString("pending")?.let { objectMapper.readValue(it, EnchantLine::class.java) },
            worn = rs.getBoolean("worn")
        )
    }

    private fun readLines(json: String?): List<EnchantLine> =
        if (json.isNullOrBlank()) emptyList() else objectMapper.readValue(json, Array<EnchantLine>::class.java).toList()

    private fun jsonb(value: Any?): PGobject = PGobject().apply {
        type = "jsonb"
        this.value = value?.let { objectMapper.writeValueAsString(it) }
    }

    /** The failures in a row on this kind of item (shared by its copies); the row is created when missing and locked until the end of the transaction. */
    fun lockPity(userId: String, itemId: String): Int {
        jdbcTemplate.update("INSERT INTO item_enchant(user_id, item_id) VALUES(?, ?) ON CONFLICT DO NOTHING", userId, itemId)
        return jdbcTemplate.queryForObject(
            "SELECT pity FROM item_enchant WHERE user_id = ? AND item_id = ? FOR UPDATE", Int::class.java, userId, itemId
        ) ?: 0
    }

    fun savePity(userId: String, itemId: String, pity: Int) {
        jdbcTemplate.update(
            "UPDATE item_enchant SET pity = ?, attempts = attempts + 1, updated_at = now() WHERE user_id = ? AND item_id = ?", pity, userId, itemId
        )
    }

    fun pityOf(userId: String): Map<String, Int> =
        jdbcTemplate.query("SELECT item_id, pity FROM item_enchant WHERE user_id = ?", { rs, _ -> rs.getString(1) to rs.getInt(2) }, userId).toMap()

    fun instancesOf(userId: String): List<EnchantInstance> = jdbcTemplate.query(
        "SELECT item_id, instance, level, lines, pending, worn FROM item_enchant_instance WHERE user_id = ? ORDER BY item_id, instance", instanceMapper, userId
    )

    fun instanceCount(userId: String, itemId: String): Int = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM item_enchant_instance WHERE user_id = ? AND item_id = ?", Int::class.java, userId, itemId
    ) ?: 0

    fun lockInstance(userId: String, itemId: String, instance: Int): EnchantInstance? = jdbcTemplate.query(
        "SELECT item_id, instance, level, lines, pending, worn FROM item_enchant_instance WHERE user_id = ? AND item_id = ? AND instance = ? FOR UPDATE",
        instanceMapper, userId, itemId, instance
    ).firstOrNull()

    /** A plain copy becomes an enchanted one: the next number among this item's copies. */
    fun createInstance(userId: String, itemId: String, level: Int, lines: List<EnchantLine>): EnchantInstance {
        val next = (jdbcTemplate.queryForObject(
            "SELECT coalesce(max(instance), 0) + 1 FROM item_enchant_instance WHERE user_id = ? AND item_id = ?", Int::class.java, userId, itemId
        ) ?: 1)
        jdbcTemplate.update(
            "INSERT INTO item_enchant_instance(user_id, item_id, instance, level, lines) VALUES(?, ?, ?, ?, ?)", userId, itemId, next, level, jsonb(lines)
        )
        return EnchantInstance(itemId, next, level, lines, null, false)
    }

    fun saveInstance(userId: String, inst: EnchantInstance) {
        jdbcTemplate.update(
            "UPDATE item_enchant_instance SET level = ?, lines = ?, pending = ?, updated_at = now() WHERE user_id = ? AND item_id = ? AND instance = ?",
            inst.level, jsonb(inst.lines), jsonb(inst.pending), userId, inst.itemId, inst.instance
        )
    }

    /** [instance] becomes the copy that counts while the item is worn; 0 means a plain copy (none of the enchanted ones). */
    fun setWorn(userId: String, itemId: String, instance: Int) {
        jdbcTemplate.update("UPDATE item_enchant_instance SET worn = (instance = ?) WHERE user_id = ? AND item_id = ?", instance, userId, itemId)
    }

    /** The user's box, locked until the end of the transaction so two tries cannot spend the same ticket. */
    fun lockBox(userId: String): JsonNode? =
        jdbcTemplate.query("SELECT box FROM users WHERE _id = ? FOR UPDATE", { rs, _ -> objectMapper.readTree(rs.getString(1) ?: "{}") }, userId).firstOrNull()

    fun saveBox(userId: String, box: JsonNode) {
        jdbcTemplate.update("UPDATE users SET box = ? WHERE _id = ?", PGobject().apply { type = "json"; value = objectMapper.writeValueAsString(box) }, userId)
    }

    /** One line per attempt: the numbers the economy test is read from. */
    fun log(userId: String, itemId: String, ticket: String, pityBefore: Int, chance: Int, success: Boolean, cost: Long, line: EnchantLine?) {
        jdbcTemplate.update(
            "INSERT INTO item_enchant_log(user_id, item_id, ticket, pity_before, chance, success, cost, line) VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
            userId, itemId, ticket, pityBefore, chance, success, cost, jsonb(line)
        )
    }
}
