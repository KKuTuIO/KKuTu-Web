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

package me.kkutuio.kkutuweb.admin.dao

import me.kkutuio.kkutuweb.admin.SortType
import me.kkutuio.kkutuweb.admin.domain.WordAuditLog
import me.kkutuio.kkutuweb.admin.mapper.WordAuditLogMapper
import me.kkutuio.kkutuweb.extension.toTimestamp
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.time.LocalDateTime

@Repository
class WordAuditLogDAO(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val wordAuditLogMapper: WordAuditLogMapper
) {
    fun getDataCount(
        lang: String,
        searchFilters: Map<String, String>
    ): Int {
        val filter = filterQuery(searchFilters)
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kkutu_${lang}_audit_log ${filter.sql}",
            Long::class.java,
            *filter.values.toTypedArray()
        )
        return (count ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun getPageData(
        lang: String,
        page: Int,
        pageSize: Int,
        sortField: String,
        sortType: SortType,
        searchFilters: Map<String, String>
    ): List<WordAuditLog> {
        val filter = filterQuery(searchFilters)
        val stableOrder = if (sortField == "id") "" else ", id ${sortType.name}"
        val sql = """
            SELECT id, log_time, log_type, word, old_type, old_mean, old_flag, old_theme,
                   new_type, new_mean, new_flag, new_theme, update_log_ignore,
                   update_log_include_detail, admin
            FROM kkutu_${lang}_audit_log ${filter.sql}
            ORDER BY $sortField ${sortType.name}$stableOrder
            LIMIT ? OFFSET ?
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            wordAuditLogMapper,
            *(filter.values + listOf(pageSize, page * pageSize)).toTypedArray()
        )
    }

    fun insert(lang: String, wordAuditLog: WordAuditLog) {
        val sql =
            "INSERT INTO kkutu_${lang}_audit_log (log_time, log_type, word, old_type, old_mean, old_flag, old_theme, new_type, new_mean, new_flag, new_theme, update_log_ignore, update_log_include_detail, admin) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

        jdbcTemplate.update(
            sql,
            wordAuditLog.time.toTimestamp(),
            wordAuditLog.type.name,
            wordAuditLog.word,
            wordAuditLog.oldType,
            wordAuditLog.oldMean,
            wordAuditLog.oldFlag,
            wordAuditLog.oldTheme,
            wordAuditLog.newType,
            wordAuditLog.newMean,
            wordAuditLog.newFlag,
            wordAuditLog.newTheme,
            wordAuditLog.updateLogIgnore,
            wordAuditLog.updateLogIncludeDetail,
            wordAuditLog.admin
        )
    }

    fun insertAll(lang: String, wordAuditLogs: List<WordAuditLog>) {
        if (wordAuditLogs.isEmpty()) return

        val sql =
            "INSERT INTO kkutu_${lang}_audit_log (log_time, log_type, word, old_type, old_mean, old_flag, old_theme, new_type, new_mean, new_flag, new_theme, update_log_ignore, update_log_include_detail, admin) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

        jdbcTemplate.batchUpdate(sql, wordAuditLogs, wordAuditLogs.size) { statement: PreparedStatement, log: WordAuditLog ->
            statement.setTimestamp(1, log.time.toTimestamp())
            statement.setString(2, log.type.name)
            statement.setString(3, log.word)
            statement.setString(4, log.oldType)
            statement.setString(5, log.oldMean)
            if (log.oldFlag == null) statement.setObject(6, null) else statement.setInt(6, log.oldFlag)
            statement.setString(7, log.oldTheme)
            statement.setString(8, log.newType)
            statement.setString(9, log.newMean)
            if (log.newFlag == null) statement.setObject(10, null) else statement.setInt(10, log.newFlag)
            statement.setString(11, log.newTheme)
            statement.setBoolean(12, log.updateLogIgnore)
            statement.setBoolean(13, log.updateLogIncludeDetail)
            statement.setString(14, log.admin)
        }
    }

    fun getLatestCreateData(
        lang: String,
        words: Collection<String>,
        adminFilter: String = ""
    ): Map<String, WordCreateData> {
        if (words.isEmpty()) return emptyMap()

        val placeholders = words.joinToString(",") { "?" }
        val values = ArrayList<Any>(words.size + 1).apply { addAll(words) }
        val adminClause = if (adminFilter.isBlank()) "" else " AND admin ILIKE ? ESCAPE '\\'"
        if (adminFilter.isNotBlank()) values.add("%${escapeLike(adminFilter.trim())}%")
        val sql = """
            SELECT DISTINCT ON (word) word, admin, log_time
            FROM kkutu_${lang}_audit_log
            WHERE log_type = 'CREATE' AND word IN ($placeholders)$adminClause
            ORDER BY word, log_time DESC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            WordCreateData(
                word = rs.getString("word"),
                admin = rs.getString("admin"),
                time = rs.getTimestamp("log_time").toLocalDateTime()
            )
        }, *values.toTypedArray()).associateBy { it.word }
    }

    private fun filterQuery(searchFilters: Map<String, String>): WordAuditFilter {
        val clauses = mutableListOf<String>()
        val values = mutableListOf<Any>()
        searchFilters.forEach { (key, value) ->
            when (key) {
                "id" -> {
                    clauses.add("id = ?")
                    values.add(value.toLongOrNull() ?: throw IllegalArgumentException("단어 내역 번호는 숫자여야 합니다."))
                }
                "log_type", "word", "admin" -> {
                    clauses.add("$key = ?")
                    values.add(value)
                }
                "old_type", "old_mean", "old_theme", "new_type", "new_mean", "new_theme" -> {
                    clauses.add("$key ILIKE ? ESCAPE '\\'")
                    values.add("%${escapeLike(value)}%")
                }
                else -> throw IllegalArgumentException("검색할 수 없는 항목입니다.")
            }
        }
        return WordAuditFilter(
            if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}",
            values
        )
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private data class WordAuditFilter(val sql: String, val values: List<Any>)
}

data class WordCreateData(
    val word: String,
    val admin: String,
    val time: LocalDateTime
)
