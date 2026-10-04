package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.SearchPaymentMethodResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/SearchPaymentMethodEndpoint.sql"])
class GetPaymentMethodsByPartyEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun search() {
        val response = rest.getForEntity("/v1/parties/100/payment-methods", SearchPaymentMethodResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val paymentMethods = response.body!!.paymentMethods
        assertEquals(2, paymentMethods.size)
        assertTrue(paymentMethods.map { it.id }.containsAll(listOf("pm-100-a", "pm-100-b")))

        val mtn = paymentMethods.first { it.id == "pm-100-a" }
        assertEquals(100L, mtn.partyId)
        assertEquals("+237671234567", mtn.number)
        assertEquals(PaymentMethodStatus.PENDING_VERIFICATION, mtn.verificationStatus)

        val orange = paymentMethods.first { it.id == "pm-100-b" }
        assertEquals(PaymentMethodStatus.VERIFIED, orange.verificationStatus)
    }

    @Test
    fun `only returns payment methods for the given party`() {
        val response = rest.getForEntity("/v1/parties/101/payment-methods", SearchPaymentMethodResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val paymentMethods = response.body!!.paymentMethods
        assertEquals(1, paymentMethods.size)
        assertEquals("pm-101-a", paymentMethods[0].id)
    }

    @Test
    fun `party not found`() {
        val response = rest.getForEntity("/v1/parties/999/payment-methods", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
    }
}
