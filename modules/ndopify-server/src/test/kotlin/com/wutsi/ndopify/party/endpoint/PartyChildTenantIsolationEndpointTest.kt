package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.GetIdentificationResponse
import com.wutsi.ndopify.party.dto.GetKycCaseResponse
import com.wutsi.ndopify.party.dto.GetPaymentMethodResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/party/PartyChildTenantIsolationEndpoint.sql"])
class PartyChildTenantIsolationEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun `payment method from another tenant is not found`() {
        val response = rest.getForEntity("/v1/payment-methods/pm-200", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PAYMENT_METHOD_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `payment method from the caller's own tenant is found`() {
        val response = rest.getForEntity("/v1/payment-methods/pm-100", GetPaymentMethodResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("pm-100", response.body!!.paymentMethod.id)
    }

    @Test
    fun `identification from another tenant is not found`() {
        val response = rest.getForEntity("/v1/identifications/id-200", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `identification from the caller's own tenant is found`() {
        val response = rest.getForEntity("/v1/identifications/id-100", GetIdentificationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("id-100", response.body!!.identification.id)
    }

    @Test
    fun `identification image from another tenant is not found`() {
        val response = rest.getForEntity("/v1/identifications/images/img-200/url", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_IMAGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `kyc case from another tenant is not found`() {
        val response = rest.getForEntity("/v1/kyc/cases/case-200", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `kyc case from the caller's own tenant is found`() {
        val response = rest.getForEntity("/v1/kyc/cases/case-100", GetKycCaseResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("case-100", response.body!!.case.id)
    }
}
