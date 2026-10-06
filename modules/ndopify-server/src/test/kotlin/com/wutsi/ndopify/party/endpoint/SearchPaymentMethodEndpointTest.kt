package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.SearchPaymentMethodResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/SearchPaymentMethodEndpoint.sql"])
class SearchPaymentMethodEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun search() {
        val response = rest.getForEntity(
            "/v1/payment-methods?partyId=100",
            SearchPaymentMethodResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val paymentMethods = response.body!!.paymentMethods
        assertEquals(3, paymentMethods.size)
        assertTrue(paymentMethods.map { it.id }.containsAll(listOf("pm-100-a", "pm-100-b", "pm-100-c")))

        val mtn = paymentMethods.first { it.id == "pm-100-a" }
        assertEquals(100L, mtn.partyId)
        assertEquals("+237671234567", mtn.number)
        assertEquals(PaymentMethodStatus.PENDING_VERIFICATION, mtn.verificationStatus)

        val orange = paymentMethods.first { it.id == "pm-100-b" }
        assertEquals(PaymentMethodStatus.VERIFIED, orange.verificationStatus)
    }

    @Test
    fun `only returns payment methods for the given party`() {
        val response = rest.getForEntity(
            "/v1/payment-methods?partyId=101",
            SearchPaymentMethodResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val paymentMethods = response.body!!.paymentMethods
        assertEquals(1, paymentMethods.size)
        assertEquals("pm-101-a", paymentMethods[0].id)
    }

    @Test
    fun `returns empty list when party has no payment methods`() {
        val response = rest.getForEntity(
            "/v1/payment-methods?partyId=999",
            SearchPaymentMethodResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.paymentMethods.isEmpty())
    }

    @Test
    fun `filter by type`() {
        val response = rest.getForEntity(
            "/v1/payment-methods?types=BANK_ACCOUNT",
            SearchPaymentMethodResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val paymentMethods = response.body!!.paymentMethods
        assertEquals(1, paymentMethods.size)
        assertEquals("pm-100-c", paymentMethods[0].id)
    }

    @Test
    fun `filter by multiple types`() {
        val response = rest.getForEntity(
            "/v1/payment-methods?partyId=100&types=MOBILE_MONEY&types=BANK_ACCOUNT",
            SearchPaymentMethodResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val paymentMethods = response.body!!.paymentMethods
        assertEquals(3, paymentMethods.size)
        assertTrue(paymentMethods.map { it.id }.containsAll(listOf("pm-100-a", "pm-100-b", "pm-100-c")))
    }

    @Test
    fun `filter by status`() {
        val response = rest.getForEntity(
            "/v1/payment-methods?statuses=VERIFIED",
            SearchPaymentMethodResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val paymentMethods = response.body!!.paymentMethods
        assertEquals(1, paymentMethods.size)
        assertEquals("pm-100-b", paymentMethods[0].id)
    }

    @Test
    fun `filter by party, type and status`() {
        val response = rest.getForEntity(
            "/v1/payment-methods?partyId=100&types=MOBILE_MONEY&statuses=PENDING_VERIFICATION",
            SearchPaymentMethodResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val paymentMethods = response.body!!.paymentMethods
        assertEquals(1, paymentMethods.size)
        assertEquals("pm-100-a", paymentMethods[0].id)
    }

    @Test
    fun `no match for filter`() {
        val response = rest.getForEntity(
            "/v1/payment-methods?statuses=REJECTED",
            SearchPaymentMethodResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.paymentMethods.isEmpty())
    }
}
