package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.setting.ConnectionLogRetentionSetting
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
        purgeLog("connection_log", "id", "접속", setting.getConnectionLogRetention())
        purgeLog("suspicion_log", "case_id", "의심 기록", setting.getSuspicionLogRetention())
        purgeLog("report_log", "report_id", "신고 기록", setting.getReportLogRetention())
    }

    private fun purgeLog(
        tableName: String,
        idColumn: String,
        logDescription: String,
        retention: ConnectionLogRetentionSetting
    ) {
        if (!retention.enabled) return

        val cutoff = Timestamp.from(ZonedDateTime.now().minusMonths(retention.months).toInstant())
        var deletedTotal = 0
        for (batch in 0 until retention.maxBatchesPerRun) {
            val deleted = jdbcTemplate.update(
                """
                WITH expired AS (
                    SELECT $idColumn
                    FROM $tableName
                    WHERE time < ?
                    ORDER BY time, $idColumn
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM $tableName logs
                USING expired
                WHERE logs.$idColumn = expired.$idColumn
                """.trimIndent(),
                cutoff,
                retention.batchSize
            )
            deletedTotal += deleted
            if (deleted < retention.batchSize) break
        }
        if (deletedTotal > 0) {
            logger.info("${retention.months}개월 보존 기간이 지난 ${logDescription} ${deletedTotal}건을 삭제했습니다.")
        }
    }
}
