package me.kkutuio.kkutuweb.configmanagement

import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

class ConfigJsonException(message: String) : RuntimeException(message)

/** 저장할 수 없는 이유 목록. 한 번에 모두 알려 주려고 첫 오류에서 멈추지 않는다. */
class ConfigRejectedException(val problems: List<String>) : RuntimeException(problems.joinToString("\n"))

enum class LockKind { SECRET, PROTECTED }

/** 가려지거나(SECRET) 보호되는(PROTECTED) 항목 하나. `segments` 로 JSON 안의 위치를 가리킨다. */
data class LockedPath(val kind: LockKind, val segments: List<String>) {
    val pointer: String get() = segments.joinToString("") { "/" + it.replace("~", "~0").replace("/", "~1") }
}

/**
 * 설정 JSON 을 읽고, 비밀 값을 가리고, 가려진·보호된 항목이 바뀌지 않았는지 확인하고, 다시 JSON 으로 쓴다.
 * 화면에는 비밀 값 대신 [SECRET_SENTINEL] 이 나가고, 저장할 때 그 자리를 원래 값으로 되돌린다. 비밀 값은 웹 배포 토큰 하나를 빼고 이 경로로 수정할 수 없다.
 */
object ConfigJson {
    const val SECRET_SENTINEL = "[비밀 값 - 수정 불가]"

    /** 웹 배포용 GitHub 토큰 자리(웹 kkutu.json). 만료되면 갈아 끼워야 해서, 비밀 값 중 유일하게 이 화면에서 새로 넣고 바꿀 수 있다(지우기는 불가). */
    private const val WEB_DEPLOY_TOKEN_POINTER = "/webDeploy/githubToken"

    /** 저장소와 권한을 좁혀 발급하는 fine-grained 토큰만 받는다. 값이 가려져 눈으로 확인할 수 없어 형식이 틀린 값은 저장 전에 막는다. */
    private val webDeployToken = Regex("^github_pat_[A-Za-z0-9_]{20,255}$")

    private val mapper: JsonMapper = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build()

    fun parse(text: String): JsonNode {
        val node = try {
            mapper.readTree(text)
        } catch (error: JacksonException) {
            throw ConfigJsonException("JSON 형식이 올바르지 않습니다: ${error.originalMessage?.lineSequence()?.firstOrNull()?.take(200) ?: "해석 실패"}")
        }
        if (node == null || node.isMissingNode) throw ConfigJsonException("JSON 내용이 비어 있습니다.")
        return node
    }

    // ───────────── 비밀·보호 항목 ─────────────

    private val secretFragments = listOf("secret", "token", "password", "passwd", "webhook", "credential", "privkey", "privatekey")

    /** 키 이름이 비밀 값처럼 보이는가. 놓치는 것보다 지나치게 가리는 쪽이 낫다(…key, …pass 로 끝나거나 secret·token·password 등을 포함). */
    fun isSecretKey(key: String): Boolean {
        val name = key.lowercase().filter { it.isLetterOrDigit() }
        return name.endsWith("key") || name.endsWith("keys") || name.endsWith("pass") || secretFragments.any(name::contains)
    }

    /** 이름은 비밀이 아니어도 권한·접속과 직결돼 이 화면에서 바꾸게 두지 않는 항목(웹 kkutu.json). */
    private fun isProtected(file: String, pointer: String): Boolean =
        file == "kkutu.json" && (pointer == "/adminMasters" || pointer == "/admins" || Regex("/gameServers/\\d+/management").matches(pointer))

    fun collectLocked(file: String, root: JsonNode): Map<String, LockedPath> {
        val found = LinkedHashMap<String, LockedPath>()
        fun walk(node: JsonNode, segments: List<String>, key: String?) {
            val here = LockedPath(LockKind.PROTECTED, segments)
            if (isProtected(file, here.pointer)) { found[here.pointer] = here; return }
            if (key != null && isSecretKey(key)) { found[here.pointer] = here.copy(kind = LockKind.SECRET); return }
            when {
                node.isObject -> node.propertyNames().toList().forEach { name -> walk(node.get(name), segments + name, name) }
                node.isArray -> (0 until node.size()).forEach { index -> walk(node.get(index), segments + index.toString(), null) }
            }
        }
        walk(root, emptyList(), null)
        return found
    }

    private fun isWritableSecret(file: String, pointer: String): Boolean = file == "kkutu.json" && pointer == WEB_DEPLOY_TOKEN_POINTER

    private fun isSentinel(node: JsonNode?): Boolean = node != null && node.isString && node.stringValue() == SECRET_SENTINEL

    private fun webDeployTokenProblem(node: JsonNode?): String? =
        if (node != null && node.isString && webDeployToken.matches(node.stringValue())) null
        else "$WEB_DEPLOY_TOKEN_POINTER 에는 github_pat_ 로 시작하는 GitHub fine-grained 토큰을 공백 없이 넣어야 합니다."

    private fun at(root: JsonNode, segments: List<String>): JsonNode? {
        var node: JsonNode = root
        for (segment in segments) {
            node = when {
                node.isObject -> node.get(segment) ?: return null
                node.isArray -> segment.toIntOrNull()?.let { node.get(it) } ?: return null
                else -> return null
            }
        }
        return node
    }

    private fun replaceAt(root: JsonNode, segments: List<String>, value: JsonNode) {
        val parent = at(root, segments.dropLast(1)) ?: return
        val last = segments.last()
        when (parent) {
            is ObjectNode -> parent.set(last, value)
            is ArrayNode -> last.toIntOrNull()?.let { parent.set(it, value) }
            else -> Unit
        }
    }

    /** 화면에 내보낼 사본: 비밀 값 자리를 [SECRET_SENTINEL] 로 바꾼다. 원본은 건드리지 않는다. */
    fun masked(file: String, original: JsonNode): JsonNode {
        val copy = original.deepCopy()
        collectLocked(file, original).values.filter { it.kind == LockKind.SECRET }.forEach {
            replaceAt(copy, it.segments, mapper.nodeFactory.stringNode(SECRET_SENTINEL))
        }
        return copy
    }

    /**
     * 사용자가 제출한 JSON 이 가려진·보호된 항목을 그대로 두었는지 확인하고, 비밀 값 자리에 원래 값을 되돌린 JSON 을 돌려준다.
     * 비밀 값을 바꾸거나 지우거나 새로 넣으면(키 이름이 비밀처럼 보이는 새 항목 포함) 거절한다.
     * 예외는 [WEB_DEPLOY_TOKEN_POINTER] 하나로, 형식에 맞는 토큰이면 새로 넣거나 바꿀 수 있다.
     */
    fun verifyAndRestore(file: String, original: JsonNode, submitted: JsonNode): JsonNode {
        val problems = mutableListOf<String>()
        val lockedOriginal = collectLocked(file, original)
        val lockedSubmitted = collectLocked(file, submitted)
        for ((pointer, locked) in lockedSubmitted) {
            if (pointer in lockedOriginal) continue
            if (isWritableSecret(file, pointer)) webDeployTokenProblem(at(submitted, locked.segments))?.let { problems += it }
            else problems += "새 비밀·보호 항목은 추가할 수 없습니다: $pointer"
        }
        for ((pointer, locked) in lockedOriginal) {
            val value = at(submitted, locked.segments)
            if (value == null) { problems += "비밀·보호 항목은 지울 수 없습니다: $pointer"; continue }
            when (locked.kind) {
                LockKind.SECRET -> when {
                    isSentinel(value) -> Unit
                    isWritableSecret(file, pointer) -> webDeployTokenProblem(value)?.let { problems += it }
                    else -> problems += "비밀 값은 수정할 수 없습니다: $pointer"
                }
                LockKind.PROTECTED -> if (value != at(original, locked.segments)) problems += "보호된 항목은 수정할 수 없습니다: $pointer"
            }
        }
        if (problems.isNotEmpty()) throw ConfigRejectedException(problems)
        val merged = submitted.deepCopy()
        lockedOriginal.values.filter { it.kind == LockKind.SECRET && isSentinel(at(submitted, it.segments)) }.forEach { locked ->
            replaceAt(merged, locked.segments, at(original, locked.segments)!!.deepCopy())
        }
        return merged
    }

    // ───────────── 검증 ─────────────

    private fun kind(node: JsonNode): String = when {
        node.isString -> "문자열"; node.isNumber -> "숫자"; node.isBoolean -> "참/거짓"
        node.isObject -> "객체"; node.isArray -> "배열"; else -> "null"
    }

    /** 원래 있던 값의 종류(문자열·숫자·참거짓·객체·배열)를 따옴표 실수 등으로 바꾸지 않았는지 본다. 원래 null 이던 값은 자유. 배열 안쪽은 보지 않는다. */
    fun typeChanges(original: JsonNode, edited: JsonNode): List<String> {
        val problems = mutableListOf<String>()
        fun walk(before: JsonNode, after: JsonNode, pointer: String) {
            if (before.isNull) return
            if (kind(before) != kind(after)) { problems += "${pointer.ifEmpty { "/" }}: ${kind(before)}였던 값이 ${kind(after)}로 바뀌었습니다."; return }
            if (before.isObject) before.propertyNames().toList().forEach { name ->
                after.get(name)?.let { walk(before.get(name), it, "$pointer/${name.replace("~", "~0").replace("/", "~1")}") }
            }
        }
        walk(original, edited, "")
        return problems
    }

    private fun JsonNode.strings() = isArray && toList().all { it.isString }

    /** 웹 서버가 시작할 때 읽는 모양을 지키는지 확인한다. 어긋나면 웹이 기동하지 못하므로 저장 전에 막는다. */
    fun validateWebStructure(file: String, root: JsonNode): List<String> {
        val problems = mutableListOf<String>()
        if (!root.isObject) return listOf("최상위는 객체여야 합니다.")
        fun need(ok: Boolean, message: String) { if (!ok) problems += message }
        when (file) {
            "kkutu.json" -> {
                need(root.get("version")?.isString == true, "version 은 문자열이어야 합니다.")
                need(root.get("maxPlayers")?.isIntegralNumber == true, "maxPlayers 는 정수여야 합니다.")
                listOf("cdnHost", "apiKey", "cryptoKey").forEach { need(root.get(it)?.isString == true, "$it 는 문자열이어야 합니다.") }
                val servers = root.get("gameServers")
                need(servers?.isArray == true && servers.size() > 0, "gameServers 는 비어 있지 않은 배열이어야 합니다.")
                servers?.takeIf { it.isArray }?.toList()?.forEachIndexed { index, server ->
                    val at = "gameServers[$index]"
                    need(server.isObject, "$at 는 객체여야 합니다.")
                    if (server.isObject) {
                        listOf("publicHost", "key", "host").forEach { need(server.get(it)?.isString == true, "$at.$it 는 문자열이어야 합니다.") }
                        listOf("port", "cid").forEach { need(server.get(it)?.isIntegralNumber == true, "$at.$it 는 정수여야 합니다.") }
                        need(server.get("isSecure")?.isBoolean == true, "$at.isSecure 는 참/거짓이어야 합니다.")
                    }
                }
                root.get("serviceMode")?.let { need(it.isString, "serviceMode 는 문자열이어야 합니다.") }
                root.get("mainPage")?.let { need(it.isObject, "mainPage 는 객체여야 합니다.") }
            }
            "games.json" -> {
                val rule = root.get("RULE"); val options = root.get("OPTIONS")
                need(rule?.isObject == true, "RULE 은 객체여야 합니다.")
                need(options?.isObject == true, "OPTIONS 는 객체여야 합니다.")
                options?.takeIf { it.isObject }?.propertyNames()?.toList()?.forEach { need(options.get(it).get("name")?.isString == true, "OPTIONS.$it.name 은 문자열이어야 합니다.") }
            }
            "moremi.json" -> {
                val moremi = root.get("moremi")
                need(moremi?.isObject == true, "moremi 는 객체여야 합니다.")
                if (moremi?.isObject == true) {
                    listOf("parts", "categories", "equips").forEach { need(moremi.get(it)?.strings() == true, "moremi.$it 는 문자열 배열이어야 합니다.") }
                    val groups = moremi.get("groups")
                    need(groups?.isObject == true && groups.propertyNames().toList().all { groups.get(it).strings() }, "moremi.groups 는 문자열 배열을 값으로 갖는 객체여야 합니다.")
                }
            }
            "themes.json" -> {
                val themes = root.get("word")?.get("themes")
                need(themes?.isObject == true, "word.themes 는 객체여야 합니다.")
                if (themes?.isObject == true) {
                    listOf("normal", "injeong").forEach { group -> listOf("ko", "en").forEach { lang ->
                        need(themes.get(group)?.get(lang)?.strings() == true, "word.themes.$group.$lang 은 문자열 배열이어야 합니다.")
                    } }
                    need(themes.get("ijpExcept")?.strings() == true, "word.themes.ijpExcept 는 문자열 배열이어야 합니다.")
                }
            }
        }
        return problems
    }

    // ───────────── 출력 ─────────────

    /** `JSON.stringify(value, null, 2)` 와 같은 모양(들여쓰기 2칸, 키 순서 유지)으로 쓴다. 끝에 줄바꿈 하나. */
    fun pretty(node: JsonNode): String = StringBuilder().also { write(node, it, 0) }.append('\n').toString()

    private fun write(node: JsonNode, out: StringBuilder, depth: Int) {
        fun indent(level: Int) = repeat(level) { out.append("  ") }
        when {
            node.isObject -> {
                val names = node.propertyNames().toList()
                if (names.isEmpty()) { out.append("{}"); return }
                out.append("{\n")
                names.forEachIndexed { index, name ->
                    indent(depth + 1); out.append(mapper.writeValueAsString(name)).append(": ")
                    write(node.get(name), out, depth + 1)
                    out.append(if (index < names.lastIndex) ",\n" else "\n")
                }
                indent(depth); out.append('}')
            }
            node.isArray -> {
                if (node.size() == 0) { out.append("[]"); return }
                out.append("[\n")
                (0 until node.size()).forEach { index ->
                    indent(depth + 1); write(node.get(index), out, depth + 1)
                    out.append(if (index < node.size() - 1) ",\n" else "\n")
                }
                indent(depth); out.append(']')
            }
            else -> out.append(mapper.writeValueAsString(node))
        }
    }

    /** 바뀐 위치 목록(값은 담지 않는다). 감사 로그와 저장 결과 안내에 쓴다. */
    fun changedPointers(before: JsonNode, after: JsonNode): List<String> {
        val changed = mutableListOf<String>()
        fun walk(a: JsonNode?, b: JsonNode?, segments: List<String>) {
            val pointer = LockedPath(LockKind.PROTECTED, segments).pointer.ifEmpty { "/" }
            when {
                a == null || b == null -> changed += pointer
                a.isObject && b.isObject -> (a.propertyNames().toList() + b.propertyNames().toList()).distinct().forEach { walk(a.get(it), b.get(it), segments + it) }
                a.isArray && b.isArray -> (0 until maxOf(a.size(), b.size())).forEach { walk(a.get(it), b.get(it), segments + it.toString()) }
                a != b -> changed += pointer
            }
        }
        walk(before, after, emptyList())
        return changed
    }
}
