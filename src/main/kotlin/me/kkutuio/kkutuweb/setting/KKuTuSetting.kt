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

package me.kkutuio.kkutuweb.setting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.kkutuio.kkutuweb.extension.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.annotation.PostConstruct
import kotlin.collections.HashMap

@Component
class KKuTuSetting(
    @Autowired private val applicationArguments: ApplicationArguments,
    @Autowired private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(KKuTuSetting::class.java)
    private val runnerUID = UUID.randomUUID().toString();
    private lateinit var kkutu: JsonNode
    private lateinit var games: JsonNode
    private lateinit var moremi: JsonNode
    private lateinit var themes: JsonNode

    @PostConstruct
    fun init() {
        val optionValues = applicationArguments.getOptionValues("SETTING_DIR")
        if (optionValues.isNullOrEmpty()) {
            logger.error("프로그램 실행 인수에 SETTING_DIR 값이 누락되었습니다.")
        }

        val settingDir = optionValues[0]
        Files.newInputStream(Paths.get(settingDir, "kkutu.json")).use {
            val br = it.bufferedReader()
            br.use { reader ->
                val jsonText = reader.readText()
                val jsonNode = objectMapper.readTree(jsonText)

                kkutu = jsonNode
            }
        }
        Files.newInputStream(Paths.get(settingDir, "games.json")).use {
            val br = it.bufferedReader()
            br.use { reader ->
                val jsonText = reader.readText()
                val jsonNode = objectMapper.readTree(jsonText)

                games = jsonNode
            }
        }
        Files.newInputStream(Paths.get(settingDir, "moremi.json")).use {
            val br = it.bufferedReader()
            br.use { reader ->
                val jsonText = reader.readText()
                val jsonNode = objectMapper.readTree(jsonText)

                moremi = jsonNode
            }
        }
        Files.newInputStream(Paths.get(settingDir, "themes.json")).use {
            val br = it.bufferedReader()
            br.use { reader ->
                val jsonText = reader.readText()
                val jsonNode = objectMapper.readTree(jsonText)

                themes = jsonNode
            }
        }
    }

    fun getVersion() = kkutu["version"].textValue()!!

    fun getMaxPlayers() = kkutu["maxPlayers"].intValue()

    fun getGameServers() = kkutu["gameServers"].toList().map {
        GameServerSetting(
            it["isSecure"].booleanValue(),
            it["publicHost"].textValue(),
            it["key"].textValue(),
            it["host"].textValue(),
            it["port"].intValue(),
            it["cid"].shortValue()
        )
    }

    fun getAdminIds(): List<String> = getAdmins().map { it.id }

    fun getAdmins(): List<AdminSetting> = kkutu["admins"].toList().map {
        AdminSetting(
            it["id"].textValue(),
            it["name"].textValue(),
            it["team"].textValue(),
            it["privileges"].toList().map { privilege -> AdminSetting.Privilege.valueOf(privilege.textValue()) }
        )
    }

    fun runnerVersion() = runnerUID

    fun getApiKey() = kkutu["apiKey"].textValue()!!

    fun getCryptoKey() = kkutu["cryptoKey"].textValue()!!

    fun getKoThemes() = themes["word"]["themes"]["normal"]["ko"].toList().map(JsonNode::textValue)

    fun getKoInjeongThemes() = themes["word"]["themes"]["injeong"]["ko"].toList().map(JsonNode::textValue)

    fun getEnThemes() = themes["word"]["themes"]["normal"]["en"].toList().map(JsonNode::textValue)

    fun getEnInjeongThemes() = themes["word"]["themes"]["injeong"]["en"].toList().map(JsonNode::textValue)

    fun getInjeongPickExcepts() = themes["word"]["themes"]["ijpExcept"].toList().map(JsonNode::textValue)

    fun getMoremiParts() = moremi["moremi"]["parts"].toList().map(JsonNode::textValue)

    fun getMoremiCategories() = moremi["moremi"]["categories"].toList().map(JsonNode::textValue)

    fun getMoremiEquips() = moremi["moremi"]["equips"].toList().map(JsonNode::textValue)

    fun getMoremiGroups(): Map<String, List<String>> {
        val resultMap = HashMap<String, List<String>>()
        for (key in moremi["moremi"]["groups"].fieldNames()) {
            resultMap[key] = moremi["moremi"]["groups"][key].toList().map(JsonNode::textValue)
        }

        return resultMap
    }

    fun getGameRules() = games["RULE"].toJson()

    fun getGameOptions() = games["OPTIONS"].toJson()

    fun getGameOptionMap(): Map<String, String> {
        val resultMap = HashMap<String, String>()
        for (key in games["OPTIONS"].fieldNames()) {
            resultMap[key] = games["OPTIONS"][key]["name"].textValue()
        }

        return resultMap
    }

    fun getGameModes() = games["RULE"].fieldNames().asSequence().toList()
}