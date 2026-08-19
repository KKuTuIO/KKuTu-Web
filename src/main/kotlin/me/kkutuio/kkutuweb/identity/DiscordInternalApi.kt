package me.kkutuio.kkutuweb.identity

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class DiscordIdentityLinkRequest(val discordSubject: String, val accountSub: String)
data class DiscordAttendanceRequest(val discordSubject: String, val date: LocalDate)

@RestController
@RequestMapping("/api/internal/discord")
class DiscordInternalApi(
    private val dao: IdentityDao,
    private val settings: IdentityProviderSettings,
    private val rewards: DiscordRewardService
) {
    @PostMapping("/identity-link")
    @Transactional
    fun link(@RequestHeader("Authorization", required = false) authorization: String?, @RequestBody request: DiscordIdentityLinkRequest): Map<String, String> {
        authorize(authorization)
        val account = dao.findAccount(UUID.fromString(request.accountSub)) ?: throw IdpException("not_found", "계정을 찾을 수 없습니다.", 404)
        val existing = dao.findIdentity("DISCORD", request.discordSubject)
        if (existing != null && existing.accountId != account.id) throw IdpException("identity_conflict", "이미 다른 계정에 연결된 로그인 수단입니다.", HttpStatus.CONFLICT.value())
        if (existing == null) {
            val identity = dao.insertIdentity(account.id, IdentityType.OAUTH, "DISCORD", request.discordSubject, verified = true)
            dao.audit(account.id, "DISCORD_IDENTITY_LINKED", identity.id)
        }
        return mapOf("status" to "linked")
    }

    @PostMapping("/attendance")
    @Transactional
    fun attendance(@RequestHeader("Authorization", required = false) authorization: String?, @RequestBody request: DiscordAttendanceRequest): Map<String, Any?> {
        authorize(authorization)
        val attendanceDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val identity = dao.findIdentity("DISCORD", request.discordSubject)?.takeIf { it.revokedAt == null }
            ?: throw IdpException("not_found", "연동된 계정을 찾을 수 없습니다.", 404)
        val account = dao.findAccount(identity.accountId) ?: throw IdpException("not_found", "계정을 찾을 수 없습니다.", 404)
        dao.attendance(account.id, attendanceDate)?.let {
            val streak = (it["streak_days"] as Number).toInt()
            val rewardGranted = processReward(account, attendanceDate, streak)
            return mapOf("already_attended" to true, "streak_days" to streak, "reward_granted" to rewardGranted)
        }
        val streak = ((dao.previousAttendance(account.id, attendanceDate)?.get("streak_days") as? Number)?.toInt() ?: 0) + 1
        dao.insertAttendance(account.id, attendanceDate, streak)
        val rewardGranted = processReward(account, attendanceDate, streak)
        dao.audit(account.id, "DISCORD_ATTENDANCE", metadata = mapOf("date" to attendanceDate.toString(), "streak_days" to streak, "reward_granted" to rewardGranted))
        return mapOf("already_attended" to false, "streak_days" to streak, "reward_granted" to rewardGranted)
    }

    private fun authorize(value: String?) {
        val token = value?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        if (settings.internalApiKey.isBlank() || token == null || !SecretTools.constantTimeEquals(settings.internalApiKey, token)) {
            throw IdpException("unauthorized", "내부 API 인증이 필요합니다.", HttpStatus.UNAUTHORIZED.value())
        }
    }

    /** Retries only a ledger event still marked PENDING; COMPLETED events remain immutable. */
    private fun processReward(account: Account, date: LocalDate, streak: Int): Boolean {
        if (streak % 7 != 0) return false
        val rewardKey = "2023_attendance_bronze"
        val eventKey = "discord-attendance:" + account.id + ":" + date + ":" + rewardKey
        if (!dao.reserveRewardEvent(eventKey, account.id, rewardKey)) return false
        rewards.grantItem(account.legacyUserId, rewardKey)
        dao.completeRewardEvent(eventKey)
        return true
    }
}
