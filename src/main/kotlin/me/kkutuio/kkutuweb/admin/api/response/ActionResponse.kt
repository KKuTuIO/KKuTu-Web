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

package me.kkutuio.kkutuweb.admin.api.response

data class ActionResponse(
    val success: Boolean,
    val resultCode: String,
    val resultMessage: String
) {
    companion object {
        fun success(): ActionResponse {
            return rest(true, RestResult.SUCCESS)
        }

        fun rest(success: Boolean, restResult: RestResult): ActionResponse {
            return ActionResponse(
                success = success,
                resultCode = restResult.name,
                resultMessage = restResult.message
            )
        }

        fun word(success: Boolean, wordResult: WordResult): ActionResponse {
            return ActionResponse(
                success = success,
                resultCode = wordResult.name,
                resultMessage = wordResult.message
            )
        }
    }
}