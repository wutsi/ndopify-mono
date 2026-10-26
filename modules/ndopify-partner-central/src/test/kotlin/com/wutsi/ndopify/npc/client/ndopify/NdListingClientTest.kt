package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.listing.dto.GetListingResponse
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.SearchListingImageResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdListingClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdListingClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdListingClient("http://localhost:8080", builder)
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/listings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{"mandateId":203}"""))
            .andRespond(withSuccess("""{"listingId":501}""", MediaType.APPLICATION_JSON))

        assertEquals(501L, client.create(203))
        server.verify()
    }

    @Test
    fun `create - conflict`() {
        server.expect(requestTo("http://localhost:8080/v1/listings"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException> { client.create(203) }
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/listings/501"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(
                        GetListingResponse(
                            Listing(id = 501, mandateId = 203, agentId = 20, status = ListingStatus.UNDER_VERIFICATION, version = 3),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val listing = client.get(501)
        assertEquals(501L, listing.id)
        assertEquals(203L, listing.mandateId)
        assertEquals(20L, listing.agentId)
        assertEquals(ListingStatus.UNDER_VERIFICATION, listing.status)
        assertEquals(3L, listing.version)
        server.verify()
    }

    @Test
    fun verify() {
        server.expect(requestTo("http://localhost:8080/v1/listings/501/verify"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.verify(501)
        server.verify()
    }

    @Test
    fun images() {
        server.expect(requestTo("http://localhost:8080/v1/listings/501/images"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(
                        SearchListingImageResponse(
                            listOf(ListingImage(id = 1, listingId = 501, url = "https://cdn/1.jpg", status = ImageStatus.REJECTED, errorMessage = "Floue")),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val images = client.images(501)
        assertEquals(1, images.size)
        assertEquals(ImageStatus.REJECTED, images[0].status)
        assertEquals("Floue", images[0].errorMessage)
        server.verify()
    }

    private fun json(value: Any): String = JsonMapper.builder().build().writeValueAsString(value)
}
