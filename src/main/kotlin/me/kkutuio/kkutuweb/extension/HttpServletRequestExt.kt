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

package me.kkutuio.kkutuweb.extension

import jakarta.servlet.http.HttpServletRequest

fun HttpServletRequest.getIp(): String {
    val cfHeader = this.getHeader("CF-Connecting-IP")
        ?: this.getHeader("HTTP_CF_CONNECTING_IP")
    val realIpHeader = this.getHeader("X-Real-IP")
    val forwardedForHeader = this.getHeader("X-Forwarded-For")

    return when {
        !cfHeader.isNullOrBlank() -> cfHeader.firstForwardedIp()
        !realIpHeader.isNullOrBlank() -> realIpHeader.firstForwardedIp()
        !forwardedForHeader.isNullOrBlank() -> forwardedForHeader.firstForwardedIp()
        else -> this.remoteAddr
    }
}

private fun String.firstForwardedIp(): String = substringBefore(',').trim()
