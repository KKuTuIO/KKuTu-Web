package me.kkutuio.kkutuweb.handler

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.resource.NoResourceFoundException

class GlobalExceptionHandlerTest {
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(FailingController())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    fun `missing static resources retain HTTP 404 semantics`() {
        mockMvc.get("/missing-resource") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value(404) }
        }
    }

    @Test
    fun `unexpected server errors do not return a successful status`() {
        mockMvc.get("/failure") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isInternalServerError() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value(470) }
        }
    }

    @RestController
    private class FailingController {
        @GetMapping("/missing-resource")
        fun missingResource(): Nothing = throw NoResourceFoundException(
            HttpMethod.GET,
            "/missing-resource",
            "No static resource missing-resource."
        )

        @GetMapping("/failure")
        fun failure(): Nothing = throw IllegalStateException("test failure")
    }
}
