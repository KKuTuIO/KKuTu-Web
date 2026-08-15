package me.kkutuio.kkutuweb.theme

import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Service
class ThemePresetAdminService(
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val setting: KKuTuSetting
) {
    private val logger = LoggerFactory.getLogger(ThemePresetAdminService::class.java)

    fun presets(): List<ThemePreset> = jdbcTemplate.query(
        """SELECT id, name, lang, themes, starts_at, ends_at, enabled, sort_order, created_at, updated_at
           FROM theme_preset ORDER BY lang, sort_order, created_at, id""".trimIndent()
    ) { rs, _ -> mapPreset(rs) }

    fun availableThemes(): Map<String, List<String>> = mapOf(
        "ko" to allowedThemes("ko").toList(),
        "en" to allowedThemes("en").toList()
    )

    @Transactional
    fun create(request: ThemePresetRequest): ThemePreset {
        validate(request, true)
        val id = request.id.trim()
        jdbcTemplate.update(
            """INSERT INTO theme_preset (id, name, lang, themes, starts_at, ends_at, enabled, sort_order)
               VALUES (?, ?, ?, string_to_array(?, ','), ?, ?, ?, ?)""".trimIndent(),
            id, request.name.trim(), request.lang, request.themes.distinct().joinToString(","),
            timestamp(request.startsAt), timestamp(request.endsAt), request.enabled, request.sortOrder
        )
        invalidateAfterCommit()
        return requirePreset(id)
    }

    @Transactional
    fun update(id: String, request: ThemePresetRequest): ThemePreset {
        validate(request.copy(id = id), false)
        val changed = jdbcTemplate.update(
            """UPDATE theme_preset
               SET name = ?, lang = ?, themes = string_to_array(?, ','), starts_at = ?, ends_at = ?, enabled = ?,
                   sort_order = ?, updated_at = NOW()
               WHERE id = ?""".trimIndent(),
            request.name.trim(), request.lang, request.themes.distinct().joinToString(","),
            timestamp(request.startsAt), timestamp(request.endsAt), request.enabled, request.sortOrder, id
        )
        require(changed == 1) { "존재하지 않는 주제 프리셋입니다." }
        invalidateAfterCommit()
        return requirePreset(id)
    }

    @Transactional
    fun delete(id: String) {
        val changed = jdbcTemplate.update("DELETE FROM theme_preset WHERE id = ?", id)
        require(changed == 1) { "존재하지 않는 주제 프리셋입니다." }
        invalidateAfterCommit()
    }

    private fun requirePreset(id: String): ThemePreset = jdbcTemplate.query(
        """SELECT id, name, lang, themes, starts_at, ends_at, enabled, sort_order, created_at, updated_at
           FROM theme_preset WHERE id = ?""".trimIndent(),
        { rs, _ -> mapPreset(rs) }, id
    ).firstOrNull() ?: throw IllegalArgumentException("존재하지 않는 주제 프리셋입니다.")

    private fun validate(request: ThemePresetRequest, creating: Boolean) {
        if (creating) require(request.id.trim().matches(Regex("[a-z0-9][a-z0-9-]{1,63}"))) {
            "프리셋 ID는 영문 소문자·숫자·하이픈 2~64자로 입력해 주세요."
        }
        require(request.name.trim().length in 1..80) { "프리셋 이름은 1~80자로 입력해 주세요." }
        require(request.lang == "ko" || request.lang == "en") { "지원하지 않는 언어입니다." }
        require(request.themes.size in 1..128) { "주제는 1~128개를 선택해 주세요." }
        require(request.themes.distinct().size == request.themes.size) { "같은 주제를 중복 선택할 수 없습니다." }
        require(request.sortOrder in -1_000_000..1_000_000) { "정렬 순서 범위가 올바르지 않습니다." }
        require(request.startsAt == null || request.startsAt >= 0) { "시작일이 올바르지 않습니다." }
        require(request.endsAt == null || request.endsAt >= 0) { "종료일이 올바르지 않습니다." }
        require(request.startsAt == null || request.endsAt == null || request.endsAt > request.startsAt) {
            "종료일은 시작일보다 뒤여야 합니다."
        }

        val allowed = allowedThemes(request.lang).toSet()
        val invalid = request.themes.filterNot(allowed::contains)
        require(invalid.isEmpty()) { "지원하지 않는 주제가 포함되어 있습니다: ${invalid.joinToString(", ")}" }
    }

    private fun allowedThemes(lang: String): List<String> {
        val configured = if (lang == "ko") {
            setting.getKoThemes() + setting.getKoInjeongThemes()
        } else {
            setting.getEnThemes() + setting.getEnInjeongThemes()
        }
        val exceptions = setting.getInjeongPickExcepts().toSet()
        return configured.distinct().filterNot(exceptions::contains)
    }

    private fun mapPreset(rs: ResultSet): ThemePreset {
        val startsAt = rs.getTimestamp("starts_at")?.time
        val endsAt = rs.getTimestamp("ends_at")?.time
        val enabled = rs.getBoolean("enabled")
        val now = System.currentTimeMillis()
        val status = when {
            !enabled -> "DISABLED"
            startsAt != null && startsAt > now -> "SCHEDULED"
            endsAt != null && endsAt <= now -> "EXPIRED"
            else -> "ACTIVE"
        }
        return ThemePreset(
            id = rs.getString("id"),
            name = rs.getString("name"),
            lang = rs.getString("lang"),
            themes = (rs.getArray("themes").array as Array<*>).map { it.toString() },
            startsAt = startsAt,
            endsAt = endsAt,
            enabled = enabled,
            sortOrder = rs.getInt("sort_order"),
            status = status,
            createdAt = rs.getTimestamp("created_at").time,
            updatedAt = rs.getTimestamp("updated_at").time
        )
    }

    private fun timestamp(value: Long?): Timestamp? = value?.let { Timestamp.from(Instant.ofEpochMilli(it)) }

    private fun invalidateAfterCommit() {
        val publish = {
            try {
                redisTemplate.convertAndSend(CACHE_INVALIDATION_CHANNEL, "changed")
            } catch (error: RuntimeException) {
                logger.warn("주제 프리셋 캐시 무효화 알림을 보내지 못했습니다. 게임 서버의 주기 갱신으로 복구합니다.", error)
            }
            Unit
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() { publish() }
            })
        } else publish()
    }

    companion object {
        const val CACHE_INVALIDATION_CHANNEL = "kkutu:theme-presets:invalidate"
    }
}
