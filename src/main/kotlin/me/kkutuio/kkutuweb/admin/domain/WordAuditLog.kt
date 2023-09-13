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

package me.kkutuio.kkutuweb.admin.domain

import java.sql.Timestamp

data class WordAuditLog(
    val id: Long,
    val time: Timestamp,
    val type: WordAuditLogType,
    val word: String,
    val oldType: String? = null,
    val oldMean: String? = null,
    val oldFlag: Int? = null,
    val oldTheme: String? = null,
    val newType: String? = null,
    val newMean: String? = null,
    val newFlag: Int? = null,
    val newTheme: String? = null,
    val updateLogIgnore: Boolean,
    val updateLogIncludeDetail: Boolean,
    val admin: String
) {
    enum class WordAuditLogType {
        CREATE,
        UPDATE,
        DELETE
    }
}
