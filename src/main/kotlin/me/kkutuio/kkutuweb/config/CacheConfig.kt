package me.kkutuio.kkutuweb.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.cache.support.CompositeCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class CacheConfig {
    companion object {
        const val RECORD_USER_INFO_CACHE = "recordUserInfo10m"
        const val RECORD_USER_HISTORY_CACHE = "recordUserHistory10m"
    }

    @Bean
    fun cacheManager(): CacheManager {
        val tenMinuteCache = CaffeineCacheManager(
            RECORD_USER_INFO_CACHE,
            RECORD_USER_HISTORY_CACHE
        ).apply {
            setCaffeine(
                Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofMinutes(10))
                    .maximumSize(20_000)
            )
            isAllowNullValues = false
        }

        val defaultCache = ConcurrentMapCacheManager().apply {
            isAllowNullValues = false
        }

        return CompositeCacheManager(tenMinuteCache, defaultCache).apply {
            setFallbackToNoOpCache(false)
        }
    }
}
