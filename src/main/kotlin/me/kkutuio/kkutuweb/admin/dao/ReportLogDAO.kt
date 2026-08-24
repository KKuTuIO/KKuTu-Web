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
import me.kkutuio.kkutuweb.admin.domain.ReportLog
import me.kkutuio.kkutuweb.admin.mapper.ReportLogMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ReportLogDAO(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val reportLogMapper: ReportLogMapper
) {
    fun getDataCount(
        searchFilters: Map<String, String>
    ): Int {
        val filter = filterQuery(searchFilters)
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM report_log ${filter.sql}",
            Long::class.java,
            *filter.values.toTypedArray()
        )
        return (count ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun getPageData(
        page: Int,
        pageSize: Int,
        sortField: String,
        sortType: SortType,
        searchFilters: Map<String, String>
    ): List<ReportLog> {
        val filter = filterQuery(searchFilters)
        val stableOrder = if (sortField == "report_id") "" else ", report_id ${sortType.name}"
        val sql = """
            SELECT report_log.report_id, report_log.time, report_log.reporter_id,
                   report_log.reporter_nick, report_log.target_id, report_log.target_ip,
                   report_log.target_type, report_log.room_title, report_log.room_owner_id,
                   report_log.status, report_log.reason, report_log.file_name,
                   COALESCE(report_log.room_title, (
                       SELECT replay.room_title FROM game_replay replay
                       WHERE replay.game_id = report_log.game_id LIMIT 1
                   )) AS resolved_room_title
            FROM report_log
            ${filter.sql}
            ORDER BY $sortField ${sortType.name}$stableOrder
            LIMIT ? OFFSET ?
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            reportLogMapper,
            *(filter.values + listOf(pageSize, page * pageSize)).toTypedArray()
        )
    }

    private fun filterQuery(searchFilters: Map<String, String>): ReportLogFilter {
        val clauses = mutableListOf<String>()
        val values = mutableListOf<Any>()
        searchFilters.forEach { (key, value) ->
            when (key) {
                "report_id" -> {
                    clauses.add("report_id = ?")
                    values.add(value.toLongOrNull() ?: throw IllegalArgumentException("신고 번호는 숫자여야 합니다."))
                }
                "reporter_id", "status", "file_name" -> {
                    clauses.add("$key = ?")
                    values.add(value)
                }
                "target_id" -> {
                    clauses.add("(target_id = ? OR host(target_ip) = ?)")
                    values.add(value)
                    values.add(value)
                }
                "reporter_nick", "reason" -> {
                    clauses.add("$key ILIKE ? ESCAPE '\\'")
                    values.add("%${escapeLike(value)}%")
                }
                else -> throw IllegalArgumentException("검색할 수 없는 항목입니다.")
            }
        }
        return ReportLogFilter(
            if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}",
            values
        )
    }

    private fun escapeLike(value: String) = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private data class ReportLogFilter(val sql: String, val values: List<Any>)
}
