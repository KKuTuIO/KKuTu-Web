package me.kkutuio.kkutuweb.servermanagement

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Paths

/** 웹 릴리스를 받을 때 쓰는 GitHub 토큰. 설정하지 않았으면 null 이며, 이때는 인증 없이 요청한다. */
fun interface WebDeployTokenSource {
    fun token(): String?
}

/**
 * 웹 `kkutu.json` 의 `webDeploy.githubToken`. 설정 편집기에서 바꾼 값이 재시작 없이 적용되도록 부를 때마다 파일을 읽는다.
 * 오류 메시지와 로그에는 값을 남기지 않는다.
 */
@Component
class KkutuJsonWebDeployTokenSource(
    private val applicationArguments: ApplicationArguments,
    private val objectMapper: ObjectMapper
) : WebDeployTokenSource {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun token(): String? {
        val directory = applicationArguments.getOptionValues("SETTING_DIR")?.firstOrNull() ?: return null
        val root = try {
            objectMapper.readTree(Files.readString(Paths.get(directory, "kkutu.json")))
        } catch (error: Exception) {
            logger.warn("web-release could not read kkutu.json error={}", error.javaClass.simpleName)
            fail("kkutu.json 을 읽지 못해 배포 토큰을 확인할 수 없습니다.")
        }
        val node = root?.get("webDeploy")?.get("githubToken") ?: return null
        val value = if (node.isString) node.stringValue() else null
        // HTTP 헤더에 못 넣는 값은 JDK 예외 메시지에 값이 그대로 실리므로 먼저 걸러 낸다.
        if (value == null || !HEADER_SAFE.matches(value)) fail("kkutu.json 의 webDeploy.githubToken 형식이 올바르지 않습니다.")
        return value
    }

    private fun fail(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_GATEWAY, message)

    private companion object {
        val HEADER_SAFE = Regex("^[A-Za-z0-9_]{1,255}$")
    }
}
