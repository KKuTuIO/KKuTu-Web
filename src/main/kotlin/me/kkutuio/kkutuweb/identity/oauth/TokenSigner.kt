package me.kkutuio.kkutuweb.identity.oauth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import me.kkutuio.kkutuweb.identity.IdentityProviderSettings
import me.kkutuio.kkutuweb.identity.SecretCipher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/** Persists the current private key and retains retired public keys for JWKS grace. */
@Component
class TokenSigner(
    private val settings: IdentityProviderSettings,
    private val cipher: SecretCipher,
    private val jdbc: JdbcTemplate
) {
    private val lock = Any()

    fun sign(subject: String, audience: String, claims: Map<String, Any?>, expiresAt: Instant): String {
        val key = activeKey()
        val builder = JWTClaimsSet.Builder()
            .issuer(settings.normalizedIssuer).subject(subject).audience(audience)
            .issueTime(Date()).expirationTime(Date.from(expiresAt))
        claims.filterValues { it != null }.forEach { (name, value) -> builder.claim(name, value) }
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), builder.build())
        jwt.sign(RSASSASigner(key.toPrivateKey()))
        return jwt.serialize()
    }

    fun jwks(): Map<String, Any> = mapOf("keys" to readableKeys().map { it.toPublicJWK().toJSONObject() })

    private fun activeKey(): RSAKey = synchronized(lock) {
        val now = Instant.now()
        jdbc.update("DELETE FROM idp_signing_key WHERE status='RETIRED' AND expires_at <= ?", now)
        val current = readKeys("status='ACTIVE'").firstOrNull()
        if (current != null && current.createdAt.plus(settings.signingKeyRotationDays.coerceAtLeast(1), ChronoUnit.DAYS).isAfter(now)) {
            return@synchronized current.key
        }
        if (current != null) {
            jdbc.update(
                "UPDATE idp_signing_key SET status='RETIRED', retired_at=?, expires_at=? WHERE kid=? AND status='ACTIVE'",
                now, now.plus(settings.signingKeyGraceDays.coerceAtLeast(1), ChronoUnit.DAYS), current.key.keyID
            )
        }
        val generated = RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate()
        try {
            jdbc.update(
                "INSERT INTO idp_signing_key(kid, private_jwk_encrypted, status) VALUES (?, ?, 'ACTIVE')",
                generated.keyID, cipher.encrypt(generated.toJSONString())
            )
            generated
        } catch (_: Exception) {
            // Another application node may have won the unique-active-key race.
            readKeys("status='ACTIVE'").firstOrNull()?.key ?: throw IllegalStateException("OIDC signing key를 만들 수 없습니다.")
        }
    }

    private fun readableKeys(): List<RSAKey> = synchronized(lock) {
        activeKey()
        readKeys("status='ACTIVE' OR (status='RETIRED' AND expires_at > CURRENT_TIMESTAMP)").map { it.key }
    }

    private data class StoredKey(val key: RSAKey, val createdAt: Instant)

    private fun readKeys(where: String): List<StoredKey> = jdbc.query(
        "SELECT private_jwk_encrypted, created_at FROM idp_signing_key WHERE $where ORDER BY created_at DESC"
    ) { rs, _ ->
        StoredKey(RSAKey.parse(cipher.decrypt(rs.getString("private_jwk_encrypted"))), rs.getTimestamp("created_at").toInstant())
    }
}
