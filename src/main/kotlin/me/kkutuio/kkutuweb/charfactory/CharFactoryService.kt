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

package me.kkutuio.kkutuweb.charfactory

import com.fasterxml.jackson.databind.ObjectMapper
import me.kkutuio.kkutuweb.extension.getOAuthUser
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.extension.toJson
import me.kkutuio.kkutuweb.shop.ShopService
import me.kkutuio.kkutuweb.user.UserDao
import me.kkutuio.kkutuweb.word.WordDao
import org.postgresql.util.PGobject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.servlet.http.HttpSession
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

@Service
class CharFactoryService(
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userDao: UserDao,
    @Autowired private val wordDao: WordDao,
    @Autowired private val shopService: ShopService
) {
    fun previewCharFactory(word: String, l: Int, b: String): CFResult {
        return if (l == -1) getCfEventRewarts(word, b == "1") else getCfRewards(word, l, b == "1")
    }

    fun charFactory(tray: List<String>, session: HttpSession): String {
        if (tray.isEmpty() || tray.size > 6) return "{\"error\":400}"

        if (session.isGuest()) return "{\"error\":400}"
        val oAuthUser = session.getOAuthUser()

        val userId = oAuthUser.getUserId()
        val user = userDao.getUser(userId) ?: return "{\"error\":400}"

        var wordString = ""
        var level = 0
        val charCountMap = HashMap<String, Int>()

        val event = tray[0][3] == 'E'
        var expire: Int? = null

        for (charItem in tray) {
            val char = charItem[4].toString()
            val charType = charItem[3]
            if (event == (charType != 'E')) return "{\"error\":412}"
            wordString += char
            level += 68 - charType.toInt()
            charCountMap[charItem] = if (charCountMap.containsKey(charItem)) charCountMap[charItem]!! + 1 else 1
            if (!user.box.has(charItem) || user.box.get(charItem)
                .get("value").intValue() < charCountMap[charItem]!!
            ) return "{\"error\":434}"
            // 아직까지는 만료 일자가 다른 경우는 없다. 모두 같은 값 혹은 만료 없음
            if (expire == null && user.box.has(charItem) && user.box.get(charItem).has("expire"))
                expire = user.box.get(charItem).get("expire").intValue()
        }

        if (event) level = -1;

        val tableName = when (findLanguage(wordString)) {
            "ko" -> "kkutu_ko"
            "en" -> "kkutu_en"
            else -> ""
        }

        if (tableName.isEmpty()) return "{\"error\":404}"
        val words = wordDao.getWords(tableName, wordString)

        var blend = false
        if (words.isEmpty()) {
            if (wordString.length == 3) {
                if (event) return "{\"error\":468}"
                blend = true
            } else return "{\"error\":404}"
        }

        val cfRewards = if (event) getCfEventRewarts(wordString, blend) else getCfRewards(wordString, level, blend)
        if (user.money < cfRewards.cost) return "{\"error\":407}"

        for (entry in charCountMap.entries) {
            shopService.consumeGood(user.box, entry.key, entry.value, true)
        }

        val gained = ArrayList<Reward>()
        for (reward in cfRewards.data) {
            var count = 0
            if (reward.rate > 1) {
                count += reward.rate.toInt()
                reward.rate %= 1
            }
            if (Random.nextDouble(0.0, 1.0) < reward.rate) count += 1
            if (count == 0) continue
            if (reward.key[4].toString() == "?") {
                reward.key =
                    reward.key.substring(0, 4) + if (blend) blendWord(wordString) else wordString.random().toString()
            }
            val itemExpire = if (reward.allowExpire) expire else null

            shopService.obtainGood(user.box, reward.key, count, itemExpire, true)
            gained.add(reward)
        }

        val afterMoney = user.money - cfRewards.cost

        val userBoxJsonObj = PGobject()
        userBoxJsonObj.type = "json"
        userBoxJsonObj.value = user.box.toJson()

        userDao.updateUser(
            user.id, mapOf(
                "money" to afterMoney,
                "box" to userBoxJsonObj
            )
        )
        return "{\"result\":200,\"box\":${user.box.toJson()},\"money\":$afterMoney,\"gain\":${
            objectMapper.writeValueAsString(
                gained
            )
        }}"
    }

    fun blendWord(word: String): String {
        val lang = findLanguage(word)
        val choseongList = HashSet<Int>()
        val jungseongList = HashSet<Int>()
        val jongseongList = HashSet<Int>()

        return when (lang) {
            "en" -> {
                "abcdefghijklmnopqrstuvwxyz".toCharArray().random().toString()
            }
            "ko" -> {
                for (i in word.indices) {
                    val char = word[i].toInt() - 0xAC00

                    choseongList.add(char / 28 / 21)
                    jungseongList.add((char / 28) % 21)
                    jongseongList.add(char % 28)
                }

                (((choseongList.shuffled()[0] * 21) + jungseongList.shuffled()[0]) * 28 + jongseongList.shuffled()[0] + 0xAC00).toChar()
                    .toString()
            }
            else -> {
                "ERROR"
            }
        }
    }

    fun findLanguage(word: String): String {
        return if ("[a-zA-Z]".toRegex().containsMatchIn(word)) "en" else "ko"
    }

    fun getCfRewards(word: String, level: Int, blend: Boolean): CFResult {
        val wordLength = word.length

        var cost = 10 * level
        var wur = wordLength / 36.0

        val rewards = ArrayList<Reward>()
        if (blend) {
            when {
                wur >= 0.5 -> {
                    rewards.add(Reward("\$WPA?", 1.0))
                }
                wur >= 0.35 -> {
                    rewards.add(Reward("\$WPB?", 1.0))
                }
                wur >= 0.05 -> {
                    rewards.add(Reward("\$WPC?", 1.0))
                }
            }
            cost = (cost * 0.2).roundToInt()
        } else {
            rewards.add(Reward("dictPage", (wordLength * 0.6) + 0.4))
            rewards.add(Reward("boxB4", min(1.0, level / 7.0)))
            if (level >= 5) {
                rewards.add(Reward("boxB3", min(1.0, level / 10.0)))
                cost += 10 * level
                wur += level / 20.0
            }
            if (level >= 10) {
                rewards.add(Reward("boxB2", min(1.0, level / 20.0)))
                cost += 20 * level
                wur += level / 10.0
            }
            if (wur >= 0.05) {
                rewards.add(Reward("\$WPC?", wur))
            }
            if (wur >= 0.35) {
                rewards.add(Reward("\$WPB?", (wur / 2.0)))
            }
            if (wur >= 0.5) {
                rewards.add(Reward("\$WPA?", (wur / 3.0)))
            }
        }

        return CFResult(cost, rewards)
    }

    fun getCfEventRewarts(word: String, blend: Boolean): CFResult {
        val wordLength = word.length

        var cost = 10 * wordLength
        // var wur = wordLength / 36.0

        val rewards = ArrayList<Reward>()
        if (blend) {
            rewards.add(Reward("\$WPE?", 1.0, true))
        } else {
            rewards.add(Reward("dictPage", wordLength * 1.0))
            rewards.add(Reward("boxE4", wordLength * 0.375, true))
            if (wordLength > 1) rewards.add(Reward("boxE3", (wordLength - 1) * 0.25, true))
            if (wordLength > 2) rewards.add(Reward("boxE2", (wordLength - 2) * 0.175, true))
            rewards.add(Reward("\$WPE?", wordLength * 0.25, true))
        }

        return CFResult(cost, rewards)
    }
}