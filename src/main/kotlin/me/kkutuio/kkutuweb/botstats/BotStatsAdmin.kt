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

package me.kkutuio.kkutuweb.botstats

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 봇 행동 학습 현황(읽기 전용). 게임 서버가 사람들의 플레이 기록에서 쌓는 집계(bot_turn_stat, bot_learn_mark)를 요약해서 보여 준다.
 * 운영 DB에는 접근권이 없으므로, 학습이 실제로 되고 있는지(얼마나 쌓였고 어느 구간을 쓸 수 있는지)를 이 화면으로 확인한다.
 * 테이블은 게임 서버가 만든다: 아직 없으면 빈 결과를 돌려준다.
 */

/** 모델이 한 구간을 쓰려면 있어야 하는 관측 수(게임 서버의 MIN_TOTAL_OBS와 같은 값. 서버는 최근일수록 무겁게 센 합을 쓰므로 여기 숫자는 대략의 기준이다). */
private const val MIN_READY_OBSERVATIONS = 150
private const val WINDOW_DAYS = 90
private const val BIN_SECONDS = 0.5

data class BotStatsReplays(
    val learned: Long,
    val inProgress: Long,
    /** 기록된 제한 시간(CTL)으로 뽑은 판 수 */
    val recordedLimit: Long,
    /** 게임 규칙을 다시 계산해 되살린 판 수(CTL이 생기기 전의 기록) */
    val rebuiltLimit: Long,
    /** 클래식이 아니거나 쓸 관측이 없어 건너뛴 판 수 */
    val skipped: Long,
    /** 되살린 제한 시간을 기록된 값과 견준 턴 수와 그중 어긋난 수(되살리기가 맞는지 확인하는 용도) */
    val checkedTurns: Long,
    val mismatchedTurns: Long
)

data class BotStatsLevel(
    val levelBucket: Int,
    val levelRange: String,
    val observations: Long,
    val timeouts: Long,
    val timeoutRate: Double,
    /** 제출한 사람들의 제출 시각 중앙값(초) */
    val medianSubmitSeconds: Double?,
    /** 이 구간만으로 모델을 만들 수 있는지(관측 수가 충분한지). 모자라면 같은 모드의 전체 레벨 표를 쓰고, 그것도 모자라면 예전 방식이다. */
    val ready: Boolean
)

data class BotStatsMode(
    val mode: String,
    val observations: Long,
    val timeoutRate: Double,
    val medianSubmitSeconds: Double?,
    val ready: Boolean,
    val levels: List<BotStatsLevel>
)

data class BotStatsResponse(
    val windowDays: Int,
    val minReadyObservations: Int,
    val replays: BotStatsReplays,
    val modes: List<BotStatsMode>
)

@Component
class BotStatsDao(@Autowired private val jdbcTemplate: JdbcTemplate) {
    private val logger = LoggerFactory.getLogger(BotStatsDao::class.java)

    data class MarkRow(val state: Int, val source: String, val games: Long, val checked: Long, val mismatch: Long)
    data class BinRow(val mode: String, val lv: Int, val outcome: String, val bin: Int, val n: Long)

    fun marks(): List<MarkRow> = guarded(emptyList()) {
        jdbcTemplate.query(
            "SELECT state, source, count(*) AS games, coalesce(sum(checked), 0) AS checked, coalesce(sum(mismatch), 0) AS mismatch FROM bot_learn_mark GROUP BY state, source"
        ) { rs, _ -> MarkRow(rs.getInt("state"), rs.getString("source"), rs.getLong("games"), rs.getLong("checked"), rs.getLong("mismatch")) }
    }

    fun bins(): List<BinRow> = guarded(emptyList()) {
        jdbcTemplate.query(
            """
            SELECT mode, lv, outcome, bin, sum(n) AS n FROM bot_turn_stat
             WHERE day >= (NOW() AT TIME ZONE 'Asia/Seoul')::date - ?
             GROUP BY mode, lv, outcome, bin
            """.trimIndent(),
            { rs, _ -> BinRow(rs.getString("mode"), rs.getInt("lv"), rs.getString("outcome"), rs.getInt("bin"), rs.getLong("n")) },
            WINDOW_DAYS
        )
    }

    private fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: BadSqlGrammarException) {
        logger.warn("봇 학습 테이블이 아직 없어 빈 결과를 돌려줍니다: {}", e.message)
        fallback
    }
}

@Service
class BotStatsService(@Autowired private val dao: BotStatsDao) {
    private val levelRanges = listOf("1~9", "10~29", "30~59", "60~99", "100~149", "150~199", "200~299", "300+")

    /** 제출한 관측들의 중앙값(초). 구간 안은 고르게 퍼져 있다고 보고 구간 한가운데로 센다. */
    private fun median(submitsByBin: Map<Int, Long>): Double? {
        val total = submitsByBin.values.sum()
        if (total == 0L) return null
        var acc = 0L
        for (bin in submitsByBin.keys.sorted()) {
            acc += submitsByBin.getValue(bin)
            if (acc * 2 >= total) return (bin + 0.5) * BIN_SECONDS
        }
        return null
    }

    fun summary(): BotStatsResponse {
        val marks = dao.marks()
        val done = marks.filter { it.state == 0 }
        val replays = BotStatsReplays(
            learned = done.sumOf { it.games },
            inProgress = marks.filter { it.state == -1 }.sumOf { it.games },
            recordedLimit = done.filter { it.source == "c" }.sumOf { it.games },
            rebuiltLimit = done.filter { it.source == "r" }.sumOf { it.games },
            skipped = done.filter { it.source == "-" }.sumOf { it.games },
            checkedTurns = done.sumOf { it.checked },
            mismatchedTurns = done.sumOf { it.mismatch }
        )
        val modes = dao.bins().groupBy { it.mode }.map { (mode, rows) ->
            fun stat(sel: List<BotStatsDao.BinRow>): Triple<Long, Long, Double?> {
                val subs = sel.filter { it.outcome == "s" }
                val timeouts = sel.filter { it.outcome == "t" }.sumOf { it.n }
                val submits = subs.sumOf { it.n }
                return Triple(submits + timeouts, timeouts, median(subs.groupBy { it.bin }.mapValues { e -> e.value.sumOf { it.n } }))
            }
            val (all, allTimeouts, allMedian) = stat(rows)
            val levels = rows.groupBy { it.lv }.toSortedMap().map { (lv, sel) ->
                val (n, timeouts, med) = stat(sel)
                BotStatsLevel(lv, levelRanges.getOrElse(lv) { "?" }, n, timeouts, if (n == 0L) 0.0 else timeouts.toDouble() / n, med, n >= MIN_READY_OBSERVATIONS)
            }
            BotStatsMode(mode, all, if (all == 0L) 0.0 else allTimeouts.toDouble() / all, allMedian, all >= MIN_READY_OBSERVATIONS, levels)
        }.sortedByDescending { it.observations }
        return BotStatsResponse(WINDOW_DAYS, MIN_READY_OBSERVATIONS, replays, modes)
    }
}

@RestController
@RequestMapping("/api/admin")
class BotStatsAdminApi(
    private val authorizer: AdminModerationAuthorizer,
    private val service: BotStatsService
) {
    @GetMapping("/bot-stats")
    fun summary(session: HttpSession): BotStatsResponse {
        authorizer.requireAny(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE, AdminSetting.Privilege.ADMIN_MANAGE)
        return service.summary()
    }
}
