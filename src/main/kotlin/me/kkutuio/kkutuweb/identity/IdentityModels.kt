package me.kkutuio.kkutuweb.identity

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import java.time.Instant
import java.util.UUID

enum class AccountStatus { ACTIVE, LOCKED, DELETED, PROVISIONED }
enum class IdentityType { PASSWORD, EMAIL, PASSKEY, OAUTH }
enum class ClientType { CONFIDENTIAL, PUBLIC }

data class Account(
    val id: UUID,
    val uuid: UUID,
    val legacyUserId: String,
    val status: AccountStatus,
    val externalMfaEnabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val sessionNotBefore: Instant,
    val primaryIdentityId: Long?,
    val originIdentityId: Long?,
    val flags: JsonNode = JsonNodeFactory.instance.objectNode(),
    val deletionRequestedAt: Instant? = null,
    val deletionScheduledAt: Instant? = null,
    val moderationSubjectUuid: UUID? = null
)

data class AccountIdentity(
    val id: Long,
    val accountId: UUID,
    val type: IdentityType,
    val provider: String,
    val subject: String,
    val displayName: String?,
    val credentialHash: String?,
    val verifiedAt: Instant?,
    val primary: Boolean,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?
)

data class RegisteredClient(
    val clientId: String,
    val clientName: String,
    val logoUri: String?,
    val type: ClientType,
    val secretHash: String?,
    val redirectUris: Set<String>,
    val allowedScopes: Set<String>,
    val firstParty: Boolean,
    val active: Boolean,
    val accessTokenTtlSeconds: Long,
    val refreshTokenTtlSeconds: Long
)

class IdpException(val error: String, message: String, val status: Int = 400) : RuntimeException(message)

data class TokenSet(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val scope: String,
    val idToken: String? = null,
    val refreshToken: String? = null
)

/** A verified bearer token bound to an active KKuTu account. */
data class AccessTokenPrincipal(
    val account: Account,
    val clientId: String,
    val scopes: Set<String>
)
