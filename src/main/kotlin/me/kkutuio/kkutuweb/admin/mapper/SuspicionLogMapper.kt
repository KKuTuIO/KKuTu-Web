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

import me.kkutuio.kkutuweb.admin.domain.SuspicionLog
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.ResultSet

@Component
class SuspicionLogMapper : RowMapper<SuspicionLog> {
    override fun mapRow(rs: ResultSet, rowNum: Int): SuspicionLog {
        val caseId = rs.getLong("case_id")
        val time = rs.getTimestamp("time")
        val action = rs.getString("action")
        val doubt = rs.getString("doubt")
        val userName = rs.getString("user_name")
        val userId = rs.getString("user_id")
        val userIp = rs.getString("user_ip")
        val extraInfo = rs.getString("extra_info")
        val reference = rs.getString("reference")

        return SuspicionLog(
            caseId,
            time,
            action,
            doubt,
            userName,
            userId,
            userIp,
            extraInfo,
            reference
        )
    }
}