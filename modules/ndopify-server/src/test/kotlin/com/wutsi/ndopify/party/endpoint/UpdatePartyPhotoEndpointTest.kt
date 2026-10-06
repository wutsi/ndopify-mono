package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.UpdatePhotoRequest
import com.wutsi.ndopify.party.server.dao.PartyRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/party/UpdatePartyPhotoEndpoint.sql"])
class UpdatePartyPhotoEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: PartyRepository

    @Test
    fun photo() {
        val request = UpdatePhotoRequest(url = "https://cdn.example.com/new-photo.jpg")

        val response = rest.postForEntity("/v1/parties/100/photo", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val party = dao.findById(100L).get()
        assertEquals("https://cdn.example.com/new-photo.jpg", party.photoUrl)
        assertEquals(TENANT_ID, party.tenantId)
    }

    @Test
    fun `no url`() {
        val request = UpdatePhotoRequest(url = "")

        val response = rest.postForEntity("/v1/parties/100/photo", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `party not found`() {
        val request = UpdatePhotoRequest(url = "https://cdn.example.com/new-photo.jpg")

        val response = rest.postForEntity("/v1/parties/999/photo", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `party belongs to a different tenant`() {
        overrideTenantId = 2L
        val request = UpdatePhotoRequest(url = "https://cdn.example.com/new-photo.jpg")

        val response = rest.postForEntity("/v1/parties/100/photo", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
    }
}
