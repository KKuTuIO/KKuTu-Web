package me.kkutuio.kkutuweb.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CdnUrlRewriteFilterTest {
    @Test
    fun `rewrites absolute and protocol-relative cdn references`() {
        val html = """<link href="//cdn.kkutu.io"><img src='https://cdn.kkutu.io/img/a.png'>"""
        assertEquals(
            """<link href="//127.0.0.1:8081"><img src='http://127.0.0.1:8081/img/a.png'>""",
            CdnUrlRewriteFilter.rewrite(html, "http://127.0.0.1:8081")
        )
    }

    @Test
    fun `rewrites slash-escaped json references`() {
        assertEquals(
            """{"a":"http:\/\/127.0.0.1:8081\/img\/a.png"}""",
            CdnUrlRewriteFilter.rewrite("""{"a":"https:\/\/cdn.kkutu.io\/img\/a.png"}""", "http://127.0.0.1:8081")
        )
    }

    @Test
    fun `leaves unrelated text untouched`() {
        assertEquals("https://static.kkutu.io/x", CdnUrlRewriteFilter.rewrite("https://static.kkutu.io/x", "http://a"))
    }
}
