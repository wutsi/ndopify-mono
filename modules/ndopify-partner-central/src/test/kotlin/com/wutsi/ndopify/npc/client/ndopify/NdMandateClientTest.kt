package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.SearchMandateResponse
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
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

class NdMandateClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdMandateClient

    private val request = CreateMandateRequest(
        mandateType = MandateType.LISTING,
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
                        "mandateType":"LISTING",
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

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/101"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                // Every field, as ndopify-server sends it
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(
                        GetMandateResponse(
                            Mandate(
                                id = 101,
                                mandateType = MandateType.LISTING,
                                cityId = 2232593,
                                neighborhoodIds = listOf(3001)
                            ),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val mandate = client.get(101)

        server.verify()
        assertEquals(101L, mandate.id)
        assertEquals(MandateType.LISTING, mandate.mandateType)
        assertEquals(listOf(3001L), mandate.neighborhoodIds)
    }

    @Test
    fun active() {
        server.expect(
            requestTo(
                "http://localhost:8080/v1/mandates?agent-id=20&status=ASSIGNED&status=PENDING_APPROVAL&status=APPROVED",
            ),
        )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(
                        SearchMandateResponse(
                            listOf(
                                MandateSummary(id = 101, status = MandateStatus.ASSIGNED),
                                MandateSummary(id = 102, status = MandateStatus.APPROVED),
                            ),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val mandates = client.active(20)

        server.verify()
        assertEquals(listOf(101L, 102L), mandates.map { it.id })
        assertEquals(MandateStatus.APPROVED, mandates[1].status)
    }

    @Test
    fun searchByIds() {
        server.expect(requestTo("http://localhost:8080/v1/mandates?id=204&id=305&agent-id=20&limit=2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(SearchMandateResponse(listOf(MandateSummary(id = 204, customerId = 14, agentId = 20)))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val mandates = client.searchByIds(listOf(204, 305), 20)

        server.verify()
        assertEquals(listOf(204L), mandates.map { it.id })
        assertEquals(14L, mandates[0].customerId)
    }

    @Test
    fun `searchByIds - none`() {
        assertEquals(emptyList(), client.searchByIds(emptyList(), 20))
        server.verify()
    }

    @Test
    fun offered() {
        server.expect(requestTo("http://localhost:8080/v1/mandates?offered-to=20"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(SearchMandateResponse(listOf(MandateSummary(id = 101, status = MandateStatus.PENDING_ASSIGNMENT)))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val mandates = client.offered(20)

        server.verify()
        assertEquals(listOf(101L), mandates.map { it.id })
    }

    @Test
    fun update() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/101"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """
                    {
                        "propertyCategory":"RESIDENTIAL",
                        "cityId":2232593,
                        "neighbourhoodIds":[3001],
                        "price":{"amount":150000,"currency":"XAF","periodicity":"MONTHLY"},
                        "movingPeriod":"WITHIN_1_MONTH"
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess())

        client.update(
            101,
            UpdateMandateRequest(
                propertyCategory = PropertyCategory.RESIDENTIAL,
                cityId = 2232593,
                neighbourhoodIds = listOf(3001),
                price = Money(150000, "XAF", PeriodicityType.MONTHLY),
                movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            ),
        )

        server.verify()
    }

    @Test
    fun requestApproval() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/101/request-approval"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.requestApproval(101)

        server.verify()
    }

    @Test
    fun `request approval - conflict`() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/101/request-approval"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException> { client.requestApproval(101) }
    }

    @Test
    fun close() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/101/close"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """{"finalPrice":{"amount":150000,"currency":"XAF","periodicity":"MONTHLY"}}""",
                ),
            )
            .andRespond(withSuccess())

        client.close(101, CloseMandateRequest(Money(150000, "XAF", PeriodicityType.MONTHLY)))

        server.verify()
    }

    @Test
    fun cancel() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/101/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """{"cancellationCode":"CHANGE_OF_PLANS","cancellationReason":"Il reste chez lui"}""",
                ),
            )
            .andRespond(withSuccess())

        client.cancel(101, CancelMandateRequest(MandateCancellationCode.CHANGE_OF_PLANS, "Il reste chez lui"))

        server.verify()
    }

    private fun json(value: Any): String = JsonMapper.builder().build().writeValueAsString(value)
}
