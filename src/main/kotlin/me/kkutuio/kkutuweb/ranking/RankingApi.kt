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

import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.ranking.response.RankResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

@RestController
class RankingApi(
    @Autowired private val rankingService: RankingService,
    @Autowired private val loginService: LoginService
) {
    sealed class RankingResult {
        data class Success(val data: RankResponse) : RankingResult()
        data class Error(val error: Int) : RankingResult()
    }

    @GetMapping("/ranking", "/api/ranking")
    fun ranking(
        @RequestParam(required = false) page: Long?,
        @RequestParam(required = false) id: String?,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false, defaultValue = "false") me: Boolean,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession
    ): RankingResult {
        if (request.getHeader("referer") == null || !request.getHeader("referer").contains("kkutu.io")) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            return RankingResult.Error(403)
        }
        if (me) {
            response.setHeader("Cache-Control", "private, no-store")
        } else {
            response.setHeader("Cache-Control", "public, max-age=15, s-maxage=60, stale-while-revalidate=300")
            response.setHeader("CDN-Cache-Control", "max-age=60, stale-while-revalidate=300")
        }
        val rankingResponse = when {
            me -> loginService.gameUserId(session)?.let(rankingService::getRankingAroundUser)
                ?: RankResponse(0, emptyList())
            !query.isNullOrBlank() -> rankingService.searchRanking(query)
            else -> rankingService.getRanking(page, id)
        }
        return RankingResult.Success(rankingResponse)
    }
}
