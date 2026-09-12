package me.kkutuio.kkutuweb.servermanagement

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

fun interface ApplicationTerminator {
    fun terminate()
}

@Component
class SpringApplicationTerminator(
    private val context: ConfigurableApplicationContext
) : ApplicationTerminator {
    override fun terminate() {
        exitProcess(SpringApplication.exit(context))
    }
}

data class WebRestartResponse(
    val accepted: Boolean,
    val shutdownDelayMillis: Long
)

@Service
class WebServerRestartService(
    private val terminator: ApplicationTerminator,
    @Value("\${server-management.web-restart-delay-millis:1500}") configuredDelayMillis: Long
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val restartScheduled = AtomicBoolean(false)
    private val shutdownDelayMillis = configuredDelayMillis.coerceIn(500L, 10_000L)

    fun schedule(actorId: String): WebRestartResponse {
        if (!restartScheduled.compareAndSet(false, true)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Web 서버 재시작이 이미 예약되어 있습니다.")
        }
        logger.warn("web-server-restart scheduled actor={} delayMillis={}", actorId, shutdownDelayMillis)
        Thread.ofPlatform().name("web-server-restart").daemon(false).start {
            try {
                Thread.sleep(shutdownDelayMillis)
                logger.warn("web-server-restart terminating current JVM actor={}", actorId)
                terminator.terminate()
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.error("web-server-restart interrupted actor={}", actorId, error)
            } catch (error: Exception) {
                logger.error("web-server-restart failed actor={}", actorId, error)
            } finally {
                restartScheduled.set(false)
            }
        }
        return WebRestartResponse(true, shutdownDelayMillis)
    }
}
