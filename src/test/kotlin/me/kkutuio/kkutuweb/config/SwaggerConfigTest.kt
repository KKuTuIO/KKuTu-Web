package me.kkutuio.kkutuweb.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SwaggerConfigTest {
    @Test
    fun `openapi preserves metadata and bearer contract`() {
        val setting = mock(KKuTuSetting::class.java)
        `when`(setting.getVersion()).thenReturn("v4-test")

        val api = SwaggerConfig(setting).openApi()

        assertEquals("끄투리오 웹 API", api.info.title)
        assertEquals("v4-test", api.info.version)
        assertEquals("bearer", api.components.securitySchemes["Bearer"]?.scheme)
    }

    @Test
    fun `only admin operations advertise bearer authorization`() {
        val config = SwaggerConfig(mock(KKuTuSetting::class.java))
        val publicOperation = Operation()
        val adminOperation = Operation()
        val api = OpenAPI().paths(
            io.swagger.v3.oas.models.Paths()
                .addPathItem("/api/rank", PathItem().get(publicOperation))
                .addPathItem("/api/admin/profile", PathItem().get(adminOperation))
        )

        config.adminApiSecurity().customise(api)

        assertNull(publicOperation.security)
        assertEquals(listOf("Bearer"), adminOperation.security.single().keys.toList())
    }
}
