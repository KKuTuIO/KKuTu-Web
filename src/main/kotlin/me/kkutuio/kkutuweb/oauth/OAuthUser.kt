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

package me.kkutuio.kkutuweb.oauth

import com.fasterxml.jackson.annotation.JsonIgnore

data class OAuthUser(
    val authVendor: AuthVendor,
    val vendorId: String,
    val name: String,
    val profileImage: String?,
    val gender: Gender?,
    val minAge: Int?,
    val maxAge: Int?
) {
    @JsonIgnore
    fun getUserId(): String {
        return "${authVendor.name.lowercase()}-${vendorId}"
    }
}