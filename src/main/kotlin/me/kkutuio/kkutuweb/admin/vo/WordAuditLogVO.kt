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
import me.kkutuio.kkutuweb.admin.domain.WordAuditLog
import me.kkutuio.kkutuweb.factory.DateFactory

data class WordAuditLogVO(
    @JsonProperty("id") val id: Long,
    @JsonProperty("log_time") val time: String,
    @JsonProperty("log_type") val logType: String,
    @JsonProperty("word") val word: String,
    @JsonProperty("old_type") val oldType: String,
    @JsonProperty("old_mean") val oldMean: String,
    @JsonProperty("old_flag") val oldFlag: Int,
    @JsonProperty("old_theme") val oldTheme: String,
    @JsonProperty("new_type") val newType: String,
    @JsonProperty("new_mean") val newMean: String,
    @JsonProperty("new_flag") val newFlag: Int,
    @JsonProperty("new_theme") val newTheme: String,
    @JsonProperty("update_log_ignore") val updateLogIgnore: Boolean,
    @JsonProperty("update_log_include_detail") val updateLogIncludeDetail: Boolean,
    @JsonProperty("admin") val admin: String
) {
    companion object {
        fun convertFrom(wordAuditLog: WordAuditLog): WordAuditLogVO {
            return WordAuditLogVO(
                id = wordAuditLog.id,
                time = DateFactory.DATABASE_FORMAT.format(wordAuditLog.time.toLocalDateTime()),
                logType = wordAuditLog.logType,
                word = wordAuditLog.word,
                oldType = wordAuditLog.oldType ?: "",
                oldMean = wordAuditLog.oldMean ?: "",
                oldFlag = wordAuditLog.oldFlag ?: "",
                oldTheme = wordAuditLog.oldTheme ?: "",
                newType = wordAuditLog.newType ?: "",
                newMean = wordAuditLog.newMean ?: "",
                newFlag = wordAuditLog.newFlag ?: "",
                newTheme = wordAuditLog.newTheme ?: "",
                updateLogIgnore = wordAuditLog.updateLogIgnore,
                updateLogIncludeDetail = wordAuditLog.updateLogIncludeDetail,
                admin = wordAuditLog.admin ?: ""
            )
        }
    }
}