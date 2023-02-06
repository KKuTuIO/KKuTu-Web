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
import me.kkutuio.kkutuweb.admin.domain.SuspicionLog
import me.kkutuio.kkutuweb.factory.DateFactory

data class SuspicionLogVO(
    @JsonProperty("case_id") val caseId: Long,
    val time: String,
    @JsonProperty("action") val action: String,
    @JsonProperty("doubt") val doubt: String,
    @JsonProperty("user_name") val userName: String,
    @JsonProperty("user_id") val userId: String,
    @JsonProperty("user_ip") val userIp: String,
    @JsonProperty("extra_info") val extraInfo: String,
    @JsonProperty("reference") val reference: String
) {
    companion object {
        fun convertFrom(suspicionLog: SuspicionLog): SuspicionLogVO {
            return SuspicionLogVO(
                caseId = suspicionLog.caseId,
                time = DateFactory.DATABASE_FORMAT.format(suspicionLog.time.toLocalDateTime()),
                action = suspicionLog.action,
                doubt = suspicionLog.doubt,
                userName = suspicionLog.userName ?: "",
                userId = suspicionLog.userId,
                userIp = suspicionLog.userIp.replace("::ffff:", ""),
                extraInfo = suspicionLog.extraInfo,
                reference = suspicionLog.reference,
            )
        }
    }
}