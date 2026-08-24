package me.kkutuio.kkutuweb.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.ObjectMapper

class Jackson3AutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))

    @Test
    fun `Boot configures a Jackson 3 mapper with Kotlin data class support`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(ObjectMapper::class.java)

            val mapper = context.getBean(ObjectMapper::class.java)
            val encoded = mapper.writeValueAsString(SamplePayload("kkutu", 25))

            assertThat(mapper.readValue(encoded, SamplePayload::class.java))
                .isEqualTo(SamplePayload("kkutu", 25))
        }
    }

    private data class SamplePayload(val name: String, val javaVersion: Int)
}
