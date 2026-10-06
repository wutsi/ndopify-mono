package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.GetIdentificationResponse
import com.wutsi.ndopify.party.dto.IdentificationImageType
import com.wutsi.ndopify.party.dto.IdentificationStatus
import com.wutsi.ndopify.party.dto.IdentificationType
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/party/GetIdentificationEndpoint.sql"])
class GetIdentificationEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/identifications/id-100", GetIdentificationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val identification = response.body!!.identification
        assertEquals("id-100", identification.id)
        assertEquals(100L, identification.partyId)
        assertEquals(IdentificationType.NATIONAL_ID, identification.type)
        assertEquals("CM", identification.issuingCountryCode)
        assertEquals("7890", identification.numberSuffix)
        assertEquals(IdentificationStatus.PENDING_VERIFICATION, identification.status)

        val images = identification.images
        assertEquals(2, images.size)
        assertEquals(
            setOf(IdentificationImageType.FRONT, IdentificationImageType.BACK),
            images.map { it.imageType }.toSet()
        )
    }

    @Test
    fun `identification not found`() {
        val response = rest.getForEntity("/v1/identifications/unknown-id", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_NOT_FOUND, response.body?.error?.code)
    }
}
