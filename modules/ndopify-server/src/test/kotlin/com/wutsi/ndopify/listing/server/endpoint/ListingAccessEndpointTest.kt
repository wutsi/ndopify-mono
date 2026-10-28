package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.CreateListingRequest
import com.wutsi.ndopify.listing.dto.CreateListingResponse
import com.wutsi.ndopify.listing.dto.GetListingResponse
import com.wutsi.ndopify.listing.dto.SearchListingImageResponse
import com.wutsi.ndopify.listing.dto.SearchListingResponse
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.jdbc.Sql
import org.springframework.util.LinkedMultiValueMap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Who may read a listing that isn't PUBLISHED, or an image that isn't VERIFIED, and who may change a listing: its agent
 * (user 11 is agent 20's, user 12 agent 30's) or an admin.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/ListingAccessEndpoint.sql"])
class ListingAccessEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun `agent gets its draft listing and its images`() {
        assertEquals(101L, get(101))
        assertEquals(listOf(1010L), images(101))
    }

    @Test
    fun `agent can't get the draft listing of another agent`() {
        assertNotFound(rest.getForEntity("/v1/listings/102", ErrorResponse::class.java))
        assertNotFound(rest.getForEntity("/v1/listings/102/images", ErrorResponse::class.java))
    }

    @Test
    fun `user who isn't an agent can't get a draft listing`() {
        userId = 13L

        assertNotFound(rest.getForEntity("/v1/listings/101", ErrorResponse::class.java))
        assertNotFound(rest.getForEntity("/v1/listings/101/images", ErrorResponse::class.java))
    }

    @Test
    fun `user who isn't an agent gets a published listing, and its verified images only`() {
        userId = 13L

        assertEquals(100L, get(100))
        assertEquals(listOf(1000L), images(100))
    }

    @Test
    fun `agent gets every image of its published listing`() {
        assertEquals(listOf(1000L, 1001L), images(100))
    }

    @Test
    fun `agent gets the verified images only of the published listing of another agent`() {
        userId = 12L

        assertEquals(listOf(1000L), images(100))
    }

    @Test
    fun `admin gets every image`() {
        admin = true

        assertEquals(listOf(1000L, 1001L), images(100))
    }

    @Test
    fun `admin gets any draft listing`() {
        admin = true

        assertEquals(102L, get(102))
        assertEquals(emptyList(), images(102))
    }

    @Test
    fun `agent searches all its listings`() {
        assertEquals(listOf(101L, 100L), search("agent-id=20"))
    }

    @Test
    fun `agent searches the published listings only, without its own agent-id`() {
        assertEquals(listOf(100L), search())
        assertEquals(listOf(100L), search("status=DRAFT"))
        assertEquals(emptyList(), search("agent-id=30"))
    }

    @Test
    fun `user who isn't an agent searches the published listings only`() {
        userId = 13L

        assertEquals(listOf(100L), search("agent-id=20"))
    }

    @Test
    fun `admin searches every listing`() {
        admin = true

        assertEquals(listOf(102L, 101L, 100L), search())
    }

    @Test
    fun `agent creates the listing of its mandate`() {
        val response = rest.postForEntity("/v1/listings", CreateListingRequest(mandateId = 4), CreateListingResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `agent can't create the listing of the mandate of another agent`() {
        userId = 12L

        assertDenied(rest.postForEntity("/v1/listings", CreateListingRequest(mandateId = 4), ErrorResponse::class.java))
    }

    @Test
    fun `agent changes its listing`() {
        // Past the access check: the listing's own rules answer
        assertEquals(ErrorCode.LISTING_VERSION_CONFLICT, post("/v1/listings/101", UpdateListingRequest()).body?.error?.code)
        assertEquals(ErrorCode.LISTING_INCOMPLETE, post("/v1/listings/101/verify").body?.error?.code)
        assertEquals(ErrorCode.LISTING_NOT_VERIFIED, post("/v1/listings/101/publish").body?.error?.code)
        assertEquals(HttpStatus.OK, delete(1010).statusCode)
    }

    @Test
    fun `admin changes any listing`() {
        admin = true

        assertEquals(ErrorCode.LISTING_INCOMPLETE, post("/v1/listings/102/verify").body?.error?.code)
    }

    @Test
    fun `agent can't change the listing of another agent`() {
        userId = 12L

        assertChangesDenied()
    }

    @Test
    fun `user who isn't an agent can't change a listing`() {
        userId = 13L

        assertChangesDenied()
    }

    @Test
    fun `changes need a token`() {
        anonymousUser = true

        assertEquals(HttpStatus.UNAUTHORIZED, post("/v1/listings/101/verify").statusCode)
    }

    private fun assertChangesDenied() {
        assertDenied(post("/v1/listings/101", UpdateListingRequest(version = 0)))
        assertDenied(post("/v1/listings/101/verify"))
        assertDenied(post("/v1/listings/100/publish"))
        assertDenied(upload(101))
        assertDenied(delete(1010))
    }

    private fun post(url: String, body: Any? = null): ResponseEntity<ErrorResponse> =
        rest.postForEntity(url, body, ErrorResponse::class.java)

    private fun upload(id: Long): ResponseEntity<ErrorResponse> {
        val resource = object : ByteArrayResource(byteArrayOf(1, 2, 3)) {
            override fun getFilename(): String = "photo.png"
        }
        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = MediaType.IMAGE_PNG
        val body = LinkedMultiValueMap<String, Any>()
        body.add("multipartFile", HttpEntity(resource, fileHeaders))
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        return rest.postForEntity("/v1/listings/$id/images/upload", HttpEntity(body, headers), ErrorResponse::class.java)
    }

    private fun delete(imageId: Long): ResponseEntity<ErrorResponse> =
        rest.exchange("/v1/listings/images/$imageId", HttpMethod.DELETE, null, ErrorResponse::class.java)

    private fun assertDenied(response: ResponseEntity<ErrorResponse>) {
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    private fun get(id: Long): Long {
        val response = rest.getForEntity("/v1/listings/$id", GetListingResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!.listing.id
    }

    private fun images(id: Long): List<Long> {
        val response = rest.getForEntity("/v1/listings/$id/images", SearchListingImageResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!.images.map { image -> image.id }
    }

    private fun search(query: String = ""): List<Long> {
        val response = rest.getForEntity("/v1/listings?$query", SearchListingResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!.listings.map { listing -> listing.id }
    }

    private fun assertNotFound(response: ResponseEntity<ErrorResponse>) {
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }
}
