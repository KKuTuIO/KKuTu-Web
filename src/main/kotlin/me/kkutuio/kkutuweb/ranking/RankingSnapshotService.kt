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
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

private const val REDIS_KEY = "KKuTu_Score"
private const val REDIS_KEY_SNAPSHOT = "KKuTu_Score_Snapshot"

@Service
class RankingSnapshotService(
    @Autowired private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(cron = "0 0 0 * * *")
    fun createRankingSnapshot() {
        logger.info("랭킹 스냅숏을 생성합니다.")

        val success = redisTemplate.execute { connection ->
            connection.keyCommands().copy(
                REDIS_KEY.toByteArray(),
                REDIS_KEY_SNAPSHOT.toByteArray(),
                true
            )
        }

        if (success == true) {
            logger.info("랭킹 스냅숏이 생성되었습니다.")
        } else {
            logger.error("오류가 발생하여 랭킹 스냅숏 생성에 실패하였습니다.")
        }
    }
}