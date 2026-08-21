package me.kkutuio.kkutuweb.identity

import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class ModerationRetentionService(
    private val jdbc: JdbcTemplate,
    private val setting: KKuTuSetting
) {
    companion object {
        private val RETAINED_RESTRICTIONS = setOf(
            "GAME_RESTRICTION",
            "CHAT_RESTRICTION",
            "EXTEND_RELATED_RESTRICTION"
        )
    }

    fun effectiveSubject(account: Account): UUID = account.moderationSubjectUuid ?: account.uuid

    /**
     * Request-time fail-closed check. A restricted account must have at least one stable
     * external identity that can be HMAC-retained before the deletion request is accepted.
     */
    fun assertDeletionCanPreserveRestrictions(account: Account) {
        val subject = effectiveSubject(account)
        if (!hasRetainedRestriction(subject)) return
        if (retentionIdentities(account.id).isEmpty()) {
            throw IdpException(
                "restriction_retention_identity_required",
                "인증 정보가 없어 계정 탈퇴를 처리할 수 없습니다. 고객센터로 문의해 주세요.",
                409
            )
        }
    }

    /** Finds a retained moderation subject without storing or comparing the raw identifier. */
    fun heldSubjectForIdentities(identities: List<Pair<String, String>>): UUID? {
        val held = identities
            .mapNotNull { (provider, subject) -> heldSubject(provider, subject) }
            .distinct()
        if (held.size > 1) {
            throw IdpException(
                "moderation_identity_conflict",
                "인증 정보가 일치하지 않아 요청을 처리할 수 없습니다. 고객센터로 문의해 주세요.",
                409
            )
        }
        return held.firstOrNull()
    }

    /**
     * Attaches a previously-retained moderation identity to a live account.  We never merge two
     * independent moderation histories implicitly; an already-moderated account must be reviewed.
     */
    @Transactional
    fun attachHeldSubject(account: Account, heldSubject: UUID) {
        val current = account.moderationSubjectUuid
        if (current == heldSubject) return
        if (current != null && current != heldSubject) {
            throw IdpException("moderation_identity_conflict", "인증 정보가 일치하지 않아 요청을 처리할 수 없습니다.", 409)
        }
        if (current == null && hasModerationHistory(account.uuid)) {
            throw IdpException(
                "moderation_identity_conflict",
                "인증 정보가 일치하지 않아 요청을 처리할 수 없습니다. 고객센터로 문의해 주세요.",
                409
            )
        }
        val updated = jdbc.update(
            "UPDATE account SET moderation_subject_uuid=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND moderation_subject_uuid IS NULL",
            heldSubject,
            account.id
        )
        if (updated == 0) {
            val actual = jdbc.queryForObject(
                "SELECT moderation_subject_uuid FROM account WHERE id=?",
                UUID::class.java,
                account.id
            )
            if (actual != heldSubject) {
                throw IdpException("moderation_identity_conflict", "이용제한 식별정보를 연결하지 못했습니다.", 409)
            }
        }
    }

    /**
     * Must run before users/account authentication data are erased.  All work is inside the caller's
     * deletion transaction: any failure rolls the whole deletion back, so sanctions cannot disappear.
     */
    @Transactional
    fun prepareForDeletion(accountId: UUID): UUID? {
        val row = jdbc.queryForMap(
            "SELECT uuid, moderation_subject_uuid FROM account WHERE id=? FOR UPDATE",
            accountId
        )
        val accountUuid = UUID.fromString(row["uuid"].toString())
        val existingModerationSubject = row["moderation_subject_uuid"]?.toString()?.let(UUID::fromString)
        val sourceSubject = existingModerationSubject ?: accountUuid
        if (!hasRetainedRestriction(sourceSubject)) return null

        val identities = retentionIdentities(accountId)
        if (identities.isEmpty()) {
            throw IllegalStateException("Restricted account $accountId has no retainable identity")
        }

        val targetSubject = existingModerationSubject ?: UUID.randomUUID()
        if (existingModerationSubject == null) {
            jdbc.update(
                "UPDATE account SET moderation_subject_uuid=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                targetSubject,
                accountId
            )
            migrateModerationSubject(sourceSubject, targetSubject)
        }

        val retainUntil = restrictionRetainUntil(targetSubject)
        identities.forEach { (provider, subject) ->
            upsertHold(provider, subject, targetSubject, retainUntil)
        }
        return targetSubject
    }

    private fun migrateModerationSubject(from: UUID, to: UUID) {
        if (from == to) return
        val fromText = from.toString()
        val toText = to.toString()
        jdbc.update(
            "UPDATE moderation_cases SET subject_user_id=? WHERE subject_type='USER' AND subject_user_id=?",
            toText,
            fromText
        )
        jdbc.update("UPDATE moderation_effects SET subject_user_id=? WHERE subject_user_id=?", toText, fromText)
        jdbc.update("UPDATE moderation_case_subjects SET user_id=? WHERE user_id=?", toText, fromText)
        jdbc.update("UPDATE moderation_counter_baselines SET subject_user_id=? WHERE subject_user_id=?", toText, fromText)
    }

    private fun hasModerationHistory(subject: UUID): Boolean = jdbc.queryForObject(
        """
        SELECT EXISTS(
            SELECT 1 FROM moderation_cases WHERE subject_type='USER' AND subject_user_id=?
            UNION ALL
            SELECT 1 FROM moderation_effects WHERE subject_user_id=?
            UNION ALL
            SELECT 1 FROM moderation_counter_baselines WHERE subject_user_id=?
        )
        """.trimIndent(),
        Boolean::class.java,
        subject.toString(), subject.toString(), subject.toString()
    ) == true

    private fun hasRetainedRestriction(subject: UUID): Boolean = jdbc.queryForObject(
        """
        SELECT EXISTS(
            SELECT 1
            FROM moderation_effects effects
            JOIN moderation_cases cases ON cases.case_id=effects.case_id
            WHERE effects.subject_user_id=?
              AND effects.effect_type IN ('GAME_RESTRICTION','CHAT_RESTRICTION','EXTEND_RELATED_RESTRICTION')
              AND effects.apply_status='APPLIED'
              AND effects.revoked_at IS NULL AND cases.revoked_at IS NULL
              AND (effects.permanent OR effects.ends_at > CURRENT_TIMESTAMP)
        )
        """.trimIndent(),
        Boolean::class.java,
        subject.toString()
    ) == true

    private fun restrictionRetainUntil(subject: UUID): Instant? {
        val row = jdbc.queryForMap(
            """
            SELECT COALESCE(bool_or(effects.permanent), FALSE) AS permanent,
                   max(effects.ends_at) AS ends_at
            FROM moderation_effects effects
            JOIN moderation_cases cases ON cases.case_id=effects.case_id
            WHERE effects.subject_user_id=?
              AND effects.effect_type IN ('GAME_RESTRICTION','CHAT_RESTRICTION','EXTEND_RELATED_RESTRICTION')
              AND effects.apply_status='APPLIED'
              AND effects.revoked_at IS NULL AND cases.revoked_at IS NULL
              AND (effects.permanent OR effects.ends_at > CURRENT_TIMESTAMP)
            """.trimIndent(),
            subject.toString()
        )
        if (row["permanent"] == true) return null
        return (row["ends_at"] as? Timestamp)?.toInstant()
    }

    private fun retentionIdentities(accountId: UUID): List<Pair<String, String>> = jdbc.query(
        """
        SELECT provider, subject
        FROM account_identity
        WHERE account_id=? AND revoked_at IS NULL
          AND (type='OAUTH' OR (type='EMAIL' AND verified_at IS NOT NULL))
        ORDER BY id
        """.trimIndent(),
        { rs, _ -> rs.getString("provider") to rs.getString("subject") },
        accountId
    )

    private fun heldSubject(provider: String, subject: String): UUID? {
        val normalizedProvider = provider.trim().uppercase()
        val hash = identityHmac(normalizedProvider, subject)
        val result = jdbc.query(
            """
            SELECT moderation_subject_uuid
            FROM privacy_retention.moderation_identity_hold
            WHERE provider=? AND subject_hmac=?
            """.trimIndent(),
            { rs, _ -> UUID.fromString(rs.getString("moderation_subject_uuid")) },
            normalizedProvider,
            hash
        ).firstOrNull()
        if (result != null) {
            jdbc.update(
                "UPDATE privacy_retention.moderation_identity_hold SET last_matched_at=CURRENT_TIMESTAMP WHERE provider=? AND subject_hmac=?",
                normalizedProvider,
                hash
            )
        }
        return result
    }

    private fun upsertHold(provider: String, subject: String, moderationSubject: UUID, retainUntil: Instant?) {
        val normalizedProvider = provider.trim().uppercase()
        val hash = identityHmac(normalizedProvider, subject)
        val updated = jdbc.update(
            """
            INSERT INTO privacy_retention.moderation_identity_hold
                (provider, subject_hmac, moderation_subject_uuid, retain_until)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (provider, subject_hmac) DO UPDATE SET
                retain_until = CASE
                    WHEN privacy_retention.moderation_identity_hold.retain_until IS NULL
                      OR EXCLUDED.retain_until IS NULL THEN NULL
                    ELSE GREATEST(privacy_retention.moderation_identity_hold.retain_until, EXCLUDED.retain_until)
                END
            WHERE privacy_retention.moderation_identity_hold.moderation_subject_uuid = EXCLUDED.moderation_subject_uuid
            """.trimIndent(),
            normalizedProvider,
            hash,
            moderationSubject,
            retainUntil?.let(Timestamp::from)
        )
        if (updated != 1) {
            throw IllegalStateException("Retention identity is already attached to a different moderation subject")
        }
    }

    private fun identityHmac(provider: String, rawSubject: String): ByteArray {
        val normalizedSubject = if (provider == "EMAIL") rawSubject.trim().lowercase() else rawSubject.trim()
        val canonical = "privacy-retention:v1\u0000$provider\u0000$normalizedSubject"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(setting.getCryptoKey().toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8))
    }

    @Scheduled(initialDelay = 300_000, fixedDelay = 3_600_000)
    fun purgeExpiredHolds(): Int = jdbc.update(
        """
        DELETE FROM privacy_retention.moderation_identity_hold hold
        WHERE NOT EXISTS (
            SELECT 1
            FROM moderation_effects effects
            JOIN moderation_cases cases ON cases.case_id=effects.case_id
            WHERE effects.subject_user_id=hold.moderation_subject_uuid::text
              AND effects.effect_type IN ('GAME_RESTRICTION','CHAT_RESTRICTION','EXTEND_RELATED_RESTRICTION')
              AND effects.apply_status='APPLIED'
              AND effects.revoked_at IS NULL AND cases.revoked_at IS NULL
              AND (effects.permanent OR effects.ends_at > CURRENT_TIMESTAMP)
        )
        """.trimIndent()
    )
}
