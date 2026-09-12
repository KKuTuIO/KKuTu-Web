package me.kkutuio.kkutuweb.servermanagement

import jakarta.annotation.PreDestroy
import me.kkutuio.kkutuweb.setting.GameServerManagementSetting
import me.kkutuio.kkutuweb.setting.GameServerSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

enum class ServerOperationType { START, RESTART, STOP, SOURCE_UPDATE }
enum class ServerOperationStatus { RUNNING, SUCCEEDED, FAILED }

data class ManagedGameServer(
    val channelId: Int,
    val name: String,
    val publicHost: String,
    val managementConfigured: Boolean
)

data class Pm2ProcessStatus(
    val status: String,
    val pid: Long?,
    val startedAt: Long?,
    val restartCount: Int?
)

data class ServerOperationSnapshot(
    val id: UUID,
    val channelId: Int,
    val channelName: String,
    val type: ServerOperationType,
    val branch: String?,
    val status: ServerOperationStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val output: String
)

@Service
class GameServerManagementService(
    private val setting: KKuTuSetting,
    private val runner: RemoteCommandRunner,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val operations = ConcurrentHashMap<UUID, MutableOperation>()
    private val activeTargets = ConcurrentHashMap<String, UUID>()

    fun listServers(): List<ManagedGameServer> = setting.getGameServers().mapIndexed { channelId, server ->
        ManagedGameServer(channelId, server.name, server.publicHost, server.management != null)
    }

    fun processStatus(channelId: Int): Pm2ProcessStatus {
        val (_, management) = managedServer(channelId)
        val result = try {
            runRemote(management, "pm2 jlist", STATUS_TIMEOUT_SECONDS)
        } catch (error: Exception) {
            val message = "SSH 상태 조회 명령을 시작하지 못했습니다: ${safeException(error)}"
            logger.warn("game-server-management status failed channel={} stage=START message={}", channelId, message)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, message)
        }
        if (result.timedOut) {
            val message = "SSH/PM2 상태 조회 시간이 초과되었습니다.${failureSuffix(result.output)}"
            logger.warn("game-server-management status failed channel={} stage=TIMEOUT message={}", channelId, message)
            throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, message)
        }
        if (result.exitCode != 0) {
            val message = "SSH/PM2 명령이 실패했습니다 (종료 코드 ${result.exitCode}).${failureSuffix(result.output)}"
            logger.warn("game-server-management status failed channel={} stage=COMMAND message={}", channelId, message)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, message)
        }
        return parsePm2Status(result.output, management.pm2ProcessName)
            ?: Pm2ProcessStatus("not_found", null, null, null)
    }

    fun startProcess(channelId: Int, actorId: String, type: ServerOperationType): ServerOperationSnapshot {
        require(type == ServerOperationType.START || type == ServerOperationType.RESTART || type == ServerOperationType.STOP)
        val (server, management) = managedServer(channelId)
        val command = when (type) {
            ServerOperationType.START -> "start"
            ServerOperationType.RESTART -> "restart"
            ServerOperationType.STOP -> "stop"
            ServerOperationType.SOURCE_UPDATE -> error("source updates do not use PM2 control")
        }
        return launch(channelId, server, management, type, null, actorId) {
            runRemote(management, "pm2 $command ${shellQuote(management.pm2ProcessName)}", PROCESS_TIMEOUT_SECONDS)
        }
    }

    fun updateSource(channelId: Int, branch: String, actorId: String): ServerOperationSnapshot {
        val normalizedBranch = branch.trim()
        if (!isSafeBranch(normalizedBranch)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "브랜치 이름 형식이 올바르지 않습니다.")
        }
        val (server, management) = managedServer(channelId)
        val quotedBranch = shellQuote(normalizedBranch)
        val quotedRemoteBranch = shellQuote("origin/$normalizedBranch")
        val quotedRefspec = shellQuote("+refs/heads/$normalizedBranch:refs/remotes/origin/$normalizedBranch")
        val script = """
            set -eu
            cd -- ${shellQuote(management.workingDirectory)}
            test -z "$(git status --porcelain)" || { echo "Working tree is not clean; source update aborted." >&2; exit 1; }
            git fetch --prune origin $quotedRefspec
            if git show-ref --verify --quiet refs/heads/$quotedBranch; then
              git checkout $quotedBranch
            else
              git checkout -b $quotedBranch --track $quotedRemoteBranch
            fi
            git pull --ff-only origin $quotedBranch
            pnpm --dir server exec tsc --noEmitOnError -p tsconfig.json
        """.trimIndent()
        return launch(channelId, server, management, ServerOperationType.SOURCE_UPDATE, normalizedBranch, actorId) {
            runRemote(management, script, SOURCE_TIMEOUT_SECONDS)
        }
    }

    fun operation(id: UUID): ServerOperationSnapshot = operations[id]?.snapshot()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다.")

    private fun launch(
        channelId: Int,
        server: GameServerSetting,
        management: GameServerManagementSetting,
        type: ServerOperationType,
        branch: String?,
        actorId: String,
        block: () -> CommandResult
    ): ServerOperationSnapshot {
        cleanup()
        val operation = MutableOperation(UUID.randomUUID(), channelId, server, type, branch)
        val targetKey = "${management.sshTarget}\u0000${management.workingDirectory}"
        if (activeTargets.putIfAbsent(targetKey, operation.id) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이 서버에서 이미 다른 관리 작업을 실행 중입니다.")
        }
        operations[operation.id] = operation
        logger.info("game-server-management started actor={} channel={} operation={} type={} branch={}", actorId, channelId, operation.id, type, branch)
        executor.submit {
            try {
                val result = block()
                operation.complete(
                    if (!result.timedOut && result.exitCode == 0) ServerOperationStatus.SUCCEEDED else ServerOperationStatus.FAILED,
                    if (result.timedOut) "작업 제한 시간을 초과했습니다.\n${result.output}".trim() else result.output
                )
                logger.info("game-server-management finished actor={} channel={} operation={} type={} status={} exitCode={}", actorId, channelId, operation.id, type, operation.status, result.exitCode)
            } catch (error: Exception) {
                operation.complete(ServerOperationStatus.FAILED, error.message ?: "원격 명령 실행에 실패했습니다.")
                logger.error("game-server-management failed actor={} channel={} operation={} type={}", actorId, channelId, operation.id, type, error)
            } finally {
                activeTargets.remove(targetKey, operation.id)
            }
        }
        return operation.snapshot()
    }

    private fun managedServer(channelId: Int): Pair<GameServerSetting, GameServerManagementSetting> {
        val server = setting.getGameServers().getOrNull(channelId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "등록되지 않은 게임 채널입니다.")
        return server to (server.management
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "이 채널의 서버 관리 설정이 없습니다."))
    }

    private fun runRemote(management: GameServerManagementSetting, script: String, timeoutSeconds: Long): CommandResult {
        val command = mutableListOf(
            "ssh", "-n", "-T", "-o", "BatchMode=yes", "-o", "PreferredAuthentications=publickey",
            "-o", "ConnectTimeout=10", "-o", "ConnectionAttempts=1"
        )
        management.sshPort?.let { command += listOf("-p", it.toString()) }
        management.sshKeyPath?.let {
            command += listOf("-o", "IdentitiesOnly=yes", "-i", it)
        }
        command += listOf(management.sshTarget, "sh", "-lc", shellQuote(script))
        return runner.run(command, timeoutSeconds)
    }

    private fun parsePm2Status(output: String, processName: String): Pm2ProcessStatus? {
        val start = output.indexOf('[')
        val end = output.lastIndexOf(']')
        if (start < 0 || end < start) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "PM2 상태 응답을 해석하지 못했습니다.${failureSuffix(output)}")
        }
        val processes = try {
            objectMapper.readTree(output.substring(start, end + 1))
        } catch (error: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "PM2 상태 응답이 올바른 JSON이 아닙니다.${failureSuffix(output)}")
        }
        val process = processes.firstOrNull { it["name"]?.stringValue() == processName } ?: return null
        val environment = process["pm2_env"]
        return Pm2ProcessStatus(
            status = environment?.get("status")?.stringValue() ?: "unknown",
            pid = process["pid"]?.longValue()?.takeIf { it > 0 },
            startedAt = environment?.get("pm_uptime")?.longValue()?.takeIf { it > 0 },
            restartCount = environment?.get("restart_time")?.intValue()
        )
    }

    private fun cleanup() {
        val cutoff = Instant.now().minusSeconds(OPERATION_RETENTION_SECONDS)
        operations.entries.removeIf { (_, operation) -> operation.finishedAt?.isBefore(cutoff) == true }
    }

    @PreDestroy
    fun close() = executor.shutdownNow()

    private class MutableOperation(
        val id: UUID,
        private val channelId: Int,
        private val server: GameServerSetting,
        private val type: ServerOperationType,
        private val branch: String?
    ) {
        val startedAt: Instant = Instant.now()
        @Volatile var status: ServerOperationStatus = ServerOperationStatus.RUNNING
            private set
        @Volatile var finishedAt: Instant? = null
            private set
        @Volatile private var output: String = ""

        @Synchronized
        fun complete(status: ServerOperationStatus, output: String) {
            this.output = output
            this.finishedAt = Instant.now()
            this.status = status
        }

        @Synchronized
        fun snapshot() = ServerOperationSnapshot(id, channelId, server.name, type, branch, status, startedAt, finishedAt, output)
    }

    companion object {
        private const val STATUS_TIMEOUT_SECONDS = 20L
        private const val PROCESS_TIMEOUT_SECONDS = 60L
        private const val SOURCE_TIMEOUT_SECONDS = 300L
        private const val OPERATION_RETENTION_SECONDS = 2 * 60 * 60L

        internal fun isSafeBranch(value: String): Boolean {
            val segments = value.split('/')
            return value.length in 1..128 && value != "HEAD" &&
                value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")) &&
                !value.contains("..") && !value.contains("//") &&
                segments.none { it.isEmpty() || it.startsWith('.') || it.endsWith('.') || it.endsWith(".lock") }
        }

        internal fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

        private fun safeFailure(output: String, fallback: String): String = output.lineSequence()
            .take(8).joinToString("\n").take(2_000).ifBlank { fallback }

        private fun failureSuffix(output: String): String {
            val diagnostic = if (output.contains("\"pm2_env\"") || output.contains("\"env\"")) {
                "PM2 JSON 출력이 손상되어 원문을 숨겼습니다."
            } else {
                safeFailure(output, "출력 없음")
            }
            return "\n원격 출력: $diagnostic"
        }

        private fun safeException(error: Exception): String = error.message?.lineSequence()?.firstOrNull()?.take(500)
            ?: error.javaClass.simpleName
    }
}
