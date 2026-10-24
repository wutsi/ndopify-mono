package com.wutsi.ndopify.config

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.UnauthorizedException
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.servlet.JwtAuthenticationFilter
import com.wutsi.ndopify.security.server.servlet.PublicEndpoints
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Every endpoint needs a token but [PublicEndpoints]. A request without one gets 401 `AUTH_UNAUTHORIZED`, through
 * [exceptionResolver] (so `ControllerErrorHandler`), like an endpoint's error.
 */
@EnableWebSecurity
@Configuration
open class SecurityConfiguration(
    private val accessTokenService: AccessTokenService,
    @param:Qualifier("handlerExceptionResolver") private val exceptionResolver: HandlerExceptionResolver,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SecurityConfiguration::class.java)
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        LOGGER.info(">>> Configuring HttpSecurity")
        return http.csrf { customizer -> customizer.disable() }
            .sessionManagement { customizer -> customizer.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(JwtAuthenticationFilter(accessTokenService), AnonymousAuthenticationFilter::class.java)
            .authorizeHttpRequests { customizer ->
                customizer
                    .requestMatchers(*PublicEndpoints.GLOBAL.toTypedArray()).permitAll()
                    .requestMatchers(*PublicEndpoints.TENANT.toTypedArray()).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { customizer ->
                customizer.authenticationEntryPoint { request, response, _ ->
                    val ex = UnauthorizedException(Error(code = ErrorCode.AUTH_UNAUTHORIZED))
                    exceptionResolver.resolveException(request, response, null, ex) ?: throw ex
                }
            }
            .build()
    }
}
