/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 */

package me.kkutuio.kkutuweb.block

import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.extension.getOAuthUser
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.extension.toTimestamp
import me.kkutuio.kkutuweb.factory.DateFactory
import me.kkutuio.kkutuweb.utils.TimeUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.servlet.http.HttpServletRequest

@Service
class BlockService(
    private val jdbcTemplate: JdbcTemplate
) {
    fun getBlockStatus(request: HttpServletRequest): BlockStatus {
        val session = request.session
        val ip = request.getIp()
        val ipBlock = findIpBlock(ip)
        if (ipBlock != null && (!ipBlock.onlyGuest || session.isGuest())) {
            return ipBlock.toStatus(BlockType.IP, ip)
        }

        if (!session.isGuest()) {
            val userId = session.getOAuthUser().getUserId()
            findUserBlock(userId)?.let { return it.toStatus(BlockType.USER, userId) }
        }
        return BlockStatus()
    }

    private fun findUserBlock(userId: String): ActiveBlock? = jdbcTemplate.query(
        """
        SELECT effects.effect_id, effects.starts_at, effects.ends_at,
               effects.permanent, cases.inquiry_id, cases.summary
        FROM moderation_effects effects
        JOIN moderation_cases cases ON cases.case_id = effects.case_id
        WHERE effects.subject_user_id = ?
          AND effects.effect_type IN ('GAME_RESTRICTION', 'EXTEND_RELATED_RESTRICTION')
          AND effects.apply_status = 'APPLIED'
          AND effects.revoked_at IS NULL AND cases.revoked_at IS NULL
          AND effects.starts_at <= NOW()
          AND (effects.permanent OR effects.ends_at > NOW())
        ORDER BY effects.permanent DESC, effects.ends_at DESC NULLS FIRST,
                 effects.effect_id DESC
        LIMIT 1
        """.trimIndent(),
        { rs, _ ->
            ActiveBlock(
                id = rs.getLong("effect_id"),
                inquiryId = rs.getString("inquiry_id"),
                startsAt = rs.getTimestamp("starts_at"),
                endsAt = rs.getTimestamp("ends_at"),
                permanent = rs.getBoolean("permanent"),
                reason = rs.getString("summary"),
                onlyGuest = false
            )
        },
        userId
    ).firstOrNull()

    private fun findIpBlock(ip: String): ActiveBlock? = jdbcTemplate.query(
        """
        SELECT effects.effect_id, effects.effect_type, effects.starts_at,
               effects.ends_at, effects.permanent, cases.inquiry_id, cases.summary
        FROM moderation_effects effects
        JOIN moderation_cases cases ON cases.case_id = effects.case_id
        WHERE cases.subject_type = 'IP' AND cases.subject_ip_address = CAST(? AS INET)
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
            ActiveBlock(
                id = rs.getLong("effect_id"),
                inquiryId = rs.getString("inquiry_id"),
                startsAt = rs.getTimestamp("starts_at"),
                endsAt = rs.getTimestamp("ends_at"),
                permanent = rs.getBoolean("permanent"),
                reason = rs.getString("summary"),
                onlyGuest = rs.getString("effect_type") == "GUEST_ACCESS_RESTRICTION"
            )
        },
        ip
    ).firstOrNull()

    private fun ActiveBlock.toStatus(type: BlockType, target: String): BlockStatus = BlockStatus(
        blocked = true,
        blockType = type,
        target = target,
        id = id,
        inquiryId = inquiryId,
        time = DateFactory.PRETTY_FORMAT.format(startsAt.toLocalDateTime()),
        onlyGuestPunish = onlyGuest,
        pardonTime = endsAt?.let { DateFactory.PRETTY_FORMAT.format(it.toLocalDateTime()) },
        duration = if (permanent) "영구 이용제한" else TimeUtils.getTimeTextForSeconds(
            durationSeconds(endsAt!!, startsAt)
        ),
        remain = if (permanent) "영구 이용제한" else TimeUtils.getTimeTextForSeconds(
            durationSeconds(endsAt!!, LocalDateTime.now().toTimestamp())
        ),
        reason = reason
    )

    private fun durationSeconds(later: Timestamp, earlier: Timestamp): Long =
        (later.time - earlier.time).coerceAtLeast(0) / 1000

    private data class ActiveBlock(
        val id: Long,
        val inquiryId: String,
        val startsAt: Timestamp,
        val endsAt: Timestamp?,
        val permanent: Boolean,
        val reason: String,
        val onlyGuest: Boolean
    )
}
