package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.mandate.dto.OverrideOwnershipVerificationRequest
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The publication gate (#299) sees the override (#377): an overridden mandate's VERIFIED listing is sent for
 * publication, and clearing the override blocks it again.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishListingEndpoint.sql"])
class OverrideOwnershipPublishEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var listingDao: ListingRepository

    @Test
    fun `an override lets the gate publish`() {
        assertEquals(HttpStatus.CONFLICT, publish(104).statusCode)

        assertEquals(HttpStatus.NO_CONTENT, override(5).statusCode)

        assertEquals(HttpStatus.OK, publish(104).statusCode)
        assertEquals(ListingStatus.PUBLISHING, listingDao.findById(104L).get().status)
    }

    @Test
    fun `clearing the override blocks the publication again`() {
        assertEquals(HttpStatus.NO_CONTENT, override(5).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, clear(5).statusCode)

        val response = publish(104)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, response.body?.error?.code)
        assertEquals(ListingStatus.VERIFIED, listingDao.findById(104L).get().status)
    }

    private fun override(mandateId: Long) =
        rest.postForEntity(
            "/v1/mandates/$mandateId/ownership/override",
            OverrideOwnershipVerificationRequest("Titre au nom de son père, vérifié en agence"),
            ErrorResponse::class.java,
        )

    private fun clear(mandateId: Long) = rest.postForEntity(
        "/v1/mandates/$mandateId/ownership/override/clear",
        null,
        ErrorResponse::class.java,
    )

    private fun publish(listingId: Long) =
        rest.postForEntity("/v1/listings/$listingId/publish", null, ErrorResponse::class.java)
}
