package me.kkutuio.kkutuweb.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

@Configuration
class WebSecurityConfig : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
        http
            .authorizeRequests {
                it
                    .antMatchers("/actuator/**").authenticated()
                    .anyRequest().permitAll()
            }
            .httpBasic(Customizer.withDefaults())
            .csrf().disable()
            .headers()
                .frameOptions().disable()
                .contentSecurityPolicy("default-src 'self'; script-src 'self' https://www.gstatic.com https://cdn.kkutu.io https://browser.sentry-cdn.com https://static.cloudflareinsights.com/beacon.min.js https://t1.daumcdn.net https://www.google-analytics.com/analytics.js https://www.googletagmanager.com/gtm.js https://www.google.com/recaptcha/api.js 'nonce-tbAXZGg7Jxozw4td6wAzkqXAWMuQXnjY'; style-src 'unsafe-inline' 'self' https://cdn.kkutu.io; object-src https://cdn.kkutu.io; base-uri 'self'; connect-src 'self' https://cdn.kkutu.io https://cloudflareinsights.com https://o1271663.ingest.sentry.io wss://ws.kkutu.io:* wss://test.kkutu.io:* https://static.kkutu.io https://www.google-analytics.com https://display.ad.daum.net https://aem-ingest.onkakao.net; font-src 'self' https://cdn.kkutu.io; frame-src 'self' https://cdn.kkutu.io https://static.kkutu.io https://t1.daumcdn.net https://www.google.com https://recaptcha.google.com/recaptcha https://youtube.com https://www.youtube.com; img-src 'self' 'unsafe-inline' https://cdn.kkutu.io https://www.google-analytics.com data:; manifest-src 'self' https://cdn.kkutu.io; media-src 'self' https://cdn.kkutu.io")
            ;
    }
}