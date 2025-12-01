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

package me.kkutuio.kkutuweb.ranking.response

import me.kkutuio.kkutuweb.ranking.Rank

data class ResponseRank(
    val id: String,
    val name: String?,
    val rank: Int,
    val score: Long,
    val delta: String
) {
    companion object {
        fun fromRank(current: Rank, nickname: String?, prevRank: Long?): ResponseRank {

            val deltaValue = when (prevRank) {
                null -> "*"                     // 변동 정보 없음
                current.rank.toLong() -> "-"    // 변동 없음
                else -> (prevRank - current.rank).toString()
            }

            return ResponseRank(
                id = current.id,
                name = nickname,
                rank = current.rank,
                score = current.score,
                delta = deltaValue
            )
        }
    }
}