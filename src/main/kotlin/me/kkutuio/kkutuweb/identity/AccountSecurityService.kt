package me.kkutuio.kkutuweb.identity

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
class AccountSecurityService(
    private val dao: IdentityDao,
    private val settings: IdentityProviderSettings,
    private val mailSender: JavaMailSender,
    private val cipher: SecretCipher,
    private val limiter: AccountRateLimiter
) {
    private val secureRandom = SecureRandom()
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
    fun consumeOneTimeLoginCode(code: String): Account {
        val accountId = dao.consumeRecoveryCodeByHash(SecretTools.sha256(code.trim().uppercase()))
            ?: throw IdpException("invalid_one_time_login_code", "일회용 비밀번호가 올바르지 않거나 이미 사용되었습니다.")
        val account = dao.findAccount(accountId)
            ?: throw IdpException("invalid_one_time_login_code", "일회용 비밀번호가 올바르지 않거나 이미 사용되었습니다.")
        dao.audit(account.id, "ONE_TIME_LOGIN_CODE_USED")
        return account
    }

    private fun replacePassword(account: Account, password: CharArray, auditEvent: String) {
        val identity = dao.listIdentities(account.id).firstOrNull { it.type == IdentityType.PASSWORD && it.revokedAt == null }
        val hash = SecretTools.hashPassword(password)
        if (identity == null) dao.insertIdentity(account.id, IdentityType.PASSWORD, "LOCAL", account.id.toString(), credentialHash = hash, verified = true) else dao.updateCredentialHash(identity.id, hash)
        dao.revokeRefreshTokens(account.id); dao.invalidateSessions(account.id); dao.audit(account.id, auditEvent)
    }

    fun setupTotp(account: Account, name: String): Map<String, String> {
        val secret = Totp.newSecret(); dao.upsertTotp(account.id, cipher.encrypt(secret), name.trim().take(100).ifBlank { "TOTP 기기" })
        dao.audit(account.id, "TOTP_SETUP_STARTED")
        return mapOf("secret" to secret, "otpauth_uri" to "otpauth://totp/KKuTuIO:${account.legacyUserId}?secret=$secret&issuer=KKuTuIO")
    }
    fun confirmTotp(account: Account, code: String) {
        val row = dao.findTotp(account.id) ?: throw IdpException("not_found", "설정된 2단계 인증 수단이 없습니다.", 404)
        if (!Totp.verify(cipher.decrypt(row["secret_encrypted"].toString()), code)) throw IdpException("invalid_code", "인증 코드가 올바르지 않습니다.")
        dao.confirmTotp(account.id); dao.audit(account.id, "TOTP_ENABLED")
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
    fun authenticate(identifier: String, password: CharArray, totpCode: String?): Account {
        requirePasswordEnabled()
        val identity = if (identifier.contains('@')) dao.findActiveIdentity("EMAIL", identifier.trim().lowercase()) else null
        val account = identity?.let { dao.findAccount(it.accountId) } ?: dao.findAccountByLegacyId(identifier.trim())
            ?: run { password.fill('\u0000'); throw IdpException("invalid_credentials", "로그인 정보가 올바르지 않습니다.", 401) }
        if (account.status != AccountStatus.ACTIVE) { password.fill('\u0000'); throw IdpException("invalid_credentials", "로그인 정보가 올바르지 않습니다.", 401) }
        val passwordIdentity = dao.listIdentities(account.id).firstOrNull { it.type == IdentityType.PASSWORD && it.revokedAt == null }
        if (passwordIdentity?.credentialHash == null || !SecretTools.verifyPassword(passwordIdentity.credentialHash, password)) throw IdpException("invalid_credentials", "로그인 정보가 올바르지 않습니다.", 401)
        val configuredTotp = dao.findTotp(account.id)?.takeIf { it["confirmed_at"] != null }
        if (configuredTotp != null && (totpCode.isNullOrBlank() || !Totp.verify(cipher.decrypt(configuredTotp["secret_encrypted"].toString()), totpCode))) throw IdpException("mfa_required", "2단계 인증 코드가 필요합니다.", 401)
        dao.touchIdentity(passwordIdentity.id); dao.audit(account.id, "PASSWORD_LOGIN_SUCCESS")
        return account
    }

    /** Re-authentication is scoped to the already logged-in account. */
    fun reauthenticate(account: Account, password: CharArray, totpCode: String?) {
        requirePasswordEnabled()
        val passwordIdentity = dao.listIdentities(account.id)
            .firstOrNull { it.type == IdentityType.PASSWORD && it.revokedAt == null }
            ?: run { password.fill('\u0000'); throw IdpException("reauthentication_unavailable", "비밀번호 로그인 수단이 없습니다.", 400) }
        if (!SecretTools.verifyPassword(passwordIdentity.credentialHash ?: "", password)) {
            throw IdpException("invalid_credentials", "로그인 정보가 올바르지 않습니다.", 401)
        }
        val configuredTotp = dao.findTotp(account.id)?.takeIf { it["confirmed_at"] != null }
        if (configuredTotp != null && (totpCode.isNullOrBlank() || !Totp.verify(cipher.decrypt(configuredTotp["secret_encrypted"].toString()), totpCode))) {
            throw IdpException("mfa_required", "2단계 인증 코드가 필요합니다.", 401)
        }
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

    private fun requirePasswordEnabled() {
        if (!settings.passwordEnabled) throw IdpException("password_disabled", "비밀번호 로그인은 비활성화되어 있습니다.", 404)
    }

    private fun deliver(to: String, subject: String, text: String) {
        mailSender.send(SimpleMailMessage().apply { setFrom(settings.mailFrom); setTo(to); setSubject(subject); setText(text) })
    }
}
