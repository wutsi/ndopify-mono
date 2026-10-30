package com.wutsi.ndopify.web.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/** A public site: no sign-in, only the pages it serves are open. */
@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { customizer ->
                customizer
                    .requestMatchers("/", "/agent", "/owner").permitAll()
                    // A listing's page (ListingController); GET only: its visit and question forms are still the
                    // prototype's
                    .requestMatchers(HttpMethod.GET, "/*/listing/*", "/*/listing/*/*", "/*/*/listing/*", "/*/*/listing/*/*")
                    .permitAll()
                    .requestMatchers("/assets/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().denyAll() // nothing else is served
            }
            .csrf { customizer -> customizer.disable() }
            .httpBasic { customizer -> customizer.disable() }
            .formLogin { customizer -> customizer.disable() }
            .logout { customizer -> customizer.disable() }
            .build()
    }
}
