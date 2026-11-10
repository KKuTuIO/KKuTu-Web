package me.kkutuio.kkutuweb.servermanagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class WebJarDeployServiceTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var jar: Path
    private val terminated = CountDownLatch(1)
    private val watchdogCalls = mutableListOf<Pair<Path, Path>>()
    private var locatedJar: Path? = null

    @BeforeEach
    fun setUp() {
        jar = dir.resolve("kkutu-web.jar")
        writeJar(jar, startClass = "com.example.App", marker = "old")
        locatedJar = jar
    }

    private fun writeJar(path: Path, startClass: String?, marker: String, mainClass: String? = "org.springframework.boot.loader.launch.JarLauncher") {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainClass?.let { mainAttributes[Attributes.Name.MAIN_CLASS] = it }
            startClass?.let { mainAttributes.putValue("Start-Class", it) }
        }
        JarOutputStream(Files.newOutputStream(path), manifest).use {
            it.putNextEntry(JarEntry("marker.txt"))
            it.write(marker.toByteArray())
            it.closeEntry()
        }
    }

    private fun release(tag: String, bytes: ByteArray, sha256: String? = sha(bytes)) =
        WebRelease(tag, tag, "2026-11-01T00:00:00Z", "Update", "kkutu-web-v4.jar", bytes.size.toLong(), sha256, "https://github.com/x/y/releases/download/$tag/kkutu-web-v4.jar")

    private fun sha(bytes: ByteArray): String {
        val temp = Files.createTempFile(dir, "sha", ".bin")
        Files.write(temp, bytes)
        return WebJarDeployService.sha256(temp).also { Files.delete(temp) }
    }

    private fun newJarBytes(startClass: String? = "com.example.App", marker: String = "new", mainClass: String? = "org.springframework.boot.loader.launch.JarLauncher"): ByteArray {
        val temp = Files.createTempFile(dir, "new", ".jar")
        writeJar(temp, startClass, marker, mainClass)
        return Files.readAllBytes(temp).also { Files.delete(temp) }
    }

    private fun service(releases: List<WebRelease>, payloads: Map<String, ByteArray>): WebJarDeployService {
        val source = object : WebReleaseSource {
            override fun listReleases() = releases
            override fun download(release: WebRelease, target: Path, maxBytes: Long) {
                Files.write(target, payloads.getValue(release.tag))
            }
        }
        return WebJarDeployService(
            source,
            { locatedJar },
            WebServerRestartService({ terminated.countDown() }, 500),
            { j, p -> watchdogCalls += j to p }
        )
    }

    private fun await(service: WebJarDeployService, snapshot: WebDeploySnapshot): WebDeploySnapshot {
        repeat(100) {
            val current = service.operation(snapshot.id)
            if (current.status != WebDeployStatus.RUNNING) return current
            Thread.sleep(50)
        }
        error("deploy did not finish: ${service.operation(snapshot.id)}")
    }

    @Test
    fun `replaces the jar atomically keeps the previous one and schedules a restart`() {
        val bytes = newJarBytes()
        val service = service(listOf(release("kkutu-web-v10", bytes)), mapOf("kkutu-web-v10" to bytes))

        val result = await(service, service.deploy("kkutu-web-v10", "actor"))

        assertEquals(WebDeployStatus.SUCCEEDED, result.status, result.output)
        assertTrue(Files.readAllBytes(jar).contentEquals(bytes))
        assertTrue(Files.exists(dir.resolve("kkutu-web.jar.prev")))
        assertFalse(Files.exists(dir.resolve("kkutu-web.jar.download")))
        assertEquals(listOf(jar to dir.resolve("kkutu-web.jar.prev")), watchdogCalls)
        assertTrue(terminated.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun `refuses a download whose sha256 differs and leaves the running jar untouched`() {
        val bytes = newJarBytes()
        val before = Files.readAllBytes(jar)
        val service = service(listOf(release("kkutu-web-v10", bytes, sha256 = "0".repeat(64))), mapOf("kkutu-web-v10" to bytes))

        val result = await(service, service.deploy("kkutu-web-v10", "actor"))

        assertEquals(WebDeployStatus.FAILED, result.status)
        assertTrue(Files.readAllBytes(jar).contentEquals(before))
        assertFalse(Files.exists(dir.resolve("kkutu-web.jar.prev")))
        assertFalse(Files.exists(dir.resolve("kkutu-web.jar.download")))
        assertTrue(watchdogCalls.isEmpty())
        assertEquals(1, terminated.count)
    }

    @Test
    fun `rejects a jar built for a different application`() {
        val bytes = newJarBytes(startClass = "com.other.App")
        val before = Files.readAllBytes(jar)
        val service = service(listOf(release("kkutu-web-v10", bytes)), mapOf("kkutu-web-v10" to bytes))

        val result = await(service, service.deploy("kkutu-web-v10", "actor"))

        assertEquals(WebDeployStatus.FAILED, result.status)
        assertTrue(Files.readAllBytes(jar).contentEquals(before))
    }

    @Test
    fun `rejects a jar without spring boot launcher attributes`() {
        val bytes = newJarBytes(startClass = null, mainClass = null)
        val service = service(listOf(release("kkutu-web-v10", bytes)), mapOf("kkutu-web-v10" to bytes))

        assertEquals(WebDeployStatus.FAILED, await(service, service.deploy("kkutu-web-v10", "actor")).status)
    }

    @Test
    fun `validates the tag and requires a listed release with a digest`() {
        val bytes = newJarBytes()
        val service = service(
            listOf(release("kkutu-web-v10", bytes, sha256 = null)),
            mapOf("kkutu-web-v10" to bytes)
        )

        assertEquals(HttpStatus.BAD_REQUEST.value(), status { service.deploy("../etc/passwd", "actor") })
        assertEquals(HttpStatus.NOT_FOUND.value(), status { service.deploy("kkutu-web-v99", "actor") })
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), status { service.deploy("kkutu-web-v10", "actor") })
    }

    @Test
    fun `refuses to redeploy the version that is already running`() {
        val current = Files.readAllBytes(jar)
        val service = service(listOf(release("kkutu-web-v10", current)), mapOf("kkutu-web-v10" to current))

        assertEquals(HttpStatus.CONFLICT.value(), status { service.deploy("kkutu-web-v10", "actor") })
        assertTrue(service.listReleases().releases.single().current)
    }

    @Test
    fun `reports that a non-jar runtime cannot be replaced`() {
        locatedJar = null
        val bytes = newJarBytes()
        val service = service(listOf(release("kkutu-web-v10", bytes)), mapOf("kkutu-web-v10" to bytes))

        assertFalse(service.inspectRuntime().replaceable)
        assertEquals(HttpStatus.CONFLICT.value(), status { service.deploy("kkutu-web-v10", "actor") })
    }

    private fun status(block: () -> Unit): Int =
        assertThrows(ResponseStatusException::class.java) { block() }.statusCode.value()
}
