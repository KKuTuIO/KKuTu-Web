package me.kkutuio.kkutuweb.identity

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
class AccountSecurityService(
    private val dao: IdentityDao,
    private val objectMapper: ObjectMapper,
    private val settings: IdentityProviderSettings,
    private val mailSender: JavaMailSender,
    private val cipher: SecretCipher,
    private val limiter: AccountRateLimiter,
    private val moderationRetention: ModerationRetentionService
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun reissueSecurityCode(account: Account): String {
        val current = dao.lockAccount(account.id)
            ?: throw IdpException("account_unavailable", "계정을 찾을 수 없습니다.", 404)
        val now = Instant.now().epochSecond
        val existingTime = current.flags.path("uid").path("time").asLong(0)
        if (existingTime > 0 && existingTime + SECURITY_CODE_REISSUE_COOLDOWN_SECONDS > now) {
            throw IdpException("security_code_rate_limited", "보안코드는 30분마다 재발급할 수 있습니다.", 429)
        }

        val flags = if (current.flags.isObject) current.flags.deepCopy().asObject() else objectMapper.createObjectNode()
        flags.set("uid", objectMapper.createObjectNode().put("value", generateSecurityCode()).put("time", now))
        val currentRevision = flags.path(ACCOUNT_REVISION_FLAG).path("value").asLong(0).coerceAtLeast(0)
        flags.set(ACCOUNT_REVISION_FLAG, objectMapper.createObjectNode().put("value", currentRevision + 1).put("time", now))
        if (!dao.updateAccountFlags(current.id, flags)) throw IdpException("account_unavailable", "보안코드를 저장하지 못했습니다.", 503)

        dao.audit(current.id, "SECURITY_CODE_REISSUED")
        return flags.path("uid").path("value").asString()
    }

    @Transactional
    fun sendEmailVerification(account: Account, email: String) {
        val normalized = email.trim().lowercase()
        require(normalized.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) { "유효하지 않은 전자 메일 주소입니다." }
        val other = dao.findIdentity("EMAIL", normalized)
        if (other != null && other.accountId != account.id) throw IdpException("identity_conflict", "이미 다른 계정에 연결된 로그인 수단입니다.")
        if (!dao.consumeEmailVerificationQuota(account.id)) throw IdpException("rate_limited", "인증 메일은 계정당 하루 6회까지 보낼 수 있습니다.", 429)
        val token = SecretTools.randomToken()
        dao.saveOneTimeToken(SecretTools.sha256(token), account.id, "EMAIL_VERIFY", mapOf("email" to normalized), Instant.now().plusSeconds(900))
        deliver(normalized, "끄투리오 계정 인증", "누군가가 고객님의 전자 메일 주소를 끄투리오 계정에 추가하였습니다.\n본인이 요청한 작업이 맞다면 아래 링크를 눌러 15분 내에 인증을 완료하세요.\n\n${settings.normalizedIssuer}/account/recovery?verify=$token")
        dao.audit(account.id, "EMAIL_VERIFICATION_SENT", metadata = mapOf("email_domain" to normalized.substringAfter('@')))
    }

    @Transactional
    fun confirmEmail(token: String) {
        val row = dao.consumeOneTimeToken(SecretTools.sha256(token), "EMAIL_VERIFY") ?: throw IdpException("invalid_token", "유효하지 않거나 만료된 인증 링크입니다.")
        val accountId = UUID.fromString(row["account_id"].toString())
        val payload = row["payload"].toString()
        val email = Regex("\"email\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1) ?: throw IdpException("invalid_token", "잘못된 인증 링크입니다.")
        val existing = dao.findIdentity("EMAIL", email)
        moderationRetention.heldSubjectForIdentities(listOf("EMAIL" to email))?.let { held ->
            val account = dao.findAccount(accountId) ?: throw IdpException("invalid_token", "계정을 찾을 수 없습니다.")
            moderationRetention.attachHeldSubject(account, held)
        }
        if (existing == null) {
            dao.revokeActiveEmailIdentitiesExcept(accountId)
            dao.insertIdentity(accountId, IdentityType.EMAIL, "EMAIL", email, verified = true)
        }
        else if (existing.accountId != accountId) throw IdpException("identity_conflict", "이미 다른 계정에서 사용 중인 전자 메일 주소입니다.")
        else {
            dao.revokeActiveEmailIdentitiesExcept(accountId, existing.id)
            if (existing.revokedAt != null) dao.restoreIdentity(existing.id)
        }
        dao.audit(accountId, "EMAIL_VERIFIED", metadata = mapOf("email_domain" to email.substringAfter('@')))
    }

    fun requestAccountRecovery(email: String) {
        val normalized = email.trim().lowercase(); limiter.check("reset:$normalized", 5, 3600)
        val identity = dao.findIdentity("EMAIL", normalized)?.takeIf { it.verifiedAt != null && it.revokedAt == null }
        if (identity != null) {
            val token = SecretTools.randomToken()
            val purpose = if (settings.passwordEnabled) "PASSWORD_RESET" else "EMAIL_RECOVERY_LOGIN"
            val link = if (settings.passwordEnabled) "${settings.normalizedIssuer}/account/recovery?reset=$token" else "${settings.normalizedIssuer}/account/recovery/login?token=$token"
            dao.saveOneTimeToken(SecretTools.sha256(token), identity.accountId, purpose, emptyMap(), Instant.now().plusSeconds(900))
            val message = if (settings.passwordEnabled) "누군가가 고객님의 전자 메일 주소로 끄투리오 계정 복구 요청을 접수하였습니다.\n본인이 요청한 작업이 맞다면 아래 링크를 눌러 15분 내에 비밀번호를 재설정하세요.\n$link" else "누군가가 고객님의 전자 메일 주소로 끄투리오 계정 로그인 요청을 접수하였습니다.\n본인이 요청한 작업이 맞다면 아래 링크를 눌러 15분 내에 로그인하세요.\n$link"
            runCatching { deliver(normalized, "끄투리오 계정 복구", message) }
            dao.audit(identity.accountId, if (settings.passwordEnabled) "PASSWORD_RESET_REQUESTED" else "EMAIL_RECOVERY_LOGIN_REQUESTED", metadata = mapOf("email_domain" to normalized.substringAfter('@')))
        }
        // Do not distinguish an unknown account from a known one.
    }

    @Transactional
    fun resetPassword(token: String, password: CharArray) {
        requirePasswordEnabled()
        require(password.size >= 12) { "비밀번호는 12자 이상이어야 합니다." }
        val row = dao.consumeOneTimeToken(SecretTools.sha256(token), "PASSWORD_RESET") ?: throw IdpException("invalid_token", "유효하지 않거나 만료된 복구 링크입니다.")
        val account = dao.findAccount(UUID.fromString(row["account_id"].toString())) ?: throw IdpException("invalid_token", "계정을 찾을 수 없습니다.")
        replacePassword(account, password, "PASSWORD_RESET")
    }

    @Transactional
    fun consumeOneTimeLoginCode(identifier: String, code: String): Account {
        val account = findRecoveryAccount(identifier)
            ?: throw IdpException("invalid_one_time_login_code", "식별번호 또는 전자 메일 주소가 올바르지 않습니다.")
        if (!dao.consumeRecoveryCodeByHash(account.id, SecretTools.sha256(code.trim().uppercase()))) {
            throw IdpException("invalid_one_time_login_code", "일회용 비밀번호가 이미 사용되었거나 올바르지 않습니다.")
        }
        dao.audit(account.id, "ONE_TIME_LOGIN_CODE_USED")
        return account
    }

    private fun findRecoveryAccount(identifier: String): Account? {
        val normalized = identifier.trim()
        if (normalized.isBlank()) return null
        dao.findAccountByLegacyId(normalized)?.let { return it }
        val emailIdentity = dao.findIdentity("EMAIL", normalized.lowercase())
            ?.takeIf { it.type == IdentityType.EMAIL && it.verifiedAt != null && it.revokedAt == null }
        return emailIdentity?.let { dao.findAccount(it.accountId) }
    }

    private fun replacePassword(account: Account, password: CharArray, auditEvent: String) {
        val identity = dao.listIdentities(account.id).firstOrNull { it.type == IdentityType.PASSWORD && it.revokedAt == null }
        val hash = SecretTools.hashPassword(password)
        if (identity == null) dao.insertIdentity(account.id, IdentityType.PASSWORD, "LOCAL", account.id.toString(), credentialHash = hash, verified = true) else dao.updateCredentialHash(identity.id, hash)
        dao.revokeRefreshTokens(account.id); dao.invalidateSessions(account.id); dao.audit(account.id, auditEvent)
    }

    fun setupTotp(account: Account, name: String): Map<String, String> {
        val secret = Totp.newSecret(); dao.upsertTotp(account.id, cipher.encrypt(secret), name.trim().take(100).ifBlank { "TOTP 매체" })
        dao.audit(account.id, "TOTP_SETUP_STARTED")
        return mapOf("secret" to secret, "otpauth_uri" to "otpauth://totp/KKuTuIO:${account.legacyUserId}?secret=$secret&issuer=KKuTuIO")
    }
    fun confirmTotp(account: Account, code: String) {
        val row = dao.findTotp(account.id) ?: throw IdpException("not_found", "설정된 2단계 인증 수단이 없습니다.", 404)
        if (!Totp.verify(cipher.decrypt(row["secret_encrypted"].toString()), code)) throw IdpException("invalid_code", "인증 코드가 올바르지 않습니다.")
        dao.confirmTotp(account.id); dao.audit(account.id, "TOTP_ENABLED")
    }

    fun requiresSecondFactor(account: Account): Boolean =
        dao.findTotp(account.id)?.get("confirmed_at") != null

    fun requiresExternalSecondFactor(account: Account): Boolean =
        requiresSecondFactor(account) && (!settings.passwordEnabled || account.externalMfaEnabled)

    /** Completes the second factor after a successful external OAuth first factor. */
    fun verifyExternalSecondFactor(account: Account, totpCode: String?, securityCode: String?, emailCode: String?) {
        val configuredTotp = dao.findTotp(account.id)?.takeIf { it["confirmed_at"] != null }
            ?: return
        if (!totpCode.isNullOrBlank() && Totp.verify(cipher.decrypt(configuredTotp["secret_encrypted"].toString()), totpCode)) return
        if (!emailCode.isNullOrBlank() && isEmailMfaBackupAvailable(account) && consumeEmailMfaCode(account, emailCode, "EMAIL_MFA_LOGIN")) {
            dao.audit(account.id, "EMAIL_MFA_LOGIN_CODE_USED")
            return
        }
        if (!securityCode.isNullOrBlank() && matchesSecurityCode(account, securityCode)) {
            dao.audit(account.id, "SECURITY_CODE_MFA_SUCCESS")
            return
        }
        throw IdpException("mfa_required", "TOTP 인증 코드 또는 보안코드가 필요합니다.", 401)
    }
    fun isEmailMfaBackupAvailable(account: Account): Boolean = requiresSecondFactor(account) && dao.hasVerifiedEmail(account.id)
    fun requestEmailMfaPendingCode(account: Account) {
        requireEmailMfaBackupAvailable(account)
        sendEmailMfaCode(account, "EMAIL_MFA_LOGIN", "로그인")
    }
    fun requestEmailMfaLoginCode(identifier: String, password: CharArray) {
        val account = findPasswordAccount(identifier, password)
        requireEmailMfaBackupAvailable(account)
        sendEmailMfaCode(account, "EMAIL_MFA_LOGIN", "로그인")
    }
    fun requestEmailMfaReauthenticationCode(account: Account, password: CharArray) {
        verifyPasswordForAccount(account, password)
        requireEmailMfaBackupAvailable(account)
        sendEmailMfaCode(account, "EMAIL_MFA_LOGIN", "본인확인")
    }
    fun rotateOneTimeLoginCodes(account: Account): List<String> {
        dao.revokeRecoveryCodes(account.id)
        val codes = (1..10).map { SecretTools.randomToken(8).uppercase() }
        codes.forEach { dao.insertRecoveryCode(account.id, SecretTools.sha256(it)) }
        dao.audit(account.id, "ONE_TIME_LOGIN_CODES_ROTATED")
        return codes
    }
    fun issueSupportPin(account: Account): String {
        val pin = (secureRandom.nextInt(900_000) + 100_000).toString()
        dao.upsertSupportPin(account.id, SecretTools.hashPassword(pin.toCharArray())); dao.audit(account.id, "SUPPORT_PIN_ISSUED")
        return pin
    }

    private fun generateSecurityCode(): String = buildString(SECURITY_CODE_LENGTH) {
        repeat(SECURITY_CODE_LENGTH) { append(SECURITY_CODE_ALPHABET[secureRandom.nextInt(SECURITY_CODE_ALPHABET.length)]) }
    }
    fun authenticate(identifier: String, password: CharArray, totpCode: String?): Account {
        requirePasswordEnabled()
        val account = findPasswordAccount(identifier, password)
        val passwordIdentity = dao.listIdentities(account.id).firstOrNull { it.type == IdentityType.PASSWORD && it.revokedAt == null }
        verifySecondFactor(account, totpCode)
        dao.touchIdentity(passwordIdentity!!.id); dao.audit(account.id, "PASSWORD_LOGIN_SUCCESS")
        return account
    }

    /** Validates only the password first factor.  MFA is completed in the shared login screen. */
    fun authenticatePasswordFirstFactor(identifier: String, password: CharArray): Account {
        requirePasswordEnabled()
        return findPasswordAccount(identifier, password)
    }

    fun recordPasswordLoginSuccess(account: Account) {
        val passwordIdentity = dao.listIdentities(account.id).firstOrNull { it.type == IdentityType.PASSWORD && it.revokedAt == null }
            ?: throw IdpException("invalid_credentials", "로그인 정보가 올바르지 않습니다.", 401)
        dao.touchIdentity(passwordIdentity.id)
        dao.audit(account.id, "PASSWORD_LOGIN_SUCCESS")
    }

    /** Re-authentication is scoped to the already logged-in account. */
    fun reauthenticate(account: Account, password: CharArray, totpCode: String?) {
        requirePasswordEnabled()
        val passwordIdentity = verifyPasswordForAccount(account, password)
        verifySecondFactor(account, totpCode)
        dao.touchIdentity(passwordIdentity.id)
        dao.audit(account.id, "ACCOUNT_REAUTHENTICATED", passwordIdentity.id)
    }

    @Transactional
    fun consumeEmailRecoveryLogin(token: String): Account {
        if (settings.passwordEnabled) throw IdpException("invalid_token", "유효하지 않거나 만료된 복구 링크입니다.", 404)
        val row = dao.consumeOneTimeToken(SecretTools.sha256(token), "EMAIL_RECOVERY_LOGIN")
            ?: throw IdpException("invalid_token", "유효하지 않거나 만료된 복구 링크입니다.", 404)
        val account = dao.findAccount(UUID.fromString(row["account_id"].toString()))
            ?: throw IdpException("invalid_token", "계정을 찾을 수 없습니다.", 404)
        dao.audit(account.id, "EMAIL_RECOVERY_LOGIN_COMPLETED")
        return account
    }

    private fun verifySecondFactor(account: Account, code: String?) {
        val configuredTotp = dao.findTotp(account.id)?.takeIf { it["confirmed_at"] != null }
        if (configuredTotp == null) return
        val emailMfaBackupAvailable = dao.hasVerifiedEmail(account.id)
        if (!code.isNullOrBlank()) {
            if (Totp.verify(cipher.decrypt(configuredTotp["secret_encrypted"].toString()), code)) return
            if (emailMfaBackupAvailable && consumeEmailMfaCode(account, code, "EMAIL_MFA_LOGIN")) return
            if (matchesSecurityCode(account, code)) {
                dao.audit(account.id, "SECURITY_CODE_MFA_SUCCESS")
                return
            }
        }
        throw IdpException("mfa_required", if (emailMfaBackupAvailable) "TOTP 인증 코드가 필요합니다. 코드를 잊으셨다면 전자 메일 백업 인증을 요청하세요." else "TOTP 인증 코드가 필요합니다.", 401)
    }

    private fun sendEmailMfaCode(account: Account, purpose: String, operation: String) {
        val email = dao.listIdentities(account.id).firstOrNull { it.type == IdentityType.EMAIL && it.verifiedAt != null && it.revokedAt == null }?.subject
            ?: throw IdpException("email_mfa_unavailable", "계정에 등록된 전자 메일 주소가 인증되지 않았습니다.", 400)
        if (!dao.consumeEmailBackupCodeQuota(account.id)) throw IdpException("rate_limited", "전자 메일 인증 코드는 5분에 한 번, 계정당 하루 10회까지 보낼 수 있습니다.", 429)
        val code = SecretTools.randomToken(8).uppercase()
        dao.saveOneTimeToken(SecretTools.sha256(code), account.id, purpose, emptyMap(), Instant.now().plusSeconds(300))
        deliver(email, "끄투리오 2단계 인증", "고객님 계정의 인증 코드는 $operation 입니다. 5분 안에 입력해 주세요.\n\n$code")
        dao.audit(account.id, "EMAIL_MFA_LOGIN_CODE_SENT")
    }

    private fun consumeEmailMfaCode(account: Account, code: String, purpose: String): Boolean {
        val row = dao.consumeOneTimeToken(SecretTools.sha256(code.trim().uppercase()), purpose) ?: return false
        return row["account_id"]?.toString() == account.id.toString()
    }

    private fun requireEmailMfaBackupAvailable(account: Account) {
        if (dao.findTotp(account.id)?.get("confirmed_at") == null || !dao.hasVerifiedEmail(account.id)) {
            throw IdpException("email_mfa_unavailable", "잘못된 요청입니다.", 400)
        }
    }

    private fun matchesSecurityCode(account: Account, code: String): Boolean {
        val expected = account.flags.path("uid").path("value").asString(null)
        return !expected.isNullOrBlank() && MessageDigest.isEqual(expected.toByteArray(), code.trim().toByteArray())
    }

    private fun findPasswordAccount(identifier: String, password: CharArray): Account {
        val identity = if (identifier.contains('@')) dao.findActiveIdentity("EMAIL", identifier.trim().lowercase()) else null
        val account = identity?.let { dao.findAccount(it.accountId) } ?: dao.findAccountByLegacyId(identifier.trim())
            ?: run { password.fill('\u0000'); throw IdpException("invalid_credentials", "로그인 정보가 올바르지 않습니다.", 401) }
        if (account.status != AccountStatus.ACTIVE) { password.fill('\u0000'); throw IdpException("invalid_credentials", "로그인 정보가 올바르지 않습니다.", 401) }
        verifyPasswordForAccount(account, password)
        return account
    }

    private fun verifyPasswordForAccount(account: Account, password: CharArray): AccountIdentity {
        val passwordIdentity = dao.listIdentities(account.id)
            .firstOrNull { it.type == IdentityType.PASSWORD && it.revokedAt == null }
            ?: run { password.fill('\u0000'); throw IdpException("reauthentication_unavailable", "잘못된 요청입니다.", 400) }
        if (!SecretTools.verifyPassword(passwordIdentity.credentialHash ?: "", password)) throw IdpException("invalid_credentials", "로그인 정보가 올바르지 않습니다.", 401)
        return passwordIdentity
    }

    private fun requirePasswordEnabled() {
        if (!settings.passwordEnabled) throw IdpException("password_disabled", "잘못된 요청입니다.", 400)
    }

    private fun deliver(to: String, subject: String, text: String) {
        mailSender.send(SimpleMailMessage().apply { setFrom(settings.mailFrom); setTo(to); setSubject(subject); setText(text) })
    }

    private companion object {
        private const val ACCOUNT_REVISION_FLAG = "kkac2AccountRevision"
        private const val SECURITY_CODE_REISSUE_COOLDOWN_SECONDS = 1800L
        private const val SECURITY_CODE_LENGTH = 24
        private const val SECURITY_CODE_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz-"
    }
}
