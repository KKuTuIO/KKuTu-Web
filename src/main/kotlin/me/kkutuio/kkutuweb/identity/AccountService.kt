package me.kkutuio.kkutuweb.identity

import me.kkutuio.kkutuweb.extension.*
import me.kkutuio.kkutuweb.oauth.AuthVendor
import me.kkutuio.kkutuweb.oauth.OAuthUser
import me.kkutuio.kkutuweb.user.UserDao
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@Service
class AccountService(
    private val dao: IdentityDao,
    private val userDao: UserDao,
    private val settings: IdentityProviderSettings,
) {
    @Transactional
    fun ensureExternalAccount(oauth: OAuthUser, request: HttpServletRequest? = null): Account {
        val provider = oauth.authVendor.name
        val subject = oauth.vendorId
        val historicalIdentity = dao.findIdentity(provider, subject)
        val existingIdentity = historicalIdentity?.takeIf { it.revokedAt == null }
        val account = if (existingIdentity != null) {
            dao.findAccount(existingIdentity.accountId) ?: throw IdpException("server_error", "Identity points to a missing account", 500)
        } else {
            val isFreshAfterRevocation = historicalIdentity?.revokedAt != null
            val legacyId = if (isFreshAfterRevocation) UUID.randomUUID().toString() else oauth.getUserId()
            val created = dao.findAccountByLegacyId(legacyId) ?: dao.createAccount(legacyId)
            val identity = dao.insertIdentity(created.id, IdentityType.OAUTH, provider, subject, oauth.name, verified = true, primary = true)
            dao.setOriginAndPrimary(created.id, identity.id)
            if (created.status == AccountStatus.PROVISIONED) {
                dao.setStatus(created.id, AccountStatus.ACTIVE)
            }
            dao.audit(
                created.id, "IDENTITY_CREATED", identity.id, request?.getIp()?.let(SecretTools::sha256),
                mapOf("type" to "OAUTH", "provider" to provider, "fresh_after_revocation" to isFreshAfterRevocation)
            )
            created
        }
        dao.createKkutuProfile(account.id, account.legacyUserId)
        val profileLegacyUserId = selectedGameProfileLegacyUserId(account)
        userDao.getUser(selectedGameProfileId(account))?.nickname?.let { dao.updateProfileNickname(account.id, profileLegacyUserId, it) }
        dao.findActiveIdentity(provider, subject)?.let { dao.touchIdentity(it.id) }
        val verifiedEmail = oauth.email?.trim()?.lowercase()?.takeIf { oauth.emailVerified }
        val hasEverRegisteredEmail = dao.listIdentities(account.id).any { it.type == IdentityType.EMAIL }
        if (verifiedEmail != null && !hasEverRegisteredEmail && dao.findIdentity("EMAIL", verifiedEmail) == null) {
            dao.insertIdentity(account.id, IdentityType.EMAIL, "EMAIL", verifiedEmail, verified = true)
            dao.audit(account.id, "EMAIL_ADDED_FROM_OAUTH", metadata = mapOf("provider" to provider))
        }
        dao.audit(account.id, "LOGIN_SUCCESS", null, request?.getIp()?.let(SecretTools::sha256), mapOf("provider" to provider))
        return dao.findAccount(account.id)!!.also(::requireLoginAllowed)
    }

    @Transactional
    fun removeEmail(account: Account) {
        val email = dao.listIdentities(account.id).firstOrNull { it.type == IdentityType.EMAIL && it.revokedAt == null }
            ?: throw IdpException("not_found", "등록된 전자 메일 주소가 없습니다.", 404)
        dao.revokeIdentity(email.id)
        dao.audit(account.id, "EMAIL_REMOVED", email.id)
    }

    fun revealSecurityCode(account: Account): String {
        val value = userDao.getUser(selectedGameProfileId(account))?.flags?.path("uid")?.path("value")?.asText()
        if (value.isNullOrBlank()) throw IdpException("not_found", "표시할 보안코드가 없습니다.", 404)
        dao.audit(account.id, "SECURITY_CODE_REVEALED")
        return value
    }

    fun currentAccount(session: HttpSession): Account? = session.getAccountId()?.let(dao::findAccount)
        ?.takeIf { it.sessionNotBefore.epochSecond <= session.authenticatedAt() }

    fun findAccount(accountId: java.util.UUID): Account? = dao.findAccount(accountId)

    fun findExternalAccount(oauth: OAuthUser): Account? = dao.findActiveIdentity(oauth.authVendor.name, oauth.vendorId)
        ?.let { dao.findAccount(it.accountId) }

    fun isRevokedExternalIdentity(oauth: OAuthUser): Boolean =
        dao.findIdentity(oauth.authVendor.name, oauth.vendorId)?.revokedAt != null

    /**
     * The OAuth method proves how the account authenticated.  It is not the
     * game profile to use for the session.  A linked provider can have a
     * different provider id while still belonging to this same account.
     */
    fun selectedGameProfileLegacyUserId(account: Account): String =
        dao.defaultProfile(account.id)?.get("legacy_user_id")?.toString()?.takeIf { it.isNotBlank() }
            ?: account.legacyUserId

    fun selectedGameProfileId(account: Account): String =
        dao.selectedProfileId(account.id) ?: account.legacyUserId

    fun selectedGameProfileNicknameSuffix(account: Account): String = dao.nicknameSuffix(account.id)

    /** Verifies an existing login method without changing the signed-in account. */
    @Transactional
    fun verifyExternalReauthentication(account: Account, oauth: OAuthUser) {
        val identity = dao.findActiveIdentity(oauth.authVendor.name, oauth.vendorId)
            ?: throw IdpException("reauthentication_failed", "현재 계정에 연결된 로그인 수단이 아닙니다.", 403)
        if (identity.accountId != account.id) {
            throw IdpException("reauthentication_failed", "현재 계정에 연결된 로그인 수단이 아닙니다.", 403)
        }
        dao.touchIdentity(identity.id)
        dao.audit(account.id, "OAUTH_REAUTHENTICATED", identity.id, metadata = mapOf("provider" to oauth.authVendor.name))
    }

    @Transactional
    fun ensureLegacyExternalIdentity(account: Account) {
        val provider = AuthVendor.values().firstOrNull { vendor ->
            vendor != AuthVendor.LOCAL && account.legacyUserId.startsWith(vendor.name.lowercase() + "-")
        } ?: return
        val subject = account.legacyUserId.removePrefix(provider.name.lowercase() + "-")
        if (subject.isBlank()) return

        val existing = dao.findIdentity(provider.name, subject)
        if (existing != null) {
            if (existing.accountId == account.id && existing.revokedAt != null) {
                dao.restoreIdentity(existing.id)
                dao.audit(account.id, "LEGACY_IDENTITY_RESTORED", existing.id, metadata = mapOf("provider" to provider.name))
            }
            return
        }

        val identity = dao.insertIdentity(
            account.id, IdentityType.OAUTH, provider.name, subject, providerDisplayName(provider),
            verified = true, primary = account.primaryIdentityId == null || account.primaryIdentityId == 0L
        )
        if (account.originIdentityId == null || account.originIdentityId == 0L || account.primaryIdentityId == null || account.primaryIdentityId == 0L) {
            dao.setOriginAndPrimary(account.id, identity.id)
        }
        dao.audit(account.id, "LEGACY_IDENTITY_REPAIRED", identity.id, metadata = mapOf("provider" to provider.name))
    }

    /**
     * V1 deliberately leaves legacy migration disabled.  A legacy game user is
     * nevertheless an existing account for registration-policy purposes, and
     * receives its IdP mapping lazily on the next successful login.
     */
    fun isKnownExternalAccount(oauth: OAuthUser): Boolean =
        findExternalAccount(oauth) != null ||
            dao.findAccountByLegacyId(oauth.getUserId()) != null ||
            userDao.getUser(oauth.getUserId()) != null

    fun requireCurrentAccount(session: HttpSession): Account = currentAccount(session)
        ?: throw IdpException("login_required", "로그인이 필요합니다.", 401)

    fun requireLoginAllowed(account: Account) {
        if (account.status != AccountStatus.ACTIVE) throw IdpException("access_denied", "로그인할 수 없는 계정입니다.", 403)
    }

    private fun providerDisplayName(provider: AuthVendor): String = when (provider) {
        AuthVendor.NAVER -> "네이버"
        AuthVendor.KAKAO -> "카카오"
        AuthVendor.DALDALSO -> "달달소"
        else -> provider.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun bindSession(session: HttpSession, account: Account) {
        session.setAccountId(account.id)
        session.markAuthenticated()
        session.markRecentlyAuthenticated()
    }

    @Transactional
    fun linkExternalIdentity(account: Account, oauth: OAuthUser) {
        val provider = oauth.authVendor.name
        val legacyUserId = oauth.getUserId()

        val existingAccount = dao.findAccountByLegacyId(legacyUserId)
        if (existingAccount != null && existingAccount.id != account.id) {
            throw IdpException("identity_conflict", "이미 다른 계정에 연결된 로그인 수단입니다.")
        }
        if (existingAccount == null && legacyUserId != account.legacyUserId && userDao.getUser(legacyUserId) != null) {
            throw IdpException("identity_conflict", "이미 존재하는 게임 계정의 로그인 수단입니다.")
        }

        val existing = dao.findIdentity(provider, oauth.vendorId)
        if (existing != null && existing.accountId != account.id) throw IdpException("identity_conflict", "이미 다른 계정에 연결된 로그인 수단입니다.")
        if (existing == null) {
            val identity = dao.insertIdentity(account.id, IdentityType.OAUTH, provider, oauth.vendorId, oauth.name, verified = true)
            dao.audit(account.id, "IDENTITY_LINKED", identity.id, metadata = mapOf("provider" to provider))
        } else if (existing.revokedAt != null) {
            dao.restoreIdentity(existing.id)
            dao.audit(account.id, "IDENTITY_RELINKED", existing.id, metadata = mapOf("provider" to provider))
        }
    }

    @Transactional
    fun setPassword(account: Account, password: CharArray) {
        requirePasswordEnabled()
        require(password.size >= 12) { "비밀번호는 12자 이상이어야 합니다." }
        val current = dao.listIdentities(account.id).firstOrNull { it.type == IdentityType.PASSWORD && it.revokedAt == null }
        val hash = SecretTools.hashPassword(password)
        if (current == null) dao.insertIdentity(account.id, IdentityType.PASSWORD, "LOCAL", account.id.toString(), credentialHash = hash, verified = true)
        else dao.updateCredentialHash(current.id, hash)
        dao.revokeRefreshTokens(account.id)
        dao.invalidateSessions(account.id)
        dao.audit(account.id, "PASSWORD_SET")
    }

    fun summary(account: Account): Map<String, Any?> {
        val selectedUserId = selectedGameProfileId(account)
        val user = userDao.getUser(selectedUserId)
        val nicknameState = userDao.nicknameState(selectedUserId)
        val identities = dao.listIdentities(account.id).filter { it.revokedAt == null && (settings.passwordEnabled || it.type != IdentityType.PASSWORD) }
        val email = identities.firstOrNull { it.type == IdentityType.EMAIL && it.verifiedAt != null }
        return mapOf(
            "sub" to account.id.toString(), "uuid" to account.uuid.toString(), "legacy_user_id" to account.legacyUserId,
            "nickname" to user?.nickname, "nickname_changed_at" to nicknameState?.lastModifiedAt?.let { Instant.ofEpochMilli(it).toString() },
            "email" to email?.subject?.let(::maskEmail),
            "email_verified" to (email != null),
            "linked_services" to identities.count { it.type == IdentityType.OAUTH },
            "password_enabled" to settings.passwordEnabled,
            "external_mfa_enabled" to account.externalMfaEnabled,
            "login_methods" to dao.countActiveLoginMethods(account.id, settings.passwordEnabled),
            "passkeys" to dao.listPasskeys(account.id).size,
            "support_pin_issued_at" to dao.supportPinIssuedAt(account.id)?.toString(),
            "selected_profile_id" to dao.defaultProfile(account.id)?.get("id")?.toString()
            ,"profiles" to dao.listProfiles(account.id)
        )
    }

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        return if (at < 2) "***" else email.take(1) + "***" + email.substring(at)
    }

    private fun requirePasswordEnabled() {
        if (!settings.passwordEnabled) throw IdpException("password_disabled", "비밀번호 로그인은 비활성화되어 있습니다.", 404)
    }
}
