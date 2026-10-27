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

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/VerifyListingEndpoint.sql"])
class VerifyListingEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: ListingRepository

    @Test
    fun verify() {
        val response = verify(100L)

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = dao.findById(100L).get()
        assertEquals(ListingStatus.UNDER_VERIFICATION, listing.status)
        assertTrue(listing.modifiedAt.after(SimpleDateFormat("yyyy-MM-dd").parse("2026-01-02")))
    }

    @Test
    fun `section left - 409`() {
        val response = verify(101L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_INCOMPLETE, response.body?.error?.code)
        assertEquals(ListingStatus.DRAFT, dao.findById(101L).get().status)
    }

    @Test
    fun `photo rejected - 409`() {
        val response = verify(102L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_INCOMPLETE, response.body?.error?.code)
        assertEquals(ListingStatus.DRAFT, dao.findById(102L).get().status)
    }

    @Test
    fun `not draft - 409`() {
        val response = verify(103L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_DRAFT, response.body?.error?.code)
        assertEquals(ListingStatus.UNDER_VERIFICATION, dao.findById(103L).get().status)
    }

    @Test
    fun `anonymous - 401`() {
        anonymousUser = true

        val response = verify(100L)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertEquals(ListingStatus.DRAFT, dao.findById(100L).get().status)
    }

    @Test
    fun `not found - 404`() {
        val response = verify(999L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `other tenant - 404`() {
        val response = verify(900L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    private fun verify(id: Long) =
        rest.postForEntity("/v1/listings/$id/verify", null, ErrorResponse::class.java)
}
