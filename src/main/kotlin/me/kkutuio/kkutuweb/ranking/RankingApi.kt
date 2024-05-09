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

package me.kkutuio.kkutuweb.ranking

import me.kkutuio.kkutuweb.ranking.response.RankResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class RankingApi(
    @Autowired private val rankingService: RankingService
) {
    sealed class RankingResult {
        data class Success(val data: RankResponse) : RankingResult()
        data class Error(val error: Int) : RankingResult()
    }

    @GetMapping("/ranking")
    fun ranking(
        @RequestParam(required = false) page: Long?,
        @RequestParam(required = false) id: String?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): RankingResult {
        if (request.getHeader("referer") == null || !request.getHeader("referer").contains("kkutu.io")) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            return RankingResult.Error(400)
        }
        val rankingResponse = rankingService.getRanking(page, id)
        return RankingResult.Success(rankingResponse)
    }
}
