package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest
import com.wutsi.ndopify.npc.ui.listing.MandateListingView
import com.wutsi.ndopify.npc.ui.listing.ProposalGroup
import com.wutsi.ndopify.npc.ui.listing.ProposalsView
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.ACCEPTED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.APPROVED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
import com.wutsi.ndopify.npc.ui.match.MatchFixtures
import com.wutsi.ndopify.npc.ui.match.MatchGroup
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.npc.ui.match.MatchSectionView
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BASTOS
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.npc.ui.visit.PendingVisitView
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures
import com.wutsi.ndopify.npc.ui.visit.VisitRules
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val contractClient = mock<NdContractClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val assignmentClient = mock<NdAssignmentClient>()
    private val listingClient = mock<NdListingClient>()
    private val matchClient = mock<NdMatchClient>()
    private val visitClient = mock<NdVisitClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        // A Long? method of a mock answers 0, not null: no listing unless a test says so
        doReturn(null).whenever(listingClient).findIdByMandate(any(), any())
        doReturn(NOW).whenever(clock).millis()
        doReturn(PENDING).whenever(mandateClient).get(201)
        doReturn(CONTRACT).whenever(contractClient).get("c-201")
        doReturn(CUSTOMER).whenever(customerClient).get(555)
        doReturn(ACCEPTED).whenever(assignmentClient).accepted(201)

        val controller = MandateController(
            currentAgent,
            mandateClient,
            contractClient,
            customerClient,
            assignmentClient,
            listingClient,
            matchClient,
            visitClient,
            locationClient,
            tenantContext,
            clock,
            MandateDealCandidates(matchClient, mandateClient, customerClient, listingClient),
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun mandate(result: MvcResult) = model(result)["mandate"] as MandateView

    @Test
    fun show() {
        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("mandate", result.modelAndView?.viewName)
        val view = mandate(result)
        assertEquals(201L, view.id)
        assertEquals("22 oct. 2026", view.signatureDeadline)
        assertEquals("ray@gmail.com", view.contact?.email)
        assertEquals("10 oct. 2026", view.acceptedAt)
        assertEquals("Secteurs" to "Bonapriso, Akwa — Douala", view.keyCriteria[0])
        assertEquals("SN", model(result)["initials"])
        assertNull(model(result)["locked"])
        assertNull(model(result)["end"])
    }

    @Test
    fun `approved landlord - listing`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.LISTING).whenever(listingClient).get(501)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertEquals("/listings/501", view.url)
        assertEquals("Continuer l'annonce", view.action)
    }

    @Test
    fun `approved landlord - published listing - clients interested`() {
        publishedListing()
        doReturn(listOf(VisitFixtures.summary(VisitFixtures.confirmed()), VisitFixtures.summary(VisitFixtures.completed())))
            .whenever(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)

        val result = mvc.perform(get("/mandates/201")).andReturn()
        val view = model(result)["mandateListing"] as MandateListingView

        assertEquals("2 personnes intéressées · 2 visites", view.stats)
        assertTrue(model(result)["listingProposals"] is ProposalsView)
        assertNull(model(result)["pendingVisit"])
        verify(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)
        // The agent is resolved once for the whole page, not again for the listing's clients.
        verify(currentAgent, times(1)).get()
    }

    @Test
    fun `approved landlord - published listing - visits unavailable, interest still shown`() {
        publishedListing()
        doThrow(ResourceAccessException("down"))
            .whenever(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertEquals("2 personnes intéressées", view.stats)
    }

    private fun publishedListing() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.COMPLETE.copy(status = ListingStatus.PUBLISHED)).whenever(listingClient).get(501)
        doReturn(ListingViewsTest.PROPOSALS).whenever(matchClient).searchByListings(listOf(501), MatchRules.SENT)
        doReturn(ListingViewsTest.SEARCHES).whenever(mandateClient).searchByIds(listOf(207L, 204L, 305L), AGENT.id)
        doReturn(ListingViewsTest.CUSTOMERS).whenever(customerClient).search(listOf(14L, 17L))
        doReturn(emptyList<VisitSummary>())
            .whenever(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)
    }

    private fun proposals(): ProposalsView? =
        model(mvc.perform(get("/mandates/201")).andReturn())["listingProposals"] as ProposalsView?

    @Test
    fun `approved landlord - published listing - its proposals`() {
        publishedListing()

        val view = proposals()!!

        assertEquals("1 refusé", view.outcomes)
        assertEquals(listOf("Client d'un autre agent", "Sandrine Eboa", "Carine Ndongo"), view.rows.map { it.client })
    }

    @Test
    fun `approved landlord - published listing - open visit stays in its proposal card`() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        publishedListing()
        doReturn(
            listOf(
                VisitFixtures.summary(
                    VisitFixtures.requested().copy(matchId = 9002, mandateId = 204, listingId = 501, customerId = 14),
                ),
            ),
        ).whenever(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)

        val result = mvc.perform(get("/mandates/201")).andReturn()
        val proposals = model(result)["listingProposals"] as ProposalsView

        assertEquals("Sandrine Eboa", proposals.rows.first().client)
        assertEquals(ProposalGroup.REQUESTED, proposals.rows.first().group)
        assertEquals(VisitStatus.REQUESTED, proposals.rows.first().visit?.status)
        assertEquals("#proposal-9002", (model(result)["pendingVisit"] as PendingVisitView).url)
        verify(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)
    }

    @Test
    fun `approved landlord - published listing - visits unavailable - every proposal shown`() {
        publishedListing()
        doThrow(ResourceAccessException("down")).whenever(visitClient).searchByListings(any(), any(), any(), any())

        val result = mvc.perform(get("/mandates/201")).andReturn()
        val proposals = model(result)["listingProposals"] as ProposalsView

        assertTrue(proposals.visitsUnavailable)
        assertEquals("1 refusé", proposals.outcomes)
        assertEquals(3, proposals.rows.size)
        assertTrue(proposals.rows.all { it.visit == null })
        assertNull(model(result)["pendingVisit"])
    }

    @Test
    fun `approved landlord - published listing - another agent's mandate returned anyway is protected`() {
        publishedListing()
        val other = ListingViewsTest.SEARCHES[1].copy(agentId = 999)
        doReturn(listOf(ListingViewsTest.SEARCHES[0], other)).whenever(mandateClient).searchByIds(any(), any())
        doReturn(ListingViewsTest.CUSTOMERS.take(1)).whenever(customerClient).search(listOf(14L))

        val view = proposals()!!

        verify(customerClient).search(listOf(14L))
        assertEquals(listOf("Client d'un autre agent", "Sandrine Eboa", "Client d'un autre agent"), view.rows.map { it.client })
    }

    @Test
    fun `approved landlord - published listing - proposals' matches unavailable - no section`() {
        publishedListing()
        doThrow(ResourceAccessException("down")).whenever(matchClient).searchByListings(listOf(501), MatchRules.SENT)

        assertNull(proposals())
    }

    @Test
    fun `approved landlord - published listing - proposals' mandates unavailable - no section`() {
        publishedListing()
        doThrow(ResourceAccessException("down")).whenever(mandateClient).searchByIds(any(), any())

        assertNull(proposals())
        verify(customerClient, never()).search(any())
    }

    @Test
    fun `approved landlord - published listing - proposals' customers unavailable - the mandates' numbers`() {
        publishedListing()
        doThrow(ResourceAccessException("down")).whenever(customerClient).search(any())

        assertEquals(listOf("Client d'un autre agent", "Mandat n° 204", "Mandat n° 207"), proposals()!!.rows.map { it.client })
    }

    @Test
    fun `approved landlord - draft listing - no proposals`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.LISTING).whenever(listingClient).get(501)

        assertNull(proposals())
    }

    @Test
    fun `approved landlord - published listing - interest unavailable`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.COMPLETE.copy(status = ListingStatus.PUBLISHED)).whenever(listingClient).get(501)
        doThrow(ResourceAccessException("down")).whenever(matchClient).searchByListings(any(), any(), any())

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertEquals("/listings/501", view.url)
        assertNull(view.stats)
    }

    @Test
    fun `approved landlord - draft listing - no interest search`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.LISTING).whenever(listingClient).get(501)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertNull(view.stats)
        verify(matchClient, never()).searchByListings(any(), any(), any())
    }

    @Test
    fun `approved landlord - listing unavailable`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doThrow(ResourceAccessException("down")).whenever(listingClient).get(501)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertEquals("/listings/501", view.url)
        assertNull(view.text)
    }

    @Test
    fun `approved landlord - no listing yet`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertNull(view.url)
        assertEquals("Créer l'annonce", view.action)
        verify(listingClient, never()).get(any())
    }

    @Test
    fun `approved landlord - listing search unavailable`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doThrow(ResourceAccessException("down")).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertEquals("Créer l'annonce", view.action)
    }

    @Test
    fun `approved search - no listing`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        assertNull(model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"])
        verify(listingClient, never()).findIdByMandate(any(), any())
        verify(listingClient, never()).get(any())
    }

    @Test
    fun `not found - locked`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("mandate", result.modelAndView?.viewName)
        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["mandate"])
    }

    @Test
    fun `malformed id - locked`() {
        val result = mvc.perform(get("/mandates/abc")).andReturn()

        assertEquals(true, model(result)["locked"])
        verify(mandateClient, never()).get(any())
    }

    @Test
    fun `pending assignment - locked`() {
        doReturn(MANDATE.copy(status = MandateStatus.PENDING_ASSIGNMENT)).whenever(mandateClient).get(201)

        assertEquals(true, model(mvc.perform(get("/mandates/201")).andReturn())["locked"])
    }

    @Test
    fun `contract unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(contractClient).get(any())

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertNull(view.signatureDeadline)
        assertEquals("ray@gmail.com", view.contact?.email)
    }

    @Test
    fun `no contract - not fetched`() {
        doReturn(MANDATE).whenever(mandateClient).get(201)

        mvc.perform(get("/mandates/201")).andReturn()

        verify(contractClient, never()).get(any())
    }

    @Test
    fun `customer unavailable`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(customerClient).get(any())

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertNull(view.contact)
        assertEquals("22 oct. 2026", view.signatureDeadline)
    }

    @Test
    fun `accepted assignment unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(assignmentClient).accepted(any())

        assertNull(mandate(mvc.perform(get("/mandates/201")).andReturn()).acceptedAt)
    }

    @Test
    fun `no accepted assignment`() {
        doReturn(null).whenever(assignmentClient).accepted(any())

        assertNull(mandate(mvc.perform(get("/mandates/201")).andReturn()).acceptedAt)
    }

    @Test
    fun `location names unavailable`() {
        whenever(locationClient.search(any())).thenThrow(ResourceAccessException("timeout"))

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertEquals("Secteurs" to "—", view.keyCriteria[0])
    }

    @Test
    fun `end - cancel open`() {
        assertEquals("cancel", model(mvc.perform(get("/mandates/201?end=cancel")).andReturn())["end"])
    }

    @Test
    fun `end - close redirects to the closing page`() {
        val result = mvc.perform(get("/mandates/201?end=close")).andReturn()

        assertEquals("redirect:/mandates/201/close", result.modelAndView?.viewName)
    }

    @Test
    fun `end - ignored once closed`() {
        doReturn(PENDING.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(201)

        assertNull(model(mvc.perform(get("/mandates/201?end=cancel")).andReturn())["end"])
    }

    @Test
    fun `end - unknown ignored`() {
        assertNull(model(mvc.perform(get("/mandates/201?end=xyz")).andReturn())["end"])
    }

    // Closed deal line

    @Test
    fun `closed - deal - the listing and its mandate`() {
        doReturn(APPROVED.copy(status = MandateStatus.CLOSED, dealMatchId = 100)).whenever(mandateClient).get(201)
        doReturn(Match(id = 100, mandateId = 201, listingId = 501)).whenever(matchClient).get(100)
        doReturn(ListingViewsTest.LISTING).whenever(listingClient).get(501)

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertEquals("Annonce n° 501", view.listingLabel)
        assertEquals("Fermé avec le mandat n° 203", view.counterpartLabel)
        assertNull(view.clientLabel)
        assertNull(view.outside)
        verify(matchClient).get(100)
        verify(listingClient).get(501)
    }

    @Test
    fun `closed - deal - match unavailable leaves it out`() {
        doReturn(APPROVED.copy(status = MandateStatus.CLOSED, dealMatchId = 100)).whenever(mandateClient).get(201)
        doThrow(ResourceAccessException("down")).whenever(matchClient).get(100)

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertNull(view.listingLabel)
        assertNull(view.counterpartLabel)
        assertNull(view.clientLabel)
        assertNull(view.outside)
    }

    @Test
    fun `closed - deal - listing unavailable leaves the number only`() {
        doReturn(APPROVED.copy(status = MandateStatus.CLOSED, dealMatchId = 100)).whenever(mandateClient).get(201)
        doReturn(Match(id = 100, mandateId = 201, listingId = 501)).whenever(matchClient).get(100)
        doThrow(ResourceAccessException("down")).whenever(listingClient).get(501)

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertEquals("Annonce n° 501", view.listingLabel)
        assertNull(view.counterpartLabel)
    }

    @Test
    fun `closed - listing deal - the client`() {
        doReturn(APPROVED.copy(status = MandateStatus.CLOSED, mandateType = MandateType.LISTING, dealMatchId = 100))
            .whenever(mandateClient).get(201)
        doReturn(Match(id = 100, mandateId = 204, listingId = 501)).whenever(matchClient).get(100)
        doReturn(APPROVED.copy(id = 204, customerId = 14)).whenever(mandateClient).get(204)
        doReturn(CUSTOMER.copy(id = 14, party = Party(firstName = "Sandrine", lastName = "Eboa"))).whenever(customerClient).get(14)

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertEquals("Annonce n° 501", view.listingLabel)
        assertEquals("Fermé avec le mandat n° 204", view.counterpartLabel)
        assertEquals("Client : Sandrine Eboa", view.clientLabel)
        assertNull(view.outside)
        verify(matchClient).get(100)
        verify(mandateClient).get(204)
        verify(customerClient).get(14)
    }

    @Test
    fun `closed - outside listing - the listing is still named`() {
        val closed = APPROVED.copy(status = MandateStatus.CLOSED, mandateType = MandateType.LISTING, dealOutside = true)
        doReturn(closed).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, closed.agentId!!)

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertEquals("Annonce n° 501", view.listingLabel)
        assertNull(view.counterpartLabel)
        assertNull(view.clientLabel)
        assertEquals("Client hors de vos recherches", view.outside)
        verify(listingClient).findIdByMandate(201, closed.agentId!!)
    }

    @Test
    fun `closed - outside search - no listing`() {
        doReturn(APPROVED.copy(status = MandateStatus.CLOSED, dealOutside = true)).whenever(mandateClient).get(201)

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertNull(view.listingLabel)
        assertEquals("Bien hors de vos annonces", view.outside)
    }

    @Test
    fun `not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/login", mvc.perform(get("/mandates/201")).andReturn().modelAndView?.viewName)
    }

    // Matches

    private val matchA = MatchFixtures.MATCH_A.copy(mandateId = 201)
    private val matchB = MatchFixtures.MATCH_B.copy(mandateId = 201, status = MatchStatus.PROPOSED)
    private val listingA = MatchFixtures.LISTING_A.copy(cityId = DOUALA, neighborhoodId = BONAPRISO)
    private val listingB = MatchFixtures.LISTING_B.copy(cityId = DOUALA, neighborhoodId = AKWA)

    private fun section(result: MvcResult) = model(result)["matchSection"] as MatchSectionView

    private fun withMatches() {
        doReturn(APPROVED).whenever(mandateClient).get(201)
        doReturn(listOf(matchB, matchA, matchA.copy(id = 9009, listingId = 3099, status = MatchStatus.DISMISSED)))
            .whenever(matchClient).search(any(), any(), any())
        doReturn(listOf(listingA, listingB)).whenever(listingClient).searchByIds(any())
    }

    @Test
    fun `approved search - matches`() {
        withMatches()

        val result = mvc.perform(get("/mandates/201")).andReturn()

        val section = section(result)
        assertEquals(listOf(9001L, 9003L), section.matches.map { it.id })
        assertEquals(1, section.dismissed)
        assertEquals("Quartier" to "Bonapriso", section.matches[0].facts[1])
        assertEquals("Quartier" to "Akwa", section.matches[1].facts[1])
        verify(matchClient).search(
            listOf(201L),
            listOf(
                MatchStatus.ACCEPTED,
                MatchStatus.ACTIVE,
                MatchStatus.PROPOSED,
                MatchStatus.EXPIRED,
                MatchStatus.REJECTED,
                MatchStatus.DISMISSED,
            ),
            100,
        )
        verify(listingClient).searchByIds(listOf(3041L, 3012L))
        assertNull(model(result)["dismissing"])
        assertEquals("2 biens", (model(result)["searchStats"] as SearchStatsView).label)
    }

    @Test
    fun `approved search - no match`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)
        doReturn(emptyList<Any>()).whenever(matchClient).search(any(), any(), any())

        val section = section(mvc.perform(get("/mandates/201")).andReturn())

        assertTrue(section.matches.isEmpty())
        assertEquals(false, section.unavailable)
    }

    @Test
    fun `approved search - search stats with visits`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)
        doReturn(listOf(matchA.copy(status = MatchStatus.ACCEPTED), matchB))
            .whenever(matchClient).search(any(), any(), any())
        doReturn(listOf(listingA, listingB)).whenever(listingClient).searchByIds(any())
        doReturn(
            listOf(
                VisitFixtures.summary(VisitFixtures.requested().copy(mandateId = 201, matchId = 9001)),
                VisitFixtures.summary(VisitFixtures.completed().copy(id = 7002, mandateId = 201, matchId = 9003)),
            ),
        ).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("2 biens · 1 intéressé · 2 visites", (model(result)["searchStats"] as SearchStatsView).label)
    }

    @Test
    fun `approved search - matches unavailable`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)
        doThrow(ResourceAccessException("down")).whenever(matchClient).search(any(), any(), any())

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("mandate", result.modelAndView?.viewName)
        assertTrue(section(result).unavailable)
        assertEquals(201L, mandate(result).id)
        assertNull(model(result)["searchStats"])
        verify(listingClient, never()).searchByIds(any())
    }

    @Test
    fun `approved search - listings unavailable`() {
        withMatches()
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(listingClient).searchByIds(any())

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertTrue(section(result).unavailable)
        assertEquals(201L, mandate(result).id)
    }

    /** A signed search whose client has an open visit on [matchA], plus a proposal still awaiting an answer. */
    private fun withVisit(vararg visits: VisitSummary) {
        doReturn(APPROVED).whenever(mandateClient).get(201)
        doReturn(
            listOf(
                matchB,
                matchA.copy(status = MatchStatus.ACCEPTED),
                matchA.copy(id = 9009, listingId = 3099, status = MatchStatus.DISMISSED),
            ),
        ).whenever(matchClient).search(any(), any(), any())
        doReturn(listOf(listingA, listingB)).whenever(listingClient).searchByIds(any())
        doReturn(visits.toList()).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)
    }

    @Test
    fun `approved search - open visit stays in its match card`() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        withVisit(VisitFixtures.summary(VisitFixtures.requested().copy(mandateId = 201, matchId = 9001)))

        val result = mvc.perform(get("/mandates/201")).andReturn()
        val section = section(result)

        assertEquals(listOf(9001L, 9003L), section.matches.map { it.id })
        assertEquals(VisitStatus.REQUESTED, section.matches[0].visit?.status)
        assertEquals(MatchGroup.REQUESTED, section.matches[0].group)
        assertTrue(section.hasPendingVisit)
        assertEquals("#match-9001", (model(result)["pendingVisit"] as PendingVisitView).url)
        verify(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)
    }

    @Test
    fun `approved search - visits unavailable keeps every match`() {
        withMatches()
        doThrow(ResourceAccessException("timeout")).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertTrue(section(result).visitsUnavailable)
        assertEquals(listOf(9001L, 9003L), section(result).matches.map { it.id })
        assertNull(model(result)["pendingVisit"])
    }

    @Test
    fun `approved search - stale visit leaves its match shown`() {
        withVisit(
            VisitFixtures.summary(
                VisitFixtures.requested().copy(
                    mandateId = 201,
                    matchId = 9001,
                    createdAt = VisitFixtures.date("2026-10-15T10:00:00Z"),
                ),
            ),
        )

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals(listOf(9001L, 9003L), section(result).matches.map { it.id })
        assertNull(section(result).matches[0].visit)
        assertNull(model(result)["pendingVisit"])
    }

    @Test
    fun `not a signed search - no matches`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)

        val landlord = mvc.perform(get("/mandates/201")).andReturn()
        doReturn(MANDATE).whenever(mandateClient).get(201)
        val assigned = mvc.perform(get("/mandates/201")).andReturn()

        assertNull(model(landlord)["matchSection"])
        assertNull(model(assigned)["matchSection"])
        assertNull(model(landlord)["searchStats"])
        assertNull(model(assigned)["searchStats"])
        verify(matchClient, never()).search(any(), any(), any())
    }

    @Test
    fun `not a signed search - no pending visit`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        val landlord = mvc.perform(get("/mandates/201")).andReturn()
        doReturn(MANDATE).whenever(mandateClient).get(201)
        val assigned = mvc.perform(get("/mandates/201")).andReturn()

        assertNull(model(landlord)["pendingVisit"])
        assertNull(model(assigned)["pendingVisit"])
        verify(visitClient, never()).search(any(), any(), any(), any())
    }

    @Test
    fun `dismiss confirmation open`() {
        withMatches()

        assertEquals(9001L, model(mvc.perform(get("/mandates/201?dismiss=9001")).andReturn())["dismissing"])
    }

    @Test
    fun `dismiss confirmation - not an active match of the page`() {
        withMatches()

        assertNull(model(mvc.perform(get("/mandates/201?dismiss=9999")).andReturn())["dismissing"])
        assertNull(model(mvc.perform(get("/mandates/201?dismiss=9003")).andReturn())["dismissing"])
        assertNull(model(mvc.perform(get("/mandates/201?dismiss=abc")).andReturn())["dismissing"])
    }

    @Test
    fun `match outcome - proposed`() {
        withMatches()

        val result = mvc.perform(get("/mandates/201").flashAttr("match", "proposed").flashAttr("matchListing", 3012L))
            .andReturn()

        assertEquals("Annonce n° 3012 envoyée à ray@gmail.com.", model(result)["matchOutcomeText"])
        assertEquals("success", model(result)["matchOutcomeStyle"])
        assertNull(model(result)["outcomeText"])
    }

    @Test
    fun `match outcome - proposed without contact`() {
        withMatches()
        doThrow(ResourceAccessException("timeout")).whenever(customerClient).get(any())

        val result = mvc.perform(get("/mandates/201").flashAttr("match", "proposed").flashAttr("matchListing", 3012L))
            .andReturn()

        assertEquals("Annonce n° 3012 envoyée au client.", model(result)["matchOutcomeText"])
    }

    @Test
    fun `match outcome - dismissed`() {
        withMatches()

        val result = mvc.perform(get("/mandates/201").flashAttr("match", "dismissed").flashAttr("matchListing", 3012L))
            .andReturn()

        assertEquals(
            "Annonce n° 3012 écartée : elle ne vous sera plus proposée pour ce mandat.",
            model(result)["matchOutcomeText"],
        )
    }

    @Test
    fun `match outcome - changed and error`() {
        withMatches()

        val changed = mvc.perform(get("/mandates/201").flashAttr("match", "changed")).andReturn()
        val failed = mvc.perform(get("/mandates/201").flashAttr("match", "error")).andReturn()

        assertEquals("Ce bien a changé entre-temps : vérifiez son état avant de recommencer.", model(changed)["matchOutcomeText"])
        assertEquals("warning", model(changed)["matchOutcomeStyle"])
        assertEquals("Action impossible pour le moment. Réessayez dans quelques instants.", model(failed)["matchOutcomeText"])
        assertEquals("error", model(failed)["matchOutcomeStyle"])
    }

    // Send for approval, close, cancel

    private fun outcome(result: MvcResult) = result.flashMap["outcome"]

    @Test
    fun `request approval`() {
        doReturn(MANDATE).whenever(mandateClient).get(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("sent", outcome(result))
        verify(mandateClient).requestApproval(201)
    }

    @Test
    fun `request approval - again`() {
        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("resent", outcome(result))
    }

    @Test
    fun `request approval - conflict`() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(mandateClient).requestApproval(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `request approval - server error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).requestApproval(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("error", outcome(result))
    }

    @Test
    fun `request approval - mandate unavailable - still sent`() {
        doThrow(ResourceAccessException("timeout")).whenever(mandateClient).get(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("sent", outcome(result))
        verify(mandateClient).requestApproval(201)
    }

    @Test
    fun `request approval - forbidden`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).requestApproval(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `forbidden - locked`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("mandate", result.modelAndView?.viewName)
        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["mandate"])
    }

    @Test
    fun cancel() {
        val result = mvc.perform(
            post("/mandates/201/cancel")
                .param("cancellationCode", "CHANGE_OF_PLANS")
                .param("cancellationReason", "  Il reste chez lui  "),
        ).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("cancelled", outcome(result))
        verify(mandateClient).cancel(201, CancelMandateRequest(MandateCancellationCode.CHANGE_OF_PLANS, "Il reste chez lui"))
    }

    @Test
    fun `cancel - blank reason sent as null`() {
        mvc.perform(post("/mandates/201/cancel").param("cancellationCode", "OTHER").param("cancellationReason", "  "))
            .andReturn()

        verify(mandateClient).cancel(201, CancelMandateRequest(MandateCancellationCode.OTHER, null))
    }

    @Test
    fun `cancel - conflict`() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(mandateClient).cancel(any(), any())

        val result = mvc.perform(post("/mandates/201/cancel").param("cancellationCode", "OTHER")).andReturn()

        assertEquals("changed", outcome(result))
    }

    @Test
    fun `cancel - no code`() {
        for (code in listOf(null, "UNKNOWN", "EXPIRED", "XYZ")) {
            val request = post("/mandates/201/cancel").param("cancellationReason", "Pourquoi pas")
            code?.let { request.param("cancellationCode", it) }

            val result = mvc.perform(request).andReturn()

            assertEquals("mandate", result.modelAndView?.viewName, code)
            assertEquals("cancel", model(result)["end"])
            assertEquals(mapOf("cancellationCode" to "Choisissez la raison de l'annulation."), model(result)["errors"])
            assertEquals("Pourquoi pas", (model(result)["form"] as MandateEndForm).cancellationReason)
        }
        verify(mandateClient, never()).cancel(any(), any())
    }

    @Test
    fun `cancel - reason too long`() {
        val result = mvc.perform(
            post("/mandates/201/cancel").param("cancellationCode", "OTHER").param("cancellationReason", "x".repeat(501)),
        ).andReturn()

        @Suppress("UNCHECKED_CAST")
        val errors = model(result)["errors"] as Map<String, String>
        assertTrue(errors.containsKey("cancellationReason"))
        verify(mandateClient, never()).cancel(any(), any())
    }

    @Test
    fun `cancel - already closed`() {
        doReturn(APPROVED.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(201)

        val result = mvc.perform(post("/mandates/201/cancel").param("cancellationCode", "")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `actions - not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        for (action in listOf("request-approval", "cancel")) {
            assertEquals(
                "redirect:/login",
                mvc.perform(post("/mandates/201/$action")).andReturn().modelAndView?.viewName,
            )
        }
        verify(mandateClient, never()).requestApproval(any())
    }

    @Test
    fun `outcome - sent`() {
        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "sent")).andReturn()

        assertEquals("Mandat envoyé à ray@gmail.com pour signature.", model(result)["outcomeText"])
        assertEquals("success", model(result)["outcomeStyle"])
    }

    @Test
    fun `outcome - sent without contact`() {
        doThrow(ResourceAccessException("timeout")).whenever(customerClient).get(any())

        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "resent")).andReturn()

        assertEquals("Mandat renvoyé au client pour signature.", model(result)["outcomeText"])
    }

    @Test
    fun `outcome - saved while pending approval`() {
        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "saved-unsent")).andReturn()

        assertEquals(
            "Mandat modifié. L'ancienne version ne peut plus être signée : envoyez-le à nouveau pour que le client " +
                "signe la nouvelle.",
            model(result)["outcomeText"],
        )
    }

    @Test
    fun `request approval - customer e-mail of another member`() {
        doThrow(conflict(ErrorCode.CONTRACT_PARTY_MEMBERSHIP_CONFLICT)).whenever(mandateClient).requestApproval(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("email-conflict", outcome(result))
    }

    @Test
    fun `outcome - email conflict`() {
        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "email-conflict")).andReturn()

        assertEquals(
            "L'adresse e-mail du client est déjà utilisée par un autre compte : corrigez-la avant de demander sa signature.",
            model(result)["outcomeText"],
        )
        assertEquals("error", model(result)["outcomeStyle"])
    }

    @Test
    fun `outcome - unknown ignored`() {
        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "xyz")).andReturn()

        assertNull(model(result)["outcomeText"])
    }

    @Test
    fun `outcome - changed on a locked mandate`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "changed")).andReturn()

        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["outcomeText"])
    }

    // Edit

    private fun answers(result: MvcResult) = model(result)["answers"] as MandateEditForm

    private fun conflict(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult) = model(result)["errors"] as Map<String, String>

    /** A valid edit, [changes] replacing its values. */
    private fun edit(vararg changes: Pair<String, String>): MockHttpServletRequestBuilder {
        val params = mapOf(
            "neighborhoodIds" to BONAPRISO.toString(),
            "propertyCategory" to "RESIDENTIAL",
            "propertyType" to "APARTMENT",
            "rooms" to "3",
            "furnitureType" to "ANY",
            "parkings" to "1",
            "area" to "80",
            "amenities" to "waterWell",
            "priceAmount" to "150 000",
            "movingPeriod" to "WITHIN_1_MONTH",
            "comments" to "Près d'une école",
        ) + changes
        val request = post("/mandates/201/edit")
        params.forEach { (name, value) -> request.param(name, value) }
        return request
    }

    @Test
    fun `edit form`() {
        val result = mvc.perform(get("/mandates/201/edit")).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals("HOUSE", answers(result).propertyType)
        assertEquals("250\u00a0000", answers(result).priceAmount)
        assertEquals(emptyMap(), errors(result))
        assertEquals("Locataire · Maison", mandate(result).title)
        assertEquals("Douala", (model(result)["city"] as com.wutsi.ndopify.refdata.dto.Location).name)
        @Suppress("UNCHECKED_CAST")
        val neighborhoods = model(result)["neighborhoods"] as List<com.wutsi.ndopify.refdata.dto.Location>
        assertEquals(listOf("Bonapriso", "Akwa"), neighborhoods.map { it.name })
        assertEquals(false, model(result)["owner"])
        assertNull(model(result)["locked"])
    }

    @Test
    fun `edit form - approved - locked`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201/edit")).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals("approved", model(result)["locked"])
        assertNull(model(result)["answers"])
    }

    @Test
    fun `edit form - not found - locked`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        assertEquals("unavailable", model(mvc.perform(get("/mandates/201/edit")).andReturn())["locked"])
    }

    @Test
    fun `edit form - malformed id - locked`() {
        assertEquals("unavailable", model(mvc.perform(get("/mandates/abc/edit")).andReturn())["locked"])
    }

    @Test
    fun save() {
        val result = mvc.perform(edit()).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("saved-unsent", outcome(result)) // Its contract cancelled by ndopify-server: to send again
        val request = argumentCaptor<UpdateMandateRequest>()
        verify(mandateClient).update(com.nhaarman.mockitokotlin2.eq(201L), request.capture())
        assertEquals(listOf(BONAPRISO), request.firstValue.neighbourhoodIds)
        assertEquals(150000L, request.firstValue.price.amount)
        assertEquals(MovingPeriod.WITHIN_1_MONTH, request.firstValue.movingPeriod)
        assertEquals(PropertyCategory.RESIDENTIAL, request.firstValue.propertyCategory)
        assertEquals(true, request.firstValue.waterWell)
        assertEquals(null, request.firstValue.furnitureType)
    }

    @Test
    fun `save - sends the version the form was loaded with`() {
        doReturn(PENDING.copy(version = 5)).whenever(mandateClient).get(201)

        mvc.perform(edit("version" to "4")).andReturn()

        val request = argumentCaptor<UpdateMandateRequest>()
        verify(mandateClient).update(com.nhaarman.mockitokotlin2.eq(201L), request.capture())
        assertEquals(4L, request.firstValue.version)
    }

    @Test
    fun `save - changed since - the form again, with the answers, based on the new version`() {
        doReturn(PENDING.copy(version = 5)).whenever(mandateClient).get(201)
        doThrow(conflict(ErrorCode.MANDATE_VERSION_CONFLICT)).whenever(mandateClient).update(any(), any())

        val result = mvc.perform(edit("version" to "4")).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals(
            "Ce mandat a été modifié entre-temps. Vos réponses sont conservées : vérifiez-les, puis enregistrez à nouveau.",
            model(result)["error"],
        )
        assertEquals("150 000", answers(result).priceAmount)
        assertEquals(5L, answers(result).version)
        assertTrue(result.flashMap.isEmpty())
    }

    @Test
    fun `save - other conflict - error`() {
        doThrow(conflict(ErrorCode.MANDATE_NOT_APPROVED)).whenever(mandateClient).update(any(), any())

        val result = mvc.perform(edit()).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals(
            "Enregistrement impossible pour le moment. Réessayez dans quelques instants.",
            model(result)["error"],
        )
    }

    @Test
    fun `save - assigned`() {
        doReturn(PENDING.copy(status = MandateStatus.ASSIGNED)).whenever(mandateClient).get(201)

        val result = mvc.perform(edit()).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("saved", outcome(result))
    }

    @Test
    fun `save - signed or closed meanwhile - changed`() {
        listOf(ErrorCode.MANDATE_ALREADY_APPROVED, ErrorCode.MANDATE_ALREADY_CLOSED).forEach { code ->
            doThrow(conflict(code)).whenever(mandateClient).update(any(), any())

            val result = mvc.perform(edit()).andReturn()

            assertEquals("redirect:/mandates/201", result.modelAndView?.viewName, code)
            assertEquals("changed", outcome(result), code)
        }
    }

    @Test
    fun `save - approved since`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        val result = mvc.perform(edit()).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - mandate gone`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(edit()).andReturn()

        assertEquals("changed", outcome(result))
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - no longer the agent's`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(201)

        val result = mvc.perform(edit()).andReturn()

        assertEquals("changed", outcome(result))
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - invalid`() {
        val result = mvc.perform(edit("priceAmount" to "", "rooms" to "")).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals(setOf("priceAmount", "rooms"), errors(result).keys)
        assertEquals("80", answers(result).area)
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - unknown enum - invalid, not a 400`() {
        val result = mvc.perform(edit("movingPeriod" to "SOON", "propertyCategory" to "XYZ")).andReturn()

        assertEquals(200, result.response.status)
        assertEquals(setOf("movingPeriod", "propertyCategory", "propertyType"), errors(result).keys)
    }

    @Test
    fun `save - neighbourhood outside the city`() {
        val result = mvc.perform(edit("neighborhoodIds" to BASTOS.toString())).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals("Choisissez des quartiers de la ville sélectionnée.", errors(result)["neighborhoodIds"])
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - server error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).update(any(), any())

        val result = mvc.perform(edit()).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals(
            "Enregistrement impossible pour le moment. Réessayez dans quelques instants.",
            model(result)["error"],
        )
        assertEquals("150 000", answers(result).priceAmount)
    }

    @Test
    fun `edit - not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/login", mvc.perform(get("/mandates/201/edit")).andReturn().modelAndView?.viewName)
        assertEquals("redirect:/login", mvc.perform(edit()).andReturn().modelAndView?.viewName)
    }
}
