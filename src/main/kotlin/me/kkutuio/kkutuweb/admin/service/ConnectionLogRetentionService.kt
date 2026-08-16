package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.ZonedDateTime

@Service
class ConnectionLogRetentionService(
    private val jdbcTemplate: JdbcTemplate,
    private val setting: KKuTuSetting
) {
    private val logger = LoggerFactory.getLogger(ConnectionLogRetentionService::class.java)

    @Scheduled(initialDelay = 300_000, fixedDelay = 3_600_000)
    fun purgeExpiredConnectionLogs() {
        val retention = setting.getConnectionLogRetention()
        if (!retention.enabled) return

        val cutoff = Timestamp.from(ZonedDateTime.now().minusMonths(retention.months).toInstant())
        var deletedTotal = 0
        for (batch in 0 until retention.maxBatchesPerRun) {
            val deleted = jdbcTemplate.update(
                """
                WITH expired AS (
                    SELECT id
                    FROM connection_log
                    WHERE time < ?
                    ORDER BY time, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM connection_log logs
                USING expired
                WHERE logs.id = expired.id
                """.trimIndent(),
                cutoff,
                retention.batchSize
            )
            deletedTotal += deleted
            if (deleted < retention.batchSize) break
        }
        if (deletedTotal > 0) {
            logger.info("${retention.months}개월 보존 기간이 지난 접속 기록 ${deletedTotal}건을 삭제했습니다.")
        }
    }
}
