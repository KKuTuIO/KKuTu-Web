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

import me.kkutuio.kkutuweb.admin.domain.WordAuditLog
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.ResultSet

@Component
class WordAuditLogMapper : RowMapper<WordAuditLog> {
    override fun mapRow(rs: ResultSet, rowNum: Int): WordAuditLog {
        val id = rs.getInt("id")
        val time = rs.getTimestamp("log_time")
        val logType = WordAuditLog.WordAuditLogType.valueOf(rs.getString("log_type"))
        val word = rs.getString("word")
        val oldType = rs.getString("old_type")
        val oldMean = rs.getString("old_mean")
        val oldFlag = rs.getInt("old_flag")
        val oldTheme = rs.getString("old_theme")
        val newType = rs.getString("new_type")
        val newMean = rs.getString("new_mean")
        val newFlag = rs.getInt("new_flag")
        val newTheme = rs.getString("new_theme")
        val updateLogIgnore = rs.getBoolean("update_log_ignore")
        val updateLogIncludeDetail = rs.getBoolean("update_log_include_detail")
        val admin = rs.getString("admin")

        return WordAuditLog(
                id,
                time,
                logType,
                word,
                oldType,
                oldMean,
                oldFlag,
                oldTheme,
                newType,
                newMean,
                newFlag,
                newTheme,
                updateLogIgnore,
                updateLogIncludeDetail,
                admin
        )
    }
}