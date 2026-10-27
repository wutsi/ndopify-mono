package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.text.SimpleDateFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishListingEndpoint.sql"])
class PublishListingEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
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

    private fun publish(id: Long) =
        rest.postForEntity("/v1/listings/$id/publish", null, ErrorResponse::class.java)
}
