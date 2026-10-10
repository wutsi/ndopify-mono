package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.npc.service.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val authenticationFilter: JwtAuthenticationFilter
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { customizer ->
                customizer
                    .requestMatchers("/login").permitAll()
                    .requestMatchers("/login/**").permitAll()
                    .anyRequest().permitAll()
            }
            .addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter::class.java)
            .csrf { customizer -> customizer.disable() }
            .httpBasic { customizer -> customizer.disable() }
            // No formLogin: it would make POST /login a username/password endpoint and swallow LoginController's
            // OTP request. Sign-in is email + one-time code, handled entirely by LoginController.
            // Spring's own logout filter would intercept /logout before LogoutController, which clears our token cookie.
            .logout { customizer -> customizer.disable() }
            .build()
    }
}
