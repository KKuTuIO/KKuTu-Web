package me.kkutuio.kkutuweb.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySourcesPropertyResolver
import org.springframework.core.io.ClassPathResource

class RuntimePackagingConfigurationTest {
    @Test
    fun `admin client defaults to the reachable HTTPS endpoint with bounded retries`() {
        val sources = MutablePropertySources().apply {
            YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
                .forEach(::addLast)
        }
        val properties = PropertySourcesPropertyResolver(sources)

        assertThat(properties.getProperty("spring.boot.admin.client.enabled", Boolean::class.java))
            .isTrue()
        assertThat(properties.getProperty("spring.boot.admin.client.url"))
            .isEqualTo("https://sba.kkutu.io")
        assertThat(properties.getProperty("spring.boot.admin.client.connect-timeout"))
            .isEqualTo("3s")
        assertThat(properties.getProperty("spring.boot.admin.client.read-timeout"))
            .isEqualTo("5s")
        assertThat(properties.getProperty("spring.boot.admin.client.period"))
            .isEqualTo("30s")
    }

    @Test
    fun `admin client can be disabled without changing the packaged configuration`() {
        val sources = MutablePropertySources().apply {
            addFirst(
                MapPropertySource(
                    "deployment",
                    mapOf("SPRING_BOOT_ADMIN_CLIENT_ENABLED" to "false")
                )
            )
            YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
                .forEach(::addLast)
        }
        val properties = PropertySourcesPropertyResolver(sources)

        assertThat(properties.getProperty("spring.boot.admin.client.enabled", Boolean::class.java))
            .isFalse()
    }
}
