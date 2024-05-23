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

package me.kkutuio.kkutuweb.admin.vo

import com.fasterxml.jackson.annotation.JsonProperty
import me.kkutuio.kkutuweb.admin.domain.ReportLog
import me.kkutuio.kkutuweb.factory.DateFactory

data class ReportLogVO(
    @JsonProperty("report_id") val reportId: Long,
    val time: String,
    @JsonProperty("reporter_id") val reporterId: String,
    @JsonProperty("reporter_nick") val reporterNick: String,
    @JsonProperty("target_id") val targetId: String,
    @JsonProperty("reason") val reason: String,
    @JsonProperty("file_name") val fileName: String
) {
    companion object {
        fun convertFrom(reportLog: ReportLog): ReportLogVO {
            return ReportLogVO(
                reportId = reportLog.reportId,
                time = DateFactory.DATABASE_FORMAT.format(reportLog.time.toLocalDateTime()),
                reporterId = reportLog.reporterId,
                reporterNick = reportLog.reporterNick ?: "",
                targetId = reportLog.targetId,
                reason = reportLog.reason,
                fileName = reportLog.fileName
            )
        }
    }
}