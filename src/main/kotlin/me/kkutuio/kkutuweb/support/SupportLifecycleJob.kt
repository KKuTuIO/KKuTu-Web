package me.kkutuio.kkutuweb.support

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/** 오래된 문의의 자동 해결·종료와 보유 기간이 지난 기록 삭제. */
@Component
class SupportLifecycleJob(private val dao: SupportDao, private val service: SupportService) {
    private val logger = LoggerFactory.getLogger(SupportLifecycleJob::class.java)

    @Scheduled(initialDelay = 600_000, fixedDelay = 3_600_000)
    fun run() {
        try {
            val now = Instant.now()
            val answeredCutoff = now.minus(SupportPolicy.ANSWERED_IDLE)
            val resolved = dao.answeredIdleSince(answeredCutoff, BATCH).count { service.resolveIdle(it, answeredCutoff) }
            val resolvedCutoff = now.minus(SupportPolicy.REOPEN_WINDOW)
            val closed = dao.resolvedSince(resolvedCutoff, BATCH).count { service.closeResolved(it, resolvedCutoff) } +
                dao.openOfDeletedAccounts(BATCH).count { service.closeAbandoned(it) }
            val purged = dao.purgeClosedBefore(SupportPolicy.RETENTION_YEARS)
            if (resolved + closed + purged > 0) logger.info("문의 정리: 자동 해결 {}, 종료 {}, 삭제 {}", resolved, closed, purged)
        } catch (error: Exception) {
            logger.warn("문의 정리 작업을 마치지 못했습니다.", error)
        }
    }

    private companion object {
        const val BATCH = 500
    }
}
