package me.kkutuio.kkutuweb.admin.dao

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class TableStatisticsDAO(
    private val jdbcTemplate: JdbcTemplate
) {
    fun getEstimatedRowCount(tableName: String): Int {
        require(TABLE_NAME.matches(tableName)) { "올바르지 않은 테이블 이름입니다." }
        val estimate = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE((
                SELECT GREATEST(reltuples, 0)::BIGINT
                FROM pg_class
                WHERE oid = to_regclass(?)
            ), 0)
            """.trimIndent(),
            Long::class.java,
            tableName
        )
        return estimate.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    companion object {
        private val TABLE_NAME = Regex("^[a-z][a-z0-9_]*$")
    }
}
