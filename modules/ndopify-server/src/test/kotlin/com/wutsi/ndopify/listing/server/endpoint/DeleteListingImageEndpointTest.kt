package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/DeleteListingImageEndpoint.sql"])
class DeleteListingImageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // The changes: see ListingAccessEndpointTest for the others

    @Autowired
    private lateinit var dao: ListingImageRepository

    @Test
    fun delete() {
        val response = delete(1000L)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(dao.existsById(1000L))
        assertTrue(dao.existsById(1001L))
    }

    @Test
    fun `not draft - 409`() {
        val response = delete(1100L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_DRAFT, response.body?.error?.code)
        assertTrue(dao.existsById(1100L))
    }

    @Test
    fun `anonymous - 401`() {
        anonymousUser = true

        val response = delete(1000L)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertTrue(dao.existsById(1000L))
    }

    @Test
    fun `not found`() {
        val response = delete(999L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_IMAGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `other tenant`() {
        val response = delete(9000L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_IMAGE_NOT_FOUND, response.body?.error?.code)
    }

    private fun delete(id: Long) =
        rest.exchange("/v1/listings/images/$id", HttpMethod.DELETE, null, ErrorResponse::class.java)
}
