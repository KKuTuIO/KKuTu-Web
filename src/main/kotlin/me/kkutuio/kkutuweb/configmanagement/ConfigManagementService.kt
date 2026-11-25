package me.kkutuio.kkutuweb.configmanagement

import me.kkutuio.kkutuweb.setting.GameServerManagementSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

data class ConfigFileSummary(val name: String, val editable: Boolean, val description: String)
data class ConfigTarget(val id: String, val label: String, val available: Boolean, val note: String?, val files: List<ConfigFileSummary>)
data class ConfigOverview(val targets: List<ConfigTarget>)
data class LockedInfo(val path: String, val kind: String)
data class ConfigFileView(
    val target: String, val file: String, val editable: Boolean, val tracked: Boolean,
    val content: String, val hash: String, val locked: List<LockedInfo>, val note: String?, val restartHint: String
)
data class ConfigSaveRequest(val content: String = "", val baseHash: String = "")
data class ConfigSaveResult(val hash: String, val backup: String, val changed: List<String>, val restartHint: String)

/**
 * 설정 파일 보기·수정. 저장은 파일만 바꾸고 서버는 재시작하지 않는다(재시작은 서버 관리 화면에서 따로).
 * 비밀 값은 화면에 나가지 않고 웹 배포 토큰 말고는 바꿀 수도 없으며, 웹 kkutu.json 의 관리자·게임 서버 접속 항목도 바꿀 수 없다.
 */
@Service
class ConfigManagementService(
    private val setting: KKuTuSetting,
    private val webStore: WebConfigStore,
    private val gameStore: GameConfigStore
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    private class Resolved(val target: String, val label: String, val store: ConfigStore, val spec: (String) -> ConfigFileSpec?, val restartHint: String)

    fun overview(): ConfigOverview {
        val web = ConfigTarget(
            ConfigCatalog.WEB_TARGET, "웹 서버", true, null,
            ConfigCatalog.webFiles.map { ConfigFileSummary(it.name, it.editable, it.description) }
        )
        val games = setting.getGameServers().mapIndexed { channelId, server ->
            ConfigTarget(
                ConfigCatalog.gameTargetId(channelId), "게임 서버 · ${server.name}", server.management != null,
                if (server.management == null) "서버 관리(SSH) 설정이 없어 설정 파일을 열 수 없습니다." else null,
                if (server.management == null) emptyList() else ConfigCatalog.gameFiles.map { ConfigFileSummary(it.name, it.editable, it.description) }
            )
        }
        return ConfigOverview(listOf(web) + games)
    }

    private fun resolve(target: String): Resolved {
        if (target == ConfigCatalog.WEB_TARGET) {
            return Resolved(target, "웹 서버", webStore, ConfigCatalog::webFile, "웹 서버를 재시작해야 반영됩니다.")
        }
        val channelId = ConfigCatalog.parseGameChannel(target) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "알 수 없는 대상입니다.")
        val server = setting.getGameServers().getOrNull(channelId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "등록되지 않은 게임 채널입니다.")
        val management: GameServerManagementSetting = server.management
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "이 채널의 서버 관리(SSH) 설정이 없습니다.")
        return Resolved(target, "게임 서버 · ${server.name}", gameStore.forServer(management), ConfigCatalog::gameFile, "'${server.name}' 채널 서버를 재시작해야 반영됩니다.")
    }

    private fun <T> storeCall(block: () -> T): T = try {
        block()
    } catch (error: ConfigConflictException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, "서버의 파일이 열람한 뒤 바뀌었습니다. 다시 불러온 다음 수정해 주세요.")
    } catch (error: ConfigStoreException) {
        throw ResponseStatusException(HttpStatus.BAD_GATEWAY, error.message ?: "설정 파일에 접근하지 못했습니다.")
    }

    private fun bad(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private fun parseCurrent(stored: StoredConfig) = try {
        ConfigJson.parse(stored.text)
    } catch (error: ConfigJsonException) {
        throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "서버의 파일이 올바른 JSON이 아니어서 열 수 없습니다. 서버에서 직접 고쳐 주세요.")
    }

    fun view(target: String, file: String): ConfigFileView {
        val resolved = resolve(target)
        val spec = resolved.spec(file) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "다룰 수 없는 파일입니다.")
        val stored = storeCall { resolved.store.read(file) } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "서버에 이 파일이 없습니다.")
        val root = parseCurrent(stored)
        val editable = spec.editable && !stored.tracked
        val note = when {
            !spec.editable -> "기본값 파일이라 보기만 가능합니다."
            stored.tracked -> "저장소가 추적하는 파일이라 보기만 가능합니다. 변경은 저장소 커밋과 소스 업데이트로 합니다."
            else -> null
        }
        val locked = ConfigJson.collectLocked(file, root).values.map { LockedInfo(it.pointer, it.kind.name) }
        return ConfigFileView(
            target, file, editable, stored.tracked, ConfigJson.pretty(ConfigJson.masked(file, root)), stored.sha256, locked, note, resolved.restartHint
        )
    }

    fun save(target: String, file: String, request: ConfigSaveRequest, actorId: String): ConfigSaveResult {
        val resolved = resolve(target)
        val spec = resolved.spec(file) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "다룰 수 없는 파일입니다.")
        if (!spec.editable) bad("이 파일은 보기만 가능합니다.")
        if (request.content.toByteArray(Charsets.UTF_8).size > ConfigCatalog.MAX_CONFIG_BYTES) bad("내용이 너무 큽니다.")
        val lock = locks.computeIfAbsent("$target/$file") { ReentrantLock() }
        if (!lock.tryLock()) throw ResponseStatusException(HttpStatus.CONFLICT, "같은 파일을 저장하는 중입니다. 잠시 뒤 다시 시도해 주세요.")
        try {
            val stored = storeCall { resolved.store.read(file) } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "서버에 이 파일이 없습니다.")
            if (stored.tracked) bad("저장소가 추적하는 파일은 수정할 수 없습니다.")
            if (stored.sha256 != request.baseHash) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "서버의 파일이 열람한 뒤 바뀌었습니다. 다시 불러온 다음 수정해 주세요.")
            }
            val original = parseCurrent(stored)
            val submitted = try { ConfigJson.parse(request.content) } catch (error: ConfigJsonException) { bad(error.message ?: "JSON 형식이 올바르지 않습니다.") }
            val merged = try {
                ConfigJson.verifyAndRestore(file, original, submitted)
            } catch (error: ConfigRejectedException) {
                bad(error.problems.joinToString("\n"))
            }
            val problems = ConfigJson.typeChanges(original, merged) +
                (if (target == ConfigCatalog.WEB_TARGET) ConfigJson.validateWebStructure(file, merged) else emptyList())
            if (problems.isNotEmpty()) bad(problems.joinToString("\n"))
            if (merged == original) bad("변경 사항이 없습니다.")
            val text = ConfigJson.pretty(merged)
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size > ConfigCatalog.MAX_CONFIG_BYTES) bad("저장하면 파일이 너무 커집니다.")
            val backup = storeCall { resolved.store.write(file, text, stored.sha256) }
            val newHash = sha256Hex(bytes)
            val changed = ConfigJson.changedPointers(original, merged)
            logger.info(
                "config-management saved actor={} target={} file={} before={} after={} changed={} backup={}",
                actorId, target, file, stored.sha256.take(12), newHash.take(12), changed.take(30).joinToString(","), backup
            )
            return ConfigSaveResult(newHash, backup, changed, resolved.restartHint)
        } finally {
            lock.unlock()
        }
    }
}
