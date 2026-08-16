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
import me.kkutuio.kkutuweb.admin.mapper.SingleNumberMapper
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
    @Autowired private val singleNumberMapper: SingleNumberMapper,
    @Autowired private val wordAuditLogMapper: WordAuditLogMapper
) {
    fun getDataCount(
        lang: String,
        searchFilters: Map<String, String>
    ): Int {
        val whereQuery = whereQuery(searchFilters)
        val whereValues = whereValues(searchFilters)

        val sql = countQuery(lang, whereQuery)
        val list = jdbcTemplate.query(sql, singleNumberMapper, *whereValues)
        return list[0]
    }

    fun getPageData(
        lang: String,
        page: Int,
        pageSize: Int,
        sortField: String,
        sortType: SortType,
        searchFilters: Map<String, String>
    ): List<WordAuditLog> {
        val whereQuery = whereQuery(searchFilters)
        val whereValues = whereValues(searchFilters)

        val sql = selectQuery(lang, whereQuery, sortField, sortType, pageSize, page)
        return jdbcTemplate.query(sql, wordAuditLogMapper, *whereValues)
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

    private fun whereQuery(searchFilters: Map<String, String>): String {
        val whereQueryParts = ArrayList<String>()
        for (key in searchFilters.keys) {
            whereQueryParts.add("CAST($key AS TEXT) ILIKE ?")
        }

        return if (whereQueryParts.isEmpty()) "" else "WHERE " + whereQueryParts.joinToString(" AND ")
    }

    private fun whereValues(searchFilters: Map<String, String>): Array<String> {
        return searchFilters.values.map { "%${it}%" }.toTypedArray()
    }

    private fun countQuery(lang: String, whereQuery: String): String {
        return "SELECT COUNT(*) FROM kkutu_${lang}_audit_log $whereQuery"
    }

    private fun selectQuery(
        lang: String,
        whereQuery: String,
        sortField: String,
        sortType: SortType,
        pageSize: Int,
        page: Int
    ): String {
        return "SELECT * FROM kkutu_${lang}_audit_log $whereQuery ORDER BY $sortField ${sortType.name} LIMIT $pageSize OFFSET ${page * pageSize}"
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

data class WordCreateData(
    val word: String,
    val admin: String,
    val time: LocalDateTime
)
