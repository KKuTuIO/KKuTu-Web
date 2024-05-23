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

package me.kkutuio.kkutuweb.admin.mapper

import me.kkutuio.kkutuweb.admin.domain.ReportLog
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.ResultSet

@Component
class ReportLogMapper : RowMapper<ReportLog> {
    override fun mapRow(rs: ResultSet, rowNum: Int): ReportLog {
        val reportId = rs.getLong("report_id")
        val time = rs.getTimestamp("time")
        val reporterId = rs.getString("reporter_id")
        val reporterNick = rs.getString("reporter_nick")
        val targetId = rs.getString("target_id")
        val reason = rs.getString("reason")
        val fileName = rs.getString("file_name")

        return ReportLog(
            reportId,
            time,
            reporterId,
            reporterNick,
            targetId,
            reason,
            fileName
        )
    }
}