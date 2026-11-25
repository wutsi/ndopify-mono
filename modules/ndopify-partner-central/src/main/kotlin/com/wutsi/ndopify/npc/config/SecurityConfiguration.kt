package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.web.core.client.ndopify.NdTokenClient
import com.wutsi.ndopify.web.core.service.security.AccessTokenRenewalFilter
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.core.service.security.JwtAuthenticationFilter
import com.wutsi.ndopify.web.core.service.security.JwtDecoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import java.time.Clock

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val accessTokenService: AccessTokenService,
    private val jwtDecoder: JwtDecoder,
    private val tokenClient: NdTokenClient,
    private val clock: Clock,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { customizer ->
                customizer
                    .requestMatchers("/assets/**").permitAll()
                    .requestMatchers("/sw.js", "/offline.html", "/manifest.json", "/favicon.ico").permitAll()
                    .requestMatchers("/login", "/login/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/join", "/join/**").permitAll()
                    .requestMatchers("/find-property", "/find-property/**").permitAll()
                    .requestMatchers("/offer-property", "/offer-property/**").permitAll()
                    .requestMatchers("/locations", "/locations/**").permitAll()
                    .anyRequest().authenticated()
            }
            // The JWT filter first: Spring Security resolves the renewal one's anchor only once it is registered.
            .addFilterBefore(authorizationFilter(), AnonymousAuthenticationFilter::class.java)
            .addFilterBefore(accessTokenRenewalFilter(), JwtAuthenticationFilter::class.java)
            .exceptionHandling { it.authenticationEntryPoint(SignInTargetEntryPoint("/login")) }
            .csrf { customizer -> customizer.disable() }
            .httpBasic { customizer -> customizer.disable() }
            .logout { customizer -> customizer.disable() } // Disable spring logout, we handle it in our own controller
            .build()
    }

    @Bean
    fun authorizationFilter(): JwtAuthenticationFilter {
        return JwtAuthenticationFilter(accessTokenService, jwtDecoder)
    }

    // Not a @Bean: Spring Boot also registers every Filter bean as a plain servlet filter, which would renew twice.
    private fun accessTokenRenewalFilter(): AccessTokenRenewalFilter =
        AccessTokenRenewalFilter(accessTokenService, jwtDecoder, tokenClient, clock)
}
