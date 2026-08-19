package me.kkutuio.kkutuweb.identity

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class AccountRateLimiter(private val redis: StringRedisTemplate) {
    private val script = DefaultRedisScript<Long>().apply {
        setScriptText("local value=redis.call('INCR', KEYS[1]); if value == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; return value")
        setResultType(Long::class.java)
    }
    fun check(key: String, maximum: Int, windowSeconds: Long) {
        val count = try { redis.execute(script, listOf("idp:rate:" + SecretTools.sha256(key)), windowSeconds.toString()) }
        catch (_: Exception) { throw IdpException("temporarily_unavailable", "보안 제한 저장소를 사용할 수 없습니다.", 503) }
            ?: throw IdpException("temporarily_unavailable", "보안 제한 저장소를 사용할 수 없습니다.", 503)
        if (count > maximum) throw IdpException("rate_limited", "잠시 후 다시 시도해 주세요.", 429)
    }
}

@Component
class RecaptchaVerifier(private val settings: IdentityProviderSettings) {
    private val rest = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3_000)
        setReadTimeout(5_000)
    })

    fun verify(token: String?, remoteIp: String, expectedAction: String) {
        if (settings.recaptchaSecret.isBlank()) throw IdpException("temporarily_unavailable", "reCAPTCHA 설정이 필요합니다.", 503)
        if (token.isNullOrBlank()) throw IdpException("invalid_captcha", "reCAPTCHA 검증이 필요합니다.")
        val form = "secret=${encode(settings.recaptchaSecret)}&response=${encode(token)}&remoteip=${encode(remoteIp)}"
        val response = try {
            rest.postForObject("https://www.google.com/recaptcha/api/siteverify", HttpEntity(form, HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }), JsonNode::class.java)
        } catch (_: Exception) {
            throw IdpException("temporarily_unavailable", "reCAPTCHA 검증 서비스를 사용할 수 없습니다.", 503)
        }
        val hostname = response?.path("hostname")?.asText()?.lowercase()
        if (response?.path("success")?.asBoolean() != true ||
            response.path("action").asText() != expectedAction ||
            hostname !in settings.recaptchaExpectedHostnames ||
            response.path("score").asDouble(0.0) < settings.recaptchaMinimumScore
        ) throw IdpException("invalid_captcha", "reCAPTCHA 검증에 실패했습니다.")
    }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
