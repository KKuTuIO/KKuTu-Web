package me.kkutuio.kkutuweb.security

import com.github.benmanes.caffeine.cache.Caffeine
import me.kkutuio.kkutuweb.setting.GameServerSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

@RestController
class SecurityFontApi(
    private val kKuTuSetting: KKuTuSetting
) {
    private data class UpstreamResult(val status: Int, val body: ByteArray?)

    private val logger = LoggerFactory.getLogger(SecurityFontApi::class.java)

    private val fontCache = Caffeine.newBuilder()
        .maximumSize(8)
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .build<Long, ByteArray>()

    private val inFlight = ConcurrentHashMap<Long, CompletableFuture<UpstreamResult>>()

    @GetMapping("/api/security/obfuscated_font.woff2")
    fun getObfuscatedFont(
        @RequestParam(required = false) slot: Long?,
        @RequestParam(required = false, defaultValue = "0") server: Int
    ): ResponseEntity<ByteArray> {
        if (slot != null) {
            fontCache.getIfPresent(slot)?.let { cached ->
                return okFontResponse(cached)
            }
        }

        val gameServers = kKuTuSetting.getGameServers()
        if (gameServers.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        if (slot == null) {
            return toResponseEntity(fetchFromGameServers(gameServers, server, null))
        }

        val created = CompletableFuture<UpstreamResult>()
        val existing = inFlight.putIfAbsent(slot, created)
        if (existing != null) {
            return try {
                toResponseEntity(existing.get(6, TimeUnit.SECONDS))
            } catch (e: Exception) {
                logger.warn("Waiting for in-flight obfuscated font failed for slot={}: {}", slot, e.message)
                ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
            }
        }

        return try {
            val result = fetchFromGameServers(gameServers, server, slot)
            if (result.status == 200 && result.body != null && result.body.isNotEmpty()) {
                fontCache.put(slot, result.body)
            }
            created.complete(result)
            toResponseEntity(result)
        } catch (e: Exception) {
            created.completeExceptionally(e)
            logger.warn("Unexpected error fetching obfuscated font for slot={}: {}", slot, e.message)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
        } finally {
            inFlight.remove(slot, created)
        }
    }

    private fun fetchFromGameServers(
        gameServers: List<GameServerSetting>,
        preferredIndex: Int,
        slot: Long?
    ): UpstreamResult {
        val candidates = buildList {
            gameServers.getOrNull(preferredIndex)?.let(::add)
            for (gs in gameServers) {
                if (gs !in this) add(gs)
            }
        }

        for (gameServer in candidates) {
            val result = fetchOnce(gameServer, slot)
            if (result.status == 200 || result.status == 400 || result.status == 410) {
                return result
            }
        }
        return UpstreamResult(HttpStatus.BAD_GATEWAY.value(), null)
    }

    private fun fetchOnce(gameServer: GameServerSetting, slot: Long?): UpstreamResult {
        val scheme = if (gameServer.isSecure) "https" else "http"
        val query = if (slot != null) "?slot=$slot" else ""
        val targetUri = URI.create("$scheme://${gameServer.host}:${gameServer.port}/internal/security/obfuscated_font.woff2$query")
        var connection: HttpURLConnection? = null
        return try {
            connection = (targetUri.toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("x-web-key", gameServer.key)
                if (this is HttpsURLConnection) {
                    val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
                    setHostnameVerifier { hostname, session ->
                        defaultVerifier.verify(hostname, session) ||
                            defaultVerifier.verify(gameServer.publicHost, session)
                    }
                }
            }

            val status = connection.responseCode
            val body = if (status == 200) connection.inputStream.use { it.readBytes() } else null
            if (status == 200 && (body == null || body.isEmpty())) {
                UpstreamResult(HttpStatus.BAD_GATEWAY.value(), null)
            } else {
                if (status != 200 && status != 400 && status != 410) {
                    logger.warn("Obfuscated font upstream returned HTTP {} from {}", status, targetUri)
                }
                UpstreamResult(status, body)
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch obfuscated font from {}: {}", targetUri, e.message)
            UpstreamResult(HttpStatus.BAD_GATEWAY.value(), null)
        } finally {
            connection?.disconnect()
        }
    }

    private fun toResponseEntity(result: UpstreamResult): ResponseEntity<ByteArray> = when (result.status) {
        200 -> okFontResponse(result.body!!)
        400 -> ResponseEntity.badRequest().build()
        410 -> ResponseEntity.status(HttpStatus.GONE).build()
        else -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
    }

    private fun okFontResponse(bytes: ByteArray): ResponseEntity<ByteArray> {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "font/woff2")
            .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
            .body(bytes)
    }
}