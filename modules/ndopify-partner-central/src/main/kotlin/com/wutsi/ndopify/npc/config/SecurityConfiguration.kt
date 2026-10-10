package com.wutsi.koki.portal.config

import com.wutsi.koki.platform.security.AccessTokenHolder
import com.wutsi.koki.platform.security.servlet.JWTAuthenticationFilter
import com.wutsi.koki.portal.security.LogoutSuccessHandlerImpl
import com.wutsi.koki.security.dto.JWTDecoder
import jakarta.servlet.Filter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val logoutSuccessHandler: LogoutSuccessHandlerImpl,
    private val accessTokenHolder: AccessTokenHolder,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { customizer ->
                customizer
                    .requestMatchers("/").authenticated()
                    .requestMatchers("/accounts").authenticated()
                    .requestMatchers("/accounts/**").authenticated()
                    .requestMatchers("/agents").authenticated()
                    .requestMatchers("/agents/**").authenticated()
                    .requestMatchers("/contacts").authenticated()
                    .requestMatchers("/contacts/**").authenticated()
                    .requestMatchers("/images").authenticated()
                    .requestMatchers("/images/**").authenticated()
                    .requestMatchers("/leads").authenticated()
                    .requestMatchers("/leads/**").authenticated()
                    .requestMatchers("/listings").authenticated()
                    .requestMatchers("/listings/**").authenticated()
                    .requestMatchers("/offers").authenticated()
                    .requestMatchers("/offers/**").authenticated()
                    .requestMatchers("/settings").authenticated()
                    .requestMatchers("/settings/**").authenticated()
                    .requestMatchers("/users").authenticated()
                    .requestMatchers("/users/**").authenticated()
                    .anyRequest().permitAll()
            }
            .addFilterBefore(authorizationFilter(), AnonymousAuthenticationFilter::class.java)
            .csrf { customizer -> customizer.disable() }
            .httpBasic { customizer -> customizer.disable() }
            .formLogin { customizer ->
                customizer.loginPage("/login")
            }
            .logout { customizer ->
                customizer.logoutSuccessHandler(logoutSuccessHandler)
            }
            .build()
    }

    @Bean
    fun authorizationFilter(): Filter {
        return JWTAuthenticationFilter(accessTokenHolder, jwtDecoder())
    }

    @Bean
    fun jwtDecoder(): JWTDecoder {
        return JWTDecoder()
    }
}
