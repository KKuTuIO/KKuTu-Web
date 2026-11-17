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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession

@RestController
class LeagueRankingApi(
    @Autowired private val service: LeagueRankingService,
    @Autowired private val loginService: LoginService
) {
    sealed class Result {
        data class Success(val data: LeagueRankResponse) : Result()
        data class Error(val error: Int) : Result()
    }

    /** 전체 랭킹(/ranking)과 같은 규칙: kkutu.io에서 온 요청만, 내 순위만 캐시하지 않는다. 응답의 rank는 0부터다. */
    @GetMapping("/ranking/league")
    fun ranking(
        @RequestParam(required = false) page: Long?,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false, defaultValue = "false") me: Boolean,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession
    ): Result {
        val referer = request.getHeader("referer")
        if (referer == null || !referer.contains("kkutu.io")) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            return Result.Error(403)
        }
        if (me) {
            response.setHeader("Cache-Control", "private, no-store")
        } else {
            response.setHeader("Cache-Control", "public, max-age=15, s-maxage=60, stale-while-revalidate=300")
            response.setHeader("CDN-Cache-Control", "max-age=60, stale-while-revalidate=300")
        }
        val body = when {
            me -> loginService.gameUserId(session)?.let(service::getRankingOf) ?: LeagueRankResponse(null, 0, emptyList())
            !query.isNullOrBlank() -> service.searchRanking(query)
            else -> service.getRanking(page)
        }
        return Result.Success(body)
    }
}
