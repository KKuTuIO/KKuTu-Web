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
import me.kkutuio.kkutuweb.admin.domain.SuspicionLog
import me.kkutuio.kkutuweb.admin.mapper.SuspicionLogMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SuspicionLogDAO(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val suspicionLogMapper: SuspicionLogMapper
) {
    fun getDataCount(
        searchFilters: Map<String, String>
    ): Int {
        val filter = filterQuery(searchFilters)
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM suspicion_log ${filter.sql}",
            Long::class.java,
            *filter.values.toTypedArray()
        )
        return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun getPageData(
        page: Int,
        pageSize: Int,
        sortField: String,
        sortType: SortType,
        searchFilters: Map<String, String>
    ): List<SuspicionLog> {
        val filter = filterQuery(searchFilters)
        val stableOrder = if (sortField == "case_id") "" else ", case_id ${sortType.name}"
        val sql = """
            SELECT case_id, time, action, doubt, user_name, user_id, user_ip, extra_info, reference
            FROM suspicion_log ${filter.sql}
            ORDER BY $sortField ${sortType.name}$stableOrder
            LIMIT ? OFFSET ?
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            suspicionLogMapper,
            *(filter.values + listOf(pageSize, page * pageSize)).toTypedArray()
        )
    }

    private fun filterQuery(searchFilters: Map<String, String>): SuspicionLogFilter {
        val clauses = mutableListOf<String>()
        val values = mutableListOf<Any>()
        searchFilters.forEach { (key, value) ->
            when (key) {
                "case_id" -> {
                    clauses.add("case_id = ?")
                    values.add(value.toLongOrNull() ?: throw IllegalArgumentException("정책 위반 번호는 숫자여야 합니다."))
                }
                "doubt", "user_id", "user_ip", "reference" -> {
                    clauses.add("$key = ?")
                    values.add(value)
                }
                "action", "user_name", "extra_info" -> {
                    clauses.add("$key ILIKE ? ESCAPE '\\'")
                    values.add("%${escapeLike(value)}%")
                }
                else -> throw IllegalArgumentException("검색할 수 없는 항목입니다.")
            }
        }
        return SuspicionLogFilter(
            if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}",
            values
        )
    }

    private fun escapeLike(value: String) = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private data class SuspicionLogFilter(val sql: String, val values: List<Any>)
}
