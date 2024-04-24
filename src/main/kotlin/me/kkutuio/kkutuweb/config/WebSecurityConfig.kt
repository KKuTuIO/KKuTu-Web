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
                .contentSecurityPolicy("default-src 'self'; script-src 'self' https://www.gstatic.com https://cdn.kkutu.io https://browser.sentry-cdn.com https://static.cloudflareinsights.com/beacon.min.js https://t1.daumcdn.net https://www.google-analytics.com/analytics.js https://www.googletagmanager.com https://www.pagespeed-mod.com https://challenges.cloudflare.com/turnstile/v0/api.js https://storage.googleapis.com/workbox-cdn/releases/ 'nonce-tbAXZGg7Jxozw4td6wAzkqXAWMuQXnjY'; style-src 'unsafe-inline' 'self' https://cdn.kkutu.io; object-src https://cdn.kkutu.io; base-uri 'self'; connect-src 'self' https://cdn.kkutu.io https://cloudflareinsights.com https://o1271663.ingest.sentry.io https://stats.g.doubleclick.net wss://ws.kkutu.io:* wss://us.kkutu.io:* wss://test.kkutu.io:* https://static.kkutu.io https://www.google-analytics.com https://display.ad.daum.net https://aem-ingest.onkakao.net https://aem-kakao-collector.onkakao.net https://analytics.google.com; font-src 'self' https://fonts.gstatic.com https://cdn.kkutu.io; frame-src 'self' https://cdn.kkutu.io https://static.kkutu.io https://t1.daumcdn.net https://www.google.com https://challenges.cloudflare.com https://youtube.com https://www.youtube.com; img-src 'self' 'unsafe-inline' https://cdn.kkutu.io https://www.google-analytics.com data:; manifest-src 'self' https://cdn.kkutu.io; media-src 'self' https://cdn.kkutu.io")
            ;
    }
}