package com.wutsi.ndopify.npc.ui.home

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.config.Parallel
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.assignment.AssignmentController
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.ASSIGNMENT
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest
import com.wutsi.ndopify.npc.ui.mandate.MandateSummariesTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures
import com.wutsi.ndopify.npc.ui.visit.VisitRules
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.web.core.service.TenantContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.ResourceAccessException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Renders the *Mandats proposés* section of `templates/index.html` through [HomeController] with a real Thymeleaf
 * engine. The profile section is covered by `ProfileTemplateTest`.
 */
class HomeTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val kycClient = mock<NdKycClient>()
    private val assignmentClient = mock<NdAssignmentClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val listingClient = mock<NdListingClient>()
    private val visitClient = mock<NdVisitClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private val parallel = Parallel()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(AssignmentViewsTest.NOW).whenever(clock).millis()
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(listOf(MandateSummariesTest.summary(MANDATE))).whenever(mandateClient).offered(AGENT.id)
        doReturn(
            listOf(
                MandateSummariesTest.summary(MandateCardsTest.MANDATE),
                MandateSummariesTest.summary(
                    MandateCardsTest.MANDATE.copy(id = 202, status = MandateStatus.PENDING_APPROVAL, contractId = "c-202"),
                ),
            ),
        ).whenever(mandateClient).active(AGENT.id)
        doReturn(emptyList<VisitSummary>()).whenever(visitClient).search(AGENT.id, VisitRules.OPEN)
        doReturn(emptyList<CustomerSummary>()).whenever(customerClient).search(any())

        val engine = SpringTemplateEngine().apply {
            setTemplateResolver(
                ClassLoaderTemplateResolver().apply {
                    prefix = "templates/"
                    suffix = ".html"
                    characterEncoding = "UTF-8"
                },
            )
        }
        val viewResolver = ThymeleafViewResolver().apply {
            templateEngine = engine
            characterEncoding = "UTF-8"
        }
        mvc = MockMvcBuilders
            .standaloneSetup(
                HomeController(
                    currentAgent,
                    kycClient,
                    assignmentClient,
                    mandateClient,
                    listingClient,
                    visitClient,
                    customerClient,
                    locationClient,
                    tenantContext,
                    clock,
                    parallel,
                ),
                AssignmentController(assignmentClient),
            )
            .setControllerAdvice(SignedInAdvice())
            .setViewResolvers(viewResolver)
            .build()
    }

    @AfterEach
    fun tearDown() {
        parallel.close()
        SecurityContextHolder.clearContext()
    }

    private fun render(request: AbstractMockHttpServletRequestBuilder<*>): Document =
        Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)

    @Test
    fun offer() {
        val page = render(get("/"))

        val card = page.selectFirst("[data-assignment]")!!
        assertEquals("assignment-7", card.id())
        assertEquals("7", card.attr("data-assignment-id"))
        assertEquals((AssignmentViewsTest.NOW + 52 * 60_000L).toString(), card.attr("data-expires-at"))
        assertEquals("Recherche", card.selectFirst("h3")?.text())
        assertEquals("search", card.selectFirst("h3 [data-mandate-kind]")?.attr("data-mandate-kind"))
        assertEquals("true", card.selectFirst("h3 [data-mandate-kind] svg")?.attr("aria-hidden"))
        assertEquals("52 min", card.selectFirst("[data-timer-value]")?.text())
        assertEquals("Secteurs", card.selectFirst("[data-fact=sector]")?.previousElementSibling()?.text())
        assertEquals("Bonapriso, Akwa", card.selectFirst("[data-fact=sector]")?.text())
        assertEquals("150K\u00a0FCFA / mois", card.selectFirst("[data-fact=price]")?.wholeText())
        assertEquals("Dans moins d'1 mois", card.selectFirst("[data-fact=moving]")?.text())
        assertEquals("/assignments/7/accept", card.selectFirst("form[data-accept-form]")?.attr("action"))
        assertEquals("post", card.selectFirst("form[data-accept-form]")?.attr("method"))
        assertEquals("/?decline=7#assignment-7", card.selectFirst("a[data-decline]")?.attr("href"))
        assertFalse(card.selectFirst("[data-actions]")!!.hasAttr("hidden"))
        assertTrue(card.selectFirst("[data-decline-confirm]")!!.hasAttr("hidden"))
        assertEquals(8, card.select(".review__item").size)
        assertEquals("Gardien de sécurité", card.select(".review__item").last()?.selectFirst(".review__value")?.text())
        assertFalse(page.text().contains("677")) // the mandate's comments
        assertNotNull(page.selectFirst("[data-assignments-section] > p:not(.visually-hidden)"))
        assertNull(page.selectFirst("[data-assignments-unavailable]"))
        assertNull(page.selectFirst("[data-assignment-outcome]"))
    }

    @Test
    fun `decline confirmation without JS`() {
        val card = render(get("/?decline=7")).selectFirst("[data-assignment]")!!

        assertFalse(card.selectFirst("[data-decline-confirm]")!!.hasAttr("hidden"))
        assertTrue(card.selectFirst("[data-actions]")!!.hasAttr("hidden"))
        assertEquals("/assignments/7/decline", card.selectFirst("[data-decline-confirm] form")?.attr("action"))
        assertEquals("/#assignment-7", card.selectFirst("[data-decline-cancel]")?.attr("href"))
    }

    @Test
    fun `no offer`() {
        doReturn(emptyList<AssignmentSummary>()).whenever(assignmentClient).pending(AGENT.id)

        val page = render(get("/"))

        assertNull(page.selectFirst("[data-assignments-section]")) // No section at all
        assertNotNull(page.selectFirst("#mandates-title"))
    }

    @Test
    fun `no offer left - the outcome only`() {
        doReturn(emptyList<AssignmentSummary>()).whenever(assignmentClient).pending(AGENT.id)

        val section = render(get("/").flashAttr("assignment", "declined")).selectFirst("[data-assignments-section]")!!

        assertEquals("Mandat refusé", section.selectFirst("[data-assignment-outcome] .alert__title")?.text())
        assertNull(section.selectFirst("[data-assignments]"))
        assertEquals(listOf("visually-hidden"), section.select("> p").map { it.className() }) // no intro
    }

    @Test
    fun `offers unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(assignmentClient).pending(any())

        val page = render(get("/"))

        assertNotNull(page.selectFirst("[data-assignments-section] [data-assignments-unavailable]"))
    }

    @Test
    fun `not verified - no section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING_VERIFICATION))).whenever(currentAgent)
            .get()
        doThrow(ResourceAccessException("no case")).whenever(kycClient).current(any())

        val page = render(get("/"))

        assertNull(page.selectFirst("#assignments-title"))
        assertNotNull(page.selectFirst("[data-kyc-banner]"))
    }

    @ParameterizedTest
    @CsvSource(
        "accepted, Mandat accepté",
        "declined, Mandat refusé",
        "closed, Ce mandat n'est plus disponible",
        "error, Action impossible pour le moment",
    )
    fun outcome(outcome: String, title: String) {
        val page = render(get("/").flashAttr("assignment", outcome))

        assertEquals(title, page.selectFirst("[data-assignment-outcome] .alert__title")?.text())
    }

    // Mes mandats

    @Test
    fun mandates() {
        val page = render(get("/"))

        assertEquals("Mes mandats", page.selectFirst("#mandates-title")?.text())
        val cards = page.select("[data-mandate-card]")
        assertEquals(listOf("201", "202"), cards.map { it.attr("data-mandate-id") })
        val card = cards[0]
        val link = card.selectFirst("h3 a.mandate-card__link")!!
        assertEquals("Locataire · Maison", link.text())
        assertEquals("/mandates/201", link.attr("href"))
        assertEquals("search", card.selectFirst("h3 [data-mandate-kind]")?.attr("data-mandate-kind"))
        assertEquals("mandate-201-title", card.selectFirst("h3")?.id())
        assertEquals("mandate-201-title", card.selectFirst("article")?.attr("aria-labelledby"))
        assertEquals("status-tag status-tag--info", card.selectFirst(".status-tag")?.className())
        assertEquals("Assigné", card.selectFirst(".status-tag")?.text())
        assertEquals(
            listOf("Secteurs", "Budget", "Emménagement"),
            card.select(".assignment__facts dt").map { it.text() })
        assertEquals("Bonapriso, Akwa", card.selectFirst(".assignment__facts dd")?.text())
        assertEquals("status-tag status-tag--warning", cards[1].selectFirst(".status-tag")?.className())
        assertTrue(page.selectFirst("[data-mandates-empty]")!!.hasAttr("hidden"))
        assertNull(page.selectFirst("[data-mandates-unavailable]"))
    }

    @Test
    fun `landlord's signed mandate - listing line`() {
        val landlord = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED, mandateType = MandateType.LISTING)
        doReturn(listOf(MandateSummariesTest.summary(landlord), MandateSummariesTest.summary(landlord.copy(id = 205))))
            .whenever(mandateClient).active(AGENT.id)
        doReturn(listOf(ListingSummary(id = 501, mandateId = 203, status = ListingStatus.DRAFT)))
            .whenever(listingClient).searchByMandates(any(), any())

        val cards = render(get("/")).select("[data-mandate-card]")

        assertEquals("listing", cards[0].selectFirst("h3 [data-mandate-kind]")?.attr("data-mandate-kind"))
        val line = cards[0].selectFirst("[data-listing-line]")!!
        assertEquals("/listings/501", line.selectFirst("a[data-listing-number]")?.attr("href"))
        assertEquals("Annonce", line.selectFirst("a[data-listing-number]")?.text()) // No number: one line on a phone
        assertEquals("status-tag status-tag--info", line.selectFirst("[data-listing-status-tag]")?.className())
        assertEquals("Brouillon", line.selectFirst("[data-listing-status-tag]")?.text())
        assertEquals("/mandates/205", cards[1].selectFirst("a[data-listing-number]")?.attr("href"))
        assertEquals("À créer", cards[1].selectFirst("[data-listing-status-tag]")?.text())
    }

    @Test
    fun `published listing - activity line`() {
        val landlord = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED, mandateType = MandateType.LISTING)
        doReturn(listOf(MandateSummariesTest.summary(landlord))).whenever(mandateClient).active(AGENT.id)
        doReturn(listOf(ListingSummary(id = 501, mandateId = 203, status = ListingStatus.PUBLISHED)))
            .whenever(listingClient).searchByMandates(any(), any())
        doReturn(mapOf(501L to ListingStats(501, 2, 1, 2))).whenever(listingClient).stats(listOf(501L))

        val card = render(get("/")).selectFirst("[data-mandate-card]")!!

        assertEquals("2 personnes intéressées · 3 visites", card.selectFirst("[data-listing-stats] > span > span:last-child")?.text())
    }

    @Test
    fun `published listing without stats - no activity line`() {
        val landlord = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED, mandateType = MandateType.LISTING)
        doReturn(listOf(MandateSummariesTest.summary(landlord))).whenever(mandateClient).active(AGENT.id)
        doReturn(listOf(ListingSummary(id = 501, mandateId = 203, status = ListingStatus.PUBLISHED)))
            .whenever(listingClient).searchByMandates(any(), any())
        doReturn(null).whenever(listingClient).stats(listOf(501L))

        val card = render(get("/")).selectFirst("[data-mandate-card]")!!

        assertNull(card.selectFirst("[data-listing-stats]"))
    }

    @Test
    fun `signed search - activity line`() {
        val search = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED)
        doReturn(listOf(MandateSummariesTest.summary(search))).whenever(mandateClient).active(AGENT.id)
        doReturn(mapOf(203L to MandateStats(203, 3, 1, 1, 2))).whenever(mandateClient).stats(any())

        val card = render(get("/")).selectFirst("[data-mandate-card]")!!

        val stats = card.selectFirst("[data-search-stats]")!!
        val link = stats.selectFirst("[data-search-stats-link]")!!
        assertEquals("/mandates/203#matches-title", link.attr("href"))
        assertEquals("3 biens · 1 intéressé · 3 visites", link.text())
        assertNull(card.selectFirst("[data-matches]"))
        assertNull(card.selectFirst("[data-proposals]"))
        assertNull(card.selectFirst(".mandate-card__next"))
    }

    @Test
    fun `signed search without stats - no activity line`() {
        val search = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED)
        doReturn(listOf(MandateSummariesTest.summary(search))).whenever(mandateClient).active(AGENT.id)
        doReturn(null).whenever(mandateClient).stats(any())

        val card = render(get("/")).selectFirst("[data-mandate-card]")!!

        assertNull(card.selectFirst("[data-search-stats]"))
    }

    @Test
    fun `signed search without match - empty label`() {
        val search = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED)
        doReturn(listOf(MandateSummariesTest.summary(search))).whenever(mandateClient).active(AGENT.id)
        doReturn(mapOf(203L to MandateStats(203))).whenever(mandateClient).stats(any())

        val link = render(get("/")).selectFirst("[data-mandate-card] [data-search-stats-link]")!!

        assertEquals("Aucun bien correspondant pour l'instant", link.text())
    }

    @Test
    fun `search - no listing line`() {
        assertNull(render(get("/")).selectFirst("[data-listing-line]"))
    }

    @Test
    fun `no mandate`() {
        doReturn(emptyList<MandateSummary>()).whenever(mandateClient).active(AGENT.id)

        val page = render(get("/"))

        assertNull(page.selectFirst("[data-mandate-card]"))
        val empty = page.selectFirst("[data-mandates-empty]")!!
        assertFalse(empty.hasAttr("hidden"))
        assertEquals(
            "Vous n'avez aucun mandat en cours. Acceptez un mandat proposé pour le retrouver ici.",
            empty.text(),
        )
    }

    @Test
    fun `mandates unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(mandateClient).active(any())

        val page = render(get("/"))

        assertNotNull(page.selectFirst("[data-mandates-unavailable]"))
        assertTrue(page.selectFirst("[data-mandates-empty]")!!.hasAttr("hidden"))
    }

    @Test
    fun `not verified - no mandates section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING_VERIFICATION))).whenever(currentAgent)
            .get()
        doThrow(ResourceAccessException("no case")).whenever(kycClient).current(any())

        assertNull(render(get("/")).selectFirst("#mandates-title"))
    }

    // Visites

    @Test
    fun visits() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        doReturn(
            listOf(
                VisitFixtures.summary(VisitFixtures.requested()),
                VisitFixtures.summary(VisitFixtures.confirmed()),
            ),
        ).whenever(visitClient).search(AGENT.id, VisitRules.OPEN)
        doReturn(listOf(VisitFixtures.listingSummary())).whenever(listingClient).searchByIds(listOf(3012L))
        doReturn(listOf(VisitFixtures.customerSummary())).whenever(customerClient).search(listOf(300L))

        val page = render(get("/"))

        assertEquals("Visites", page.selectFirst("#visits-title")?.text())
        val requested = page.selectFirst("[data-visit-group=requested]")!!
        assertEquals(1, requested.select("[data-visit-card]").size)
        val card = requested.selectFirst("[data-visit-card]")!!
        assertEquals("/visits/7001", card.selectFirst("[data-visit-link]")?.attr("href"))
        assertEquals("Sandrine Eboa", card.selectFirst("[data-visit-link]")?.text())
        assertEquals("sam. 7 novembre 2026 à 10:30", card.selectFirst("[data-visit-when]")?.text())
        assertEquals("Sur place · Appartement 3 chambres · Bonapriso", card.selectFirst("[data-visit-listing]")?.text())
        assertEquals("42 h", card.selectFirst("[data-visit-left]")?.text())
        assertNull(card.selectFirst("[data-visit-next]"))

        val upcoming = page.selectFirst("[data-visit-group=upcoming]")!!
        assertEquals(1, upcoming.select("[data-visit-card]").size)
        assertNull(upcoming.selectFirst("[data-visit-timer]"))
    }

    @Test
    fun `no visits - empty state`() {
        val page = render(get("/"))

        val empty = page.selectFirst("[data-visits-empty]")!!
        assertEquals(
            "Aucune visite prévue. Quand un client demande à visiter un bien que vous lui avez envoyé, " +
                "sa demande apparaît ici et vous êtes prévenu par e-mail.",
            empty.text(),
        )
        assertNull(page.selectFirst("[data-visits-unavailable]"))
    }

    @Test
    fun `visits unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(visitClient).search(any(), any(), any(), anyOrNull())

        val page = render(get("/"))

        assertNotNull(page.selectFirst("[data-visits-unavailable]"))
        assertNull(page.selectFirst("[data-visits-empty]"))
    }

    @Test
    fun `not verified - no visit section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING_VERIFICATION))).whenever(currentAgent)
            .get()
        doThrow(ResourceAccessException("no case")).whenever(kycClient).current(any())

        assertNull(render(get("/")).selectFirst("#visits-title"))
    }

    @Test
    fun upcoming() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()

        fun card(visit: com.wutsi.ndopify.visit.dto.Visit): Element {
            doReturn(listOf(VisitFixtures.summary(visit))).whenever(visitClient).search(AGENT.id, VisitRules.OPEN)
            return render(get("/")).selectFirst("[data-visit-group=upcoming] [data-visit-card]")!!
        }

        val confirmed = card(VisitFixtures.confirmed())
        assertEquals("Confirmée", confirmed.selectFirst("[data-visit-tag]")?.text())
        assertEquals("sam. 7 novembre 2026 à 10:30", confirmed.selectFirst("[data-visit-when]")?.text())
        assertNull(confirmed.selectFirst("[data-visit-timer]"))

        val window = card(VisitFixtures.window())
        assertEquals("À démarrer", window.selectFirst("[data-visit-tag]")?.text())
        assertEquals("Aujourd'hui · 10:30", window.selectFirst("[data-visit-when]")?.text())

        val checkedIn = card(VisitFixtures.checkedIn())
        assertEquals("En cours", checkedIn.selectFirst("[data-visit-tag]")?.text())
        assertEquals("En cours depuis 10:05", checkedIn.selectFirst("[data-visit-when]")?.text())
    }
}
