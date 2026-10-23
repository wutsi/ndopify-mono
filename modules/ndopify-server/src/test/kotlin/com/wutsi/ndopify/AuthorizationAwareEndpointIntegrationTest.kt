package com.wutsi.ndopify

import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.server.service.AccessTokenService
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse

/**
 * Base class for integration tests that require authorization.
 */
abstract class AuthorizationAwareEndpointIntegrationTest : TenantAwareEndpointIntegrationTest() {
    companion object {
        const val USER_ID = 11L
    }

    @Autowired
    private lateinit var accessTokenService: AccessTokenService

    protected var anonymousUser: Boolean = false
    protected var userId: Long = USER_ID

    // The token's roles: override with RoleCode.ADMIN to call as an admin of the admin console
    protected open val roles: Array<String> = arrayOf("ROLE_USER")

    // The token's application: override with ApplicationCode.E_SIGNATURE to call as the e-signature portal
    protected open val application: String = ApplicationCode.ADMIN_CONSOLE

    @BeforeEach
    override fun setUp() {
        anonymousUser = false
        userId = USER_ID
        super.setUp()
    }

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        if (!this.anonymousUser) {
            val accessToken = createAccessToken(application)
            // set, not add: the interceptors of earlier test instances still run, and this one (the last) must win
            request.headers.set(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        } else {
            request.headers.remove(HttpHeaders.AUTHORIZATION)
        }
        return super.intercept(request, body, execution)
    }

    protected fun createAccessToken(application: String = ApplicationCode.ADMIN_CONSOLE): String {
        return accessTokenService.create(
            application = application,
            tenantId = overrideTenantId ?: TENANT_ID,
            userId = userId,
            ttlSeconds = 60 * 60, // 1 hour
            roles = roles,
        )
    }
}
