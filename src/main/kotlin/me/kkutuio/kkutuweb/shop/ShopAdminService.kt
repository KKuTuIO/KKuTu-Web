package me.kkutuio.kkutuweb.shop

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.game.GameClientManager
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet

@Service
class ShopAdminService(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val gameClientManager: GameClientManager
) {
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val groupPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$")

    fun list(
        page: Int,
        size: Int,
        sort: String,
        query: String,
        queryTarget: String,
        queryMatch: String,
        groups: String,
        saleStatus: String,
        itemStatus: String,
        flags: String,
        minCost: Long?,
        maxCost: Long?,
        minHit: Int?,
        maxHit: Int?,
        minTerm: Int?,
        maxTerm: Int?
    ): ListResponse<ShopAdminItem> {
        require(page >= 0) { "페이지는 0 이상이어야 합니다." }
        require(size in 1..150) { "페이지 크기는 1~150이어야 합니다." }
        require(minCost == null || maxCost == null || minCost <= maxCost) { "최소 가격은 최대 가격보다 클 수 없습니다." }
        require(minHit == null || maxHit == null || minHit <= maxHit) { "최소 판매량은 최대 판매량보다 클 수 없습니다." }
        require(minTerm == null || maxTerm == null || minTerm <= maxTerm) { "최소 기간은 최대 기간보다 클 수 없습니다." }
        val where = mutableListOf<String>()
        val parameters = mutableListOf<Any>()
        addTextSearch(where, parameters, query, queryTarget, queryMatch)
        val selectedGroups = groups.split(',').map(String::trim).filter(String::isNotEmpty).distinct().take(50)
        if (selectedGroups.isNotEmpty()) {
            where += "s.\"group\" IN (${selectedGroups.joinToString(",") { "?" }})"
            parameters.addAll(selectedGroups)
        }
        when (saleStatus.uppercase()) {
            "FOR_SALE" -> where += "s.cost >= 0"
            "NOT_FOR_SALE" -> where += "s.cost < 0"
        }
        when (itemStatus.uppercase()) {
            "RANDOM_BOX" -> where += "s.options::jsonb ? 'gives'"
            "NORMAL" -> where += "NOT (s.options::jsonb ? 'gives')"
        }
        flags.split(',').map(String::trim).map(String::uppercase).distinct().forEach { flag ->
            when (flag) {
                "GIFTABLE" -> where += "COALESCE((s.options::jsonb ->> 'giftable')::boolean, FALSE)"
                "GIF" -> where += "s.options::jsonb ? 'gif'"
                "EVENT" -> where += "s.options::jsonb ? 'event'"
                "EFFECT" -> where += "s.options::jsonb ?| ARRAY['gEXP', 'hEXP', 'gMNY', 'hMNY']"
            }
        }
        minCost?.let { where += "s.cost >= ?"; parameters += it }
        maxCost?.let { where += "s.cost <= ?"; parameters += it }
        minHit?.let { where += "s.hit >= ?"; parameters += it }
        maxHit?.let { where += "s.hit <= ?"; parameters += it }
        minTerm?.let { where += "s.term >= ?"; parameters += it }
        maxTerm?.let { where += "s.term <= ?"; parameters += it }
        val whereSql = if (where.isEmpty()) "" else " WHERE ${where.joinToString(" AND ")}"
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM kkutu_shop s
            LEFT JOIN kkutu_shop_desc d ON d._id = s._id
            $whereSql
            """.trimIndent(),
            Int::class.java,
            *parameters.toTypedArray()
        ) ?: 0
        val (sortColumn, sortDirection) = parseItemSort(sort)
        val content = jdbcTemplate.query(
            """
            SELECT s._id, s.cost, s.hit, s.term, s."group", s."updatedAt", s.options,
                   d."name_ko_KR" AS "name_ko_KR", d."desc_ko_KR" AS "desc_ko_KR",
                   d."name_en_US" AS "name_en_US", d."desc_en_US" AS "desc_en_US"
            FROM kkutu_shop s
            LEFT JOIN kkutu_shop_desc d ON d._id = s._id
            $whereSql
            ORDER BY $sortColumn $sortDirection NULLS LAST, s._id ASC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            ::mapItem,
            *(parameters + listOf(size, page * size)).toTypedArray()
        )
        return ListResponse(count, content)
    }

    @Transactional
    fun create(request: ShopAdminItemRequest, adminId: String): ShopAdminItem {
        val normalized = validate(request)
        try {
            jdbcTemplate.update(
                """
                INSERT INTO kkutu_shop (_id, cost, hit, term, "group", "updatedAt", options)
                VALUES (?, ?, 0, ?, ?, NOW(), CAST(? AS json))
                """.trimIndent(),
                normalized.id, normalized.cost, normalized.term, normalized.group,
                objectMapper.writeValueAsString(normalized.options)
            )
            upsertDescription(normalized)
        } catch (error: DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 아이템 ID입니다.", error)
        }
        val created = find(normalized.id)
        audit(normalized.id, "CREATE", null, created, adminId)
        return created
    }

    @Transactional
    fun update(itemId: String, request: ShopAdminItemRequest, adminId: String): ShopAdminItem {
        require(request.id == itemId) { "아이템 ID는 수정할 수 없습니다." }
        val normalized = validate(request)
        val before = find(itemId)
        jdbcTemplate.update(
            """
            UPDATE kkutu_shop
            SET cost = ?, term = ?, "group" = ?, "updatedAt" = NOW(), options = CAST(? AS json)
            WHERE _id = ?
            """.trimIndent(),
            normalized.cost, normalized.term, normalized.group,
            objectMapper.writeValueAsString(normalized.options), itemId
        )
        upsertDescription(normalized)
        val after = find(itemId)
        audit(itemId, "UPDATE", before, after, adminId)
        return after
    }

    @Transactional
    fun delete(itemId: String, adminId: String) {
        val before = find(itemId)
        jdbcTemplate.update("DELETE FROM kkutu_shop_desc WHERE _id = ?", itemId)
        jdbcTemplate.update("DELETE FROM kkutu_shop WHERE _id = ?", itemId)
        audit(itemId, "DELETE", before, null, adminId)
    }

    @Transactional
    fun refresh(adminId: String): ShopRefreshResponse {
        val connectedServers = gameClientManager.refreshShop()
        val result = ShopRefreshResponse(connectedServers)
        audit("*", "REFRESH", null, result, adminId)
        return result
    }

    fun audits(
        page: Int,
        size: Int,
        sort: String,
        itemId: String,
        action: String,
        admin: String
    ): ListResponse<ShopAuditEntry> {
        require(page >= 0) { "페이지는 0 이상이어야 합니다." }
        require(size in 1..150) { "페이지 크기는 1~150이어야 합니다." }
        val (sortColumn, sortDirection) = parseSort(sort)
        val where = mutableListOf<String>()
        val parameters = mutableListOf<Any>()
        if (itemId.isNotBlank()) {
            where += "item_id ILIKE ?"
            parameters += "%${itemId.trim()}%"
        }
        if (action.isNotBlank()) {
            where += "action = ?"
            parameters += action.trim().uppercase()
        }
        if (admin.isNotBlank()) {
            where += "admin_id ILIKE ?"
            parameters += "%${admin.trim()}%"
        }
        val whereSql = if (where.isEmpty()) "" else " WHERE ${where.joinToString(" AND ")}"
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM shop_audit_log$whereSql",
            Int::class.java,
            *parameters.toTypedArray()
        ) ?: 0
        val content = jdbcTemplate.query(
            """
            SELECT id, item_id, action, before_data, after_data, admin_id, created_at
            FROM shop_audit_log$whereSql
            ORDER BY $sortColumn $sortDirection
            LIMIT ? OFFSET ?
            """.trimIndent(),
            ::mapAudit,
            *(parameters + listOf(size, page * size)).toTypedArray()
        )
        return ListResponse(count, content)
    }

    private fun validate(request: ShopAdminItemRequest): ShopAdminItemRequest {
        val id = request.id.trim()
        val nameKoKR = request.nameKoKR.trim()
        val descKoKR = request.descKoKR.trim()
        val nameEnUS = request.nameEnUS?.trim()?.takeIf(String::isNotEmpty)
        val descEnUS = request.descEnUS?.trim()?.takeIf(String::isNotEmpty)
        val group = request.group.trim()
        require(idPattern.matches(id)) { "아이템 ID는 영문, 숫자, 점, 밑줄, 콜론, 하이픈만 사용할 수 있습니다." }
        require(nameKoKR.isNotEmpty() && nameKoKR.length <= 100) { "한국어 이름은 1~100자여야 합니다." }
        require(descKoKR.length <= 4_000 && descKoKR.none { it == '\u0000' }) { "한국어 설명은 4,000자 이하여야 합니다." }
        require(nameEnUS == null || nameEnUS.length <= 100) { "영어 이름은 100자 이하여야 합니다." }
        require(descEnUS == null || descEnUS.length <= 4_000) { "영어 설명은 4,000자 이하여야 합니다." }
        require(request.cost >= -1) { "가격은 0 이상이어야 하며, 비매품은 -1을 사용합니다." }
        require(request.term >= 0) { "사용 기간은 0 이상이어야 합니다." }
        require(groupPattern.matches(group)) { "아이템 종류 형식이 올바르지 않습니다." }
        validateOptions(request.options)
        return request.copy(
            id = id,
            nameKoKR = nameKoKR,
            descKoKR = descKoKR,
            nameEnUS = nameEnUS,
            descEnUS = descEnUS,
            group = group
        )
    }

    private fun validateOptions(options: JsonNode) {
        require(options.isObject) { "아이템 옵션은 JSON 객체여야 합니다." }
        require(objectMapper.writeValueAsBytes(options).size <= 64 * 1024) { "아이템 옵션은 64KB 이하여야 합니다." }
        for (effect in listOf("gEXP", "hEXP", "gMNY", "hMNY")) {
            if (options.has(effect)) require(options[effect].isNumber) { "$effect 효과는 숫자여야 합니다." }
        }
        for (flag in listOf("giftable", "gif", "event")) {
            if (options.has(flag)) require(options[flag].isBoolean) { "$flag 플래그는 true/false여야 합니다." }
        }
        if (!options.has("gives")) return
        val gives = options["gives"]
        require(gives.isArray && gives.size() in 1..50) { "gives는 1~50개의 지급 설정 배열이어야 합니다." }
        gives.forEachIndexed { index, give -> validateGive(give, index + 1) }
    }

    private fun validateGive(give: JsonNode, position: Int) {
        require(give.isObject) { "gives $position 번 항목은 객체여야 합니다." }
        val type = give.path("type").asText()
        val from = give.path("from").asText()
        require(type in setOf("item", "money", "exp")) { "gives $position 번 항목의 type이 올바르지 않습니다." }
        if (give.has("roll")) require(give["roll"].canConvertToInt() && give["roll"].asInt() >= 1) {
            "gives $position 번 항목의 roll은 1 이상이어야 합니다."
        }
        if (type == "item") {
            require(from in setOf("all", "full", "wei", "abs", "rel")) { "아이템 지급 방식이 올바르지 않습니다." }
            if (give.has("value")) require(give["value"].canConvertToInt() && give["value"].asInt() >= 1) {
                "아이템 지급 수량은 1 이상이어야 합니다."
            }
            if (give.has("expire")) require(give["expire"].canConvertToInt() && give["expire"].asInt() >= 0) {
                "아이템 만료 값은 0 이상이어야 합니다."
            }
            if (give.has("xmul")) require(give["xmul"].isBoolean) { "xmul은 true/false여야 합니다." }
            val pool = give.path("pool")
            if (from == "all" || from == "full") {
                require(pool.isArray && pool.size() > 0 && pool.all { it.isTextual && it.asText().isNotBlank() }) {
                    "$from 풀에는 하나 이상의 아이템 ID가 필요합니다."
                }
            } else {
                require(pool.isObject && pool.size() > 0) { "$from 풀은 아이템 ID와 확률/가중치의 객체여야 합니다." }
                pool.fields().forEach { (itemId, chance) ->
                    require(itemId.isNotBlank() && chance.isNumber && chance.asDouble() >= 0) { "풀의 확률/가중치는 0 이상이어야 합니다." }
                    if (from == "abs") require(chance.asDouble() <= 1) { "abs 확률은 0~1 사이여야 합니다." }
                    if (from == "rel") require(chance.asDouble() <= 100) { "rel 확률은 0~100 사이여야 합니다." }
                }
                if (from == "wei") require(pool.fields().asSequence().any { it.value.asDouble() > 0 }) { "wei 풀에는 0보다 큰 가중치가 필요합니다." }
                if (from == "rel") require(pool.fields().asSequence().sumOf { it.value.asDouble() } <= 100.000001) { "rel 확률 합계는 100을 넘을 수 없습니다." }
            }
            return
        }
        require(from in setOf("fix", "per", "dict")) { "재화 지급 방식이 올바르지 않습니다." }
        when (from) {
            "fix" -> require(give.path("value").isNumber && give["value"].asDouble() >= 0) { "고정 지급량은 0 이상이어야 합니다." }
            "per" -> require(give.path("per").isNumber && give["per"].asDouble() >= 0) { "재화 지급 비율이 필요합니다." }
            "dict" -> if (give.has("value")) require(give["value"].canConvertToInt() && give["value"].asInt() >= 1) { "낱장 조각 수는 1 이상이어야 합니다." }
        }
    }

    private fun upsertDescription(item: ShopAdminItemRequest) {
        jdbcTemplate.update(
            """
            INSERT INTO kkutu_shop_desc (_id, "name_ko_KR", "desc_ko_KR", "name_en_US", "desc_en_US")
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (_id) DO UPDATE SET
                "name_ko_KR" = EXCLUDED."name_ko_KR",
                "desc_ko_KR" = EXCLUDED."desc_ko_KR",
                "name_en_US" = EXCLUDED."name_en_US",
                "desc_en_US" = EXCLUDED."desc_en_US"
            """.trimIndent(),
            item.id, item.nameKoKR, item.descKoKR, item.nameEnUS, item.descEnUS
        )
    }

    private fun find(itemId: String): ShopAdminItem = jdbcTemplate.query(
        """
        SELECT s._id, s.cost, s.hit, s.term, s."group", s."updatedAt", s.options,
               d."name_ko_KR" AS "name_ko_KR", d."desc_ko_KR" AS "desc_ko_KR",
               d."name_en_US" AS "name_en_US", d."desc_en_US" AS "desc_en_US"
        FROM kkutu_shop s
        LEFT JOIN kkutu_shop_desc d ON d._id = s._id
        WHERE s._id = ?
        """.trimIndent(),
        ::mapItem,
        itemId
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "아이템을 찾을 수 없습니다.")

    private fun audit(itemId: String, action: String, before: Any?, after: Any?, adminId: String) {
        jdbcTemplate.update(
            """
            INSERT INTO shop_audit_log (item_id, action, before_data, after_data, admin_id)
            VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
            """.trimIndent(),
            itemId,
            action,
            before?.let(objectMapper::writeValueAsString),
            after?.let(objectMapper::writeValueAsString),
            adminId
        )
    }

    private fun parseSort(sort: String): Pair<String, String> {
        val values = sort.split(',', limit = 2)
        val column = when (values.firstOrNull()?.trim()) {
            "id" -> "id"
            "item_id" -> "item_id"
            "action" -> "action"
            "admin_id" -> "admin_id"
            else -> "created_at"
        }
        val direction = if (values.getOrNull(1)?.trim()?.uppercase() == "ASC") "ASC" else "DESC"
        return column to direction
    }

    private fun parseItemSort(sort: String): Pair<String, String> {
        val values = sort.split(',', limit = 2)
        val column = when (values.firstOrNull()?.trim()) {
            "id" -> "s._id"
            "name" -> "d.\"name_ko_KR\""
            "cost" -> "s.cost"
            "hit" -> "s.hit"
            "term" -> "s.term"
            "group" -> "s.\"group\""
            else -> "s.\"updatedAt\""
        }
        val direction = if (values.getOrNull(1)?.trim()?.uppercase() == "ASC") "ASC" else "DESC"
        return column to direction
    }

    private fun addTextSearch(
        where: MutableList<String>,
        parameters: MutableList<Any>,
        rawQuery: String,
        rawTarget: String,
        rawMatch: String
    ) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        val columns = when (rawTarget.uppercase()) {
            "ID" -> listOf("s._id")
            "NAME" -> listOf("COALESCE(d.\"name_ko_KR\", '')", "COALESCE(d.\"name_en_US\", '')")
            else -> listOf("s._id", "COALESCE(d.\"name_ko_KR\", '')", "COALESCE(d.\"name_en_US\", '')")
        }
        val match = rawMatch.uppercase()
        val escapedQuery = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val value = when (match) {
            "EXACT" -> escapedQuery
            "STARTS_WITH" -> "$escapedQuery%"
            "ENDS_WITH" -> "%$escapedQuery"
            else -> "%$escapedQuery%"
        }
        where += "(${columns.joinToString(" OR ") { "$it ILIKE ? ESCAPE '\\'" }})"
        repeat(columns.size) { parameters += value }
    }

    private fun mapItem(rs: ResultSet, ignored: Int) = ShopAdminItem(
        id = rs.getString("_id"),
        nameKoKR = rs.getString("name_ko_KR") ?: "",
        descKoKR = rs.getString("desc_ko_KR") ?: "",
        nameEnUS = rs.getString("name_en_US"),
        descEnUS = rs.getString("desc_en_US"),
        cost = rs.getLong("cost"),
        hit = rs.getInt("hit"),
        term = rs.getInt("term"),
        group = rs.getString("group") ?: "",
        updatedAt = rs.getTimestamp("updatedAt")?.toInstant(),
        options = rs.getString("options")?.let(objectMapper::readTree) ?: objectMapper.createObjectNode()
    )

    private fun mapAudit(rs: ResultSet, ignored: Int) = ShopAuditEntry(
        id = rs.getLong("id"),
        itemId = rs.getString("item_id"),
        action = rs.getString("action"),
        beforeData = rs.getString("before_data")?.let(objectMapper::readTree),
        afterData = rs.getString("after_data")?.let(objectMapper::readTree),
        adminId = rs.getString("admin_id"),
        createdAt = rs.getTimestamp("created_at").toInstant()
    )
}
