package me.kkutuio.kkutuweb.servermanagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class GithubWebReleaseSourceTest {
    private val source = GithubWebReleaseSource(JsonMapper.builder().build(), "KKuTuIO/KKuTu-Web", 0)
    private val digest = "a".repeat(64)

    @Test
    fun `parses jar assets and skips drafts, prereleases, plain jars and foreign hosts`() {
        val json = """
        [
          {"tag_name":"kkutu-web-v3","name":"v3","published_at":"2026-11-03T00:00:00Z","body":"Update","draft":false,"prerelease":false,
           "assets":[
             {"name":"kkutu-web-v4-plain.jar","size":1,"browser_download_url":"https://github.com/a/b/releases/download/kkutu-web-v3/kkutu-web-v4-plain.jar","digest":"sha256:$digest"},
             {"name":"kkutu-web-v4.jar","size":105,"browser_download_url":"https://github.com/a/b/releases/download/kkutu-web-v3/kkutu-web-v4.jar","digest":"sha256:$digest"}]},
          {"tag_name":"kkutu-web-v2","draft":true,"assets":[{"name":"kkutu-web-v4.jar","size":1,"browser_download_url":"https://github.com/x.jar"}]},
          {"tag_name":"kkutu-web-v1","prerelease":true,"assets":[{"name":"kkutu-web-v4.jar","size":1,"browser_download_url":"https://github.com/x.jar"}]},
          {"tag_name":"kkutu-web-v0","assets":[{"name":"kkutu-web-v4.jar","size":1,"browser_download_url":"https://evil.example.com/kkutu-web-v4.jar"}]},
          {"tag_name":"kkutu-web-v-1","assets":[{"name":"kkutu-web-v4.jar","size":1,"browser_download_url":"https://github.com/a/b/releases/download/x/kkutu-web-v4.jar"}]}
        ]
        """.trimIndent()

        val releases = source.parseReleases(json)

        assertEquals(listOf("kkutu-web-v3", "kkutu-web-v-1"), releases.map { it.tag })
        assertEquals("kkutu-web-v4.jar", releases[0].assetName)
        assertEquals(digest, releases[0].sha256)
        assertEquals(105L, releases[0].size)
        assertNull(releases[1].sha256)
    }

    @Test
    fun `only accepts github https hosts for downloads`() {
        assertTrue(GithubWebReleaseSource.isAllowedUrl("https://github.com/a/b/releases/download/t/x.jar"))
        assertTrue(GithubWebReleaseSource.isAllowedUrl("https://release-assets.githubusercontent.com/x"))
        assertFalse(GithubWebReleaseSource.isAllowedUrl("http://github.com/a"))
        assertFalse(GithubWebReleaseSource.isAllowedUrl("https://github.com.evil.com/a"))
        assertFalse(GithubWebReleaseSource.isAllowedUrl("https://user@github.com/a"))
        assertFalse(GithubWebReleaseSource.isAllowedUrl("https://evilgithubusercontent.com/a"))
        assertFalse(GithubWebReleaseSource.isAllowedUrl("not a url"))
    }

    @Test
    fun `parses only well-formed sha256 digests`() {
        assertEquals(digest, GithubWebReleaseSource.parseDigest("sha256:${digest.uppercase()}"))
        assertNull(GithubWebReleaseSource.parseDigest("sha1:$digest"))
        assertNull(GithubWebReleaseSource.parseDigest("sha256:abc"))
    }
}
