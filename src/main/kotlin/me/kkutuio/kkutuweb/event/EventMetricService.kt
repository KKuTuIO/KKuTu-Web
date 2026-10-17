package me.kkutuio.kkutuweb.event

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

/** Records authoritative web-side actions into the same mission ledger as the game server. */
@Service
class EventMetricService(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    private data class Mission(
        val eventId: String,
        val startsAt: Long,
        val endsAt: Long,
        val id: String,
        val metric: String,
        val aggregation: String,
        val period: String,
        val timezone: String,
        val filters: JsonNode?
    )

    private val logger = LoggerFactory.getLogger(javaClass)
    @Volatile private var missions: List<Mission> = emptyList()

    @PostConstruct
    fun initialize() = refresh()

    @Scheduled(fixedDelay = 30_000)
    fun refresh() {
        try {
            missions = jdbc.query(
                """SELECT event_id, starts_at, ends_at, definition
                     FROM game_events WHERE active=TRUE ORDER BY priority DESC, event_id"""
            ) { rs, _ ->
                val definition = mapper.readTree(rs.getString("definition"))
                val config = definition.path("EVENT_MISSIONS")
                if (!config.isObject || (config.path("IS_ENABLED").isBoolean && !config.path("IS_ENABLED").asBoolean())) return@query emptyList()
                val timezone = config.path("TIMEZONE").asString("Asia/Seoul")
                config.path("MISSIONS").takeIf(JsonNode::isArray)?.mapNotNull { mission ->
                    val id = mission.path("id").asString()
                    val metric = mission.path("metric").asString()
                    if (id.isBlank() || metric.isBlank()) null else Mission(
                        rs.getString("event_id"), rs.getLong("starts_at"), rs.getLong("ends_at"), id, metric,
                        mission.path("aggregation").asString("count"), mission.path("period").asString("daily"), timezone,
                        mission.path("filters").takeUnless(JsonNode::isMissingNode)
                    )
                } ?: emptyList()
            }.flatten()
        } catch (error: Exception) {
            logger.warn("행사 미션 캐시 갱신 실패, 직전 스냅샷을 유지합니다: {}", error.message)
        }
    }

    @Transactional
    fun record(userId: String, metric: String, amount: Number = 1, attributes: Map<String, Any?> = emptyMap()) {
        val now = System.currentTimeMillis()
        val matched = missions.filter { it.metric == metric && now in it.startsAt until it.endsAt && filtersMatch(it.filters, attributes) }
        for (mission in matched) {
            val value = if (mission.aggregation == "count") 1L else floor(amount.toDouble()).toLong().coerceAtLeast(0)
            val expression = if (mission.aggregation == "max")
                "GREATEST(game_event_mission_progress.progress, EXCLUDED.progress)"
            else "game_event_mission_progress.progress + EXCLUDED.progress"
            jdbc.update(
                """INSERT INTO game_event_mission_progress(user_id,event_id,mission_id,period_key,progress)
                     VALUES(?,?,?,?,?)
                     ON CONFLICT(user_id,event_id,mission_id,period_key) DO UPDATE
                     SET progress=$expression, updated_at=NOW()""",
                userId, mission.eventId, mission.id, periodKey(mission, now), value
            )
        }
    }

    private fun periodKey(mission: Mission, now: Long): String {
        if (mission.period == "event") return "event"
        val date = Instant.ofEpochMilli(now).atZone(ZoneId.of(mission.timezone)).toLocalDate()
        return if (mission.period == "weekly") "week:${date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))}" else date.toString()
    }

    private fun filtersMatch(filters: JsonNode?, attributes: Map<String, Any?>): Boolean {
        if (filters == null || !filters.isObject) return true
        return filters.properties().all { (key, expected) ->
            val actual = attributes[key]
            if (expected.isArray) expected.any { matches(it, actual) } else matches(expected, actual)
        }
    }

    private fun matches(expected: JsonNode, actual: Any?): Boolean = when {
        actual == null -> expected.isNull
        expected.isBoolean -> actual is Boolean && expected.asBoolean() == actual
        expected.isNumber -> actual is Number && expected.asDouble() == actual.toDouble()
        else -> expected.asString() == actual.toString()
    }
}
