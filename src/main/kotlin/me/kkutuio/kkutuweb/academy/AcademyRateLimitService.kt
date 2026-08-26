/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package me.kkutuio.kkutuweb.academy

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalDate

@Service
class AcademyRateLimitService(private val redis: StringRedisTemplate) {
    private val logger = LoggerFactory.getLogger(AcademyRateLimitService::class.java)
    private val incrementScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            "local value=redis.call('INCR',KEYS[1]); " +
                "if value == 1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return value"
        )
        setResultType(Long::class.java)
    }

    fun allowPublic(scope: String, identity: String, maximum: Int, windowSeconds: Long): Boolean =
        try {
            increment("public:$scope", identity, windowSeconds) <= maximum
        } catch (error: Exception) {
            logger.warn("단어 아카데미 공개 요청 제한 저장소를 사용할 수 없어 요청을 허용합니다.", error)
            true
        }

    /** Restricted lookups fail closed when Redis is unavailable. */
    fun consumeRestricted(accountUuid: String, ip: String, dailyMaximum: Int): Int? = try {
        val day = LocalDate.now().toString()
        val accountCount = increment("restricted:account:$day", accountUuid, 48 * 60 * 60L)
        val ipCount = increment("restricted:ip:$day", ip, 48 * 60 * 60L)
        if (accountCount > dailyMaximum || ipCount > dailyMaximum * 3L) null
        else (dailyMaximum - accountCount.toInt()).coerceAtLeast(0)
    } catch (error: Exception) {
        logger.error("어인정 제한 조회 저장소를 사용할 수 없어 요청을 차단합니다.", error)
        null
    }

    private fun increment(scope: String, identity: String, windowSeconds: Long): Long {
        val key = "academy:rate:$scope:${sha256(identity)}"
        return redis.execute(incrementScript, listOf(key), windowSeconds.toString())
            ?: throw IllegalStateException("rate-limit counter unavailable")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
