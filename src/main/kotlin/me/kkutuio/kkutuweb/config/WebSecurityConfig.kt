package me.kkutuio.kkutuweb.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.util.matcher.RequestMatcher
import javax.servlet.http.HttpServletRequest

@Configuration
class WebSecurityConfig : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
        val csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
            setCookiePath("/")
        }
        http
            .authorizeRequests {
                it
                    .antMatchers("/actuator/**").authenticated()
                    .anyRequest().permitAll()
            }
            .httpBasic(Customizer.withDefaults())
            // The legacy API predates CSRF tokens.  Protect every cookie-backed
            // IdP account mutation and the Consent form without breaking its
            // independent, bearer-authenticated API endpoints.
            .csrf()
                .csrfTokenRepository(csrfRepository)
                .requireCsrfProtectionMatcher(IdpCsrfRequestMatcher)
                .and()
            .headers()
                .frameOptions().sameOrigin()
    }

    private object IdpCsrfRequestMatcher : RequestMatcher {
        private val unsafeMethods = setOf("POST", "PUT", "PATCH", "DELETE")
        private val publicAccountPaths = setOf(
            "/api/account/recovery/request",
            "/api/account/recovery/reset",
            "/api/account/password/login",
            "/api/account/passkeys/authentication/options",
            "/api/account/passkeys/authentication/complete",
            "/api/account/email/confirm"
        )

        override fun matches(request: HttpServletRequest): Boolean {
            if (request.method !in unsafeMethods) return false
            if (request.getHeader("Authorization")?.startsWith("Bearer ") == true) return false
            val path = request.requestURI.removePrefix(request.contextPath)
            return path == "/oauth/authorize/consent" || path == "/account/recovery/login" ||
                path.startsWith("/api/admin/oauth-clients") ||
                (path.startsWith("/api/account/") && path !in publicAccountPaths)
        }
    }
}
