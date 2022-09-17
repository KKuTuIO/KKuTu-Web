package me.kkutuio.kkutuweb.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

@Configuration
class WebSecurityConfig : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
        http
            .headers().frameOptions().disable()
            .and()
            .authorizeRequests {
                it
                    .antMatchers("/actuator/**").authenticated()
                    .anyRequest().permitAll()
            }
            .httpBasic(Customizer.withDefaults())
            .csrf().disable()
            .headers().contentSecurityPolicy("default-src 'self'; script-src 'report-sample' 'self' https://www.gstatic.com https://cdn.kkutu.io https://browser.sentry-cdn.com https://static.cloudflareinsights.com/beacon.min.js https://t1.daumcdn.net https://www.google-analytics.com/analytics.js https://www.googletagmanager.com/gtm.js https://www.google.com/recaptcha/api.js 'nonce-csp-kkutu-a' 'nonce-csp-kkutu-b' 'nonce-csp-portal-a' 'nonce-gtm'; style-src 'unsafe-inline' 'report-sample' 'self' https://cdn.kkutu.io; object-src https://cdn.kkutu.io; base-uri 'self'; connect-src 'self' https://cdn.kkutu.io https://cloudflareinsights.com https://o1271663.ingest.sentry.io wss://ws.kkutu.io https://static.kkutu.io https://www.google-analytics.com https://display.ad.daum.net https://aem-ingest.onkakao.net; font-src 'self' https://cdn.kkutu.io; frame-src 'self' https://cdn.kkutu.io https://static.kkutu.io https://t1.daumcdn.net; img-src 'self' 'unsafe-inline' https://cdn.kkutu.io https://www.google-analytics.com; manifest-src 'self' https://cdn.kkutu.io; media-src 'self' https://cdn.kkutu.io; report-uri https://63242520ef389e2c71224945.endpoint.csper.io/?v=1; worker-src https://cdn.kkutu.io;")
            ;
    }
}