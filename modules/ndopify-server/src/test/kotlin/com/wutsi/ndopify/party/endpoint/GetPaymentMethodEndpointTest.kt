package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.GetPaymentMethodResponse
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/party/GetPaymentMethodEndpoint.sql"])
class GetPaymentMethodEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun get() {
        val response = rest.getForEntity("/v1/payment-methods/pm-100", GetPaymentMethodResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val paymentMethod = response.body!!.paymentMethod
        assertEquals("pm-100", paymentMethod.id)
        assertEquals(100L, paymentMethod.partyId)
        assertEquals("+237671234567", paymentMethod.number)
        assertEquals("MTN", paymentMethod.providerName)
        assertEquals("Ray Sponsible", paymentMethod.holderName)
        assertEquals(PaymentMethodType.MOBILE_MONEY, paymentMethod.methodType)
        assertEquals(PaymentMethodStatus.PENDING_VERIFICATION, paymentMethod.verificationStatus)
    }

    @Test
    fun `another party - forbidden`() {
        userId = 12L
        val response = rest.getForEntity("/v1/payment-methods/pm-100", ErrorResponse::class.java)
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `admin reads another party's payment method`() {
        admin = true
        assertEquals(HttpStatus.OK, rest.getForEntity("/v1/payment-methods/pm-101", GetPaymentMethodResponse::class.java).statusCode)
    }

    @Test
    fun `caller without a party - forbidden`() {
        userId = 13L
        val response = rest.getForEntity("/v1/payment-methods/pm-100", ErrorResponse::class.java)
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `payment method not found`() {
        val response = rest.getForEntity("/v1/payment-methods/unknown-id", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PAYMENT_METHOD_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `payment method from another tenant is not found`() {
        val response = rest.getForEntity("/v1/payment-methods/pm-900", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PAYMENT_METHOD_NOT_FOUND, response.body?.error?.code)
    }
}
