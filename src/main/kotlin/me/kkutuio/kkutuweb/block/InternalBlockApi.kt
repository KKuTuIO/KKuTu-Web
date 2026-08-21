package me.kkutuio.kkutuweb.block

import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
@RequestMapping("/api/internal/moderation")
class InternalBlockApi(
    private val jdbcTemplate: JdbcTemplate,
    private val setting: KKuTuSetting
) {
    @GetMapping("/users/{userId}")
    fun user(
        @PathVariable userId: String,
        @RequestParam apiKey: String
    ): InternalUserBlocks {
        requireApiKey(apiKey)
        val blocks = jdbcTemplate.query(
            """
            SELECT effects.effect_id, effects.effect_type, effects.starts_at,
                   effects.ends_at, effects.permanent, cases.summary, cases.issued_by
            FROM moderation_effects effects
            JOIN moderation_cases cases ON cases.case_id = effects.case_id
            JOIN game_profile profile ON (
                profile.id::text = ? OR profile.legacy_user_id = ? OR profile.uuid::text = ?
            )
            JOIN account account ON account.id = profile.account_id
            WHERE effects.subject_user_id = COALESCE(account.moderation_subject_uuid, account.uuid)::text
              AND effects.effect_type IN ('GAME_RESTRICTION', 'CHAT_RESTRICTION', 'EXTEND_RELATED_RESTRICTION')
              AND effects.apply_status = 'APPLIED'
              AND effects.revoked_at IS NULL AND cases.revoked_at IS NULL
              AND effects.starts_at <= NOW()
              AND (effects.permanent OR effects.ends_at > NOW())
            ORDER BY effects.permanent DESC, effects.ends_at DESC NULLS FIRST,
                     effects.effect_id DESC
            """.trimIndent(),
            { rs, _ ->
                InternalBlock(
                    id = rs.getLong("effect_id"),
                    type = rs.getString("effect_type"),
                    time = rs.getTimestamp("starts_at").toInstant(),
                    pardonTime = rs.getTimestamp("ends_at")?.toInstant(),
                    permanent = rs.getBoolean("permanent"),
                    reason = rs.getString("summary"),
                    admin = rs.getString("issued_by")
                )
            },
            userId, userId, userId
        )
        return InternalUserBlocks(
            game = blocks.firstOrNull { it.type == "GAME_RESTRICTION" || it.type == "EXTEND_RELATED_RESTRICTION" },
            chat = blocks.firstOrNull { it.type == "CHAT_RESTRICTION" }
        )
    }

    @GetMapping("/ips/{ip}")
    fun ip(
        @PathVariable ip: String,
        @RequestParam apiKey: String
    ): InternalBlock? {
        requireApiKey(apiKey)
        return jdbcTemplate.query(
            """
            SELECT effects.effect_id, effects.effect_type, effects.starts_at,
                   effects.ends_at, effects.permanent, cases.summary, cases.issued_by
            FROM moderation_effects effects
            JOIN moderation_cases cases ON cases.case_id = effects.case_id
            WHERE cases.subject_type = 'IP'
              AND cases.subject_ip_address = CAST(? AS INET)
              AND effects.effect_type IN ('GUEST_ACCESS_RESTRICTION', 'IP_RESTRICTION')
              AND effects.apply_status = 'APPLIED'
              AND effects.revoked_at IS NULL AND cases.revoked_at IS NULL
              AND effects.starts_at <= NOW()
              AND (effects.permanent OR effects.ends_at > NOW())
            ORDER BY CASE WHEN effects.effect_type = 'IP_RESTRICTION' THEN 1 ELSE 0 END DESC,
                     effects.permanent DESC, effects.ends_at DESC NULLS FIRST,
                     effects.effect_id DESC
            LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                InternalBlock(
                    id = rs.getLong("effect_id"),
                    type = rs.getString("effect_type"),
                    time = rs.getTimestamp("starts_at").toInstant(),
                    pardonTime = rs.getTimestamp("ends_at")?.toInstant(),
                    permanent = rs.getBoolean("permanent"),
                    reason = rs.getString("summary"),
                    admin = rs.getString("issued_by")
                )
            },
            ip
        ).firstOrNull()
    }

    private fun requireApiKey(apiKey: String) {
        if (setting.getApiKey() != apiKey) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }
}

data class InternalUserBlocks(val game: InternalBlock?, val chat: InternalBlock?)

data class InternalBlock(
    val id: Long,
    val type: String,
    val time: Instant,
    val pardonTime: Instant?,
    val permanent: Boolean,
    val reason: String,
    val admin: String
)
