package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.npc.service.security.AccessTokenService
import com.wutsi.ndopify.npc.service.security.JwtAuthenticationFilter
import com.wutsi.ndopify.npc.service.security.JwtDecoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint

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
                    .requestMatchers("/login", "/login/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/join", "/join/**").permitAll()
                    .requestMatchers("/locations", "/locations/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(authorizationFilter(), AnonymousAuthenticationFilter::class.java)
            .exceptionHandling { it.authenticationEntryPoint(LoginUrlAuthenticationEntryPoint("/login")) }
            .csrf { customizer -> customizer.disable() }
            .httpBasic { customizer -> customizer.disable() }
            .build()
    }

    @Bean
    fun authorizationFilter(): JwtAuthenticationFilter {
        return JwtAuthenticationFilter(accessTokenService, jwtDecoder)
    }
}
