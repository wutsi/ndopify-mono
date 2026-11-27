package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.mandate.server.service.OwnershipReasonCodes
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.jdbc.Sql
import java.text.SimpleDateFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishListingEndpoint.sql"])
class PublishListingEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // The changes: see ListingAccessEndpointTest for the others

    @Autowired
    private lateinit var dao: ListingRepository

    @Test
    fun publish() {
        val response = publish(100L)

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = dao.findById(100L).get()
        assertEquals(ListingStatus.PUBLISHING, listing.status)
        assertEquals(0, listing.retries)
        assertTrue(listing.modifiedAt.after(SimpleDateFormat("yyyy-MM-dd").parse("2026-01-02")))
    }

    @Test
    fun `publish freezes the ownership verification`() {
        assertEquals(HttpStatus.OK, publish(100L).statusCode)

        assertTrue(dao.findById(100L).get().ownershipVerified)
    }

    @Test
    fun `a refused publication freezes nothing`() {
        assertEquals(HttpStatus.CONFLICT, publish(103L).statusCode)

        val listing = dao.findById(103L).get()
        assertEquals(ListingStatus.VERIFIED, listing.status)
        assertFalse(listing.ownershipVerified)
    }

    @Test
    fun `a listing inserted without the column reads false`() {
        assertFalse(dao.findById(102L).get().ownershipVerified)
    }

    @Test
    fun `not verified - 409`() {
        val response = publish(101L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_VERIFIED, response.body?.error?.code)
        assertEquals(ListingStatus.UNDER_VERIFICATION, dao.findById(101L).get().status)
    }

    @Test
    fun `already published - 409`() {
        val response = publish(102L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_VERIFIED, response.body?.error?.code)
        assertEquals(ListingStatus.PUBLISHED, dao.findById(102L).get().status)
    }

    @Test
    fun `anonymous - 401`() {
        anonymousUser = true

        val response = publish(100L)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertEquals(ListingStatus.VERIFIED, dao.findById(100L).get().status)
    }

    @Test
    fun `not found - 404`() {
        val response = publish(999L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `other tenant - 404`() {
        val response = publish(900L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `owner not verified - 409 with the missing reasons`() {
        val response = publish(103L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, response.body?.error?.code)
        assertEquals(
            listOf(OwnershipReasonCodes.ID_MISSING, OwnershipReasonCodes.DOCUMENT_MISSING),
            reasons(response),
        )
        assertEquals(ListingStatus.VERIFIED, dao.findById(103L).get().status)
    }

    @Test
    fun `failed owner - 409 with its reasons`() {
        val response = publish(104L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, response.body?.error?.code)
        assertEquals(
            listOf(OwnershipReasonCodes.ID_EXPIRED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH),
            reasons(response),
        )
        assertEquals(ListingStatus.VERIFIED, dao.findById(104L).get().status)
    }

    @Test
    fun `in-flight owner - 409 with no reason`() {
        val response = publish(105L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, response.body?.error?.code)
        assertTrue(reasons(response).isEmpty())
        assertEquals(ListingStatus.VERIFIED, dao.findById(105L).get().status)
    }

    @Test
    fun `not verified wins over the owner verdict`() {
        // Listing 101 is UNDER_VERIFICATION and its mandate has no document: the status check answers first
        val response = publish(101L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_VERIFIED, response.body?.error?.code)
    }

    @Test
    fun `the ownership 409 carries no name, document nor path`() {
        val body = rest.postForEntity("/v1/listings/104/publish", null, String::class.java).body!!

        assertFalse(body.contains("Ray"), body)
        assertFalse(body.contains("Pierre"), body)
        assertFalse(body.contains("TF-"), body)
        assertFalse(body.contains("mandate/"), body)
        assertFalse(body.contains("1003"), body)
    }

    private fun reasons(response: ResponseEntity<ErrorResponse>): List<*> {
        val reasons = response.body!!.error.data!!["reasons"] as List<*>
        return reasons.map { reason -> (reason as Map<*, *>)["code"] }
    }

    private fun publish(id: Long) =
        rest.postForEntity("/v1/listings/$id/publish", null, ErrorResponse::class.java)
}
