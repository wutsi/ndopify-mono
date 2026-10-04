package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.SearchIdentificationResponse
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/SearchIdentificationEndpoint.sql"])
class SearchIdentificationEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun search() {
        val response = rest.getForEntity("/v1/parties/100/identifications", SearchIdentificationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val identifications = response.body!!.identifications
        assertEquals(2, identifications.size)
        assertTrue(identifications.map { it.id }.containsAll(listOf("id-100-a", "id-100-b")))

        val national = identifications.first { it.id == "id-100-a" }
        assertEquals(100L, national.partyId)
        assertEquals(IdentificationType.NATIONAL_ID, national.type)
        assertEquals("CM", national.issuingCountryCode)
        assertEquals("7890", national.numberSuffix)
        assertEquals(KycStatus.PENDING, national.status)

        val passport = identifications.first { it.id == "id-100-b" }
        assertEquals(IdentificationType.PASSPORT, passport.type)
        assertEquals(KycStatus.VERIFIED, passport.status)
    }

    @Test
    fun `only returns identifications for the given party`() {
        val response = rest.getForEntity("/v1/parties/101/identifications", SearchIdentificationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val identifications = response.body!!.identifications
        assertEquals(1, identifications.size)
        assertEquals("id-101-a", identifications[0].id)
    }

    @Test
    fun `party not found`() {
        val response = rest.getForEntity("/v1/parties/999/identifications", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
    }
}
