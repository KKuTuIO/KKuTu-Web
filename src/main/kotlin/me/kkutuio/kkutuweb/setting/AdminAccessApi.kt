package me.kkutuio.kkutuweb.setting

import tools.jackson.databind.ObjectMapper
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import jakarta.servlet.http.HttpSession

data class AdminAccessRequest(
    val name: String,
    val team: String = "",
    val privileges: Set<AdminSetting.Privilege> = emptySet(),
    val gameAdmin: Boolean = false,
    val active: Boolean = true
)

@RestController
@RequestMapping("/api/admin/admin-access")
class AdminAccessApi(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val setting: KKuTuSetting,
    private val adminDirectoryBroadcaster: AdminDirectoryBroadcaster,
    private val authorizer: AdminModerationAuthorizer
) {
    @GetMapping
    fun list(session: HttpSession): List<Map<String, Any?>> {
        authorizer.requireMaster(session)
        val masters = setting.getAdminMasterIds()
        val rows = try {
            jdbc.query("SELECT account_uuid, display_name, team, privileges, game_admin, active, updated_at FROM admin_access ORDER BY active DESC, display_name, account_uuid") { rs, _ ->
                mapOf(
                    "id" to rs.getString("account_uuid"), "name" to rs.getString("display_name"), "team" to rs.getString("team"),
                    "privileges" to objectMapper.readTree(rs.getString("privileges")).toList().map { it.stringValue() }.sorted(),
                    "gameAdmin" to rs.getBoolean("game_admin"), "active" to rs.getBoolean("active"),
                    "source" to if (rs.getString("account_uuid") in masters) "CONFIG" else "DATABASE",
                    "updatedAt" to rs.getTimestamp("updated_at").toInstant().toString()
                )
            }
        } catch (error: DataAccessException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "내부 오류가 발생했습니다.")
        }
        val present = rows.map { it["id"] }.toSet()
        return rows + masters.filterNot(present::contains).map { id ->
            mapOf("id" to id, "name" to "총관리자", "team" to "시스템", "privileges" to AdminSetting.Privilege.values().map { it.name }, "gameAdmin" to true, "active" to true, "source" to "CONFIG", "updatedAt" to null)
        }
    }

    @PutMapping("/{accountId}")
    fun save(@PathVariable accountId: String, @RequestBody request: AdminAccessRequest, session: HttpSession): Map<String, Any> {
        val actorId = authorizer.requireMaster(session)
        validateAccountId(accountId)
        if (setting.isAdminMaster(accountId)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.")
        val name = request.name.trim()
        val team = request.team.trim()
        if (name.isEmpty() || name.length > 80 || team.length > 80) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이름과 팀은 각각 1~80자로 입력하세요.")
        if (AdminSetting.Privilege.ADMIN_MANAGE in request.privileges) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "권한이 부족합니다.")
        try {
            jdbc.update(
                "INSERT INTO admin_access(account_uuid, display_name, team, privileges, game_admin, active, updated_by) VALUES (CAST(? AS uuid), ?, ?, CAST(? AS jsonb), ?, ?, CAST(? AS uuid)) " +
                    "ON CONFLICT(account_uuid) DO UPDATE SET display_name=EXCLUDED.display_name, team=EXCLUDED.team, privileges=EXCLUDED.privileges, game_admin=EXCLUDED.game_admin, active=EXCLUDED.active, updated_by=EXCLUDED.updated_by, updated_at=CURRENT_TIMESTAMP",
                accountId, name, team, objectMapper.writeValueAsString(request.privileges.map { it.name }.sorted()), request.gameAdmin, request.active, actorId
            )
            audit(accountId, actorId, "UPSERT", request)
        } catch (error: DataAccessException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "내부 오류가 발생했습니다.")
        }
        setting.refreshAdmins()
        adminDirectoryBroadcaster.broadcast()
        return mapOf("ok" to true)
    }

    @DeleteMapping("/{accountId}")
    fun deactivate(@PathVariable accountId: String, session: HttpSession): Map<String, Any> {
        val actorId = authorizer.requireMaster(session)
        validateAccountId(accountId)
        if (setting.isAdminMaster(accountId)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "총관리자는 비활성화할 수 없습니다.")
        jdbc.update("UPDATE admin_access SET active=FALSE, updated_by=CAST(? AS uuid), updated_at=CURRENT_TIMESTAMP WHERE account_uuid=CAST(? AS uuid)", actorId, accountId)
        audit(accountId, actorId, "DEACTIVATE", null)
        setting.refreshAdmins()
        adminDirectoryBroadcaster.broadcast()
        return mapOf("ok" to true)
    }

    private fun validateAccountId(value: String) {
        try { UUID.fromString(value) } catch (error: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "계정 UUID 형식이 올바르지 않습니다.")
        }
    }

    private fun audit(accountId: String, actorId: String, action: String, request: AdminAccessRequest?) {
        jdbc.update("INSERT INTO admin_access_audit(account_uuid, actor_uuid, action, snapshot) VALUES (CAST(? AS uuid), CAST(? AS uuid), ?, CAST(? AS jsonb))", accountId, actorId, action, objectMapper.writeValueAsString(request ?: emptyMap<String, String>()))
    }
}
