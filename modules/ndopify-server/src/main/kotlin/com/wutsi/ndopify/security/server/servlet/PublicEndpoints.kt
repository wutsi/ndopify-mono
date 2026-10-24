package com.wutsi.ndopify.security.server.servlet

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpMethod
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * The endpoints open without a token: everything else needs one (`SecurityConfiguration`), and only these may take
 * their tenant from the `X-Tenant-ID` header ([TenantContextFilter]).
 */
object PublicEndpoints {
    private val matcher = PathPatternRequestMatcher.withDefaults()

    /** Not tenant-scoped: called before a tenant is known (ex: Partner Central's GET /v1/tenants), so not filtered. */
    val GLOBAL: List<RequestMatcher> = listOf(
        "/v1/tenants/**",
        "/v1/locations/**",
        "/v1/applications/**",

        // Swagger UI (springdoc.swagger-ui.path) and the OpenAPI docs it loads
        "/api.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",

        // Local storage
        "/storage/**",

        // Actuator
        "/actuator/**",

        // Spring Boot's error page, so that an error is answered rather than turned into a 401
        "/error",
    ).map { matcher.matcher(it) }

    /** Tenant-scoped, called before the caller has a token: the tenant comes from the header. */
    val TENANT: List<RequestMatcher> = listOf(
        matcher.matcher(HttpMethod.POST, "/v1/otp"),
        matcher.matcher(HttpMethod.POST, "/v1/auth"),
        matcher.matcher(HttpMethod.POST, "/v1/agents"), // Partner Central's registration
        matcher.matcher(HttpMethod.POST, "/v1/mandates"), // Partner Central's public wizards
        matcher.matcher(HttpMethod.GET, "/v1/listings"), // Listings are public: search and get
        matcher.matcher(HttpMethod.GET, "/v1/listings/*"),
        matcher.matcher(HttpMethod.GET, "/v1/listings/*/images"),
        matcher.matcher(HttpMethod.GET, "/v1/contracts/*/signatures/*"), // e-signature's verify page
    )

    fun isGlobal(request: HttpServletRequest): Boolean = GLOBAL.any { it.matches(request) }

    fun isTenant(request: HttpServletRequest): Boolean = TENANT.any { it.matches(request) }
}
