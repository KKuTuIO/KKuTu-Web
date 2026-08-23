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
import me.kkutuio.kkutuweb.ranking.response.ResponseRank
import me.kkutuio.kkutuweb.user.UserDao
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RankingService(
    @Autowired private val rankDao: RankDao,
    @Autowired private val userDao: UserDao
) {
    fun getRanking(p: Long?, id: String?): RankResponse {
        val pageIndex = if (id == null) p ?: 0L else 0L
        val currentRanks = getValidRanks(pageIndex, id)

        if (currentRanks.isEmpty()) return RankResponse(pageIndex, emptyList())

        val ids = currentRanks.map { it.rank.id }
        val prevRanks = rankDao.getSnapshotRanks(ids)

        val responseData = currentRanks.mapIndexed { index, resolvedRank ->
            ResponseRank.fromRank(
                current = resolvedRank.rank,
                nickname = resolvedRank.nickname,
                prevRank = prevRanks.getOrNull(index)
            )
        }

        val responsePage = if (id == null) pageIndex else currentRanks.first().rank.rank.toLong() / PAGE_SIZE
        return RankResponse(responsePage, responseData)
    }

    /** Returns the ranked user plus nearby entries, suitable for the "my rank" view. */
    fun getRankingAroundUser(id: String): RankResponse = getRanking(null, id)

    /** Resolves a nickname/UUID, then returns that user's surrounding ranking page. */
    fun searchRanking(query: String): RankResponse {
        val user = userDao.searchUsers(query.trim()).firstOrNull() ?: return RankResponse(0, emptyList())
        return getRankingAroundUser(user.id)
    }

    private fun getValidRanks(pageIndex: Long, id: String?): List<ResolvedRank> {
        while (true) {
            val ranks = getRanks(pageIndex, id)
            if (ranks.isEmpty()) return emptyList()

            val nicknames = userDao.getNicknames(ranks.map { it.id })
            val deletedIds = ranks.map { it.id }.filterNot { nicknames.containsKey(it) }
            val validRanks = ranks.mapNotNull { rank ->
                if (nicknames.containsKey(rank.id)) ResolvedRank(rank, nicknames[rank.id]) else null
            }

            if (deletedIds.isEmpty()) return validRanks

            // Re-read the same page after compaction so a deleted account does not
            // leave a short page or stale rank numbers behind.
            if (rankDao.removeDeleted(deletedIds) == 0L) return validRanks
        }
    }

    private fun getRanks(pageIndex: Long, id: String?): List<Rank> = if (id == null) {
        rankDao.getPage(pageIndex, PAGE_SIZE)
    } else {
        try {
            rankDao.getSurround(id, PAGE_SIZE.toInt())
        } catch (e: NullPointerException) {
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private data class ResolvedRank(val rank: Rank, val nickname: String?)

    companion object {
        private const val PAGE_SIZE = 15L
    }
}
