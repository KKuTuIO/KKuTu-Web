package me.kkutuio.kkutuweb.servermanagement

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class SourceUpdateRequest(val branch: String)
data class ServerManagementOverview(
    val servers: List<ManagedGameServer>,
    val canControlProcess: Boolean,
    val canManageSource: Boolean
)

@RestController
@RequestMapping("/api/admin/server-management")
class GameServerManagementApi(
    private val service: GameServerManagementService,
    private val webRestartService: WebServerRestartService,
    private val webJarDeployService: WebJarDeployService,
    private val authorizer: AdminModerationAuthorizer
) {
    @GetMapping
    fun list(session: HttpSession): ServerManagementOverview {
        authorizer.requireAny(session, AdminSetting.Privilege.GAME_SERVER_RESTART, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        return ServerManagementOverview(
            servers = service.listServers(),
            canControlProcess = authorizer.hasPrivilege(session, AdminSetting.Privilege.GAME_SERVER_RESTART),
            canManageSource = authorizer.hasPrivilege(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        )
    }

    @GetMapping("/{channelId}/status")
    fun status(@PathVariable channelId: Int, session: HttpSession): Pm2ProcessStatus {
        authorizer.require(session, AdminSetting.Privilege.GAME_SERVER_RESTART)
        return service.processStatus(channelId)
    }

    @GetMapping("/web/releases")
    fun webReleases(session: HttpSession): WebReleaseList {
        authorizer.require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        return webJarDeployService.listReleases()
    }

    @PostMapping("/web/deploy")
    fun deployWeb(@RequestBody request: WebDeployRequest, session: HttpSession): WebDeploySnapshot {
        // 교체 후 재시작까지 일어나므로 소스 관리와 재시작 권한을 모두 요구한다.
        authorizer.require(session, AdminSetting.Privilege.GAME_SERVER_RESTART)
        val actor = authorizer.require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        return webJarDeployService.deploy(request.tag.trim(), actor)
    }

    @GetMapping("/web/deploy/{operationId}")
    fun webDeployOperation(@PathVariable operationId: UUID, session: HttpSession): WebDeploySnapshot {
        authorizer.require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        return webJarDeployService.operation(operationId)
    }

    @PostMapping("/web/restart")
    fun restartWebServer(session: HttpSession): WebRestartResponse {
        val actor = authorizer.require(session, AdminSetting.Privilege.GAME_SERVER_RESTART)
        return webRestartService.schedule(actor)
    }

    @PostMapping("/{channelId}/process/{action}")
    fun process(
        @PathVariable channelId: Int,
        @PathVariable action: String,
        session: HttpSession
    ): ServerOperationSnapshot {
        val actor = authorizer.require(session, AdminSetting.Privilege.GAME_SERVER_RESTART)
        val type = when (action.lowercase()) {
            "start" -> ServerOperationType.START
            "restart" -> ServerOperationType.RESTART
            "stop" -> ServerOperationType.STOP
            else -> throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "지원하지 않는 PM2 작업입니다."
            )
        }
        return service.startProcess(channelId, actor, type)
    }

    @PostMapping("/{channelId}/source-update")
    fun updateSource(
        @PathVariable channelId: Int,
        @RequestBody request: SourceUpdateRequest,
        session: HttpSession
    ): ServerOperationSnapshot {
        val actor = authorizer.require(session, AdminSetting.Privilege.GAME_SOURCE_MANAGE)
        return service.updateSource(channelId, request.branch, actor)
    }

    @GetMapping("/operations/{operationId}")
    fun operation(@PathVariable operationId: UUID, session: HttpSession): ServerOperationSnapshot {
        val operation = service.operation(operationId)
        val privilege = if (operation.type == ServerOperationType.SOURCE_UPDATE) {
            AdminSetting.Privilege.GAME_SOURCE_MANAGE
        } else {
            AdminSetting.Privilege.GAME_SERVER_RESTART
        }
        authorizer.require(session, privilege)
        return operation
    }
}
