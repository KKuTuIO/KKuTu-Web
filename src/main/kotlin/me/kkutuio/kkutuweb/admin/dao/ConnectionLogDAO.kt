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
import me.kkutuio.kkutuweb.admin.domain.ConnectionLog
import me.kkutuio.kkutuweb.admin.mapper.ConnectionLogMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ConnectionLogDAO(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val connectionLogMapper: ConnectionLogMapper
) {
    fun getDataCount(
        searchFilters: Map<String, String>
    ): Int {
        val filter = filterQuery(searchFilters)
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connection_log ${filter.sql}",
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
    ): List<ConnectionLog> {
        val filter = filterQuery(searchFilters)
        val stableOrder = if (sortField == "id") "" else ", id ${sortType.name}"
        val sql = """
            SELECT id, time, user_id, user_name, user_ip, channel, user_agent,
                   finger_print_2, pcid_cookie, pcid_localstorage
            FROM connection_log ${filter.sql}
            ORDER BY $sortField ${sortType.name}$stableOrder
            LIMIT ? OFFSET ?
        """.trimIndent()
        return jdbcTemplate.query(
            sql,
            connectionLogMapper,
            *(filter.values + listOf(pageSize, page * pageSize)).toTypedArray()
        )
    }

    private fun filterQuery(searchFilters: Map<String, String>): ConnectionLogFilter {
        val clauses = mutableListOf<String>()
        val values = mutableListOf<Any>()
        searchFilters.forEach { (key, value) ->
            when (key) {
                "user_id", "user_ip" -> {
                    clauses.add("$key = ?")
                    values.add(value)
                }
                "finger_print_2", "pcid_cookie", "pcid_localstorage" -> {
                    clauses.add("$key = ? AND $key <> ''")
                    values.add(value)
                }
                "channel" -> {
                    val channel = value.toIntOrNull()
                        ?: throw IllegalArgumentException("채널은 숫자로 입력해야 합니다.")
                    clauses.add("channel = ?")
                    values.add(channel)
                }
                "user_name", "user_agent" -> {
                    clauses.add("$key ILIKE ? ESCAPE '\\'")
                    values.add("%${escapeLike(value)}%")
                }
                else -> throw IllegalArgumentException("검색할 수 없는 항목입니다.")
            }
        }
        return ConnectionLogFilter(
            if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}",
            values
        )
    }

    private fun escapeLike(value: String) = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private data class ConnectionLogFilter(val sql: String, val values: List<Any>)
}
