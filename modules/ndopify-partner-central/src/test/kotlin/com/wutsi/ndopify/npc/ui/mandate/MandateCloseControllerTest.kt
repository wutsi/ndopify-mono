package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.CloseMatchRequest
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.APPROVED
import com.wutsi.ndopify.npc.ui.match.MatchFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateCloseControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val matchClient = mock<NdMatchClient>()
    private val listingClient = mock<NdListingClient>()
    private val locationClient = mock<NdLocationClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    private val matchA = MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.ACCEPTED)
    private val listingA = MatchFixtures.LISTING_A

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(APPROVED).whenever(mandateClient).get(201)

        val controller = MandateCloseController(
            currentAgent,
            mandateClient,
            matchClient,
            listingClient,
            locationClient,
            customerClient,
            tenantContext,
            MandateDealCandidates(matchClient, mandateClient, customerClient, listingClient),
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun view(result: MvcResult) = model(result)["close"] as MandateCloseView

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult) = model(result)["errors"] as Map<String, String>

    private fun session(result: MvcResult) = result.request.session

    private fun draft(result: MvcResult): PendingMandateClose? =
        session(result).getAttribute(PendingMandateClose.SESSION_ATTRIBUTE) as PendingMandateClose?

    private fun withCandidate() {
        doReturn(listOf(matchA)).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))
        doReturn(listOf(listingA)).whenever(listingClient).searchByIds(listOf(3012L))
    }

    /** The reverse-order close: the match survives but its listing is no longer PUBLISHED (RENTED). */
    private fun withReverseOrderListing() {
        doReturn(listOf(matchA)).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))
        doReturn(emptyList<ListingSummary>()).whenever(listingClient).searchByIds(listOf(3012L))
        doReturn(ListingViewsTest.LISTING.copy(id = 3012, status = ListingStatus.RENTED, price = 240000))
            .whenever(listingClient).get(3012)
    }

    private fun outcome(result: MvcResult) = result.flashMap[MandateController.OUTCOME]

    // Locked

    @Test
    fun `locked when not the agent's`() {
        doReturn(APPROVED.copy(agentId = 999)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201/close")).andReturn()

        assertEquals("mandate/close", result.modelAndView?.viewName)
        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["close"])
    }

    @Test
    fun `locked when not approved`() {
        doReturn(APPROVED.copy(status = MandateStatus.ASSIGNED)).whenever(mandateClient).get(201)

        assertEquals(true, model(mvc.perform(get("/mandates/201/close")).andReturn())["locked"])
    }

    @Test
    fun `locked when the mandate is gone`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        assertEquals(true, model(mvc.perform(get("/mandates/201/close")).andReturn())["locked"])
    }

    // Counterpart step

    @Test
    fun `counterpart is skipped without candidates`() {
        doReturn(emptyList<Match>()).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))

        val result = mvc.perform(get("/mandates/201/close?step=counterpart")).andReturn()

        assertEquals("redirect:/mandates/201/close?step=price", result.modelAndView?.viewName)
    }

    @Test
    fun `no candidates - the price step is shown first`() {
        doReturn(emptyList<Match>()).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))

        val result = mvc.perform(get("/mandates/201/close")).andReturn()

        assertEquals("price", view(result).step)
        assertEquals(1, view(result).stepNumber)
        assertEquals(2, view(result).stepCount)
    }

    @Test
    fun `counterpart - a listing outside the candidates is refused`() {
        withCandidate()

        val result = mvc.perform(post("/mandates/201/close/counterpart").param("counterpart", "9999")).andReturn()

        assertEquals("mandate/close", result.modelAndView?.viewName)
        assertEquals("counterpart", view(result).step)
        assertEquals(mapOf("counterpart" to "Choisissez une contrepartie dans la liste, ou « hors de vos annonces »."), errors(result))
        assertNull(draft(result))
    }

    @Test
    fun `counterpart - outside is accepted`() {
        withCandidate()

        val result = mvc.perform(post("/mandates/201/close/counterpart").param("counterpart", "outside")).andReturn()

        assertEquals("redirect:/mandates/201/close?step=price", result.modelAndView?.viewName)
        assertEquals(true, draft(result)?.outside)
        assertNull(draft(result)?.matchId)
    }

    @Test
    fun `counterpart - a match is stored`() {
        withCandidate()

        val result = mvc.perform(post("/mandates/201/close/counterpart").param("counterpart", "9001")).andReturn()

        assertEquals("redirect:/mandates/201/close?step=price", result.modelAndView?.viewName)
        assertEquals(9001L, draft(result)?.matchId)
        assertEquals(false, draft(result)?.outside)
    }

    // Price step

    @Test
    fun `price - invalid`() {
        withCandidate()

        val result = mvc.perform(post("/mandates/201/close/price").param("finalPrice", "abc")).andReturn()

        assertEquals("mandate/close", result.modelAndView?.viewName)
        assertEquals("price", view(result).step)
        assertEquals(mapOf("finalPrice" to "Indiquez le loyer convenu, en FCFA par mois."), errors(result))
    }

    @Test
    fun `price - kept between steps`() {
        withCandidate()

        val result = mvc.perform(
            post("/mandates/201/close/price")
                .param("finalPrice", "240 000")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(outside = true)),
        ).andReturn()

        assertEquals("redirect:/mandates/201/close?step=confirm", result.modelAndView?.viewName)
        assertEquals(true, draft(result)?.outside)
        assertEquals(240000L, draft(result)?.priceAmount)
    }

    @Test
    fun `price - prefilled from the selected listing`() {
        withCandidate()

        val result = mvc.perform(
            get("/mandates/201/close?step=price")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9001)),
        ).andReturn()

        assertEquals("240000", (model(result)["form"] as PriceForm).finalPrice)
    }

    @Test
    fun `price - prefilled from the mandate when outside`() {
        withCandidate()

        val result = mvc.perform(
            get("/mandates/201/close?step=price")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(outside = true)),
        ).andReturn()

        assertEquals("250000", (model(result)["form"] as PriceForm).finalPrice)
    }

    @Test
    fun `price - a stored amount wins over the pre-fill`() {
        withCandidate()

        val result = mvc.perform(
            get("/mandates/201/close?step=price")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9001, priceAmount = 123456)),
        ).andReturn()

        assertEquals("123456", (model(result)["form"] as PriceForm).finalPrice)
    }

    @Test
    fun `price - prefilled for a listing mandate from its own listing`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, AGENT.id)
        doReturn(ListingViewsTest.LISTING.copy(status = ListingStatus.PUBLISHED)).whenever(listingClient).get(501)
        doReturn(listOf(ListingViewsTest.PROPOSALS[1]))
            .whenever(matchClient).searchByListings(listOf(501L), listOf(MatchStatus.ACCEPTED))
        doReturn(ListingViewsTest.SEARCHES.take(1)).whenever(mandateClient).searchByIds(listOf(204L), AGENT.id)
        doReturn(ListingViewsTest.CUSTOMERS.take(1)).whenever(customerClient).search(listOf(14L))

        val result = mvc.perform(
            get("/mandates/201/close?step=price")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9002)),
        ).andReturn()

        assertEquals("150000", (model(result)["form"] as PriceForm).finalPrice)
    }

    @Test
    fun `counterpart is skipped for a listing mandate with no listing`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(null).whenever(listingClient).findIdByMandate(201, AGENT.id)

        assertEquals(
            "redirect:/mandates/201/close?step=price",
            mvc.perform(get("/mandates/201/close?step=counterpart")).andReturn().modelAndView?.viewName,
        )

        val price = mvc.perform(get("/mandates/201/close?step=price")).andReturn()
        assertEquals("250000", (model(price)["form"] as PriceForm).finalPrice)

        mvc.perform(
            post("/mandates/201/close/confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(outside = true, priceAmount = 250000)),
        ).andReturn()
        verify(mandateClient).close(201, CloseMandateRequest(Money(250000, "XAF", PeriodicityType.MONTHLY)))
    }

    @Test
    fun `counterpart is skipped for a listing mandate whose listing is not published`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, AGENT.id)
        doReturn(ListingViewsTest.LISTING.copy(status = ListingStatus.DRAFT)).whenever(listingClient).get(501)

        assertEquals(
            "redirect:/mandates/201/close?step=price",
            mvc.perform(get("/mandates/201/close?step=counterpart")).andReturn().modelAndView?.viewName,
        )

        mvc.perform(
            post("/mandates/201/close/confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(outside = true, priceAmount = 250000)),
        ).andReturn()
        verify(mandateClient).close(201, CloseMandateRequest(Money(250000, "XAF", PeriodicityType.MONTHLY)))
    }

    // Confirm step

    @Test
    fun `confirm - match`() {
        val result = mvc.perform(
            post("/mandates/201/close/confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 100, priceAmount = 240000)),
        ).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("closed", outcome(result))
        verify(matchClient).close(100, CloseMatchRequest(Money(240000, "XAF", PeriodicityType.MONTHLY)))
    }

    @Test
    fun `confirm - outside`() {
        mvc.perform(
            post("/mandates/201/close/confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(outside = true, priceAmount = 240000)),
        ).andReturn()

        verify(mandateClient).close(201, CloseMandateRequest(Money(240000, "XAF", PeriodicityType.MONTHLY)))
    }

    @Test
    fun `confirm - a match that is no longer a candidate sends back to step 1`() {
        withCandidate()

        val result = mvc.perform(
            get("/mandates/201/close?step=confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9999, priceAmount = 240000)),
        ).andReturn()

        assertEquals("redirect:/mandates/201/close?step=counterpart", result.modelAndView?.viewName)
        assertEquals(true, result.flashMap[MandateCloseController.CONFLICT])
    }

    @Test
    fun `confirm - a match gone with no candidates left goes to the price step`() {
        doReturn(emptyList<Match>()).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))

        val result = mvc.perform(
            get("/mandates/201/close?step=confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9999)),
        ).andReturn()

        assertEquals("redirect:/mandates/201/close?step=price", result.modelAndView?.viewName)
        assertEquals(true, result.flashMap[MandateCloseController.CONFLICT])
    }

    @Test
    fun `confirm - read failure keeps the stored match`() {
        doThrow(ResourceAccessException("down")).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))

        val result = mvc.perform(
            get("/mandates/201/close?step=confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9001, priceAmount = 240000)),
        ).andReturn()

        assertEquals("mandate/close", result.modelAndView?.viewName)
        assertEquals("confirm", view(result).step)
        assertTrue(view(result).candidatesUnavailable)
        assertTrue(result.flashMap.isEmpty())
        assertEquals(9001L, draft(result)?.matchId)
    }

    // Reverse-order close: the listing side closed first, its listing is no longer PUBLISHED (RENTED/SOLD).

    @Test
    fun `counterpart - a search candidate whose listing is no longer published is still offered`() {
        withReverseOrderListing()

        val result = mvc.perform(get("/mandates/201/close?step=counterpart")).andReturn()

        assertEquals("mandate/close", result.modelAndView?.viewName)
        assertEquals(listOf(9001L), view(result).candidates.map { it.matchId })

        val post = mvc.perform(
            post("/mandates/201/close/counterpart").param("counterpart", "9001")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose()),
        ).andReturn()
        assertEquals("redirect:/mandates/201/close?step=price", post.modelAndView?.viewName)
        assertEquals(9001L, draft(post)?.matchId)
    }

    @Test
    fun `confirm - the reverse-order candidate closes the match`() {
        withReverseOrderListing()

        val confirm = mvc.perform(
            get("/mandates/201/close?step=confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9001, priceAmount = 240000)),
        ).andReturn()
        assertEquals("mandate/close", confirm.modelAndView?.viewName)
        assertEquals("confirm", view(confirm).step)

        val post = mvc.perform(
            post("/mandates/201/close/confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9001, priceAmount = 240000)),
        ).andReturn()
        assertEquals("redirect:/mandates/201", post.modelAndView?.viewName)
        assertEquals("closed", outcome(post))
        verify(matchClient).close(9001, CloseMatchRequest(Money(240000, "XAF", PeriodicityType.MONTHLY)))
        verify(mandateClient, never()).close(any(), any())
    }

    @Test
    fun `counterpart - a search candidate whose listing cannot be read shows the unavailable state`() {
        doReturn(listOf(matchA)).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))
        doReturn(emptyList<ListingSummary>()).whenever(listingClient).searchByIds(listOf(3012L))
        doThrow(ResourceAccessException("down")).whenever(listingClient).get(3012)

        val result = mvc.perform(get("/mandates/201/close?step=counterpart")).andReturn()

        assertEquals("mandate/close", result.modelAndView?.viewName)
        assertTrue(view(result).candidatesUnavailable)
        assertTrue(view(result).candidates.isEmpty())
    }

    @Test
    fun `confirm - 409 is changed`() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(matchClient).close(any(), any())

        val result = mvc.perform(
            post("/mandates/201/close/confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 100, priceAmount = 240000)),
        ).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `confirm - 5xx is error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(matchClient).close(any(), any())

        val result = mvc.perform(
            post("/mandates/201/close/confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 100, priceAmount = 240000)),
        ).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("error", outcome(result))
    }

    // Draft

    @Test
    fun `no step clears a stale draft`() {
        val result = mvc.perform(
            get("/mandates/201/close")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9001, priceAmount = 240000)),
        ).andReturn()

        assertEquals("mandate/close", result.modelAndView?.viewName)
        assertNull(draft(result))
    }

    @Test
    fun `not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/login", mvc.perform(get("/mandates/201/close")).andReturn().modelAndView?.viewName)
        assertEquals(
            "redirect:/login",
            mvc.perform(post("/mandates/201/close/confirm")).andReturn().modelAndView?.viewName,
        )
    }

    @Test
    fun `the candidates are shown as choices`() {
        withCandidate()

        val result = mvc.perform(get("/mandates/201/close?step=counterpart")).andReturn()

        assertEquals("counterpart", view(result).step)
        assertEquals(listOf(9001L), view(result).candidates.map { it.matchId })
        assertEquals(3, view(result).stepCount)
    }
}
