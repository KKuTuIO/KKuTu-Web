package me.kkutuio.kkutuweb.identity.oauth

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import me.kkutuio.kkutuweb.identity.*
import me.kkutuio.kkutuweb.user.UserDao
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class OidcService(
    private val dao: IdentityDao,
    private val settings: IdentityProviderSettings,
    private val signer: TokenSigner,
    private val userDao: UserDao,
    private val objectMapper: ObjectMapper
) {
    val supportedScopes = setOf("openid", "profile", "email", "account", "offline", "game:kkutu", "admin:access")

    fun validateAuthorization(clientId: String, redirectUri: String, scopes: Set<String>, codeChallenge: String, method: String): RegisteredClient {
        val client = dao.findClient(clientId) ?: throw IdpException("unauthorized_client", "등록되지 않은 클라이언트입니다.", 400)
        if (!client.active || redirectUri !in client.redirectUris) throw IdpException("invalid_request", "허용되지 않은 redirect URI입니다.")
        if (method != "S256" || codeChallenge.length !in 43..128) throw IdpException("invalid_request", "PKCE S256이 필요합니다.")
        if (scopes.isEmpty() || !client.allowedScopes.containsAll(scopes) || !supportedScopes.containsAll(scopes)) throw IdpException("invalid_scope", "허용되지 않은 scope입니다.")
        return client
    }

    fun issueAuthorizationCode(account: Account, client: RegisteredClient, redirectUri: String, scopes: Set<String>, nonce: String?, challenge: String, selectedProfileId: UUID? = null): String {
        if (account.status != AccountStatus.ACTIVE) throw IdpException("access_denied", "로그인할 수 없는 계정입니다.", 403)
        val selectedProfile = selectedProfileId?.let { dao.findActiveProfile(account.id, it) } ?: dao.defaultProfile(account.id)
        if (selectedProfileId != null && selectedProfile == null) throw IdpException("invalid_request", "선택한 게임 프로필을 사용할 수 없습니다.")
        val code = SecretTools.randomToken()
        dao.saveAuthorizationCode(SecretTools.sha256(code), account.id, client.clientId, redirectUri, scopes, nonce, challenge, selectedProfile?.get("id")?.toString()?.let(UUID::fromString),
            Instant.now().plusSeconds(settings.authorizationCodeTtlSeconds))
        dao.audit(account.id, "OIDC_AUTHORIZATION_CODE_ISSUED", metadata = mapOf("client_id" to client.clientId, "scopes" to scopes.sorted()))
        return code
    }

    @Transactional
    fun exchangeCode(clientId: String, clientSecret: String?, code: String, redirectUri: String, verifier: String): TokenSet {
        val client = authenticateClient(clientId, clientSecret)
        val codeHash = SecretTools.sha256(code)
        // Validate the binding before consuming.  A request with a wrong client,
        // redirect URI, or verifier must not let an interceptor burn the code.
        val preview = dao.findUsableAuthorizationCode(codeHash) ?: throw IdpException("invalid_grant", "유효하지 않거나 이미 사용된 authorization code입니다.")
        if (preview["client_id"] != client.clientId || preview["redirect_uri"] != redirectUri) throw IdpException("invalid_grant", "Authorization code 요청이 일치하지 않습니다.")
        val expectedChallenge = preview["code_challenge"] as String
        val verifierHash = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val actualChallenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(verifierHash)
        if (!SecretTools.constantTimeEquals(expectedChallenge, actualChallenge)) throw IdpException("invalid_grant", "PKCE verifier가 일치하지 않습니다.")
        val row = dao.consumeAuthorizationCode(codeHash) ?: throw IdpException("invalid_grant", "유효하지 않거나 이미 사용된 authorization code입니다.")
        if (row["client_id"] != client.clientId || row["redirect_uri"] != redirectUri) throw IdpException("invalid_grant", "Authorization code 요청이 일치하지 않습니다.")
        val account = dao.findAccount(UUID.fromString(row["account_id"].toString())) ?: throw IdpException("invalid_grant", "계정을 찾을 수 없습니다.")
        requireActive(account)
        val scopes = readScopes(row["scopes"])
        val nonce = row["nonce"] as? String
        return issueTokens(account, client, scopes, nonce, selectedProfileId = row["selected_profile_id"]?.toString()?.let(UUID::fromString))
    }

    @Transactional
    fun refresh(clientId: String, clientSecret: String?, refreshToken: String): TokenSet {
        val client = authenticateClient(clientId, clientSecret)
        val hash = SecretTools.sha256(refreshToken)
        val row = dao.consumeRefreshToken(hash, client.clientId)
        if (row == null) {
            dao.findRefreshTokenForClient(hash, client.clientId)?.get("family_id")?.toString()?.let { dao.revokeRefreshFamily(UUID.fromString(it)) }
            throw IdpException("invalid_grant", "유효하지 않은 refresh token입니다.")
        }
        val account = dao.findAccount(UUID.fromString(row["account_id"].toString())) ?: throw IdpException("invalid_grant", "계정을 찾을 수 없습니다.")
        requireActive(account)
        return issueTokens(account, client, readScopes(row["scopes"]), null, UUID.fromString(row["family_id"].toString()), hash, row["selected_profile_id"]?.toString()?.let(UUID::fromString))
    }

    fun userInfo(accessToken: String): Map<String, Any?> {
        val row = dao.findActiveAccessToken(SecretTools.sha256(accessToken)) ?: throw IdpException("invalid_token", "유효하지 않은 인증 토큰입니다. 다시 로그인해 주세요.", 401)
        val account = dao.findAccount(UUID.fromString(row["account_id"].toString())) ?: throw IdpException("invalid_token", "계정을 찾을 수 없습니다.", 401)
        requireActive(account)
        return claims(account, readScopes(row["scopes"]), row["selected_profile_id"]?.toString()?.let(UUID::fromString))
    }

    /** Verifies an opaque access token for resource-server use. */
    fun authenticateAccessToken(accessToken: String): AccessTokenPrincipal {
        val row = dao.findActiveAccessToken(SecretTools.sha256(accessToken))
            ?: throw IdpException("invalid_token", "유효하지 않은 인증 토큰입니다. 다시 로그인해 주세요.", 401)
        val account = dao.findAccount(UUID.fromString(row["account_id"].toString()))
            ?: throw IdpException("invalid_token", "계정을 찾을 수 없습니다.", 401)
        requireActive(account)
        return AccessTokenPrincipal(account, row["client_id"].toString(), readScopes(row["scopes"]))
    }

    fun introspect(clientId: String, secret: String?, token: String): Map<String, Any?> {
        authenticateClient(clientId, secret, confidentialOnly = true)
        val row = dao.findActiveAccessToken(SecretTools.sha256(token)) ?: return mapOf("active" to false)
        return mapOf("active" to true, "client_id" to row["client_id"], "sub" to row["account_id"], "scope" to readScopes(row["scopes"]).joinToString(" "), "exp" to (row["expires_at"] as java.sql.Timestamp).toInstant().epochSecond)
    }

    fun revoke(clientId: String, secret: String?, token: String) {
        val client = authenticateClient(clientId, secret)
        val tokenHash = SecretTools.sha256(token)
        dao.findActiveAccessToken(tokenHash)?.let { if (it["client_id"] == client.clientId) dao.revokeAccessToken(tokenHash) }
        dao.findRefreshToken(tokenHash)?.let { if (it["client_id"] == client.clientId) dao.revokeRefreshFamily(UUID.fromString(it["family_id"].toString())) }
    }

    private fun issueTokens(account: Account, client: RegisteredClient, scopes: Set<String>, nonce: String?, existingFamily: UUID? = null, parentHash: String? = null, selectedProfileId: UUID? = null): TokenSet {
        requireActive(account)
        val now = Instant.now()
        val access = SecretTools.randomToken()
        dao.saveAccessToken(SecretTools.sha256(access), account.id, client.clientId, scopes, selectedProfileId, now.plusSeconds(client.accessTokenTtlSeconds))
        val idToken = if ("openid" in scopes) signer.sign(account.id.toString(), client.clientId, claims(account, scopes, selectedProfileId) + mapOf("nonce" to nonce), now.plusSeconds(client.accessTokenTtlSeconds)) else null
        val refresh = if ("offline" in scopes) SecretTools.randomToken().also {
            dao.saveRefreshToken(SecretTools.sha256(it), existingFamily ?: UUID.randomUUID(), parentHash, account.id, client.clientId, scopes, selectedProfileId, now.plusSeconds(client.refreshTokenTtlSeconds))
        } else null
        dao.audit(account.id, "OIDC_TOKEN_ISSUED", metadata = mapOf("client_id" to client.clientId, "scopes" to scopes.sorted()))
        return TokenSet(access, expiresIn = client.accessTokenTtlSeconds, scope = scopes.sorted().joinToString(" "), idToken = idToken, refreshToken = refresh)
    }

    private fun claims(account: Account, scopes: Set<String>, selectedProfileId: UUID? = null): Map<String, Any?> {
        val profile = selectedProfileId?.let { dao.findActiveProfile(account.id, it) } ?: dao.defaultProfile(account.id)
        val gameUserId = profile?.get("id")?.toString() ?: account.legacyUserId
        val user = userDao.getUser(gameUserId)
        val verifiedEmail = dao.listIdentities(account.id).firstOrNull { it.type == IdentityType.EMAIL && it.verifiedAt != null && it.revokedAt == null }?.subject
        val out = linkedMapOf<String, Any?>("sub" to account.id.toString())
        if ("profile" in scopes) out += mapOf("preferred_username" to user?.nickname, "legacy_user_id" to account.legacyUserId)
        if ("email" in scopes) out += mapOf("email" to verifiedEmail, "email_verified" to (verifiedEmail != null))
        if ("account" in scopes) out += mapOf("account_status" to account.status.name, "uuid" to account.uuid.toString())
        if ("game:kkutu" in scopes) {
            if (profile != null) out += mapOf("game_profile_id" to profile["id"].toString(), "game_key" to profile["game_key"], "game_profile_legacy_user_id" to profile["legacy_user_id"])
        }
        return out
    }

    private fun authenticateClient(clientId: String, secret: String?, confidentialOnly: Boolean = false): RegisteredClient {
        val client = dao.findClient(clientId) ?: throw IdpException("invalid_client", "유효하지 않은 client입니다.", 401)
        if (!client.active || (confidentialOnly && client.type != ClientType.CONFIDENTIAL)) throw IdpException("invalid_client", "유효하지 않은 client입니다.", 401)
        if (client.type == ClientType.CONFIDENTIAL && (secret == null || client.secretHash == null || !SecretTools.verifyPassword(client.secretHash, secret.toCharArray()))) throw IdpException("invalid_client", "유효하지 않은 client secret입니다.", 401)
        return client
    }
    private fun requireActive(account: Account) {
        if (account.status != AccountStatus.ACTIVE) throw IdpException("access_denied", "로그인할 수 없는 계정입니다.", 403)
    }
    private fun readScopes(raw: Any?): Set<String> = objectMapper.readValue(raw.toString(), object : TypeReference<Set<String>>() {})
}
