package me.kkutuio.kkutuweb.identity

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Repository
class IdentityDao(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    private val accountMapper = RowMapper { rs: ResultSet, _: Int ->
        Account(
            UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("uuid")), rs.getString("legacy_user_id"), AccountStatus.valueOf(rs.getString("status")), rs.getBoolean("external_mfa_enabled"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getTimestamp("session_not_before").toInstant(),
            rs.getLongOrNull("primary_identity_id"), rs.getLongOrNull("origin_identity_id")
        )
    }
    private val identityMapper = RowMapper { rs: ResultSet, _: Int ->
        AccountIdentity(
            rs.getLong("id"), UUID.fromString(rs.getString("account_id")), IdentityType.valueOf(rs.getString("type")),
            rs.getString("provider"), rs.getString("subject"), rs.getString("display_name"), rs.getString("credential_hash"),
            rs.getTimestamp("verified_at")?.toInstant(), rs.getBoolean("is_primary"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("last_used_at")?.toInstant(), rs.getTimestamp("revoked_at")?.toInstant()
        )
    }

    fun findAccount(id: UUID): Account? = queryOne("SELECT * FROM account WHERE id = ?", accountMapper, id)
    fun findAccountByLegacyId(legacyUserId: String): Account? = queryOne("SELECT * FROM account WHERE legacy_user_id = ?", accountMapper, legacyUserId)
    fun findIdentity(provider: String, subject: String): AccountIdentity? = queryOne("SELECT * FROM account_identity WHERE provider = ? AND subject = ?", identityMapper, provider, subject)
    fun findActiveIdentity(provider: String, subject: String): AccountIdentity? = queryOne("SELECT * FROM account_identity WHERE provider = ? AND subject = ? AND revoked_at IS NULL", identityMapper, provider, subject)
    fun findIdentity(id: Long): AccountIdentity? = queryOne("SELECT * FROM account_identity WHERE id = ?", identityMapper, id)
    fun listIdentities(accountId: UUID): List<AccountIdentity> = jdbc.query("SELECT * FROM account_identity WHERE account_id = ? ORDER BY created_at", identityMapper, accountId)

    fun createAccount(legacyUserId: String): Account {
        val id = UUID.randomUUID()
        val uuid = UUID.randomUUID()
        // Both identity references are deferrable foreign keys.  A temporary
        // sentinel lets the account and immutable origin identity be created in
        // one transaction while keeping the database-level NOT NULL invariant.
        jdbc.update("INSERT INTO account(id, uuid, legacy_user_id, primary_identity_id, origin_identity_id) VALUES (?, ?, ?, 0, 0)", id, uuid, legacyUserId)
        return findAccount(id)!!
    }
    fun createKkutuProfile(accountId: UUID, legacyUserId: String) = jdbc.update(
        "INSERT INTO game_profile(id, account_id, uuid, game_key, legacy_user_id) " +
            "SELECT ?, id, uuid, 'kkutu', ? FROM account WHERE id=? ON CONFLICT(account_id, game_key) DO NOTHING",
        UUID.randomUUID(), legacyUserId, accountId
    )
    fun listProfiles(accountId: UUID): List<Map<String, Any?>> = jdbc.queryForList("SELECT id, uuid, game_key, legacy_user_id, nickname, status FROM game_profile WHERE account_id=? AND status='ACTIVE' ORDER BY created_at", accountId)
    fun findActiveProfile(accountId: UUID, profileId: UUID): Map<String, Any?>? = jdbc.queryForList("SELECT id, uuid, game_key, legacy_user_id, nickname, status FROM game_profile WHERE account_id=? AND id=? AND status='ACTIVE'", accountId, profileId).firstOrNull()
    fun defaultProfile(accountId: UUID): Map<String, Any?>? = jdbc.queryForList(
        "SELECT id, uuid, game_key, legacy_user_id, nickname, status FROM game_profile WHERE id = COALESCE(" +
            "(SELECT a.selected_profile_id FROM account a JOIN game_profile selected ON selected.id=a.selected_profile_id AND selected.account_id=a.id AND selected.status='ACTIVE' WHERE a.id=?), " +
            "(SELECT id FROM game_profile WHERE account_id=? AND status='ACTIVE' ORDER BY created_at LIMIT 1))",
        accountId, accountId
    ).firstOrNull()
    fun setSelectedProfile(accountId: UUID, profileId: UUID): Boolean = jdbc.update(
        "UPDATE account SET selected_profile_id=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND EXISTS (SELECT 1 FROM game_profile WHERE id=? AND account_id=? AND status='ACTIVE')",
        profileId, accountId, profileId, accountId
    ) == 1
    fun updateProfileNickname(accountId: UUID, legacyUserId: String, nickname: String) = jdbc.update(
        "UPDATE game_profile SET nickname=?, updated_at=CURRENT_TIMESTAMP WHERE account_id=? AND legacy_user_id=?",
        nickname, accountId, legacyUserId
    )

    fun insertIdentity(accountId: UUID, type: IdentityType, provider: String, subject: String, displayName: String? = null,
                       credentialHash: String? = null, verified: Boolean = false, primary: Boolean = false): AccountIdentity {
        val id = jdbc.queryForObject(
            "INSERT INTO account_identity(account_id, type, provider, subject, display_name, credential_hash, verified_at, is_primary) VALUES (?, ?, ?, ?, ?, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END, ?) RETURNING id",
            Long::class.java, accountId, type.name, provider, subject, displayName, credentialHash, verified, primary
        )!!
        return findIdentity(id)!!
    }

    fun setOriginAndPrimary(accountId: UUID, identityId: Long) {
        jdbc.update("UPDATE account SET primary_identity_id = ?, origin_identity_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", identityId, identityId, accountId)
    }
    fun touchIdentity(identityId: Long) = jdbc.update("UPDATE account_identity SET last_used_at = CURRENT_TIMESTAMP WHERE id = ?", identityId)
    fun updateCredentialHash(identityId: Long, credentialHash: String) = jdbc.update("UPDATE account_identity SET credential_hash = ?, verified_at = COALESCE(verified_at, CURRENT_TIMESTAMP), last_used_at = CURRENT_TIMESTAMP WHERE id = ?", credentialHash, identityId)
    fun revokeIdentity(identityId: Long) = jdbc.update("UPDATE account_identity SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND is_primary = FALSE", identityId)
    fun restoreIdentity(identityId: Long) = jdbc.update("UPDATE account_identity SET revoked_at = NULL, verified_at = CURRENT_TIMESTAMP, last_used_at = CURRENT_TIMESTAMP WHERE id = ?", identityId)
    fun revokeActiveEmailIdentitiesExcept(accountId: UUID, exceptId: Long? = null): Int =
        if (exceptId == null) jdbc.update(
            "UPDATE account_identity SET revoked_at = CURRENT_TIMESTAMP WHERE account_id = ? AND type = 'EMAIL' AND revoked_at IS NULL",
            accountId
        ) else jdbc.update(
            "UPDATE account_identity SET revoked_at = CURRENT_TIMESTAMP WHERE account_id = ? AND type = 'EMAIL' AND revoked_at IS NULL AND id <> ?",
            accountId, exceptId
        )
    fun hasVerifiedEmail(accountId: UUID): Boolean = jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM account_identity WHERE account_id = ? AND type = 'EMAIL' AND verified_at IS NOT NULL AND revoked_at IS NULL)",
        Boolean::class.java, accountId
    ) == true
    fun updateNicknameTimestamp(accountId: UUID) = jdbc.update("UPDATE account SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", accountId)
    fun setStatus(accountId: UUID, status: AccountStatus) = jdbc.update("UPDATE account SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status.name, accountId)
    fun setExternalMfaEnabled(accountId: UUID, enabled: Boolean) = jdbc.update("UPDATE account SET external_mfa_enabled=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", enabled, accountId)

    fun audit(accountId: UUID?, event: String, identityId: Long? = null, ipHash: String? = null, metadata: Map<String, Any?> = emptyMap()) {
        jdbc.update("INSERT INTO account_audit_log(account_id, event_type, identity_id, ip_hash, metadata) VALUES (?, ?, ?, ?, CAST(? AS jsonb))",
            accountId, event, identityId, ipHash, objectMapper.writeValueAsString(metadata))
    }

    /** Atomically consumes one of the six email-verification sends allowed per KST calendar day. */
    fun consumeEmailVerificationQuota(accountId: UUID): Boolean = jdbc.queryForList(
        "INSERT INTO account_email_verification_quota(account_id, quota_date, attempts) VALUES (?, ?, 1) " +
            "ON CONFLICT(account_id, quota_date) DO UPDATE SET attempts = account_email_verification_quota.attempts + 1 " +
            "WHERE account_email_verification_quota.attempts < 6 RETURNING attempts",
        accountId, LocalDate.now(ZoneId.of("Asia/Seoul"))
    ).isNotEmpty()

    fun consumeEmailBackupCodeQuota(accountId: UUID): Boolean = jdbc.queryForList(
        "INSERT INTO account_email_backup_code_quota(account_id, quota_date, attempts, last_sent_at) VALUES (?, ?, 1, CURRENT_TIMESTAMP) " +
            "ON CONFLICT(account_id, quota_date) DO UPDATE SET attempts=account_email_backup_code_quota.attempts+1, last_sent_at=CURRENT_TIMESTAMP " +
            "WHERE account_email_backup_code_quota.attempts < 10 AND account_email_backup_code_quota.last_sent_at <= CURRENT_TIMESTAMP - INTERVAL '5 minutes' RETURNING attempts",
        accountId, LocalDate.now(ZoneId.of("Asia/Seoul"))
    ).isNotEmpty()

    fun saveClient(client: RegisteredClient) {
        jdbc.update("INSERT INTO idp_client(client_id, client_name, logo_uri, client_type, client_secret_hash, redirect_uris, allowed_scopes, first_party, active, access_token_ttl_seconds, refresh_token_ttl_seconds) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?) ON CONFLICT(client_id) DO UPDATE SET client_name=EXCLUDED.client_name, logo_uri=EXCLUDED.logo_uri, client_type=EXCLUDED.client_type, client_secret_hash=EXCLUDED.client_secret_hash, redirect_uris=EXCLUDED.redirect_uris, allowed_scopes=EXCLUDED.allowed_scopes, first_party=EXCLUDED.first_party, active=EXCLUDED.active, access_token_ttl_seconds=EXCLUDED.access_token_ttl_seconds, refresh_token_ttl_seconds=EXCLUDED.refresh_token_ttl_seconds",
            client.clientId, client.clientName, client.logoUri, client.type.name, client.secretHash, json(client.redirectUris), json(client.allowedScopes), client.firstParty, client.active, client.accessTokenTtlSeconds, client.refreshTokenTtlSeconds)
    }
    fun findClient(id: String): RegisteredClient? = queryOne("SELECT * FROM idp_client WHERE client_id = ?", RowMapper { rs, _ ->
        RegisteredClient(rs.getString("client_id"), rs.getString("client_name"), rs.getString("logo_uri"), ClientType.valueOf(rs.getString("client_type")), rs.getString("client_secret_hash"),
            readSet(rs.getString("redirect_uris")), readSet(rs.getString("allowed_scopes")), rs.getBoolean("first_party"), rs.getBoolean("active"), rs.getLong("access_token_ttl_seconds"), rs.getLong("refresh_token_ttl_seconds"))
    }, id)
    fun listClients(): List<RegisteredClient> = jdbc.query("SELECT * FROM idp_client ORDER BY created_at", RowMapper { rs, _ ->
        RegisteredClient(rs.getString("client_id"), rs.getString("client_name"), rs.getString("logo_uri"), ClientType.valueOf(rs.getString("client_type")), rs.getString("client_secret_hash"), readSet(rs.getString("redirect_uris")), readSet(rs.getString("allowed_scopes")), rs.getBoolean("first_party"), rs.getBoolean("active"), rs.getLong("access_token_ttl_seconds"), rs.getLong("refresh_token_ttl_seconds"))
    })
    fun updateClient(client: RegisteredClient) = jdbc.update("UPDATE idp_client SET client_name=?, logo_uri=?, redirect_uris=CAST(? AS jsonb), allowed_scopes=CAST(? AS jsonb), first_party=?, active=?, access_token_ttl_seconds=?, refresh_token_ttl_seconds=? WHERE client_id=?", client.clientName, client.logoUri, json(client.redirectUris), json(client.allowedScopes), client.firstParty, client.active, client.accessTokenTtlSeconds, client.refreshTokenTtlSeconds, client.clientId)
    fun rotateClientSecret(clientId: String, hash: String) = jdbc.update("UPDATE idp_client SET client_secret_hash=? WHERE client_id=? AND client_type='CONFIDENTIAL'", hash, clientId)
    fun consentedScopes(accountId: UUID, clientId: String): Set<String> = jdbc.queryForList("SELECT scopes FROM idp_consent WHERE account_id=? AND client_id=?", String::class.java, accountId, clientId).firstOrNull()?.let(::readSet) ?: emptySet()
    fun grantConsent(accountId: UUID, clientId: String, scopes: Set<String>) = jdbc.update("INSERT INTO idp_consent(account_id, client_id, scopes) VALUES (?, ?, CAST(? AS jsonb)) ON CONFLICT(account_id, client_id) DO UPDATE SET scopes=EXCLUDED.scopes, updated_at=CURRENT_TIMESTAMP", accountId, clientId, json(scopes))
    fun listConnectedApplications(accountId: UUID): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT c.client_id, c.client_name, c.logo_uri, c.first_party, c.active, consent.scopes, consent.updated_at " +
            "FROM idp_consent consent JOIN idp_client c ON c.client_id=consent.client_id " +
            "WHERE consent.account_id=? ORDER BY consent.updated_at DESC, c.client_name",
        accountId
    ).map { row ->
        mapOf(
            "client_id" to row["client_id"],
            "client_name" to row["client_name"],
            "logo_uri" to row["logo_uri"],
            "first_party" to row["first_party"],
            "active" to row["active"],
            "scopes" to readSet(row["scopes"].toString()).sorted(),
            "updated_at" to row["updated_at"]
        )
    }
    fun revokeConnectedApplication(accountId: UUID, clientId: String) {
        jdbc.update("DELETE FROM idp_consent WHERE account_id=? AND client_id=?", accountId, clientId)
        jdbc.update("UPDATE idp_access_token SET revoked_at=CURRENT_TIMESTAMP WHERE account_id=? AND client_id=? AND revoked_at IS NULL", accountId, clientId)
        jdbc.update("UPDATE idp_refresh_token SET revoked_at=CURRENT_TIMESTAMP WHERE account_id=? AND client_id=? AND revoked_at IS NULL", accountId, clientId)
        jdbc.update("UPDATE idp_authorization_code SET used_at=CURRENT_TIMESTAMP WHERE account_id=? AND client_id=? AND used_at IS NULL", accountId, clientId)
    }

    fun saveAuthorizationCode(hash: String, accountId: UUID, clientId: String, redirectUri: String, scopes: Set<String>, nonce: String?, challenge: String, profileId: UUID?, expiresAt: Instant) {
        jdbc.update("INSERT INTO idp_authorization_code(code_hash, account_id, client_id, redirect_uri, scopes, nonce, code_challenge, code_challenge_method, selected_profile_id, expires_at) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, 'S256', ?, ?)", hash, accountId, clientId, redirectUri, json(scopes), nonce, challenge, profileId, Timestamp.from(expiresAt))
    }
    fun consumeAuthorizationCode(hash: String): Map<String, Any?>? {
        val rows = jdbc.queryForList("UPDATE idp_authorization_code SET used_at = CURRENT_TIMESTAMP WHERE code_hash = ? AND used_at IS NULL AND expires_at > CURRENT_TIMESTAMP RETURNING *", hash)
        return rows.firstOrNull()
    }
    fun findUsableAuthorizationCode(hash: String): Map<String, Any?>? = jdbc.queryForList(
        "SELECT * FROM idp_authorization_code WHERE code_hash = ? AND used_at IS NULL AND expires_at > CURRENT_TIMESTAMP",
        hash
    ).firstOrNull()

    fun saveAccessToken(hash: String, accountId: UUID, clientId: String, scopes: Set<String>, profileId: UUID?, expiresAt: Instant) = jdbc.update(
        "INSERT INTO idp_access_token(token_hash, account_id, client_id, scopes, selected_profile_id, expires_at) VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)", hash, accountId, clientId, json(scopes), profileId, Timestamp.from(expiresAt))
    fun findActiveAccessToken(hash: String): Map<String, Any?>? = jdbc.queryForList("SELECT * FROM idp_access_token WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP", hash).firstOrNull()
    fun revokeAccessToken(hash: String) = jdbc.update("UPDATE idp_access_token SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ?", hash)

    fun saveRefreshToken(hash: String, familyId: UUID, parentHash: String?, accountId: UUID, clientId: String, scopes: Set<String>, profileId: UUID?, expiresAt: Instant) = jdbc.update(
        "INSERT INTO idp_refresh_token(token_hash, family_id, parent_hash, account_id, client_id, scopes, selected_profile_id, expires_at) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)", hash, familyId, parentHash, accountId, clientId, json(scopes), profileId, Timestamp.from(expiresAt))
    fun consumeRefreshToken(hash: String, clientId: String): Map<String, Any?>? = jdbc.queryForList(
        "UPDATE idp_refresh_token SET used_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND client_id = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP RETURNING *",
        hash, clientId
    ).firstOrNull()
    fun findRefreshToken(hash: String): Map<String, Any?>? = jdbc.queryForList("SELECT * FROM idp_refresh_token WHERE token_hash = ?", hash).firstOrNull()
    fun findRefreshTokenForClient(hash: String, clientId: String): Map<String, Any?>? = jdbc.queryForList(
        "SELECT * FROM idp_refresh_token WHERE token_hash = ? AND client_id = ?", hash, clientId
    ).firstOrNull()
    fun revokeRefreshFamily(family: UUID) = jdbc.update("UPDATE idp_refresh_token SET revoked_at = CURRENT_TIMESTAMP WHERE family_id = ? AND revoked_at IS NULL", family)
    fun revokeRefreshTokens(accountId: UUID) = jdbc.update("UPDATE idp_refresh_token SET revoked_at = CURRENT_TIMESTAMP WHERE account_id = ? AND revoked_at IS NULL", accountId)
    fun invalidateSessions(accountId: UUID) = jdbc.update("UPDATE account SET session_not_before=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE id=?", accountId)

    fun saveOneTimeToken(hash: String, accountId: UUID?, purpose: String, payload: Map<String, Any?>, expiresAt: Instant) = jdbc.update(
        "INSERT INTO account_one_time_token(token_hash, account_id, purpose, payload, expires_at) VALUES (?, ?, ?, CAST(? AS jsonb), ?)", hash, accountId, purpose, objectMapper.writeValueAsString(payload), Timestamp.from(expiresAt))
    fun consumeOneTimeToken(hash: String, purpose: String): Map<String, Any?>? = jdbc.queryForList("UPDATE account_one_time_token SET consumed_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND purpose = ? AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP RETURNING *", hash, purpose).firstOrNull()

    fun insertPasskey(accountId: UUID, identityId: Long, credentialId: String, publicKeyCose: String, deviceName: String) = jdbc.update(
        "INSERT INTO account_passkey(account_id, identity_id, credential_id, public_key_cose, device_name) VALUES (?, ?, ?, ?, ?)", accountId, identityId, credentialId, publicKeyCose, deviceName)
    fun listPasskeys(accountId: UUID): List<Map<String, Any?>> = jdbc.queryForList("SELECT * FROM account_passkey WHERE account_id = ? AND revoked_at IS NULL ORDER BY created_at", accountId)
    fun lockAccount(accountId: UUID): Account? = queryOne("SELECT * FROM account WHERE id=? FOR UPDATE", accountMapper, accountId)
    /**
     * A passkey has both a credential row and a login identity.  Revoking only
     * the former made the stale identity appear as an available login method.
     */
    fun revokePasskey(accountId: UUID, id: Long) = jdbc.update(
        "WITH revoked_passkey AS (" +
            "UPDATE account_passkey SET revoked_at = CURRENT_TIMESTAMP " +
            "WHERE id = ? AND account_id = ? AND revoked_at IS NULL RETURNING identity_id" +
        ") UPDATE account_identity SET revoked_at = CURRENT_TIMESTAMP " +
            "WHERE id IN (SELECT identity_id FROM revoked_passkey) AND revoked_at IS NULL",
        id, accountId
    )
    fun findPasskeyByCredential(credentialId: String): Map<String, Any?>? = jdbc.queryForList("SELECT * FROM account_passkey WHERE credential_id=? AND revoked_at IS NULL", credentialId).firstOrNull()
    fun updatePasskeyCounter(id: Long, counter: Long) = jdbc.update("UPDATE account_passkey SET sign_count=?, last_used_at=CURRENT_TIMESTAMP WHERE id=?", counter, id)

    fun upsertTotp(accountId: UUID, encryptedSecret: String, displayName: String) = jdbc.update("INSERT INTO account_totp(account_id, secret_encrypted, display_name) VALUES (?, ?, ?) ON CONFLICT(account_id) DO UPDATE SET secret_encrypted=EXCLUDED.secret_encrypted, display_name=EXCLUDED.display_name, confirmed_at=NULL, revoked_at=NULL", accountId, encryptedSecret, displayName)
    fun findTotp(accountId: UUID): Map<String, Any?>? = jdbc.queryForList("SELECT * FROM account_totp WHERE account_id = ? AND revoked_at IS NULL", accountId).firstOrNull()
    fun confirmTotp(accountId: UUID) = jdbc.update("UPDATE account_totp SET confirmed_at=CURRENT_TIMESTAMP WHERE account_id=? AND revoked_at IS NULL", accountId)
    fun renameTotp(accountId: UUID, name: String) = jdbc.update("UPDATE account_totp SET display_name=? WHERE account_id=? AND revoked_at IS NULL", name, accountId)
    fun revokeTotp(accountId: UUID) = jdbc.update("UPDATE account_totp SET revoked_at=CURRENT_TIMESTAMP WHERE account_id=? AND revoked_at IS NULL", accountId)

    fun revokeRecoveryCodes(accountId: UUID) = jdbc.update("UPDATE account_recovery_code SET revoked_at=CURRENT_TIMESTAMP WHERE account_id=? AND revoked_at IS NULL", accountId)
    fun insertRecoveryCode(accountId: UUID, hash: String) = jdbc.update("INSERT INTO account_recovery_code(account_id, code_hash) VALUES (?, ?)", accountId, hash)
    fun consumeRecoveryCodeByHash(hash: String): UUID? = jdbc.queryForList(
        "UPDATE account_recovery_code SET used_at=CURRENT_TIMESTAMP WHERE code_hash=? AND used_at IS NULL AND revoked_at IS NULL RETURNING account_id",
        hash
    ).firstOrNull()?.get("account_id")?.toString()?.let(UUID::fromString)
    fun activeRecoveryCodeCount(accountId: UUID): Int = jdbc.queryForObject("SELECT count(*) FROM account_recovery_code WHERE account_id=? AND used_at IS NULL AND revoked_at IS NULL", Int::class.java, accountId) ?: 0

    fun upsertSupportPin(accountId: UUID, hash: String) = jdbc.update("INSERT INTO account_support_pin(account_id, pin_hash) VALUES (?, ?) ON CONFLICT(account_id) DO UPDATE SET pin_hash=EXCLUDED.pin_hash, issued_at=CURRENT_TIMESTAMP, revoked_at=NULL", accountId, hash)
    fun supportPinIssuedAt(accountId: UUID): Instant? = jdbc.queryForList("SELECT issued_at FROM account_support_pin WHERE account_id=? AND revoked_at IS NULL", accountId).firstOrNull()?.get("issued_at")?.let { (it as java.sql.Timestamp).toInstant() }
    fun supportPinHash(accountId: UUID): String? = jdbc.queryForList("SELECT pin_hash FROM account_support_pin WHERE account_id=? AND revoked_at IS NULL", accountId).firstOrNull()?.get("pin_hash")?.toString()

    fun saveDiscordLinkRequest(hash: String, discordSubject: String, legacyUserId: String, client: String, expiresAt: Instant) = jdbc.update("INSERT INTO discord_link_request(nonce_hash, discord_subject, legacy_user_id, request_client, expires_at) VALUES (?, ?, ?, ?, ?)", hash, discordSubject, legacyUserId, client, Timestamp.from(expiresAt))
    fun findDiscordLinkRequest(hash: String): Map<String, Any?>? = jdbc.queryForList("SELECT * FROM discord_link_request WHERE nonce_hash=?", hash).firstOrNull()
    fun completeDiscordLinkRequest(hash: String, accountId: UUID, status: String) = jdbc.update("UPDATE discord_link_request SET status=?, account_id=?, completed_at=CURRENT_TIMESTAMP WHERE nonce_hash=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP", status, accountId, hash)
    fun expireDiscordRequests() = jdbc.update("UPDATE discord_link_request SET status='EXPIRED' WHERE status='PENDING' AND expires_at<=CURRENT_TIMESTAMP")
    fun attendance(accountId: UUID, date: java.time.LocalDate): Map<String, Any?>? = jdbc.queryForList("SELECT * FROM discord_attendance WHERE account_id=? AND attendance_date=?", accountId, date).firstOrNull()
    fun previousAttendance(accountId: UUID, date: java.time.LocalDate): Map<String, Any?>? = jdbc.queryForList("SELECT * FROM discord_attendance WHERE account_id=? AND attendance_date=?", accountId, date.minusDays(1)).firstOrNull()
    fun insertAttendance(accountId: UUID, date: java.time.LocalDate, streak: Int) = jdbc.update("INSERT INTO discord_attendance(account_id, attendance_date, streak_days) VALUES (?, ?, ?)", accountId, date, streak)
    /** Returns true once per event, and also resumes a previously pending reward after a delivery failure. */
    fun reserveRewardEvent(key: String, accountId: UUID, reward: String): Boolean = jdbc.queryForList(
        "INSERT INTO discord_reward_event(event_key, account_id, reward_key) VALUES (?, ?, ?) " +
            "ON CONFLICT(event_key) DO UPDATE SET status='PENDING' WHERE discord_reward_event.status='PENDING' " +
            "RETURNING event_key",
        key, accountId, reward
    ).isNotEmpty()
    fun completeRewardEvent(key: String) = jdbc.update("UPDATE discord_reward_event SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE event_key=?", key)

    fun countActiveLoginMethods(accountId: UUID, includePassword: Boolean = true): Int = jdbc.queryForObject(
        "SELECT count(*) FROM account_identity i WHERE i.account_id = ? AND i.revoked_at IS NULL " +
            "AND i.type <> 'EMAIL' " +
            "AND (? OR i.type <> 'PASSWORD') " +
            "AND (i.type <> 'PASSKEY' OR EXISTS (SELECT 1 FROM account_passkey p WHERE p.identity_id = i.id AND p.revoked_at IS NULL))",
        Int::class.java, accountId, includePassword
    ) ?: 0

    private fun json(value: Any): String = objectMapper.writeValueAsString(value)
    private fun readSet(value: String): Set<String> = objectMapper.readValue(value, object : TypeReference<Set<String>>() {})
    private fun <T> queryOne(sql: String, mapper: RowMapper<T>, vararg args: Any): T? = jdbc.query(sql, mapper, *args).firstOrNull()
    private fun ResultSet.getLongOrNull(column: String): Long? = getLong(column).let { if (wasNull()) null else it }
}
