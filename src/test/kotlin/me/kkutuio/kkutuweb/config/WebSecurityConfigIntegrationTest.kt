package me.kkutuio.kkutuweb.config

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity

@SpringJUnitWebConfig(
    classes = [
        WebSecurityConfig::class,
        WebSecurityConfigIntegrationTest.TestWebConfiguration::class,
        WebSecurityConfigIntegrationTest.CsrfController::class
    ]
)
class WebSecurityConfigIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Test
    fun `SPA can submit the plain cookie token in the XSRF header`() {
        val tokenCookie = requireNotNull(
            mockMvc.get("/api/account/csrf")
                .andExpect { status { isOk() } }
                .andReturn()
                .response
                .getCookie("XSRF-TOKEN")
        )

        mockMvc.patch("/api/account/test-mutation") {
            cookie(tokenCookie)
            header("X-XSRF-TOKEN", tokenCookie.value)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `cookie alone cannot authorize an account mutation`() {
        val tokenCookie = requireNotNull(
            mockMvc.get("/api/account/csrf")
                .andReturn()
                .response
                .getCookie("XSRF-TOKEN")
        )

        mockMvc.patch("/api/account/test-mutation") {
            cookie(tokenCookie)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    class TestWebConfiguration

    @RestController
    class CsrfController {
        @GetMapping("/api/account/csrf")
        fun csrf(request: HttpServletRequest): Map<String, String> {
            val token = request.getAttribute(CsrfToken::class.java.name) as CsrfToken
            return mapOf("token" to token.token, "header" to token.headerName)
        }

        @PatchMapping("/api/account/test-mutation")
        fun mutate() = org.springframework.http.ResponseEntity.noContent().build<Void>()
    }
}
