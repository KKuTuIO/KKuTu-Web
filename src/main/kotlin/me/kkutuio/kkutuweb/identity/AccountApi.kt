package me.kkutuio.kkutuweb.identity

import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.extension.getOAuthUser
import me.kkutuio.kkutuweb.extension.hasRecentAuthentication
import me.kkutuio.kkutuweb.extension.markRecentlyAuthenticated
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.SessionAttribute
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import com.fasterxml.jackson.databind.JsonNode
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.security.web.csrf.CsrfToken

data class NicknameRequest(val nickname: String, val fixed: Boolean = false)
data class EmailRequest(val email: String)
data class PasswordRequest(val password: String)
data class PasswordLoginRequest(val identifier: String, val password: String)
data class ReauthenticateRequest(val password: String, val totpCode: String? = null)
data class TotpConfirmRequest(val code: String)
data class TotpSetupRequest(val name: String? = null)
data class TotpRenameRequest(val name: String)
data class RecoveryRequest(val email: String, val recaptchaToken: String? = null)
data class ResetPasswordRequest(val token: String, val password: String, val recaptchaToken: String? = null)
data class OneTimeLoginCodeRequest(val code: String, val recaptchaToken: String? = null)
data class ExternalSecondFactorRequest(val totpCode: String? = null, val securityCode: String? = null, val emailCode: String? = null)
data class ExternalMfaSettingRequest(val enabled: Boolean)
data class ProfileSelectionRequest(val profileId: String)
data class PasskeyCompletionRequest(val operationToken: String, val credential: JsonNode, val deviceName: String? = null)

@RestController
@RequestMapping("/api/account")
class AccountApi(
    private val accounts: AccountService,
    private val nicknames: NicknameService,
    private val security: AccountSecurityService,
    private val dao: IdentityDao,
    private val loginService: LoginService,
    private val captcha: RecaptchaVerifier,
    private val limiter: AccountRateLimiter,
    private val webAuthn: WebAuthnService,
    private val settings: IdentityProviderSettings
) {
    @GetMapping("/csrf") fun csrf(request: HttpServletRequest): Map<String, String> {
        val token = (request.getAttribute(CsrfToken::class.java.name) ?: request.getAttribute("_csrf")) as? CsrfToken
            ?: throw IdpException("temporarily_unavailable", "CSRF 토큰을 만들 수 없습니다.", 503)
        return mapOf("token" to token.token, "header" to token.headerName, "parameter" to token.parameterName)
    }
    @GetMapping("/summary") fun summary(session: HttpSession): Map<String, Any?> = accounts.summary(accounts.requireCurrentAccount(session))
    @PutMapping("/profile") fun selectProfile(@RequestBody body: ProfileSelectionRequest, session: HttpSession): ResponseEntity<Void> {
        val account = recent(session)
        val profileId = runCatching { java.util.UUID.fromString(body.profileId) }.getOrElse { throw IdpException("invalid_request", "잘못된 게임 프로필입니다.") }
        if (!dao.setSelectedProfile(account.id, profileId)) throw IdpException("not_found", "게임 프로필을 찾을 수 없습니다.", 404)
        dao.audit(account.id, "GAME_PROFILE_SELECTED", metadata = mapOf("profile_id" to profileId.toString()))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/nickname-policy") fun nicknamePolicy(session: HttpSession): Map<String, Any?> = nicknames.status(accounts.requireCurrentAccount(session))
    @PatchMapping("/nickname") fun nickname(@RequestBody body: NicknameRequest, session: HttpSession): NicknameChangeResult = nicknames.change(recent(session), body.nickname, body.fixed)
    @PostMapping("/password") fun password(@RequestBody body: PasswordRequest, session: HttpSession): ResponseEntity<Void> {
        requirePasswordEnabled()
        accounts.setPassword(recent(session), body.password.toCharArray()); return ResponseEntity.noContent().build()
    }
    @PostMapping("/reauthenticate") fun reauthenticate(@RequestBody body: ReauthenticateRequest, session: HttpSession): ResponseEntity<Void> {
        requirePasswordEnabled()
        val account = accounts.requireCurrentAccount(session)
        security.reauthenticate(account, body.password.toCharArray(), body.totpCode)
        session.markRecentlyAuthenticated()
        return ResponseEntity.noContent().build()
    }
    @PostMapping("/reauthenticate/email-mfa-code") fun reauthenticationEmailMfaCode(@RequestBody body: ReauthenticateRequest, session: HttpSession): ResponseEntity<Void> {
        requirePasswordEnabled()
        security.requestEmailMfaReauthenticationCode(accounts.requireCurrentAccount(session), body.password.toCharArray())
        return ResponseEntity.accepted().build()
    }
    @GetMapping("/reauthenticate/oauth/{provider}") fun beginOAuthReauthentication(@PathVariable provider: String, session: HttpSession): ResponseEntity<Void> {
        val account = accounts.requireCurrentAccount(session)
        limiter.check("reauthenticate-oauth:${account.id}", 20, 3600)
        val vendor = me.kkutuio.kkutuweb.oauth.AuthVendor.fromName(provider) ?: throw IdpException("invalid_request", "지원하지 않는 제공사입니다.")
        val linked = dao.listIdentities(account.id).any { it.type == IdentityType.OAUTH && it.revokedAt == null && it.provider.equals(vendor.name, ignoreCase = true) }
        if (!linked) throw IdpException("forbidden", "연결된 로그인 수단만 사용할 수 있습니다.", 403)
        session.setAttribute(SessionAttribute.AFTER_LOGIN_URL.attributeName, "/account")
        val url = loginService.getAuthorizationUrl(session, vendor) ?: throw IdpException("temporarily_unavailable", "로그인 제공사가 설정되지 않았습니다.", 503)
        return ResponseEntity.status(302).header("Location", url).build()
    }
    @PostMapping("/email/verify") fun emailVerify(@RequestBody body: EmailRequest, session: HttpSession): ResponseEntity<Void> {
        security.sendEmailVerification(recent(session), body.email); return ResponseEntity.accepted().build()
    }
    @PostMapping("/email/resend") fun emailResend(@RequestBody body: EmailRequest, session: HttpSession): ResponseEntity<Void> {
        security.sendEmailVerification(recent(session), body.email); return ResponseEntity.accepted().build()
    }
    @DeleteMapping("/email") fun removeEmail(session: HttpSession): ResponseEntity<Void> { accounts.removeEmail(recent(session)); return ResponseEntity.noContent().build() }
    @GetMapping("/identities") fun identities(session: HttpSession): List<Map<String, Any?>> {
        val account = accounts.requireCurrentAccount(session)
        runCatching { session.getOAuthUser() }.getOrNull()?.takeIf {
            it.authVendor.name != "LOCAL" && it.getUserId() == account.legacyUserId
        }?.let { accounts.linkExternalIdentity(account, it) }
        return dao.listIdentities(account.id).filter {
            it.revokedAt == null && (it.type == IdentityType.OAUTH || (settings.passwordEnabled && it.type == IdentityType.PASSWORD))
        }.map {
            mapOf("id" to it.id, "type" to it.type.name, "provider" to it.provider, "display_name" to it.displayName, "verified" to (it.verifiedAt != null), "is_primary" to it.primary, "is_origin" to (it.id == account.originIdentityId), "created_at" to it.createdAt, "last_used_at" to it.lastUsedAt, "revocable" to (!it.primary && it.id != account.originIdentityId && dao.countActiveLoginMethods(account.id, settings.passwordEnabled) > 1))
        }
    }
    @PostMapping("/identities/{id}/revoke") fun revokeIdentity(@PathVariable id: Long, session: HttpSession): ResponseEntity<Void> {
        val account = recent(session); limiter.check("identity-revoke:${account.id}", 10, 3600); val identity = dao.findIdentity(id) ?: throw IdpException("not_found", "로그인 수단을 찾을 수 없습니다.", 404)
        if (identity.accountId != account.id || identity.primary || identity.id == account.originIdentityId) throw IdpException("forbidden", "삭제할 수 없는 기본 수단입니다.", 403)
        if (dao.countActiveLoginMethods(account.id, settings.passwordEnabled) <= 1) throw IdpException("last_login_method", "다른 로그인 수단을 먼저 추가해야 합니다.")
        dao.revokeIdentity(id); dao.audit(account.id, "IDENTITY_REVOKED", id); return ResponseEntity.noContent().build()
    }
    @GetMapping("/identities/oauth/{provider}") fun beginOAuthLink(@PathVariable provider: String, session: HttpSession): ResponseEntity<Void> {
        limiter.check("identity-link:${accounts.requireCurrentAccount(session).id}", 20, 3600)
        val vendor = me.kkutuio.kkutuweb.oauth.AuthVendor.fromName(provider) ?: throw IdpException("invalid_request", "지원하지 않는 제공사입니다.")
        val url = loginService.beginIdentityLink(session, recent(session), vendor) ?: throw IdpException("temporarily_unavailable", "로그인 제공사가 설정되지 않았습니다.", 503)
        return ResponseEntity.status(302).header("Location", url).build()
    }
    @GetMapping("/passkeys") fun passkeys(session: HttpSession): List<Map<String, Any?>> = dao.listPasskeys(accounts.requireCurrentAccount(session).id).map { row ->
        mapOf("id" to row["id"], "device_name" to row["device_name"], "created_at" to row["created_at"], "last_used_at" to row["last_used_at"], "recently_used" to ((row["last_used_at"] as? java.sql.Timestamp)?.toInstant()?.isAfter(Instant.now().minusSeconds(30L * 86400)) == true))
    }
    @PostMapping("/passkeys/registration/options") fun passkeyRegistrationOptions(session: HttpSession): Map<String, Any> { val account = recent(session); limiter.check("passkey-register:${account.id}", 20, 3600); return webAuthn.registrationOptions(account) }
    @PostMapping("/passkeys/registration/complete") fun passkeyRegistrationComplete(@RequestBody body: PasskeyCompletionRequest, session: HttpSession): ResponseEntity<Void> { val account = recent(session); limiter.check("passkey-register:${account.id}", 20, 3600); webAuthn.completeRegistration(account, body.operationToken, body.credential, body.deviceName ?: "Passkey"); return ResponseEntity.noContent().build() }
    @DeleteMapping("/passkeys/{id}") fun removePasskey(@PathVariable id: Long, session: HttpSession): ResponseEntity<Void> {
        val account = recent(session); limiter.check("passkey-revoke:${account.id}", 10, 3600); if (dao.countActiveLoginMethods(account.id, settings.passwordEnabled) <= 1) throw IdpException("last_login_method", "다른 로그인 수단을 먼저 추가해야 합니다.")
        if (dao.revokePasskey(account.id, id) != 1) throw IdpException("not_found", "패스키를 찾을 수 없습니다.", 404)
        dao.audit(account.id, "PASSKEY_REVOKED"); return ResponseEntity.noContent().build()
    }
    @GetMapping("/mfa") fun mfa(session: HttpSession): Map<String, Any?> { val a = accounts.requireCurrentAccount(session); val row = dao.findTotp(a.id); return mapOf("totp" to (row?.get("confirmed_at") != null), "totp_name" to row?.get("display_name"), "external_login_mfa_enabled" to a.externalMfaEnabled, "one_time_login_codes_remaining" to dao.activeRecoveryCodeCount(a.id)) }
    @PatchMapping("/mfa/external-login") fun setExternalLoginMfa(@RequestBody body: ExternalMfaSettingRequest, session: HttpSession): ResponseEntity<Void> {
        if (!settings.passwordEnabled) throw IdpException("password_disabled", "비밀번호 로그인 기능이 꺼진 경우 외부 로그인 2단계 인증은 항상 적용됩니다.", 400)
        val account = recent(session)
        if (body.enabled && !security.requiresSecondFactor(account)) throw IdpException("totp_required", "먼저 TOTP 2단계 인증을 설정해 주세요.", 400)
        dao.setExternalMfaEnabled(account.id, body.enabled)
        dao.audit(account.id, if (body.enabled) "EXTERNAL_LOGIN_MFA_ENABLED" else "EXTERNAL_LOGIN_MFA_DISABLED")
        return ResponseEntity.noContent().build()
    }
    @PostMapping("/mfa/totp/setup") fun totpSetup(@RequestBody(required = false) body: TotpSetupRequest?, session: HttpSession) = security.setupTotp(recent(session), body?.name ?: "TOTP 매체")
    @PostMapping("/mfa/totp/confirm") fun totpConfirm(@RequestBody body: TotpConfirmRequest, session: HttpSession): ResponseEntity<Void> { security.confirmTotp(recent(session), body.code); return ResponseEntity.noContent().build() }
    @PatchMapping("/mfa/totp") fun totpRename(@RequestBody body: TotpRenameRequest, session: HttpSession): ResponseEntity<Void> { val a = recent(session); dao.renameTotp(a.id, body.name.trim().take(100)); dao.audit(a.id, "TOTP_RENAMED"); return ResponseEntity.noContent().build() }
    @DeleteMapping("/mfa/totp") fun totpRemove(session: HttpSession): ResponseEntity<Void> {
        val a = recent(session)
        if (dao.activeRecoveryCodeCount(a.id) == 0 && dao.listPasskeys(a.id).isEmpty() && !dao.hasVerifiedEmail(a.id)) {
            throw IdpException("recovery_method_required", "다른 복구 수단을 먼저 추가해야 합니다.")
        }
        dao.revokeTotp(a.id); dao.audit(a.id, "TOTP_REVOKED"); return ResponseEntity.noContent().build()
    }
    @PostMapping("/one-time-login-codes/rotate") fun oneTimeLoginCodes(session: HttpSession): Map<String, Any> = mapOf("codes" to security.rotateOneTimeLoginCodes(recent(session)))
    @PostMapping("/support-pin/issue") fun supportPin(session: HttpSession): Map<String, String> = mapOf("pin" to security.issueSupportPin(recent(session)))
    @PostMapping("/security-code/reveal") fun revealSecurityCode(session: HttpSession): Map<String, String> = mapOf("securityCode" to accounts.revealSecurityCode(recent(session)))
    @PostMapping("/email/confirm") fun emailConfirm(@RequestParam token: String): ResponseEntity<Void> { security.confirmEmail(token); return ResponseEntity.noContent().build() }

    private fun recent(session: HttpSession): Account {
        val account = accounts.requireCurrentAccount(session)
        if (!session.hasRecentAuthentication()) throw IdpException("reauthentication_required", "최근 인증이 필요합니다.", 401)
        return account
    }
    private fun requirePasswordEnabled() {
        if (!settings.passwordEnabled) throw IdpException("password_disabled", "비밀번호 로그인은 비활성화되어 있습니다.", 404)
    }
}

@RestController
class AccountRecoveryApi(
    private val security: AccountSecurityService,
    private val loginService: LoginService,
    private val captcha: RecaptchaVerifier,
    private val limiter: AccountRateLimiter,
    private val webAuthn: WebAuthnService,
    private val settings: IdentityProviderSettings
) {
    /** Public configuration only; the reCAPTCHA secret is never exposed. */
    @GetMapping("/api/account/recovery/config") fun config(): Map<String, Any> =
        mapOf("recaptcha_site_key" to settings.recaptchaSiteKey, "password_enabled" to settings.passwordEnabled)
    @PostMapping("/api/account/recovery/request") fun request(@RequestBody body: RecoveryRequest, request: HttpServletRequest): ResponseEntity<Map<String, String>> {
        captcha.verify(body.recaptchaToken, request.getIp(), "account_recovery_request"); limiter.check("recovery-ip:${request.getIp()}", 10, 3600); security.requestAccountRecovery(body.email)
        return ResponseEntity.accepted().body(mapOf("message" to "입력한 정보가 등록되어 있다면 복구 안내를 보냈습니다."))
    }
    @PostMapping("/api/account/recovery/reset") fun reset(@RequestBody body: ResetPasswordRequest, request: HttpServletRequest): ResponseEntity<Void> { requirePasswordEnabled(); captcha.verify(body.recaptchaToken, request.getIp(), "account_recovery_reset"); limiter.check("reset-ip:" + request.getIp(), 10, 3600); security.resetPassword(body.token, body.password.toCharArray()); return ResponseEntity.noContent().build() }
    @PostMapping("/api/account/recovery/one-time-login-code") fun oneTimeLoginCode(@RequestBody body: OneTimeLoginCodeRequest, request: HttpServletRequest): ResponseEntity<Void> { captcha.verify(body.recaptchaToken, request.getIp(), "account_one_time_login_code"); limiter.check("one-time-login-code-ip:" + request.getIp(), 10, 3600); loginService.loginWithAccount(request, security.consumeOneTimeLoginCode(body.code)); return ResponseEntity.noContent().build() }
    @GetMapping("/api/account/login/mfa") fun pendingExternalSecondFactor(session: HttpSession): Map<String, Boolean> {
        val account = loginService.pendingSecondFactorAccount(session)
        return mapOf("pending" to (account != null), "email_backup_available" to (account?.let(security::isEmailMfaBackupAvailable) ?: false))
    }
    @PostMapping("/api/account/login/mfa") fun completeExternalSecondFactor(@RequestBody body: ExternalSecondFactorRequest, request: HttpServletRequest): ResponseEntity<Map<String, String>> {
        val pendingAccount = loginService.pendingSecondFactorAccount(request.session)
            ?: throw IdpException("mfa_session_expired", "2단계 인증 세션이 만료되었습니다. 다시 로그인해 주세요.", 401)
        try {
            limiter.check("external-mfa-session:${request.session.id}", 10, 300)
            limiter.check("external-mfa-account:${pendingAccount.id}", 10, 300)
            limiter.check("external-mfa-ip:${request.getIp()}", 20, 900)
        } catch (error: IdpException) {
            if (error.error == "rate_limited") loginService.cancelPendingSecondFactor(request.session)
            throw error
        }
        if (!loginService.completePendingSecondFactor(request.session, body.totpCode, body.securityCode, body.emailCode)) throw IdpException("mfa_session_expired", "2단계 인증 세션이 만료되었습니다. 다시 로그인해 주세요.", 401)
        val continuation = request.session.getAttribute(SessionAttribute.AFTER_LOGIN_URL.attributeName) as? String
        request.session.removeAttribute(SessionAttribute.AFTER_LOGIN_URL.attributeName)
        val redirect = continuation?.takeIf { it.startsWith('/') && !it.startsWith("//") } ?: "/"
        return ResponseEntity.ok(mapOf("redirect" to redirect))
    }
    @PostMapping("/api/account/login/mfa/email") fun requestExternalSecondFactorEmail(request: HttpServletRequest): ResponseEntity<Void> {
        limiter.check("external-mfa-email-ip:${request.getIp()}", 10, 3600)
        val account = loginService.pendingSecondFactorAccount(request.session)
            ?: throw IdpException("mfa_session_expired", "2단계 인증 세션이 만료되었습니다. 다시 로그인해 주세요.", 401)
        security.requestEmailMfaPendingCode(account)
        return ResponseEntity.accepted().build()
    }
    @PostMapping("/api/account/password/login") fun passwordLogin(@RequestBody body: PasswordLoginRequest, request: HttpServletRequest): ResponseEntity<Map<String, Boolean>> {
        requirePasswordEnabled()
        val normalized = body.identifier.trim().lowercase()
        limiter.check("password-login-ip:" + request.getIp(), 50, 900)
        limiter.check("password-login-identifier:" + normalized, 10, 900)
        val account = security.authenticatePasswordFirstFactor(body.identifier, body.password.toCharArray())
        if (security.requiresSecondFactor(account)) {
            loginService.beginPendingPasswordSecondFactor(request, account)
            return ResponseEntity.status(202).body(mapOf("mfa_required" to true))
        }
        security.recordPasswordLoginSuccess(account)
        loginService.loginWithAccount(request, account)
        return ResponseEntity.noContent().build()
    }
    @PostMapping("/api/account/password/login/email-mfa-code") fun passwordLoginEmailMfaCode(@RequestBody body: PasswordLoginRequest, request: HttpServletRequest): ResponseEntity<Void> { requirePasswordEnabled(); limiter.check("email-mfa-request-ip:" + request.getIp(), 10, 3600); security.requestEmailMfaLoginCode(body.identifier, body.password.toCharArray()); return ResponseEntity.accepted().build() }
    @PostMapping("/api/account/passkeys/authentication/options") fun passkeyAuthenticationOptions(request: HttpServletRequest): Map<String, Any> { limiter.check("passkey-login:${request.getIp()}", 20, 900); return webAuthn.authenticationOptions() }
    @PostMapping("/api/account/passkeys/authentication/complete") fun passkeyAuthenticationComplete(@RequestBody body: PasskeyCompletionRequest, request: HttpServletRequest): ResponseEntity<Void> { limiter.check("passkey-login:${request.getIp()}", 20, 900); loginService.loginWithAccount(request, webAuthn.completeAuthentication(body.operationToken, body.credential)); return ResponseEntity.noContent().build() }
    private fun requirePasswordEnabled() {
        if (!settings.passwordEnabled) throw IdpException("password_disabled", "비밀번호 로그인은 비활성화되어 있습니다.", 404)
    }
}
