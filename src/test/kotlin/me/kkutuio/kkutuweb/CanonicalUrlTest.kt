package me.kkutuio.kkutuweb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.thymeleaf.context.WebContext
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.web.servlet.JakartaServletWebApplication
import java.util.Locale

class CanonicalUrlTest {
    @Test
    fun `canonical URL preserves the normalized request path`() {
        assertThat(canonicalUrl("/game/server/0"))
            .isEqualTo("https://kkutu.io/game/server/0")
    }

    @Test
    fun `canonical URL falls back to the origin for unsafe paths`() {
        listOf("", "game/server/0", "//other.example/path", "/game\r\nX-Test: value").forEach { path ->
            assertThat(canonicalUrl(path)).isEqualTo("https://kkutu.io/")
        }
    }

    @Test
    fun `layouts use model data instead of removed servlet expression objects`() {
        listOf("layout.html", "m_layout.html").forEach { template ->
            val source = ClassPathResource("templates/$template").getContentAsString(Charsets.UTF_8)

            assertThat(source)
                .contains("th:href=\"\${canonicalUrl}\"")
                .doesNotContain("#httpServletRequest")
        }
    }

    @Test
    fun `desktop and mobile layouts render a canonical URL on Thymeleaf 3`() {
        val servletContext = MockServletContext()
        val request = MockHttpServletRequest(servletContext, "GET", "/game/server/0")
        val response = MockHttpServletResponse()
        val exchange = JakartaServletWebApplication.buildApplication(servletContext)
            .buildExchange(request, response)
        val context = WebContext(
            exchange,
            Locale.KOREAN,
            mapOf(
                "canonicalUrl" to canonicalUrl(requireNotNull(request.requestURI)),
                "runnerVersion" to "test",
                "cdnHost" to "https://cdn.kkutu.io",
                "profile" to "null",
                "messages" to emptyMap<String, String>(),
                "goodDetails" to emptyMap<String, String>(),
                "mobile" to false,
                "viewName" to "view/loginFailed"
            )
        )
        val templateEngine = SpringTemplateEngine().apply {
            setTemplateResolver(ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = Charsets.UTF_8.name()
            })
        }

        listOf("layout", "m_layout").forEach { template ->
            assertThat(templateEngine.process(template, context))
                .contains("rel=\"canonical\" href=\"https://kkutu.io/game/server/0\"")
        }
    }
}
