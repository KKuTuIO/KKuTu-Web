package me.kkutuio.kkutuweb.config

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.status.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogbackConfigurationTest {
    @Test
    fun `production Logback configuration parses without errors`() {
        val context = LoggerContext()
        try {
            val configuration = requireNotNull(javaClass.getResourceAsStream("/logback-spring.xml"))
            configuration.use {
                JoranConfigurator().apply { this.context = context }.doConfigure(it)
            }

            assertThat(context.statusManager.copyOfStatusList.filter { it.level == Status.ERROR })
                .isEmpty()
        } finally {
            context.stop()
        }
    }
}
