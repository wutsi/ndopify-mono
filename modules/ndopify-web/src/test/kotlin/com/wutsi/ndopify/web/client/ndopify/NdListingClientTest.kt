package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.listing.dto.GetListingResponse
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.SearchListingImageResponse
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
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
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/listings/123"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(GetListingResponse(Listing(id = 123, status = ListingStatus.PUBLISHED, title = "Appartement"))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val listing = client.get(123)

        assertEquals(123L, listing.id)
        assertEquals("Appartement", listing.title)
        server.verify()
    }

    @Test
    fun `get - not found`() {
        server.expect(requestTo("http://localhost:8080/v1/listings/123")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<HttpClientErrorException.NotFound> { client.get(123) }
    }

    @Test
    fun images() {
        server.expect(requestTo("http://localhost:8080/v1/listings/123/images"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(
                        SearchListingImageResponse(
                            images = listOf(ListingImage(id = 1, url = "https://cdn/1.jpg"), ListingImage(id = 2, url = "https://cdn/2.jpg")),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(listOf(1L, 2L), client.images(123).map { it.id })
        server.verify()
    }

    @Test
    fun `images - not found`() {
        server.expect(requestTo("http://localhost:8080/v1/listings/123/images")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<HttpClientErrorException.NotFound> { client.images(123) }
    }

    private fun json(value: Any): String = JsonMapper.builder().build().writeValueAsString(value)
}
