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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.roundToInt

private const val REDIS_KEY = "KKuTu_Score"

@Component
class RankDao(
    @Autowired val redisTemplate: RedisTemplate<String, Any>
) {
    fun getPage(pageNum: Long, dataCount: Long): List<Rank> {
        if (pageNum < 0) {
            val start = 0 * dataCount
            val end = 1 * dataCount - 1
            return getRanks(start, end)
        }

        val start = pageNum * dataCount
        val end = (pageNum + 1) * dataCount - 1

        return getRanks(start, end)
    }

    fun remove(id: String) {
        val opsForZSet = redisTemplate.opsForZSet()
        opsForZSet.remove(REDIS_KEY, id)
    }

    fun getSurround(id: String, dataCount: Int): List<Rank> {
        val opsForZSet = redisTemplate.opsForZSet()
        val reverseRank = opsForZSet.reverseRank(REDIS_KEY, id)!!

        val start = max(0, reverseRank - (dataCount / 2.0 + 1.0).roundToInt())
        val end = start + dataCount - 1

        return getRanks(start, end)
    }

    private fun getRanks(start: Long, end: Long): List<Rank> {
        val opsForZSet = redisTemplate.opsForZSet()
        val scores = opsForZSet.reverseRangeWithScores(REDIS_KEY, start, end)

        val ranks = ArrayList<Rank>()
        var curRank = start

        for (score in scores!!) {
            val id = score.value as String

            ranks.add(Rank(id, curRank.toInt(), score.score!!.toLong()))
            curRank++
        }

        return ranks
    }
}