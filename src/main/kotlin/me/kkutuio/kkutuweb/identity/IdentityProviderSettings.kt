package me.kkutuio.kkutuweb.identity

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Settings are deliberately kept outside the game OAuth settings: this server is the issuer. */
@Component
class IdentityProviderSettings(
    @Value("\${idp.issuer:https://kkutu.io}") val issuer: String,
    @Value("\${idp.rp-id:kkutu.io}") val rpId: String,
    @Value("\${idp.allowed-origins:https://kkutu.io}") allowedOrigins: String,
    @Value("\${idp.internal-api-key:}") val internalApiKey: String,
    @Value("\${idp.recaptcha.site-key:}") val recaptchaSiteKey: String,
    @Value("\${idp.recaptcha.secret:}") val recaptchaSecret: String,
    @Value("\${idp.recaptcha.minimum-score:0.5}") val recaptchaMinimumScore: Double,
    @Value("\${idp.recaptcha.expected-hostnames:kkutu.io}") recaptchaExpectedHostnames: String,
    @Value("\${idp.password.enabled:true}") val passwordEnabled: Boolean,
    @Value("\${idp.token.authorization-code-ttl-seconds:300}") val authorizationCodeTtlSeconds: Long,
    @Value("\${idp.token.access-token-ttl-seconds:3600}") val accessTokenTtlSeconds: Long,
    @Value("\${idp.token.refresh-token-ttl-seconds:2592000}") val refreshTokenTtlSeconds: Long,
    @Value("\${idp.signing-key.rotation-days:90}") val signingKeyRotationDays: Long,
    @Value("\${idp.signing-key.previous-key-grace-days:30}") val signingKeyGraceDays: Long,
    @Value("\${idp.mail.from:no-reply@kkutu.io}") val mailFrom: String
) {
    val allowedOrigins = allowedOrigins.split(',').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }.toSet()
    val recaptchaExpectedHostnames = recaptchaExpectedHostnames.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    val normalizedIssuer = issuer.trim().trimEnd('/')
}
