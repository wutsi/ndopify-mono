package com.wutsi.ndopify.security.server.endpoints

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.server.service.PasswordEncryptor
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** The lock's threshold comes from the yml: `ndopify.security.otp.max-failed-attempts` is 2 here. */
@TestPropertySource(properties = ["ndopify.security.otp.max-failed-attempts=2"])
@Sql(value = ["/db/test/clean.sql", "/db/test/security/AuthenticateOtpEndpoint.sql"])
class AuthenticateOtpLockConfigEndpointTest : TenantAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var passwordEncryptor: PasswordEncryptor

    @BeforeEach
    override fun setUp() {
        super.setUp()

        doReturn(false).whenever(passwordEncryptor).matches(any(), any(), anyOrNull())
    }

    @Test
    fun `the second wrong code locks the account`() {
        val first = loginError("ray.sponsible@gmail.com", "654321")

        assertEquals(HttpStatus.CONFLICT, first.statusCode)
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, first.body?.error?.code)
        assertEquals(1, first.body?.error?.data?.get("remainingAttempts"))

        val second = loginError("ray.sponsible@gmail.com", "654321")

        assertEquals(HttpStatus.CONFLICT, second.statusCode)
        assertEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, second.body?.error?.code)
    }

    private fun loginError(email: String, secret: String) = rest.postForEntity(
        "/v1/auth",
        AuthenticateRequest(
            email = email,
            secret = secret,
            applicationCode = ApplicationCode.PARTNER_CENTRAL,
            authType = AuthType.OTP,
        ),
        ErrorResponse::class.java,
    )
}
