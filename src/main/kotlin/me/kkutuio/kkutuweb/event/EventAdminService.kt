package me.kkutuio.kkutuweb.event

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.time.ZoneId

@Service
class EventAdminService(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val periods = setOf("daily", "weekly", "event")
    private val aggregations = setOf("count", "sum", "max")

    fun list(): List<EventAdminEvent> = jdbc.query(
        """SELECT event_id, name, starts_at, ends_at, expires_at, force_for_admin, active,
                  priority, definition, created_at, updated_at
             FROM game_events ORDER BY starts_at DESC, priority DESC, event_id""", ::mapEvent)

    @Transactional
    fun create(request: EventAdminRequest, adminId: String): EventAdminEvent {
        val value = validate(request)
        try {
            jdbc.update(
                """INSERT INTO game_events(event_id, name, starts_at, ends_at, expires_at,
                       force_for_admin, active, priority, definition)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))""",
                value.id, value.name, value.startsAt, value.endsAt, value.expiresAt,
                value.forceForAdmin, value.active, value.priority, mapper.writeValueAsString(value.definition)
            )
        } catch (error: DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 행사 ID입니다.", error)
        }
        val created = find(value.id)
        audit(value.id, "CREATE", null, created, adminId)
        notifyChanged(value.id)
        return created
    }

    @Transactional
    fun update(eventId: String, request: EventAdminRequest, adminId: String): EventAdminEvent {
        require(request.id == eventId) { "행사 ID는 수정할 수 없습니다." }
        val value = validate(request)
        val before = find(eventId)
        jdbc.update(
            """UPDATE game_events SET name=?, starts_at=?, ends_at=?, expires_at=?,
                   force_for_admin=?, active=?, priority=?, definition=CAST(? AS jsonb), updated_at=NOW()
                 WHERE event_id=?""",
            value.name, value.startsAt, value.endsAt, value.expiresAt, value.forceForAdmin,
            value.active, value.priority, mapper.writeValueAsString(value.definition), eventId
        )
        val after = find(eventId)
        audit(eventId, "UPDATE", before, after, adminId)
        notifyChanged(eventId)
        return after
    }

    @Transactional
    fun delete(eventId: String, adminId: String) {
        val before = find(eventId)
        jdbc.update("DELETE FROM game_events WHERE event_id=?", eventId)
        audit(eventId, "DELETE", before, null, adminId)
        notifyChanged(eventId)
    }

    fun audits(page: Int, size: Int, sort: String, eventId: String, action: String, admin: String): ListResponse<EventAuditEntry> {
        require(page >= 0 && size in 1..150) { "페이지 범위가 올바르지 않습니다." }
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (eventId.isNotBlank()) { where += "event_id ILIKE ?"; args += "%${eventId.trim()}%" }
        if (action.isNotBlank()) { where += "action=?"; args += action.trim().uppercase() }
        if (admin.isNotBlank()) { where += "admin_id ILIKE ?"; args += "%${admin.trim()}%" }
        val clause = if (where.isEmpty()) "" else " WHERE ${where.joinToString(" AND ")}"
        val count = jdbc.queryForObject("SELECT COUNT(*) FROM game_event_audit_log$clause", Int::class.java, *args.toTypedArray()) ?: 0
        val sortColumn = when (sort.substringBefore(',').trim()) { "event_id", "action", "admin_id" -> sort.substringBefore(',').trim(); else -> "created_at" }
        val direction = if (sort.substringAfter(',', "DESC").trim().uppercase() == "ASC") "ASC" else "DESC"
        val rows = jdbc.query(
            """SELECT id, event_id, action, before_data, after_data, admin_id, created_at
                 FROM game_event_audit_log$clause ORDER BY $sortColumn $direction LIMIT ? OFFSET ?""",
            ::mapAudit, *(args + listOf(size, page * size)).toTypedArray())
        return ListResponse(count, rows)
    }

    private fun validate(request: EventAdminRequest): EventAdminRequest {
        val id = request.id.trim()
        val name = request.name.trim()
        require(idPattern.matches(id)) { "행사 ID 형식이 올바르지 않습니다." }
        require(name.isNotEmpty() && name.length <= 100) { "행사 이름은 1~100자여야 합니다." }
        require(request.startsAt > 0 && request.startsAt < request.endsAt && request.endsAt <= request.expiresAt) { "행사 시작·종료·만료 시각의 순서가 올바르지 않습니다." }
        require(request.priority in -10_000..10_000) { "우선순위 범위가 올바르지 않습니다." }
        require(request.definition.isObject) { "행사 설정은 JSON 객체여야 합니다." }
        assignExchangeIds(request.definition, id)
        validateWordPiece(request.definition.path("EVENT_WORDPIECE"))
        validatePoint(request.definition.path("EVENT_POINT"))
        validateItemPiece(request.definition.path("EVENT_ITEMPIECE"))
        validateSupport(request.definition.path("EVENT_SUPPORT"))
        validateMissions(request.definition.path("EVENT_MISSIONS"))
        validateAttendance(request.definition.path("EVENT_ATTENDANCE"))
        return request.copy(id = id, name = name)
    }

    private fun assignExchangeIds(root: JsonNode, eventId: String) {
        val exchanges = root.path("EVENT_ITEMPIECE").path("EXCHANGE")
        if (!exchanges.isArray) return
        exchanges.forEachIndexed { index, exchange ->
            if (exchange.path("id").asString().isBlank() && exchange is ObjectNode) {
                exchange.put("id", "$eventId.exchange.$index")
            }
        }
    }

    private fun disabled(node: JsonNode) = node.path("IS_ENABLED").isBoolean && !node.path("IS_ENABLED").asBoolean()

    private fun validateWordPiece(node: JsonNode) {
        if (node.isMissingNode) return
        require(node.isObject) { "EVENT_WORDPIECE 설정은 객체여야 합니다." }
        if (disabled(node)) return
        require(node.path("DROP_RATE").asDouble(-1.0) in 0.0..1.0) { "글자 조각 드랍률은 0~1이어야 합니다." }
    }

    private fun validatePoint(node: JsonNode) {
        if (node.isMissingNode) return
        require(node.isObject) { "EVENT_POINT 설정은 객체여야 합니다." }
        if (disabled(node)) return
        require(node.path("INIT_POINT").asLong(0) >= 0) { "초기 EP는 0 이상이어야 합니다." }
        if (node.path("ENABLE_TEAM").asBoolean(false)) require(node.path("TEAM_LIST").isArray && node.path("TEAM_LIST").size() > 0) { "팀 행사에는 팀이 하나 이상 필요합니다." }
        validateRewardAmounts(node.path("REWARD_AMOUNT"), "EP")
    }

    private fun validateItemPiece(node: JsonNode) {
        if (node.isMissingNode) return
        require(node.isObject) { "EVENT_ITEMPIECE 설정은 객체여야 합니다." }
        if (disabled(node)) return
        require(node.path("POINT_VALUE").asLong(0) >= 0) { "아이템 조각 포인트 값은 0 이상이어야 합니다." }
        require(node.path("PIECE_POOL").isObject && node.path("REWARD_AMOUNT").isObject) { "조각 풀과 보상 수량 설정은 객체여야 합니다." }
        node.path("PIECE_POOL").properties().forEach { (id, weight) -> require(id.isNotBlank() && weight.asDouble() > 0) { "조각 '$id' 가중치는 0보다 커야 합니다." } }
        validateRewardAmounts(node.path("REWARD_AMOUNT"), "아이템 조각")
        val exchanges = node.path("EXCHANGE")
        require(exchanges.isArray && exchanges.size() <= 500) { "교환소 목록은 최대 500개의 배열이어야 합니다." }
        val ids = mutableSetOf<String>()
        exchanges.forEach { exchange ->
            val id = exchange.path("id").asString()
            if (id.isNotBlank()) require(idPattern.matches(id) && ids.add(id)) { "교환 ID가 올바르지 않거나 중복되었습니다: $id" }
            require(exchange.path("cost").isObject && exchange.path("gives").isObject && exchange.path("gives").size() > 0) { "교환 비용과 지급품 설정이 올바르지 않습니다." }
            require(exchange.path("stockLimit").asLong(0) >= 0 && exchange.path("buyLimit").asLong(0) >= 0 && exchange.path("buyLimitInterval").asLong(86400) > 0) { "교환 제한 값이 올바르지 않습니다." }
            (exchange.path("cost").properties() + exchange.path("gives").properties()).forEach { (_, amount) -> require(amount.asLong() > 0) { "교환 아이템 수량은 1 이상이어야 합니다." } }
        }
    }

    private fun validateSupport(node: JsonNode) {
        if (node.isMissingNode) return
        require(node.isObject) { "EVENT_SUPPORT 설정은 객체여야 합니다." }
        if (disabled(node)) return
        val items = node.path("ITEMS")
        require(items.isArray && items.size() <= 100) { "지원 아이템은 최대 100개의 배열이어야 합니다." }
        items.forEach { item ->
            require(item.path("id").asString().isNotBlank() && item.path("value").asLong() > 0) { "지원 아이템 ID와 수량이 올바르지 않습니다." }
            val expire = item.path("expire")
            if (!expire.isMissingNode) require(expire.asLong() == -1L || expire.asLong() > 0) { "지원 아이템 만료일은 -1 또는 1일 이상이어야 합니다." }
        }
    }

    private fun validateRewardAmounts(node: JsonNode, context: String) {
        require(node.isObject) { "$context 점수별 보상 설정은 객체여야 합니다." }
        node.properties().forEach { (border, pool) ->
            require(border.toLongOrNull()?.let { it >= 0 } == true && pool.isArray) { "$context 보상 기준 '$border'가 올바르지 않습니다." }
            pool.forEach { require(it.isNumber && it.asLong() >= 0) { "$context 보상 수량은 0 이상 숫자여야 합니다." } }
        }
    }

    private fun validateMissions(node: JsonNode) {
        if (node.isMissingNode) return
        require(node.isObject) { "미션 설정은 객체여야 합니다." }
        if (disabled(node)) return
        validateTimezone(node.path("TIMEZONE").asString("Asia/Seoul"), "미션")
        val missions = node.path("MISSIONS")
        require(missions.isArray && missions.size() <= 100) { "미션은 배열이며 최대 100개까지 등록할 수 있습니다." }
        val ids = mutableSetOf<String>()
        missions.forEach { mission ->
            val id = mission.path("id").asString()
            require(idPattern.matches(id) && ids.add(id)) { "미션 ID가 비어 있거나 중복되었습니다: $id" }
            require(mission.path("name").asString().isNotBlank()) { "미션 이름이 필요합니다." }
            require(mission.path("metric").asString().isNotBlank()) { "미션 지표가 필요합니다." }
            require(mission.path("target").asLong() > 0) { "미션 목표는 1 이상이어야 합니다." }
            require(mission.path("period").asString("daily") in periods) { "미션 주기가 올바르지 않습니다." }
            require(mission.path("aggregation").asString("count") in aggregations) { "미션 집계 방식이 올바르지 않습니다." }
            val filters = mission.path("filters")
            require(filters.isMissingNode || filters.isObject) { "미션 필터는 JSON 객체여야 합니다." }
            validateRewards(mission.path("rewards"))
        }
    }

    private fun validateAttendance(node: JsonNode) {
        if (node.isMissingNode) return
        require(node.isObject) { "출석 설정은 객체여야 합니다." }
        if (disabled(node)) return
        val type = node.path("TYPE").asString()
        require(type in setOf("7", "14", "30", "calendar")) { "출석부 유형이 올바르지 않습니다." }
        validateTimezone(node.path("TIMEZONE").asString("Asia/Seoul"), "출석부")
        val limit = if (type == "calendar") 31 else type.toInt()
        listOf("DAILY_REWARDS", "STREAK_REWARDS", "CUMULATIVE_REWARDS").forEach { key ->
            val rewards = node.path(key)
            if (rewards.isMissingNode) return@forEach
            require(rewards.isArray) { "$key 는 배열이어야 합니다." }
            val days = mutableSetOf<Int>()
            rewards.forEach { entry ->
                val day = entry.path("day").asInt()
                require(day in 1..limit && days.add(day)) { "$key 보상 일수는 1~${limit} 사이의 고유 값이어야 합니다." }
                validateRewards(entry.path("rewards"))
            }
        }
    }

    private fun validateTimezone(timezone: String, context: String) {
        try { ZoneId.of(timezone) } catch (error: Exception) { throw IllegalArgumentException("$context 시간대가 올바르지 않습니다.", error) }
    }

    private fun validateRewards(node: JsonNode) {
        require(node.isArray && node.size() <= 20) { "보상은 최대 20개의 배열이어야 합니다." }
        node.forEach { reward ->
            require(reward.path("type").asString() in setOf("ping", "item", "ep")) { "지원하지 않는 보상 종류입니다." }
            require(reward.path("q").asLong() > 0) { "보상 수량은 1 이상이어야 합니다." }
            if (reward.path("type").asString() == "item") require(reward.path("name").asString().isNotBlank()) { "아이템 ID가 필요합니다." }
            val expire = reward.path("expire")
            if (!expire.isMissingNode) require(expire.asLong() == -1L || expire.asLong() > 0) { "보상 만료일은 -1 또는 1일 이상이어야 합니다." }
        }
    }

    private fun find(id: String) = jdbc.query(
        """SELECT event_id, name, starts_at, ends_at, expires_at, force_for_admin, active,
                  priority, definition, created_at, updated_at FROM game_events WHERE event_id=?""",
        ::mapEvent, id).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "행사를 찾을 수 없습니다.")

    private fun audit(id: String, action: String, before: EventAdminEvent?, after: EventAdminEvent?, admin: String) = jdbc.update(
        """INSERT INTO game_event_audit_log(event_id, action, before_data, after_data, admin_id)
             VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)""",
        id, action, before?.let(mapper::writeValueAsString), after?.let(mapper::writeValueAsString), admin)

    private fun notifyChanged(id: String) { jdbc.queryForList("SELECT pg_notify('kkutu_event_changed', ?)", id) }

    private fun mapEvent(rs: ResultSet, ignored: Int) = EventAdminEvent(
        rs.getString("event_id"), rs.getString("name"), rs.getLong("starts_at"), rs.getLong("ends_at"),
        rs.getLong("expires_at"), rs.getBoolean("force_for_admin"), rs.getBoolean("active"), rs.getInt("priority"),
        mapper.readTree(rs.getString("definition")), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant())

    private fun mapAudit(rs: ResultSet, ignored: Int) = EventAuditEntry(
        rs.getLong("id"), rs.getString("event_id"), rs.getString("action"),
        rs.getString("before_data")?.let(mapper::readTree), rs.getString("after_data")?.let(mapper::readTree),
        rs.getString("admin_id"), rs.getTimestamp("created_at").toInstant())
}
