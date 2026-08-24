package me.kkutuio.kkutuweb.record

import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.login.LoginService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession

@Component
class RecordCheckRateLimiter(
    @Autowired private val redisTemplate: RedisTemplate<String, Any>,
    @Autowired private val loginService: LoginService
) {
    companion object {
        private const val KEY_PREFIX = "kkutu:web:record:checks:ratelimit"
        private const val PER_MINUTE_LIMIT = 20L
        private const val PER_FIVE_MINUTE_LIMIT = 60L
        private const val GUEST_PAYLOAD_DAILY_LIMIT = 10L
        private const val MEMBER_PAYLOAD_HOURLY_LIMIT = 60L
        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")
    }

    fun allow(request: HttpServletRequest, session: HttpSession): Boolean {
        val requester = resolveRequesterKey(request, session)
        val nowSeconds = System.currentTimeMillis() / 1000
        val minuteBucket = nowSeconds / 60
        val fiveMinuteBucket = nowSeconds / 300

        if (!consume("$KEY_PREFIX:1m:$requester:$minuteBucket", PER_MINUTE_LIMIT, Duration.ofMinutes(2))) {
            return false
        }
        if (!consume("$KEY_PREFIX:5m:$requester:$fiveMinuteBucket", PER_FIVE_MINUTE_LIMIT, Duration.ofMinutes(7))) {
            return false
        }
        return true
    }

    fun allowReplayPayloadDownload(request: HttpServletRequest, session: HttpSession): Boolean {
        val now = ZonedDateTime.now(KOREA_ZONE)
        val userId = if (!session.isGuest()) loginService.gameUserId(session).orEmpty() else ""
        if (userId.isNotBlank()) {
            val bucket = now.toEpochSecond() / 3600
            return consume(
                "$KEY_PREFIX:payload:member:$userId:$bucket",
                MEMBER_PAYLOAD_HOURLY_LIMIT,
                Duration.ofHours(2)
            )
        }

        val ip = request.getIp().ifBlank { "unknown" }
        val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(KOREA_ZONE)
        val ttl = Duration.between(now, nextDay).plusMinutes(5)
        return consume(
            "$KEY_PREFIX:payload:guest:$ip:${now.toLocalDate()}",
            GUEST_PAYLOAD_DAILY_LIMIT,
            ttl
        )
    }

    private fun resolveRequesterKey(request: HttpServletRequest, session: HttpSession): String {
        val userId = if (!session.isGuest()) loginService.gameUserId(session).orEmpty() else ""

        if (userId.isNotBlank()) return "uid:$userId"
        val ip = request.getIp().ifBlank { "unknown" }
        return "ip:$ip"
    }

    private fun consume(key: String, limit: Long, ttl: Duration): Boolean {
        val current = redisTemplate.opsForValue().increment(key) ?: Long.MAX_VALUE
        if (current == 1L) redisTemplate.expire(key, ttl)
        return current <= limit
    }
}
