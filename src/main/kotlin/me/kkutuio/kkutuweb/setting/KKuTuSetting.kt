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

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import me.kkutuio.kkutuweb.extension.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import jakarta.annotation.PostConstruct
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

internal const val DEFAULT_GAME_SERVER_RECONNECT_INTERVAL_SECONDS = 30L

internal fun parseGameServerReconnectSetting(node: JsonNode?): GameServerReconnectSetting {
    return GameServerReconnectSetting(
        enabled = node?.get("enabled")?.booleanValue() ?: true,
        retryInterval = node?.get("retryInterval")?.longValue()
            ?: DEFAULT_GAME_SERVER_RECONNECT_INTERVAL_SECONDS
    )
}

@Component
class KKuTuSetting(
    @Autowired private val applicationArguments: ApplicationArguments,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbc: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(KKuTuSetting::class.java)
    private val runnerUID = UUID.randomUUID().toString();
    private lateinit var kkutu: JsonNode
    private lateinit var games: JsonNode
    private lateinit var moremi: JsonNode
    private lateinit var themes: JsonNode
    private val adminCache = AtomicReference<List<AdminSetting>?>(null)
    private val gameAdminCache = AtomicReference<Set<String>?>(null)

    @PostConstruct
    fun init() {
        val settingDir = applicationArguments.getOptionValues("SETTING_DIR")?.firstOrNull()
            ?: throw IllegalStateException("프로그램 실행 인수에 SETTING_DIR 값이 누락되었습니다.")
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

    @EventListener(ApplicationReadyEvent::class)
    fun initializeAdminDirectory() {
        refreshAdmins()
    }

    fun getVersion() = kkutu["version"].stringValue()!!

    fun getMaxPlayers() = kkutu["maxPlayers"].intValue()

    fun getAdvancedBadWordPatterns(): List<String> = kkutu["advancedBadWords"]?.toList()
        ?.mapNotNull { it.stringValue()?.trim()?.takeIf(String::isNotEmpty) }
        ?: emptyList()

    fun getGameServers() = kkutu["gameServers"].toList().map {
        GameServerSetting(
            it["isSecure"].booleanValue(),
            it["publicHost"].stringValue(),
            it["key"].stringValue(),
            it["host"].stringValue(),
            it["port"].intValue(),
            it["cid"].shortValue(),
            parseGameServerReconnectSetting(it["reconnect"])
        )
    }

    fun getAdminIds(): List<String> = getAdmins().map { it.id }

    /**
     * Game servers receive this small, in-memory snapshot over their existing
     * control WebSocket. They never query the administrator table themselves.
     */
    fun getGameAdminIds(): Set<String> {
        getAdmins()
        return gameAdminCache.get() ?: emptySet()
    }

    /** Immutable break-glass accounts. They are configured, never editable through the admin UI. */
    fun getAdminMasterIds(): Set<String> = kkutu["adminMasters"]?.toList()
        ?.mapNotNull { it.stringValue()?.trim()?.takeIf(String::isNotEmpty) }
        ?.toSet()
        ?: emptySet()

    fun isAdminMaster(accountId: String): Boolean = accountId in getAdminMasterIds()

    private fun legacyAdmins(): List<AdminSetting> = kkutu["admins"]?.toList()?.map {
        AdminSetting(
            it["id"].stringValue(),
            it["name"].stringValue(),
            it["team"].stringValue(),
            it["privileges"].toList().map { privilege -> AdminSetting.Privilege.valueOf(privilege.stringValue()) }
        )
    } ?: emptyList()

    /**
     * Returns the live administrator directory. `adminMasters` remains the only
     * file-based authority; all delegated roles come from PostgreSQL and can be
     * refreshed after an admin UI write without restarting the web process.
     */
    fun getAdmins(): List<AdminSetting> = adminCache.get() ?: refreshAdmins()

    @Synchronized
    fun refreshAdmins(): List<AdminSetting> {
        val masters = getAdminMasterIds()
        val legacy = legacyAdmins()
        val stored = try {
            syncMasters(masters)
            jdbc.query("SELECT account_uuid, display_name, team, privileges, game_admin FROM admin_access WHERE active = TRUE ORDER BY display_name, account_uuid") { rs, _ ->
                val privileges = runCatching {
                    objectMapper.readTree(rs.getString("privileges")).toList().map { AdminSetting.Privilege.valueOf(it.stringValue()) }
                }.getOrDefault(emptyList())
                StoredAdmin(
                    AdminSetting(rs.getString("account_uuid"), rs.getString("display_name"), rs.getString("team"), privileges),
                    rs.getBoolean("game_admin")
                )
            }
        } catch (error: DataAccessException) {
            logger.warn("관리자 디렉터리 테이블을 읽을 수 없습니다. DB 마이그레이션 전에는 기존 설정만 사용합니다.")
            emptyList()
        }
        val delegated = stored.map { it.setting }.filterNot { it.id in masters }
        val masterAdmins = if (masters.isNotEmpty()) masters.map { id ->
            val configured = legacy.firstOrNull { it.id == id }
            val storedAdmin = stored.firstOrNull { it.setting.id == id }?.setting
            AdminSetting(id, configured?.name ?: storedAdmin?.name ?: "총관리자", configured?.team ?: storedAdmin?.team ?: "시스템", AdminSetting.Privilege.values().toList())
        } else legacy
        gameAdminCache.set((masters + stored.filter { it.gameAdmin }.map { it.setting.id }).toSet())
        return (masterAdmins + delegated).also { adminCache.set(it) }
    }

    /** Persists configuration-only break-glass accounts alongside delegated roles. */
    private fun syncMasters(masters: Set<String>) {
        if (masters.isEmpty()) return
        val allPrivileges = objectMapper.writeValueAsString(AdminSetting.Privilege.values().map { it.name })
        masters.forEach { id ->
            jdbc.update(
                "INSERT INTO admin_access(account_uuid, display_name, team, privileges, game_admin, active, managed_by_config) VALUES (CAST(? AS uuid), '총관리자', '시스템', CAST(? AS jsonb), TRUE, TRUE, TRUE) " +
                    "ON CONFLICT(account_uuid) DO UPDATE SET game_admin=TRUE, active=TRUE, managed_by_config=TRUE, updated_at=CURRENT_TIMESTAMP",
                id, allPrivileges
            )
        }
        val postgresArray = "{${masters.joinToString(",")}}"
        jdbc.update("UPDATE admin_access SET active=FALSE, game_admin=FALSE, managed_by_config=FALSE, updated_at=CURRENT_TIMESTAMP WHERE managed_by_config=TRUE AND account_uuid <> ALL(CAST(? AS uuid[]))", postgresArray)
    }

    private data class StoredAdmin(val setting: AdminSetting, val gameAdmin: Boolean)

    fun getRunnerVersion() = runnerUID

    fun getCdnHost() = kkutu["cdnHost"].stringValue()!!

    fun getApiKey() = kkutu["apiKey"].stringValue()!!

    fun getCryptoKey() = kkutu["cryptoKey"].stringValue()!!

    fun getModerationLogService(): ModerationLogServiceSetting {
        val setting = kkutu["moderationLogService"]
        return ModerationLogServiceSetting(
            publicUrl = setting?.get("publicUrl")?.stringValue()?.trim()?.trimEnd('/') ?: "",
            signingSecret = setting?.get("signingSecret")?.stringValue() ?: ""
        )
    }

    fun getGeoIpDb11Path(): String = kkutu["geoIp"]?.get("db11Path")?.stringValue()?.trim() ?: ""

    fun getGeoIpAsnPath(): String = kkutu["geoIp"]?.get("asnPath")?.stringValue()?.trim() ?: ""

    fun getGeoIpDomesticExemptCidrs(): List<String> = kkutu["geoIp"]
        ?.get("domesticExemptCidrs")
        ?.takeIf(JsonNode::isArray)
        ?.toList()
        ?.mapNotNull { it.stringValue()?.trim()?.takeIf(String::isNotEmpty) }
        ?: emptyList()

    fun getConnectionLogRetention(): ConnectionLogRetentionSetting {
        return getLogRetention("connectionLog", 12L)
    }

    fun getSuspicionLogRetention(): ConnectionLogRetentionSetting {
        return getLogRetention("suspicionLog", 12L)
    }

    fun getReportLogRetention(): ConnectionLogRetentionSetting {
        return getLogRetention("reportLog", 36L)
    }

    private fun getLogRetention(logName: String, defaultMonths: Long): ConnectionLogRetentionSetting {
        val retention = kkutu[logName]?.get("retention")
        return ConnectionLogRetentionSetting(
            enabled = retention?.get("enabled")?.booleanValue() ?: true,
            months = (retention?.get("months")?.longValue() ?: defaultMonths).coerceIn(1L, 120L),
            batchSize = (retention?.get("batchSize")?.intValue() ?: 10_000).coerceIn(100, 50_000),
            maxBatchesPerRun = (retention?.get("maxBatchesPerRun")?.intValue() ?: 10).coerceIn(1, 100)
        )
    }

    fun getKoThemes() = themes["word"]["themes"]["normal"]["ko"].toList().map(JsonNode::stringValue)

    fun getKoInjeongThemes() = themes["word"]["themes"]["injeong"]["ko"].toList().map(JsonNode::stringValue)

    fun getEnThemes() = themes["word"]["themes"]["normal"]["en"].toList().map(JsonNode::stringValue)

    fun getEnInjeongThemes() = themes["word"]["themes"]["injeong"]["en"].toList().map(JsonNode::stringValue)

    fun getInjeongPickExcepts() = themes["word"]["themes"]["ijpExcept"].toList().map(JsonNode::stringValue)

    fun getMoremiParts() = moremi["moremi"]["parts"].toList().map(JsonNode::stringValue)

    fun getMoremiCategories() = moremi["moremi"]["categories"].toList().map(JsonNode::stringValue)

    fun getMoremiEquips() = moremi["moremi"]["equips"].toList().map(JsonNode::stringValue)

    fun getMoremiGroups(): Map<String, List<String>> {
        val resultMap = HashMap<String, List<String>>()
        for (key in moremi["moremi"]["groups"].propertyNames()) {
            resultMap[key] = moremi["moremi"]["groups"][key].toList().map(JsonNode::stringValue)
        }

        return resultMap
    }

    fun getGameRules() = games["RULE"].toJson()

    fun getGameOptions() = games["OPTIONS"].toJson()

    fun getGameOptionMap(): Map<String, String> {
        val resultMap = LinkedHashMap<String, String>()
        for (key in games["OPTIONS"].propertyNames()) {
            resultMap[key] = games["OPTIONS"][key]["name"].stringValue()
        }

        return resultMap
    }

    fun getGameModes() = games["RULE"].propertyNames().toList()

    fun getServiceMode() = kkutu["serviceMode"]?.stringValue() ?: "NORMAL"

    fun getMainPageConfig(): MainPageSetting {
        if (getServiceMode() == "NORMAL") {
            return MainPageSetting(
                status = false,
                title = "",
                body = "",
                noticeTitle = "",
                noticeMessage = "",
                showNotice = false
            );
        }

        val mainPage = kkutu["mainPage"]
        return MainPageSetting(
            status = mainPage["status"]?.booleanValue() ?: false,
            title = mainPage["title"]?.stringValue() ?: "",
            body = mainPage["body"]?.stringValue() ?: "",
            noticeTitle = mainPage["noticeTitle"]?.stringValue() ?: "",
            noticeMessage = mainPage["noticeMessage"]?.stringValue() ?: "",
            showNotice = mainPage["showNotice"]?.booleanValue() ?: false
        )
    }
}
