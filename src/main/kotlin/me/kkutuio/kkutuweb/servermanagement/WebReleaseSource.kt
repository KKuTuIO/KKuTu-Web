package me.kkutuio.kkutuweb.servermanagement

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

data class WebRelease(
    val tag: String,
    val name: String?,
    val publishedAt: String?,
    val notes: String?,
    val assetName: String,
    val size: Long,
    /** 소문자 hex SHA-256. GitHub가 digest를 제공하지 않으면 null이며, 이 경우 배포는 거부된다. */
    val sha256: String?,
    val downloadUrl: String
)

/** 웹 서버 jar 릴리스의 조회와 다운로드. 네트워크 계층을 분리해 배포 로직을 오프라인으로 테스트한다. */
interface WebReleaseSource {
    fun listReleases(): List<WebRelease>

    /** [release]의 jar를 [target]에 저장한다. [maxBytes]를 넘으면 중단하고 예외를 던진다. */
    fun download(release: WebRelease, target: Path, maxBytes: Long)
}

@Component
class GithubWebReleaseSource(
    private val objectMapper: ObjectMapper,
    @Value("\${server-management.web-deploy.repository:KKuTuIO/KKuTu-Web}") private val repository: String,
    @Value("\${server-management.web-deploy.release-cache-seconds:120}") cacheSeconds: Long
) : WebReleaseSource {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    private val cacheMillis = cacheSeconds.coerceIn(0, 3600) * 1000
    private var cached: List<WebRelease> = emptyList()
    private var cachedAt = 0L

    @Synchronized
    override fun listReleases(): List<WebRelease> {
        val now = System.currentTimeMillis()
        if (cachedAt != 0L && now - cachedAt < cacheMillis) return cached
        val body = fetchText("https://api.github.com/repos/$repository/releases?per_page=$PAGE_SIZE")
        val parsed = parseReleases(body)
        cached = parsed
        cachedAt = now
        return parsed
    }

    internal fun parseReleases(body: String): List<WebRelease> {
        val root = try {
            objectMapper.readTree(body)
        } catch (error: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub 릴리스 응답을 해석하지 못했습니다.")
        }
        if (root == null || !root.isArray) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub 릴리스 응답 형식이 올바르지 않습니다.")
        }
        return root.mapNotNull { release ->
            if (release["draft"]?.booleanValue() == true || release["prerelease"]?.booleanValue() == true) return@mapNotNull null
            val tag = release["tag_name"]?.stringValue() ?: return@mapNotNull null
            val asset = release["assets"]?.firstOrNull { asset ->
                val name = asset["name"]?.stringValue().orEmpty()
                JAR_ASSET.matches(name)
            } ?: return@mapNotNull null
            val url = asset["browser_download_url"]?.stringValue() ?: return@mapNotNull null
            if (!isAllowedUrl(url)) {
                logger.warn("web-release ignoring asset with disallowed url tag={} url={}", tag, url)
                return@mapNotNull null
            }
            WebRelease(
                tag = tag,
                name = release["name"]?.stringValue(),
                publishedAt = release["published_at"]?.stringValue(),
                notes = release["body"]?.stringValue()?.trim()?.take(NOTES_LIMIT)?.ifBlank { null },
                assetName = asset["name"].stringValue(),
                size = asset["size"]?.longValue() ?: 0L,
                sha256 = asset["digest"]?.stringValue()?.let(::parseDigest),
                downloadUrl = url
            )
        }
    }

    override fun download(release: WebRelease, target: Path, maxBytes: Long) {
        var url = release.downloadUrl
        repeat(MAX_REDIRECTS + 1) {
            requireAllowed(url)
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/octet-stream")
                .GET()
                .build()
            val response = send(request) { HttpResponse.BodyHandlers.ofInputStream() }
            when (val status = response.statusCode()) {
                200 -> {
                    response.body().use { input ->
                        val written = copyLimited(input, target, maxBytes)
                        logger.info("web-release downloaded tag={} bytes={}", release.tag, written)
                    }
                    return
                }
                301, 302, 303, 307, 308 -> {
                    response.body().close()
                    val location = response.headers().firstValue("Location").orElse(null)
                        ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "다운로드 리다이렉트 위치가 없습니다.")
                    url = URI.create(url).resolve(location).toString()
                }
                else -> {
                    response.body().close()
                    throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "릴리스 다운로드에 실패했습니다. (HTTP $status)")
                }
            }
        }
        throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "다운로드 리다이렉트가 너무 많습니다.")
    }

    private fun fetchText(url: String): String {
        requireAllowed(url, api = true)
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build()
        val response = send(request) { HttpResponse.BodyHandlers.ofString() }
        if (response.statusCode() != 200) {
            val hint = if (response.statusCode() == 403 || response.statusCode() == 429) " (GitHub API 호출 한도일 수 있습니다)" else ""
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "릴리스 목록을 가져오지 못했습니다. HTTP ${response.statusCode()}$hint")
        }
        return response.body()
    }

    private fun <T> send(request: HttpRequest, handler: () -> HttpResponse.BodyHandler<T>): HttpResponse<T> = try {
        client.send(request, handler())
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub 요청이 중단되었습니다.")
    } catch (error: IOException) {
        logger.warn("web-release request failed host={} message={}", request.uri().host, error.message)
        throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub에 연결하지 못했습니다.")
    }

    private fun copyLimited(input: java.io.InputStream, target: Path, maxBytes: Long): Long {
        Files.newOutputStream(target).use { output ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "릴리스 파일이 허용 크기를 넘었습니다.")
                }
                output.write(buffer, 0, read)
            }
            return total
        }
    }

    private fun requireAllowed(url: String, api: Boolean = false) {
        val allowed = if (api) url.startsWith("https://api.github.com/") else isAllowedUrl(url)
        if (!allowed) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "허용되지 않은 다운로드 주소입니다.")
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val MAX_REDIRECTS = 5
        private const val NOTES_LIMIT = 200
        private const val USER_AGENT = "KKuTuIO-Web-Deploy"
        private val JAR_ASSET = Regex("^kkutu-web-(?!.*-plain).*\\.jar$")

        internal fun parseDigest(value: String): String? {
            val hex = value.removePrefix("sha256:").lowercase()
            return hex.takeIf { value.startsWith("sha256:") && it.matches(Regex("^[0-9a-f]{64}$")) }
        }

        /** https이며 github.com 또는 GitHub 자산 CDN(*.githubusercontent.com)만 허용한다. */
        internal fun isAllowedUrl(url: String): Boolean {
            val uri = try {
                URI.create(url)
            } catch (error: IllegalArgumentException) {
                return false
            }
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme == "https" && uri.userInfo == null &&
                (host == "github.com" || host.endsWith(".githubusercontent.com"))
        }
    }
}
