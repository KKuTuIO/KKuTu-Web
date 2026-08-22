/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package me.kkutuio.kkutuweb.session

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import com.fasterxml.jackson.databind.JsonNode
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

@Component
class GameSessionTicketStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val issued = AtomicLong()
    private val redisErrors = AtomicLong()

    fun issue(
        ticket: String,
        profile: SessionProfile?,
        serverId: String,
        ttlMillis: Long = 5 * 60 * 1000L,
        accountUuid: String? = null,
        accountFlags: JsonNode? = null
    ) {
        require(ttlMillis in 1..(10 * 60 * 1000L)) { "invalid game ticket TTL" }
        val key = KEY_PREFIX + sha256(ticket)
        val profileJson = objectMapper.writeValueAsString(profile)
        val accountUuidValue = accountUuid.orEmpty()
        val accountFlagsJson = objectMapper.writeValueAsString(accountFlags ?: objectMapper.createObjectNode())
        try {
            val result = redisTemplate.execute(
                ISSUE_SCRIPT,
                listOf(key),
                serverId,
                profileJson,
                accountUuidValue,
                accountFlagsJson,
                ttlMillis.toString()
            )
            check(result == 1L) { "game session ticket collision" }
            issued.incrementAndGet()
        } catch (error: RuntimeException) {
            redisErrors.incrementAndGet()
            throw error
        }
    }

    @Scheduled(fixedDelay = 600000)
    fun reportMetrics() {
        logger.info(
            "AC-Lite ticket metrics issued={} redisErrors={}",
            issued.getAndSet(0),
            redisErrors.getAndSet(0)
        )
    }

    companion object {
        private const val KEY_PREFIX = "ac-lite:ticket:"
        private val logger = LoggerFactory.getLogger(GameSessionTicketStore::class.java)
        private val ISSUE_SCRIPT = DefaultRedisScript(
            """
            if redis.call("EXISTS", KEYS[1]) == 1 then
                return 0
            end
            redis.call("HSET", KEYS[1], "server_id", ARGV[1], "profile", ARGV[2], "account_uuid", ARGV[3], "account_flags", ARGV[4])
            redis.call("PEXPIRE", KEYS[1], ARGV[5])
            return 1
            """.trimIndent(),
            Long::class.java
        )

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
