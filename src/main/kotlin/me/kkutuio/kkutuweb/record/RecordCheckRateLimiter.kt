package me.kkutuio.kkutuweb.record

import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.login.LoginService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@Component
class RecordCheckRateLimiter(
    @Autowired private val redisTemplate: RedisTemplate<String, Any>,
    @Autowired private val loginService: LoginService
) {
    companion object {
        private const val KEY_PREFIX = "kkutu:web:record:checks:ratelimit"
        private const val PER_MINUTE_LIMIT = 20L
        private const val PER_FIVE_MINUTE_LIMIT = 60L
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
