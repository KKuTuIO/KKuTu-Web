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
    private val linkPattern = Regex("^(https://\\S+|/(?!/)\\S*)$")
    private val accentPattern = Regex("^#[0-9a-fA-F]{6}$")
    private val periods = setOf("daily", "weekly", "event")
    private val aggregations = setOf("count", "sum", "max")
    private val missionGoto = Regex("^(shop|gacha|practice|dictionary|rooms|tutorial:(lobby|chain|room))$")

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
        require(request.definition.path("EVENT_PERMANENT").isMissingNode || request.definition.path("EVENT_PERMANENT").isBoolean) { "상시 행사 여부는 true 또는 false여야 합니다." }
        assignExchangeIds(request.definition, id)
        validateWordPiece(request.definition.path("EVENT_WORDPIECE"))
        validatePoint(request.definition.path("EVENT_POINT"))
        validateItemPiece(request.definition.path("EVENT_ITEMPIECE"))
        validateSupport(request.definition.path("EVENT_SUPPORT"))
        validateMissions(request.definition.path("EVENT_MISSIONS"))
        validateAttendance(request.definition.path("EVENT_ATTENDANCE"))
        validateTileRoulette(request.definition.path("EVENT_TILEROULETTE"))
        validateAudience(request.definition.path("EVENT_AUDIENCE"))
        validateInvite(request.definition.path("EVENT_INVITE"))
        validateCampaign(request.definition.path("EVENT_CAMPAIGN"))
        validatePresentation(request.definition)
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
            require(exchange.path("enabled").isMissingNode || exchange.path("enabled").isBoolean) { "교환 사용 여부는 true 또는 false여야 합니다." }
            require(exchange.path("cost").isObject && exchange.path("gives").isObject && exchange.path("gives").size() > 0) { "교환 비용과 지급품 설정이 올바르지 않습니다." }
            val interval = exchange.path("buyLimitInterval").asLong(86400)
            require(exchange.path("stockLimit").asLong(0) >= 0 && exchange.path("buyLimit").asLong(0) >= 0 && interval > 0 && interval % 86400L == 0L) {
                "교환 제한은 1일 이상의 정수 단위로 설정해야 합니다."
            }
            exchange.path("cost").properties().forEach { (_, amount) -> require(amount.asLong() >= 0) { "교환 비용은 0 이상이어야 합니다." } }
            exchange.path("gives").properties().forEach { (_, amount) -> require(amount.asLong() > 0) { "교환 지급 수량은 1 이상이어야 합니다." } }
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
            require(mission.path("goto").asString("").let { it.isEmpty() || missionGoto.matches(it) }) { "미션 이동 버튼의 목적지가 올바르지 않습니다." }
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

    private fun moduleEnabled(node: JsonNode) = node.isObject && !disabled(node)

    private fun optionalText(node: JsonNode, key: String, maximum: Int, label: String, required: Boolean = false) {
        val value = node.path(key)
        if (value.isMissingNode || value.isNull || (value.isString && value.asString().isEmpty())) {
            require(!required) { "$label 값이 필요합니다." }
            return
        }
        require(value.isString && value.asString().length <= maximum) { "$label 은(는) ${maximum}자 이하의 문자열이어야 합니다." }
        require(!required || value.asString().isNotBlank()) { "$label 값이 필요합니다." }
    }

    /** https 주소 또는 이 사이트의 경로만 허용한다(javascript:, data:, //host 는 거부). */
    private fun optionalLink(node: JsonNode, key: String, label: String, required: Boolean = false) {
        optionalText(node, key, 2048, label, required)
        val value = node.path(key)
        if (value.isString && value.asString().isNotEmpty()) {
            require(linkPattern.matches(value.asString())) { "$label 은(는) https 주소이거나 '/'로 시작하는 경로여야 합니다." }
        }
    }

    private fun validatePresentation(root: JsonNode) {
        val node = root.path("EVENT_PRESENTATION")
        if (node.isMissingNode || node.isNull) return
        require(node.isObject) { "표시 설정은 객체여야 합니다." }
        require(node.path("stage").isMissingNode || node.path("stage").isBoolean) { "무대형 화면 값은 true 또는 false여야 합니다." }
        optionalText(node, "summary", 120, "카드 한 줄 소개")
        optionalLink(node, "banner", "배너 이미지")
        optionalLink(node, "nudge", "넛지 이미지")
        optionalLink(node, "background", "배경 이미지")
        optionalLink(node, "titleArt", "제목 이미지")
        optionalLink(node, "cutout", "캐릭터 이미지")
        val accent = node.path("accent")
        if (!accent.isMissingNode && !accent.isNull && accent.asString() != "") require(accent.isString && accentPattern.matches(accent.asString())) { "강조 색은 #1a2b3c 형식이어야 합니다." }
        val rewards = node.path("rewards")
        if (!rewards.isMissingNode) {
            require(rewards.isArray && rewards.size() <= 6) { "카드 보상은 최대 6개의 배열이어야 합니다." }
            validateRewards(rewards)
        }
        val blocks = node.path("blocks")
        if (blocks.isMissingNode) return
        require(blocks.isArray && blocks.size() <= 40) { "표시 블록은 최대 40개의 배열이어야 합니다." }
        blocks.forEachIndexed { index, block ->
            val label = "표시 블록 ${index + 1}"
            require(block.isObject) { "$label 은(는) 객체여야 합니다." }
            when (block.path("type").asString()) {
                "text" -> { optionalText(block, "title", 100, "$label 제목"); optionalText(block, "body", 2000, "$label 본문", true) }
                "note" -> {
                    optionalText(block, "body", 500, "$label 본문", true)
                    require(block.path("tone").isMissingNode || block.path("tone").asString() in setOf("info", "warn")) { "$label 색조가 올바르지 않습니다." }
                }
                "image" -> { optionalLink(block, "src", "$label 이미지", true); optionalText(block, "alt", 120, "$label 대체 텍스트", true) }
                "button" -> { optionalText(block, "label", 40, "$label 라벨", true); optionalLink(block, "link", "$label 링크", true) }
                "rewardTrack" -> {
                    optionalText(block, "title", 100, "$label 제목")
                    when (block.path("source").let { if (it.isMissingNode || it.isNull) "" else it.asString() }) {
                        "" -> {}
                        "attendance" -> require(moduleEnabled(root.path("EVENT_ATTENDANCE"))) { "$label 의 진행도는 출석부 기능을 켠 행사에서만 쓸 수 있습니다." }
                        "missions" -> require(moduleEnabled(root.path("EVENT_MISSIONS"))) { "$label 의 진행도는 미션 기능을 켠 행사에서만 쓸 수 있습니다." }
                        else -> throw IllegalArgumentException("$label 의 진행도 출처가 올바르지 않습니다.")
                    }
                    val steps = block.path("steps")
                    require(steps.isArray && steps.size() in 1..12) { "$label 은(는) 1~12단계여야 합니다." }
                    steps.forEach { step ->
                        require(step.isObject) { "$label 단계는 객체여야 합니다." }
                        optionalText(step, "label", 40, "$label 단계 이름")
                        validateRewards(step.path("rewards"))
                    }
                }
                "missions" -> require(moduleEnabled(root.path("EVENT_MISSIONS"))) { "$label 은(는) 미션 기능을 켠 행사에서만 쓸 수 있습니다." }
                "attendance" -> require(moduleEnabled(root.path("EVENT_ATTENDANCE"))) { "$label 은(는) 출석부 기능을 켠 행사에서만 쓸 수 있습니다." }
                "exchange" -> require(moduleEnabled(root.path("EVENT_ITEMPIECE"))) { "$label 은(는) 아이템 조각 기능을 켠 행사에서만 쓸 수 있습니다." }
                "invite" -> require(moduleEnabled(root.path("EVENT_INVITE"))) { "$label 은(는) 친구 초대 기능을 켠 행사에서만 쓸 수 있습니다." }
                "campaign" -> require(moduleEnabled(root.path("EVENT_CAMPAIGN"))) { "$label 은(는) 캠페인 기능을 켠 행사에서만 쓸 수 있습니다." }
                "tileRoulette" -> require(moduleEnabled(root.path("EVENT_TILEROULETTE"))) { "$label 은(는) 타일 룰렛 기능을 켠 행사에서만 쓸 수 있습니다." }
                else -> throw IllegalArgumentException("$label 의 종류가 올바르지 않습니다.")
            }
        }
    }

    private val itemIdPattern = Regex("^[A-Za-z0-9$][A-Za-z0-9$._:-]{0,63}$")

    /** 타일 룰렛: 게임 서버의 EventDefinition.normalizeTileRoulette 와 같은 규칙. 칸은 정확히 25개, 가중치는 0보다 커야 한다. */
    private fun validateTileRoulette(node: JsonNode) {
        if (node.isMissingNode) return
        require(node.isObject) { "타일 룰렛 설정은 객체여야 합니다." }
        if (disabled(node)) return
        require(itemIdPattern.matches(node.path("TOKEN").asString())) { "타일 룰렛의 토큰 아이템 ID가 필요합니다." }
        val cost = node.path("COST")
        require(cost.isMissingNode || (cost.isIntegralNumber && cost.asInt() in 1..100)) { "타일 룰렛 소모 개수는 1~100 사이의 정수여야 합니다." }
        val multi = node.path("MULTI")
        if (!multi.isMissingNode) {
            require(multi.isArray && multi.size() <= 3 && multi.all { it.isIntegralNumber && it.asInt() in 2..10 }) { "타일 룰렛의 묶음 횟수는 2~10 사이 정수 최대 3개여야 합니다." }
        }
        val cells = node.path("CELLS")
        require(cells.isArray && cells.size() == 25) { "타일 룰렛의 칸은 정확히 25개여야 합니다." }
        cells.forEachIndexed { index, cell ->
            val label = "타일 룰렛 ${index + 1}번 칸"
            require(cell.isObject) { "$label 은(는) 객체여야 합니다." }
            val weight = cell.path("weight")
            require(weight.isNumber && weight.asDouble() > 0 && weight.asDouble() <= 1_000_000) { "$label 의 가중치는 0보다 커야 합니다." }
            require(cell.path("unique").isMissingNode || cell.path("unique").isBoolean) { "$label 의 한 번만 지급 값은 true 또는 false여야 합니다." }
            val reward = cell.path("reward")
            require(reward.isObject) { "$label 의 보상이 필요합니다." }
            validateReward(reward, false)
        }
        val gauge = node.path("GAUGE")
        if (gauge.isMissingNode || gauge.isNull) return
        require(gauge.isObject) { "타일 룰렛의 마일리지 설정은 객체여야 합니다." }
        val perDraw = gauge.path("PER_DRAW")
        require(perDraw.isMissingNode || (perDraw.isIntegralNumber && perDraw.asInt() in 1..100)) { "마일리지가 한 번에 쌓이는 양은 1~100 사이의 정수여야 합니다." }
        val steps = gauge.path("STEPS")
        require(steps.isArray && steps.size() in 1..5) { "마일리지 단계는 1~5개여야 합니다." }
        var previous = 0
        steps.forEachIndexed { index, step ->
            val points = step.path("points")
            require(points.isIntegralNumber && points.asInt() in 1..100_000) { "마일리지 ${index + 1}단계 포인트는 1~100000 사이의 정수여야 합니다." }
            require(points.asInt() > previous) { "마일리지 단계의 포인트는 점점 커져야 합니다." }
            previous = points.asInt()
            val rewards = step.path("rewards")
            require(rewards.isArray && rewards.size() in 1..10) { "마일리지 ${index + 1}단계의 보상은 1~10개여야 합니다." }
            validateRewards(rewards, false)
        }
    }

    private val clockPattern = Regex("^(\\d{2}):(\\d{2})$")
    private fun clockMinutes(text: String): Int? {
        val m = clockPattern.matchEntire(text) ?: return null
        val minutes = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
        return if (m.groupValues[2].toInt() < 60 && minutes <= 1440) minutes else null
    }

    /** 캠페인: 게임 서버의 EventDefinition.normalizeCampaign 과 같은 규칙. */
    /** 대상: 신규(가입 후 N일 이내) 또는 복귀(N일 이상 쉬다 돌아온 뒤 window일 동안). 비워 두면 모두. */
    private fun validateAudience(node: JsonNode) {
        if (node.isMissingNode || node.isNull) return
        val kind = node.path("kind").asString("")
        require(kind == "newcomer" || kind == "returning") { "행사 대상 종류가 올바르지 않습니다." }
        val days = node.path("days")
        require(days.isIntegralNumber && days.asInt() in 1..365) { "대상 기간은 1~365일이어야 합니다." }
        val window = node.path("window")
        require(window.isMissingNode || (kind == "returning" && window.isIntegralNumber && window.asInt() in 1..365)) { "복귀 대상 진행 기간은 1~365일이어야 합니다." }
    }

    /** 친구 초대: 인원, 신규로 보는 기간, 그리고 친구가 해야 할 단계와 양쪽의 보상. */
    private fun validateInvite(node: JsonNode) {
        if (node.isMissingNode || node.isNull || disabled(node)) return
        require(node.isObject) { "친구 초대 설정은 객체여야 합니다." }
        val max = node.path("MAX_INVITES")
        require(max.isMissingNode || (max.isIntegralNumber && max.asInt() in 1..20)) { "초대 인원은 1~20 사이의 정수여야 합니다." }
        val days = node.path("NEWCOMER_DAYS")
        require(days.isMissingNode || (days.isIntegralNumber && days.asInt() in 1..365)) { "신규로 보는 기간은 1~365일이어야 합니다." }
        val stages = node.path("STAGES")
        require(stages.isArray && stages.size() in 1..10) { "친구 초대 단계는 1~10개여야 합니다." }
        val ids = mutableSetOf<String>()
        stages.forEachIndexed { index, stage ->
            val label = "친구 초대 ${index + 1}단계"
            require(stage.path("id").asString("").matches(Regex("^[A-Za-z0-9_.:-]{1,64}$")) && ids.add(stage.path("id").asString())) { "$label 의 ID가 올바르지 않거나 겹칩니다." }
            require(stage.path("name").asString("").isNotBlank() && stage.path("name").asString().length <= 40) { "$label 의 이름이 필요합니다(40자 이내)." }
            require(stage.path("metric").asString("").isNotBlank()) { "$label 의 신호(metric)가 필요합니다." }
            require(stage.path("target").isIntegralNumber && stage.path("target").asLong() in 1..1_000_000) { "$label 의 목표는 1~1000000 사이의 정수여야 합니다." }
            val invitee = stage.path("inviteeRewards")
            val inviter = stage.path("inviterRewards")
            if (!invitee.isMissingNode) validateRewards(invitee)
            if (!inviter.isMissingNode) validateRewards(inviter)
            require(invitee.size() + inviter.size() > 0) { "$label 은(는) 양쪽 모두 보상이 없습니다." }
        }
    }

    private fun validateCampaign(node: JsonNode) {
        if (node.isMissingNode) return
        require(node.isObject) { "캠페인 설정은 객체여야 합니다." }
        if (disabled(node)) return
        validateTimezone(node.path("TIMEZONE").asString("Asia/Seoul"), "캠페인")
        val schedule = node.path("SCHEDULE")
        require(schedule.isArray && schedule.size() in 1..7) { "캠페인 핫타임은 1~7개여야 합니다." }
        val spans = mutableListOf<Triple<Set<Int>, Int, Int>>()
        schedule.forEachIndexed { index, window ->
            val label = "캠페인 핫타임 ${index + 1}"
            val from = clockMinutes(window.path("from").asString())
            val to = clockMinutes(window.path("to").asString())
            require(from != null && to != null && from < 1440) { "$label 의 시작과 끝은 20:00 같은 형식이어야 합니다(끝은 24:00 가능)." }
            require(from < to) { "$label 은(는) 같은 날 안에서 시작보다 늦게 끝나야 합니다." }
            val days = window.path("days")
            val set = if (days.isMissingNode) (0..6).toSet() else {
                require(days.isArray && days.size() > 0 && days.all { it.isIntegralNumber && it.asInt() in 0..6 }) { "$label 의 요일은 0(일)~6(토) 정수 목록이어야 합니다." }
                val collected = mutableSetOf<Int>()
                days.forEach { collected.add(it.asInt()) }
                collected
            }
            spans.add(Triple(set, from, to))
        }
        for (weekday in 0..6) {
            val day = spans.filter { weekday in it.first }.sortedBy { it.second }
            for (i in 1 until day.size) require(day[i].second >= day[i - 1].third) { "캠페인 핫타임이 요일 ${weekday}에 겹칩니다." }
        }
        val goal = node.path("GOAL")
        require(goal.isObject && goal.path("metric").asString().isNotBlank() && goal.path("metric").asString().length <= 64) { "캠페인 목표 지표가 필요합니다." }
        require(goal.path("filters").let { it.isMissingNode || it.isNull || it.isObject }) { "캠페인 목표 필터는 객체여야 합니다." }
        val unit = goal.path("points")
        require(unit.isMissingNode || (unit.isIntegralNumber && unit.asInt() in 1..1_000_000)) { "캠페인 단위당 점수는 1~1000000 사이의 정수여야 합니다." }
        val tiers = node.path("TIERS")
        require(tiers.isArray && tiers.size() in 1..8) { "캠페인 단계는 1~8개여야 합니다." }
        var previous = 0L
        tiers.forEachIndexed { index, tier ->
            val points = tier.path("points")
            require(points.isIntegralNumber && points.asLong() in 1..1_000_000_000_000L) { "캠페인 ${index + 1}단계 포인트는 1 이상의 정수여야 합니다." }
            require(points.asLong() > previous) { "캠페인 단계의 포인트는 점점 커져야 합니다." }
            previous = points.asLong()
            val rewards = tier.path("rewards")
            require(rewards.isArray && rewards.size() in 1..10) { "캠페인 ${index + 1}단계의 보상은 1~10개여야 합니다." }
            validateRewards(rewards)
        }
        val cap = node.path("CONTRIBUTION_CAP")
        require(cap.isMissingNode || cap.isNull || (cap.isIntegralNumber && cap.asLong() in 1..1_000_000_000L)) { "캠페인 기여 상한은 1 이상의 정수여야 합니다." }
        require(node.path("ANNOUNCE").let { it.isMissingNode || it.isBoolean }) { "캠페인 공지 여부는 true 또는 false여야 합니다." }
    }

    private fun validateTimezone(timezone: String, context: String) {
        try { ZoneId.of(timezone) } catch (error: Exception) { throw IllegalArgumentException("$context 시간대가 올바르지 않습니다.", error) }
    }

    private fun validateRewards(node: JsonNode, allowLetters: Boolean = true) {
        require(node.isArray && node.size() <= 20) { "보상은 최대 20개의 배열이어야 합니다." }
        node.forEach { validateReward(it, allowLetters) }
    }

    private val letterTiers = setOf("common", "advanced", "rare")

    private fun validateReward(reward: JsonNode, allowLetters: Boolean = true) {
        require(reward.path("type").asString() in (if (allowLetters) setOf("ping", "item", "ep", "letter") else setOf("ping", "item", "ep"))) { "지원하지 않는 보상 종류입니다." }
        if (reward.path("type").asString() == "letter") require(reward.path("tier").asString("") in letterTiers) { "글자 조각 보상에는 등급(common, advanced, rare)이 필요합니다." }
        require(reward.path("q").asLong() > 0) { "보상 수량은 1 이상이어야 합니다." }
        if (reward.path("type").asString() == "item") require(reward.path("name").asString().isNotBlank()) { "아이템 ID가 필요합니다." }
        val expire = reward.path("expire")
        if (!expire.isMissingNode) require(expire.asLong() == -1L || expire.asLong() > 0) { "보상 만료일은 -1 또는 1일 이상이어야 합니다." }
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
