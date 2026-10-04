package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.CreateIdentificationRequest
import com.wutsi.ndopify.party.dto.IdentificationImageType
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.server.dao.IdentificationImageRepository
import com.wutsi.ndopify.party.server.dao.IdentificationRepository
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/party/CreateIdentificationEndpoint.sql"])
class CreateIdentificationEndpointTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: IdentificationRepository

    @Autowired
    private lateinit var imageDao: IdentificationImageRepository

    @Test
    fun identifications() {
        val request = CreateIdentificationRequest(
            type = IdentificationType.NATIONAL_ID,
            issuingCountryCode = "CM",
            imageTypes = listOf(IdentificationImageType.FRONT, IdentificationImageType.BACK),
        )

        val response = rest.postForEntity("/v1/parties/100/identifications", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val identifications = dao.findAll().toList()
        assertEquals(1, identifications.size)

        val identification = identifications[0]
        assertEquals(100L, identification.party.id)
        assertEquals(IdentificationType.NATIONAL_ID, identification.type)
        assertEquals("CM", identification.issuingCountryCode)
        assertEquals(KycStatus.PENDING, identification.status)

        val images = imageDao.findAll().toList()
        assertEquals(2, images.size)
        assertEquals(setOf(IdentificationImageType.FRONT, IdentificationImageType.BACK), images.map { it.imageType }.toSet())
        images.forEach { image ->
            assertEquals(identification.id, image.identification.id)
        }
    }

    @Test
    fun `no issuing country code`() {
        val request = CreateIdentificationRequest(
            type = IdentificationType.NATIONAL_ID,
            issuingCountryCode = "",
            imageTypes = listOf(IdentificationImageType.FRONT),
        )

        val response = rest.postForEntity("/v1/parties/100/identifications", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `no image types`() {
        val request = CreateIdentificationRequest(
            type = IdentificationType.NATIONAL_ID,
            issuingCountryCode = "CM",
            imageTypes = emptyList(),
        )

        val response = rest.postForEntity("/v1/parties/100/identifications", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `party not found`() {
        val request = CreateIdentificationRequest(
            type = IdentificationType.NATIONAL_ID,
            issuingCountryCode = "CM",
            imageTypes = listOf(IdentificationImageType.FRONT),
        )

        val response = rest.postForEntity("/v1/parties/999/identifications", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
    }
}
