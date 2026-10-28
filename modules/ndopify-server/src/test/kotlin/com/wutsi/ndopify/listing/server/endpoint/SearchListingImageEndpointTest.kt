package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.SearchListingImageResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/SearchListingImageEndpoint.sql"])
class SearchListingImageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // Every status: see ListingAccessEndpointTest for the others

    @Test
    fun `search - oldest first`() {
        val response = rest.getForEntity("/v1/listings/100/images", SearchListingImageResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val images = response.body!!.images
        assertEquals(listOf(1000L, 1001L, 1002L), images.map { image -> image.id })

        assertEquals(100L, images[0].listingId)
        assertEquals("https://cdn.example.com/listings/100/images/a.jpg", images[0].url)
        assertEquals("image/jpeg", images[0].mimeType)
        assertEquals(ImageStatus.VERIFIED, images[0].status)
        assertNull(images[0].errorMessage)

        assertEquals(ImageStatus.REJECTED, images[1].status)
        assertEquals("Blurry", images[1].errorMessage)
    }

    @Test
    fun `by status`() {
        val response = rest.getForEntity(
            "/v1/listings/100/images?status=PENDING_VERIFICATION&status=REJECTED",
            SearchListingImageResponse::class.java,
        )

        assertEquals(listOf(1001L, 1002L), response.body!!.images.map { image -> image.id })
    }

    @Test
    fun `public - no token needed, VERIFIED only`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/listings/100/images", SearchListingImageResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(1000L), response.body!!.images.map { image -> image.id })
    }

    @Test
    fun `public - the statuses asked for are ignored`() {
        anonymousUser = true

        val response = rest.getForEntity(
            "/v1/listings/100/images?status=REJECTED",
            SearchListingImageResponse::class.java,
        )

        assertEquals(listOf(1000L), response.body!!.images.map { image -> image.id })
    }

    @Test
    fun `public - listing not published, not found without a token`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/listings/101/images", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `listing without image`() {
        val response = rest.getForEntity("/v1/listings/101/images", SearchListingImageResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body!!.images)
    }

    @Test
    fun `listing not found`() {
        val response = rest.getForEntity("/v1/listings/999/images", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `listing of another tenant`() {
        val response = rest.getForEntity("/v1/listings/900/images", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }
}
