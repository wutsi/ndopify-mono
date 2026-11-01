package com.wutsi.ndopify.web.config

import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.core.service.security.JwtAuthenticationFilter
import com.wutsi.ndopify.web.core.service.security.JwtDecoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter

/**
 * A public site: only the pages it serves are open. The only sign-in is the `public-portal` token of a match link,
 * kept in a cookie by `MatchController` and read by [authorizationFilter]; the controller decides what a visitor
 * without it sees.
 */
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
                    .requestMatchers("/", "/agent", "/owner").permitAll()
                    // A listing's page (ListingController); GET only: its visit and question forms are still the
                    // prototype's
                    .requestMatchers(HttpMethod.GET, "/*/listing/*", "/*/listing/*/*", "/*/*/listing/*", "/*/*/listing/*/*")
                    .permitAll()
                    // A match proposal (MatchController): the page, and the customer's answer (CSRF-protected)
                    .requestMatchers(HttpMethod.GET, "/match/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/match/*/accept", "/match/*/reject").permitAll()
                    .requestMatchers("/assets/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().denyAll() // nothing else is served
            }
            .addFilterBefore(authorizationFilter(), AnonymousAuthenticationFilter::class.java)
            .httpBasic { customizer -> customizer.disable() }
            .formLogin { customizer -> customizer.disable() }
            .logout { customizer -> customizer.disable() }
            .build()
    }

    @Bean
    fun authorizationFilter(): JwtAuthenticationFilter = JwtAuthenticationFilter(accessTokenService, jwtDecoder)
}
