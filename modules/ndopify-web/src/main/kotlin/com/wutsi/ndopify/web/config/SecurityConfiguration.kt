package com.wutsi.ndopify.web.config

import com.wutsi.ndopify.web.core.client.ndopify.NdTokenClient
import com.wutsi.ndopify.web.core.service.security.AccessTokenRenewalFilter
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
import java.time.Clock

/**
 * A public site: only the pages it serves are open. The only sign-in is the `public-portal` token of a match link,
 * kept in a cookie by `MatchController` and read by [authorizationFilter]; the controller decides what a visitor
 * without it sees.
 *
 * CSRF is off because the cookie is `SameSite=Lax`, which keeps it off cross-site posts. A same-site sibling origin
 * (portals sharing a registrable domain) could still post: turn CSRF on, with a cookie repository, if the portals move
 * to shared subdomains.
 */
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
                    .requestMatchers("/", "/louer", "/acheter", "/agent", "/owner", "/owner/sell").permitAll()
                    // A listing's page (ListingController), and its "Être contacté par un agent" form
                    .requestMatchers(HttpMethod.GET, "/*/listing/*", "/*/listing/*/*", "/*/*/listing/*", "/*/*/listing/*/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/listings/*/interest").permitAll()
                    // A match proposal (MatchController): the page, and the customer's answer
                    .requestMatchers(HttpMethod.GET, "/match/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/match/*/accept", "/match/*/reject", "/match/*/visit").permitAll()
                    // A visit (VisitController): the page, and the customer's cancellation
                    .requestMatchers(HttpMethod.GET, "/visits/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/visits/*/cancel").permitAll()
                    .requestMatchers(HttpMethod.POST, "/visits/*/feedback").permitAll()
                    .requestMatchers("/assets/**").permitAll()
                    // The PWA files: the worker, its offline page and the tenant-branded manifest
                    .requestMatchers("/sw.js", "/offline.html", "/manifest.json").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().denyAll() // nothing else is served
            }
            // The JWT filter first: Spring Security resolves the renewal one's anchor only once it is registered.
            .addFilterBefore(authorizationFilter(), AnonymousAuthenticationFilter::class.java)
            .addFilterBefore(accessTokenRenewalFilter(), JwtAuthenticationFilter::class.java)
            .csrf { customizer -> customizer.disable() }
            .httpBasic { customizer -> customizer.disable() }
            .formLogin { customizer -> customizer.disable() }
            .logout { customizer -> customizer.disable() }
            .build()
    }

    @Bean
    fun authorizationFilter(): JwtAuthenticationFilter = JwtAuthenticationFilter(accessTokenService, jwtDecoder)

    // Not a @Bean: Spring Boot also registers every Filter bean as a plain servlet filter, which would renew twice.
    private fun accessTokenRenewalFilter(): AccessTokenRenewalFilter =
        AccessTokenRenewalFilter(accessTokenService, jwtDecoder, tokenClient, clock)
}
