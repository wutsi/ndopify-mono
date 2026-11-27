package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The publication gate off: the verdict no longer refuses, the listing status still does. Fixture: see PublishListingEndpointTest. */
@TestPropertySource(properties = ["ndopify.listing.publish.require-owner-verification=false"])
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishListingEndpoint.sql"])
class PublishListingWithoutOwnerVerificationEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // The changes: see ListingAccessEndpointTest for the others

    @Autowired
    private lateinit var dao: ListingRepository

    @Test
    fun `a pending owner is published`() {
        assertEquals(HttpStatus.OK, publish(103L).statusCode)

        val listing = dao.findById(103L).get()
        assertEquals(ListingStatus.PUBLISHING, listing.status)
        assertEquals(0, listing.retries)
        assertFalse(listing.ownershipVerified)
    }

    @Test
    fun `a failed owner is published`() {
        assertEquals(HttpStatus.OK, publish(104L).statusCode)

        val listing = dao.findById(104L).get()
        assertEquals(ListingStatus.PUBLISHING, listing.status)
        assertFalse(listing.ownershipVerified)
    }

    @Test
    fun `a verified owner is published`() {
        assertEquals(HttpStatus.OK, publish(100L).statusCode)

        assertEquals(ListingStatus.PUBLISHING, dao.findById(100L).get().status)
        assertTrue(dao.findById(100L).get().ownershipVerified)
    }

    @Test
    fun `a listing not verified is still refused`() {
        val response = publish(101L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_VERIFIED, response.body?.error?.code)
        assertEquals(ListingStatus.UNDER_VERIFICATION, dao.findById(101L).get().status)
    }

    @Test
    fun `not found - 404`() {
        val response = publish(999L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    private fun publish(id: Long) =
        rest.postForEntity("/v1/listings/$id/publish", null, ErrorResponse::class.java)
}
