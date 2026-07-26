package me.kkutuio.kkutuweb.moderation.policy

import org.postgresql.util.PGobject
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class ModerationPolicyRegistry(
    private val loader: ModerationPolicyLoader,
    private val jdbcTemplate: JdbcTemplate
) {
    @EventListener(ApplicationReadyEvent::class)
    fun registerActivePolicy() {
        register(loader.current())
    }

    fun register(loaded: LoadedModerationPolicy) {
        val policy = loaded.document
        val json = PGobject().apply {
            type = "jsonb"
            value = loaded.rawJson
        }
        jdbcTemplate.update(
            """
            INSERT INTO moderation_policy_versions
                (policy_id, revision, source_url, published_at, effective_from, sha256, document, activated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (policy_id) DO UPDATE SET
                activated_at = COALESCE(moderation_policy_versions.activated_at, NOW())
            WHERE moderation_policy_versions.sha256 = EXCLUDED.sha256
            """.trimIndent(),
            policy.policyId,
            policy.revision,
            policy.source.url,
            java.sql.Date.valueOf(LocalDate.parse(policy.source.publishedAt)),
            Timestamp.from(OffsetDateTime.parse(policy.source.effectiveFrom).toInstant()),
            loaded.digest,
            json
        )
        val digest = jdbcTemplate.queryForObject(
            "SELECT sha256 FROM moderation_policy_versions WHERE policy_id = ?",
            String::class.java,
            policy.policyId
        )
        check(digest == loaded.digest) {
            "Policy ${policy.policyId} already exists with another digest"
        }
    }
}
