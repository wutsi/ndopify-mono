package com.wutsi.ndopify.mandate.server

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import com.wutsi.ndopify.mandate.server.job.MandateJobs
import com.wutsi.ndopify.mandate.server.service.MandateDispatcher
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.jdbc.Sql
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The dispatch end to end: what offers a mandate (creation, decline, expiry, the retry job), and the e-mail the agent
 * gets. The clock is pinned: the dispatch depends on the time of day (no offer at night).
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateDispatchFlow.sql"])
class MandateDispatchFlowTest : AuthorizationAwareEndpointIntegrationTest() {
    companion object {
        private val DOUALA = ZoneId.of("Africa/Douala")
        val NOW = time("2026-10-15T12:00")

        // Shared: the interceptors of earlier test instances are still registered on the shared RestTemplate, and
        // mint tokens too
        @Volatile
        private var minting = false

        private fun time(local: String) = LocalDateTime.parse(local).atZone(DOUALA).toInstant().toEpochMilli()
    }

    @MockitoBean
    private lateinit var clock: Clock

    @MockitoSpyBean
    private lateinit var dispatcher: MandateDispatcher

    @Autowired
    private lateinit var jobs: MandateJobs

    private var now = NOW

    @BeforeEach
    override fun setUp() {
        doAnswer { if (minting) System.currentTimeMillis() else now }.whenever(clock).millis()
        super.setUp()
    }

    // The token is checked against the real time: minted with the pinned one, it could already be expired
    override fun createAccessToken(application: String): String {
        minting = true
        try {
            return super.createAccessToken(application)
        } finally {
            minting = false
        }
    }

    private fun assignments(mandateId: Long): List<AssignmentSummary> =
        rest.getForEntity("/v1/assignments?mandate-id=$mandateId", SearchAssignmentResponse::class.java)
            .body!!.assignments

    private fun pending(mandateId: Long) = assignments(mandateId).filter { it.status == AssignmentStatus.PENDING }

    /** The offer e-mail sent to agent 30 (John Doe), the only candidate left in every scenario. */
    private fun assertOfferEmailToJohn() {
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received") // sent asynchronously
        val messages = smtp.receivedMessages
        assertEquals(1, messages.size)
        assertEquals("John Doe <john.doe@gmail.com>", messages[0].allRecipients.single().toString())
        assertEquals("Ndopify: Un nouveau mandat vous est proposé à Yaounde", messages[0].subject)

        val body = messages[0].content.toString()
        assertTrue(body.contains("John Doe"), body)
        assertTrue(body.contains("Location"), body)
        assertTrue(body.contains("Appartement"), body)
        assertTrue(body.contains("150 000 XAF / mois"), body)
        assertTrue(body.contains("15/10/2026 &agrave; 13:00") || body.contains("15/10/2026 à 13:00"), body)
        assertTrue(body.contains("http://localhost:8081/partner-central"), body)
    }

    private fun assertNoEmail() {
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }

    // --- Creation

    @Test
    fun `a new mandate is offered`() {
        val request = CreateMandateRequest(
            propertyCategory = PropertyCategory.RESIDENTIAL,
            mandateType = MandateType.TENANT,
            listingType = ListingType.RENTAL,
            cityId = 2370201L,
            propertyType = PropertyType.APARTMENT,
            rooms = "2",
            price = Money(amount = 150000, "XAF", PeriodicityType.MONTHLY),
            firstName = "Ray",
            lastName = "Sponsible",
            email = "ray.sponsible@gmail.com",
            whatsappNumber = "+237671234567",
        )

        val response = rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        awaitAsyncTasks() // Dispatched after commit

        // Agent 20 already works on mandate 4: the idle agent 30 wins
        val offer = pending(response.body!!.mandateId).single()
        assertEquals(30L, offer.agentId)
        assertEquals(NOW + 3_600_000, offer.expiresAt.time)
        assertOfferEmailToJohn()
    }

    // --- Decline

    @Test
    fun `a declined mandate goes to the next agent`() {
        val response = rest.postForEntity("/v1/assignments/1/decline", null, Any::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        awaitAsyncTasks() // Re-dispatched after commit

        val offers = assignments(1)
        assertEquals(AssignmentStatus.DECLINED, offers.single { it.id == 1L }.status)
        assertEquals(30L, pending(1).single().agentId)
        assertOfferEmailToJohn()
    }

    @Test
    fun `a failing re-dispatch never fails the decline`() {
        doThrow(IllegalStateException("boom")).whenever(dispatcher).dispatch(1L)

        val response = rest.postForEntity("/v1/assignments/1/decline", null, Any::class.java)
        awaitAsyncTasks()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(AssignmentStatus.DECLINED, assignments(1).single().status)
        assertNoEmail()
    }

    @Test
    fun `declined at night - offered the next morning`() {
        now = time("2026-10-15T22:00")
        val response = rest.postForEntity("/v1/assignments/1/decline", null, String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, response.body)
        awaitAsyncTasks()

        assertTrue(pending(1).isEmpty(), pending(1).toString())
        assertNoEmail()

        now = time("2026-10-16T07:15")
        jobs.dispatch()

        assertEquals(30L, pending(1).single().agentId)
    }

    // --- Expiry

    @Test
    fun `expire job - offers past their TTL expire, and their mandate goes to the next agent`() {
        jobs.expire()
        awaitAsyncTasks() // Re-dispatched after commit

        assertEquals(AssignmentStatus.EXPIRED, assignments(2).single { it.id == 2L }.status)
        assertEquals(30L, pending(2).single().agentId)
        assertEquals(AssignmentStatus.PENDING, assignments(1).single().status) // Before its TTL: untouched
        assertOfferEmailToJohn()
    }

    // --- Retry

    @Test
    fun `dispatch job - the mandates waiting without an offer are offered`() {
        jobs.dispatch()
        awaitAsyncTasks()

        assertEquals(30L, pending(3).single().agentId)
        assertEquals(listOf(1L), assignments(1).map { it.id }) // Already offered: untouched
        assertEquals(listOf(2L), assignments(2).map { it.id }) // Still PENDING, the expiry job's business
        assertTrue(assignments(4).isEmpty()) // Already assigned
        assertOfferEmailToJohn()
    }

    @Test
    fun `dispatch job - nothing offered at night`() {
        now = time("2026-10-15T22:00")

        jobs.dispatch()
        awaitAsyncTasks()

        assertTrue(assignments(3).isEmpty())
        assertNoEmail()
    }

    @Test
    fun `dispatch job - a mandate without an agent for too long goes to manual assignment`() {
        now = time("2026-10-19T12:00")

        jobs.dispatch()

        val mandate = rest.getForEntity("/v1/mandates/3", GetMandateResponse::class.java).body!!.mandate
        assertEquals(MandateStatus.MANUAL_ASSIGNMENT, mandate.status)
        assertTrue(assignments(3).isEmpty())
    }
}
