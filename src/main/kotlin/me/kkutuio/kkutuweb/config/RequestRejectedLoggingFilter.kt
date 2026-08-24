package me.kkutuio.kkutuweb.config

import org.slf4j.LoggerFactory
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.web.firewall.RequestRejectedException
import java.io.IOException
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Configuration
class RequestRejectedLoggingFilter {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun requestRejectedLoggingFilterRegistration(): FilterRegistrationBean<Filter> {
        val registration = FilterRegistrationBean<Filter>()
        registration.filter = object : Filter {
            private val logger = LoggerFactory.getLogger("me.kkutuio.kkutuweb.security.RequestRejected")

            @Throws(IOException::class, ServletException::class)
            override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
                try {
                    chain.doFilter(request, response)
                } catch (exception: RequestRejectedException) {
                    val httpRequest = request as? HttpServletRequest
                    val httpResponse = response as? HttpServletResponse
                    logger.warn(
                        "Rejected request from {} {}: {}",
                        httpRequest?.remoteAddr ?: "unknown",
                        httpRequest?.requestURI ?: "unknown",
                        exception.message
                    )
                    if (httpResponse != null && !httpResponse.isCommitted) {
                        httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST)
                    }
                }
            }
        }
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}
