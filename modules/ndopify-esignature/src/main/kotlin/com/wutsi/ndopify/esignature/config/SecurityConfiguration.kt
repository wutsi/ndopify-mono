package com.wutsi.ndopify.esignature.config

import com.wutsi.ndopify.esignature.service.security.AccessTokenService
import com.wutsi.ndopify.esignature.service.security.JwtAuthenticationFilter
import com.wutsi.ndopify.esignature.service.security.JwtDecoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val accessTokenService: AccessTokenService,
    private val jwtDecoder: JwtDecoder,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { customizer ->
                customizer
                    .requestMatchers("/assets/**").permitAll()
                    .requestMatchers("/sign", "/sign/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().denyAll() // nothing else is served
            }
            .addFilterBefore(authorizationFilter(), AnonymousAuthenticationFilter::class.java)
            .csrf { customizer -> customizer.disable() }
            .httpBasic { customizer -> customizer.disable() }
            .logout { customizer -> customizer.disable() } // Disable spring logout, we handle it in our own controller
            .build()
    }

    @Bean
    fun authorizationFilter(): JwtAuthenticationFilter {
        return JwtAuthenticationFilter(accessTokenService, jwtDecoder)
    }
}
