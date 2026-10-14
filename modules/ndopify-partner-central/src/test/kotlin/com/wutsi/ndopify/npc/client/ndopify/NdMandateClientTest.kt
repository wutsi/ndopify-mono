package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateType
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdMandateClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdMandateClient

    private val request = CreateMandateRequest(
        mandateType = MandateType.TENANT,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        cityId = 2220957,
        neighbourhoodIds = listOf(1001),
        firstName = "Ray",
        lastName = "Sponsible",
        email = "ray@gmail.com",
        whatsappNumber = "+237670000001",
    )

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdMandateClient("http://localhost:8080", builder)
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/mandates"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(
                content().json(
                    """
                    {
                        "mandateType":"TENANT",
                        "listingType":"RENTAL",
                        "propertyCategory":"RESIDENTIAL",
                        "cityId":2220957,
                        "neighbourhoodIds":[1001],
                        "email":"ray@gmail.com"
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess("""{"mandateId":42}""", MediaType.APPLICATION_JSON))

        assertEquals(42L, client.create(request).mandateId)
        server.verify()
    }

    @Test
    fun createBadRequest() {
        server.expect(requestTo("http://localhost:8080/v1/mandates"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertFailsWith<HttpClientErrorException> { client.create(request) }
    }
}
