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

    @BeforeEach
    override fun setUp() {
        anonymousUser = false
        super.setUp()
    }

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        if (!this.anonymousUser) {
            val accessToken = createAccessToken()
            request.headers.add(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        } else {
            request.headers.remove(HttpHeaders.AUTHORIZATION)
        }
        return super.intercept(request, body, execution)
    }

    protected fun createAccessToken(application: String = ApplicationCode.ADMIN_CONSOLE): String {
        return accessTokenService.create(
            application = application,
            tenantId = TENANT_ID,
            userId = USER_ID,
            ttlSeconds = null,
            roles = arrayOf("ROLE_USER")
        )
    }
}
