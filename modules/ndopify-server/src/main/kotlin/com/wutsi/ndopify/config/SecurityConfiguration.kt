package com.wutsi.ndopify.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import kotlin.jvm.java

@EnableWebSecurity
@Configuration
open class SecurityConfiguration {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SecurityConfiguration::class.java)
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        LOGGER.info(">>> Configuring HttpSecurity")
        return http.csrf { customizer -> customizer.disable() }
            .sessionManagement { customizer -> customizer.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { customizer ->
                customizer.anyRequest().permitAll()
            }.build()
    }
}
