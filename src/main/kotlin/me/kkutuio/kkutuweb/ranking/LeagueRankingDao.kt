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

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 트로피 리그 랭킹 읽기. 테이블(league_profile)은 게임 서버가 시작할 때 만든다(웹은 읽기만 한다).
 * 아직 만들어지지 않았으면(게임 서버가 한 번도 뜨지 않은 DB) 빈 결과를 돌려준다.
 */
@Component
class LeagueRankingDao(
    @Autowired private val jdbcTemplate: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(LeagueRankingDao::class.java)

    data class Row(val id: String, val rank: Int, val trophies: Int, val tier: Int, val games: Int, val wins: Int)

    /** 가장 최근에 기록이 있는 시즌. 아직 없으면 null. */
    fun currentSeason(): Int? = guarded(null) {
        jdbcTemplate.queryForObject("SELECT max(season) FROM league_profile", Int::class.javaObjectType)
    }

    /** 한 시즌의 한 페이지. 순위는 동점이면 같은 값(자신보다 트로피가 많은 사람 수 + 1). */
    fun page(season: Int, offset: Long, limit: Long): List<Row> = guarded(emptyList()) {
        jdbcTemplate.query(
            """
            SELECT user_id, trophies, tier, games, wins, rank() OVER (ORDER BY trophies DESC) AS pos
            FROM league_profile
            WHERE season = ?
            ORDER BY trophies DESC, user_id
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                Row(rs.getString("user_id"), rs.getInt("pos"), rs.getInt("trophies"), rs.getInt("tier"), rs.getInt("games"), rs.getInt("wins"))
            },
            season, limit, offset
        )
    }

    /** 그 사람 앞에 몇 명이 있는지(0부터). 기록이 없으면 null. */
    fun position(season: Int, userId: String): Long? = guarded(null) {
        val mine = jdbcTemplate.query(
            "SELECT trophies FROM league_profile WHERE season = ? AND user_id = ?",
            { rs, _ -> rs.getInt(1) },
            season, userId
        ).firstOrNull() ?: return@guarded null
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM league_profile WHERE season = ? AND trophies > ?",
            Long::class.javaObjectType, season, mine
        )
    }

    private fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: BadSqlGrammarException) {
        logger.warn("리그 테이블이 아직 없어 빈 랭킹을 돌려줍니다: {}", e.message)
        fallback
    }
}
