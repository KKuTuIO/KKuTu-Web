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

import me.kkutuio.kkutuweb.user.UserDao
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

data class LeagueRankRow(val id: String, val name: String?, val rank: Int, val score: Int, val tier: Int, val games: Int, val wins: Int)

/** season은 지금 보여 주는 시즌 번호(프리시즌 -1), 기록이 아직 없으면 null. */
data class LeagueRankResponse(val season: Int?, val page: Long, val data: List<LeagueRankRow>)

@Service
class LeagueRankingService(
    @Autowired private val dao: LeagueRankingDao,
    @Autowired private val userDao: UserDao
) {
    fun getRanking(page: Long?): LeagueRankResponse {
        val season = dao.currentSeason() ?: return LeagueRankResponse(null, 0, emptyList())
        return pageOf(season, (page ?: 0L).coerceAtLeast(0L))
    }

    /** 그 사람이 있는 페이지. 기록이 없으면 빈 결과. */
    fun getRankingOf(userId: String): LeagueRankResponse {
        val season = dao.currentSeason() ?: return LeagueRankResponse(null, 0, emptyList())
        val ahead = dao.position(season, userId) ?: return LeagueRankResponse(season, 0, emptyList())
        return pageOf(season, ahead / PAGE_SIZE)
    }

    fun searchRanking(query: String): LeagueRankResponse {
        val user = userDao.searchUsers(query.trim()).firstOrNull()
            ?: return LeagueRankResponse(dao.currentSeason(), 0, emptyList())
        return getRankingOf(user.id)
    }

    private fun pageOf(season: Int, page: Long): LeagueRankResponse {
        val rows = dao.page(season, page * PAGE_SIZE, PAGE_SIZE)
        val names = userDao.getNicknames(rows.map { it.id })
        // 탈퇴한 프로필은 보여 주지 않는다(순위 번호는 DB가 매긴 값 그대로라 건너뛴 번호가 남을 수 있다).
        val data = rows.mapNotNull { r ->
            if (!names.containsKey(r.id)) null else LeagueRankRow(r.id, names[r.id], r.rank - 1, r.trophies, r.tier, r.games, r.wins)
        }
        return LeagueRankResponse(season, page, data)
    }

    companion object {
        const val PAGE_SIZE = 15L
    }
}
