package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
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
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.ACCEPTED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.APPROVED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.date
import com.wutsi.ndopify.npc.ui.match.MatchFixtures
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures
import com.wutsi.ndopify.npc.ui.visit.VisitRules
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
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
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

/** Renders `templates/mandate.html` through [MandateController] with a real Thymeleaf engine. */
class MandateTemplateTest {
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
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        // A Long? method of a mock answers 0, not null: no listing unless a test says so
        doReturn(null).whenever(listingClient).findIdByMandate(any(), any())
        doReturn(NOW).whenever(clock).millis()
        doReturn(PENDING).whenever(mandateClient).get(201)
        doReturn(CONTRACT).whenever(contractClient).get("c-201")
        doReturn(CUSTOMER).whenever(customerClient).get(555)
        doReturn(ACCEPTED).whenever(assignmentClient).accepted(201)

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
                MandateController(
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
                ),
            )
            .setControllerAdvice(SignedInAdvice())
            .setViewResolvers(viewResolver)
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun render(request: AbstractMockHttpServletRequestBuilder<*>): Document =
        Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)

    private fun render(mandate: Mandate, url: String = "/mandates/201"): Document {
        doReturn(mandate).whenever(mandateClient).get(201)
        return render(get(url))
    }

    @Test
    fun `template comment markers are not rendered`() {
        val content = mvc.perform(get("/mandates/201")).andReturn().response.contentAsString

        assertFalse(content.contains("*/-->"))
    }

    @Test
    fun `pending approval`() {
        val page = render(get("/mandates/201"))

        assertEquals("Locataire · Maison — Ndopify Partner Central", page.title())
        assertEquals("Locataire · Maison", page.selectFirst("h1")?.text())
        assertEquals("search", page.selectFirst("h1 [data-mandate-kind]")?.attr("data-mandate-kind"))
        assertEquals("status-tag status-tag--warning", page.selectFirst(".mandate-intro .status-tag")?.className())
        assertEquals("En approbation", page.selectFirst(".mandate-intro .status-tag")?.text())
        assertEquals("Mandat n° 201 · accepté le 10 oct. 2026", page.selectFirst(".mandate__meta")?.text())
        assertEquals("/#mandates-title", page.selectFirst(".back-link a")?.attr("href"))
        assertNotNull(page.selectFirst("nav.user-menu"))

        val steps = page.select("[data-mandate-steps] li")
        assertEquals(listOf("ASSIGNED", "PENDING_APPROVAL", "APPROVED", "CLOSED"), steps.map { it.attr("data-step") })
        assertEquals(listOf("done", "current", "todo", "todo"), steps.map { it.attr("data-state") })
        assertEquals(listOf("", "step", "", ""), steps.map { it.attr("aria-current") })
        assertNotNull(steps[0].selectFirst(".tracker__mark svg"))
        assertNull(steps[1].selectFirst(".tracker__mark svg"))
        assertEquals("15 oct. 2026", steps[1].selectFirst("[data-step-date]")?.text())
        assertEquals("Étape 2 sur 4 · En approbation, le 15 oct. 2026", page.selectFirst("[data-step-summary]")?.text())

        assertEquals(listOf("PENDING_APPROVAL"), page.select("[data-panel]").map { it.attr("data-panel") })
        val panel = page.selectFirst("[data-panel=PENDING_APPROVAL]")!!
        assertEquals("Envoyé le 15 oct. 2026 · à signer avant le 22 oct. 2026.", panel.selectFirst("p")?.text())
        val resend = panel.selectFirst("form[data-request-approval-form]")!!
        assertEquals("/mandates/201/request-approval", resend.attr("action"))
        assertEquals("post", resend.attr("method"))
        assertEquals("btn btn--outline", resend.selectFirst("button")?.className())
        assertEquals("Renvoyer", resend.selectFirst("button")?.text())
        assertEquals("/mandates/201/edit", panel.selectFirst("a[data-edit-link]")?.attr("href"))
        assertTrue(panel.text().contains("Le mandat a été envoyé à ray@gmail.com."))
        assertTrue(panel.text().contains("le client a de nouveau 7 jours pour le signer."))
        assertFalse(page.text().contains("Simuler"))
    }

    @Test
    fun `pending approval - no request date`() {
        doReturn(CONTRACT.copy(signatureRequestedAt = null)).whenever(contractClient).get("c-201")

        val page = render(get("/mandates/201"))

        val panel = page.selectFirst("[data-panel=PENDING_APPROVAL]")!!
        assertTrue(panel.text().contains("renvoyez-le : le client pourra de nouveau le signer."), panel.text())
        assertFalse(panel.text().contains("jours"))
    }

    @Test
    fun `pending approval - deadline passed`() {
        doReturn(date("2026-10-23T10:00:00Z").time).whenever(clock).millis()

        val page = render(get("/mandates/201"))

        assertEquals("22 oct. 2026 (dépassé)", page.selectFirst("[data-signature-deadline]")?.text())
    }

    @Test
    fun assigned() {
        val page = render(MANDATE)

        val panel = page.selectFirst("[data-panel=ASSIGNED]")!!
        assertEquals(
            "Confirmez sa recherche avec le client, puis envoyez-lui le mandat à signer.",
            panel.selectFirst("p")?.text(),
        )
        assertEquals("Envoyer au client", panel.selectFirst("form[data-request-approval-form] button")?.text())
        assertEquals("btn btn--primary", panel.selectFirst("form[data-request-approval-form] button")?.className())
        assertEquals("/mandates/201/edit", panel.selectFirst("a[data-edit-link]")?.attr("href"))
        // No contract yet: no number of days
        assertTrue(
            panel.text().contains("qui le signe en ligne avant la date limite indiquée dans l'e-mail."),
            panel.text(),
        )
        assertFalse(panel.text().contains("jours"))
        // Cancel only: closing needs the approval.
        assertNull(page.selectFirst("a[data-end-open=close]"))
        assertEquals("/mandates/201?end=cancel#end-title", page.selectFirst("a[data-end-open=cancel]")?.attr("href"))
        // Present for mandate.js, hidden without ?end=.
        assertEquals(listOf("cancel"), page.select("form[data-end-form]").map { it.attr("data-end-form") })
        assertTrue(page.selectFirst("form[data-end-form]")!!.hasAttr("hidden"))
        assertFalse(page.selectFirst("[data-end-buttons]")!!.hasAttr("hidden"))
    }

    @Test
    fun approved() {
        val page = render(APPROVED)

        val panel = page.selectFirst("[data-panel=APPROVED]")!!
        assertEquals("Signé le 20 oct. 2026 · valable jusqu'au 28 mars 2027.", panel.selectFirst("p")?.text())
        assertTrue(panel.text().contains("Proposez des biens au client et organisez les visites."))
        assertNull(page.selectFirst("a[data-edit-link]"))
        assertEquals("/mandates/201?end=close#end-title", page.selectFirst("a[data-end-open=close]")?.attr("href"))
        assertEquals("btn btn--danger", page.selectFirst("a[data-end-open=cancel]")?.className())
    }

    @Test
    fun `approved - landlord`() {
        val page = render(APPROVED.copy(mandateType = MandateType.LISTING))

        assertEquals("listing", page.selectFirst("h1 [data-mandate-kind]")?.attr("data-mandate-kind"))
        assertEquals("Le bailleur", page.selectFirst("#contact-title")?.text())
        assertEquals("Son bien", page.selectFirst("#search-title")?.text())
        assertTrue(page.text().contains("Présentez le bien aux locataires et organisez les visites."))
    }

    @Test
    fun `approved - landlord without listing`() {
        val page = render(APPROVED.copy(mandateType = MandateType.LISTING))

        val box = page.selectFirst("[data-panel=APPROVED] [data-listing-actions]")!!
        assertEquals("Annonce", box.selectFirst("strong[data-listing-number]")?.text())
        assertEquals("À créer", box.selectFirst("[data-listing-status-tag]")?.text())
        assertTrue(box.selectFirst("[data-listing-text]")!!.text().startsWith("Visitez le bien"))
        val form = box.selectFirst("form[data-listing-create-form]")!!
        assertEquals("/mandates/201/listing", form.attr("action"))
        assertEquals("post", form.attr("method"))
        assertEquals("Créer l'annonce", form.selectFirst("button[data-listing-create]")?.text())
        assertNull(box.selectFirst("a[data-listing-link]"))
    }

    @Test
    fun `approved - landlord with listing`() {
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.LISTING).whenever(listingClient).get(501)

        val page = render(APPROVED.copy(mandateType = MandateType.LISTING))

        val box = page.selectFirst("[data-listing-actions]")!!
        val link = box.selectFirst("a[data-listing-link]")!!
        assertEquals("/listings/501", link.attr("href"))
        assertEquals("Continuer l'annonce", link.text())
        assertEquals("btn btn--primary", link.className())
        assertTrue(box.selectFirst("[data-listing-text]")!!.text().startsWith("Annonce en brouillon : encore à faire"))
        assertNull(box.selectFirst("form[data-listing-create-form]"))
        val number = box.selectFirst("a[data-listing-number]")!!
        assertEquals("Annonce n° 501", number.text())
        assertEquals("/listings/501", number.attr("href"))
        assertEquals("status-tag status-tag--info", box.selectFirst("[data-listing-status-tag]")?.className())
    }

    @Test
    fun `approved - landlord with listing published - the line only`() {
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = ListingViewsTest.SENT))
            .whenever(listingClient).get(501)

        val page = render(APPROVED.copy(mandateType = MandateType.LISTING))

        val box = page.selectFirst("[data-listing-actions]")!!
        assertEquals("/listings/501", box.selectFirst("a[data-listing-number]")?.attr("href"))
        assertEquals("Publiée le 22 oct. 2026", box.selectFirst("[data-listing-status-tag]")?.text())
        assertNull(box.selectFirst("[data-listing-text]"))
        assertNull(box.selectFirst(".mandate-panel__actions"))
        assertEquals(
            "Aucune personne intéressée pour l'instant · Aucune visite",
            box.selectFirst("[data-listing-stats] > span > span:last-child")?.text(),
        )
        assertNull(page.selectFirst("[data-mandate-visit-stats]")) // no duplicate visits line
    }

    @Test
    fun `approved - landlord with listing published - activity`() {
        publishedListing()
        doReturn(listOf(VisitFixtures.summary(VisitFixtures.confirmed()), VisitFixtures.summary(VisitFixtures.completed())))
            .whenever(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)

        val box = render(APPROVED.copy(mandateType = MandateType.LISTING)).selectFirst("[data-listing-actions]")!!

        assertEquals(
            "2 personnes intéressées · 2 visites",
            box.selectFirst("[data-listing-stats] > span > span:last-child")?.text(),
        )
    }

    private fun publishedListing() {
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = ListingViewsTest.SENT))
            .whenever(listingClient).get(501)
        doReturn(ListingViewsTest.PROPOSALS).whenever(matchClient).searchByListings(listOf(501), MatchRules.SENT)
        doReturn(ListingViewsTest.SEARCHES).whenever(mandateClient).searchByIds(listOf(207L, 204L, 305L), AGENT.id)
        doReturn(ListingViewsTest.CUSTOMERS).whenever(customerClient).search(listOf(14L, 17L))
        doReturn(emptyList<VisitSummary>())
            .whenever(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)
    }

    @Test
    fun `approved - landlord with listing published - proposals`() {
        publishedListing()

        val page = render(APPROVED.copy(mandateType = MandateType.LISTING))

        val section = page.selectFirst("[data-listing-proposals]")!!
        assertEquals("Propositions et visites", section.selectFirst("h2")?.text())
        assertEquals("Les visites à confirmer d'abord.", section.selectFirst("p")?.text())
        assertEquals(
            listOf("Client d'un autre agent", "Sandrine Eboa", "Carine Ndongo"),
            section.select("[data-proposal-client]").map { it.text() },
        )
        // Another agent's client: not a link
        assertEquals("span", section.selectFirst("[data-proposal-protected]")?.tagName())
        assertEquals(
            listOf("/mandates/204#matches-title", "/mandates/207#matches-title"),
            section.select("a[data-proposal-link]").map { it.attr("href") },
        )
        assertEquals("status-tag status-tag--success", section.selectFirst("[data-proposal-tag]")?.className())
        assertEquals("Intéressé le 29 oct. 2026", section.select("[data-proposal-summary]")[1].text())
        assertEquals(listOf("Intéressés (2)", "En attente (1)"), section.select(".assignments__group-title").map { it.text() })
        assertTrue(section.select(".assignments__group-title").all { it.text().contains("(") })
        assertNull(section.selectFirst("[data-proposals-empty]"))
        assertNull(section.selectFirst("[data-visits-unavailable]"))
        assertNull(page.selectFirst("[data-listing-visits]"))
        assertNull(page.selectFirst("[data-proposals-in-visit]"))
        // After the progress, where a search has its Biens correspondants
        assertEquals("mandate-progress", section.previousElementSiblings().first { it.tagName() == "section" }.className())
    }

    @Test
    fun `approved - landlord with listing published - an open visit stays in its card`() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        publishedListing()
        doReturn(
            listOf(
                VisitFixtures.summary(
                    VisitFixtures.requested().copy(matchId = 9002, mandateId = 204, listingId = 501, customerId = 14),
                ),
            ),
        ).whenever(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)

        val page = render(APPROVED.copy(mandateType = MandateType.LISTING))

        val card = page.selectFirst("[data-proposal][id^=proposal-]")!!
        assertEquals("proposal-9002", card.id())
        assertNull(card.selectFirst("[data-proposal-tag]"))
        assertEquals("Visite demandée", card.selectFirst("[data-visit-kind]")?.text())
        assertEquals("pour le sam. 7 novembre 2026 à 10:30", card.selectFirst("[data-visit-when]")?.text())
        assertEquals("Il reste 42 h pour confirmer", card.selectFirst("[data-visit-timer]")?.text())
        assertEquals("Confirmer la visite", card.selectFirst("[data-visit-link]")?.text())
        assertEquals("/visits/7001", card.selectFirst("[data-visit-link]")?.attr("href"))
        assertEquals("Envoyé le 28 oct. 2026", card.selectFirst("[data-proposal-summary]")?.text())
        assertEquals("1 refusé", page.selectFirst("[data-proposal-outcomes]")?.text())
        assertNull(page.selectFirst("section[data-listing-visits]"))
        assertNull(page.selectFirst("[data-proposals-in-visit]"))

        val pending = page.selectFirst("[data-mandate-pending-visit]")!!
        assertEquals("1 visite à confirmer · 42 h — Sandrine Eboa", pending.text())
        assertEquals("#proposal-9002", pending.attr("href"))
    }

    @Test
    fun `approved - landlord with listing published - visits unavailable`() {
        publishedListing()
        doThrow(ResourceAccessException("down")).whenever(visitClient).searchByListings(any(), any(), any(), any())

        val page = render(APPROVED.copy(mandateType = MandateType.LISTING))

        val section = page.selectFirst("[data-listing-proposals]")!!
        assertEquals(
            "Les visites de ce bien ne peuvent pas être affichées pour le moment.",
            section.selectFirst("[data-visits-unavailable]")?.text(),
        )
        assertEquals(3, page.select("[data-proposals-list] li").size)
        assertNull(page.selectFirst("[data-mandate-pending-visit]"))
    }

    @Test
    fun `approved - landlord with listing published - not sent to anyone`() {
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = ListingViewsTest.SENT))
            .whenever(listingClient).get(501)
        doReturn(emptyList<Any>()).whenever(matchClient).searchByListings(listOf(501), MatchRules.SENT)

        val section = render(APPROVED.copy(mandateType = MandateType.LISTING)).selectFirst("[data-listing-proposals]")!!

        assertNotNull(section.selectFirst("[data-proposals-empty]"))
        assertNull(section.selectFirst("[data-proposals-list]"))
    }

    @Test
    fun `approved - landlord with listing not published - no proposals`() {
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.LISTING).whenever(listingClient).get(501)

        assertNull(render(APPROVED.copy(mandateType = MandateType.LISTING)).selectFirst("[data-listing-proposals]"))
    }

    @Test
    fun `approved - landlord with listing not published - no interest line`() {
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.LISTING).whenever(listingClient).get(501)

        val box = render(APPROVED.copy(mandateType = MandateType.LISTING)).selectFirst("[data-listing-actions]")!!

        assertNull(box.selectFirst("[data-listing-stats]"))
    }

    @Test
    fun `approved - search has no listing`() {
        assertNull(render(APPROVED).selectFirst("[data-listing-actions]"))
    }

    @Test
    fun closed() {
        val page = render(
            APPROVED.copy(status = MandateStatus.CLOSED, closedAt = date("2026-11-01T10:00:00Z"), finalPrice = 240000, age = 12),
        )

        assertEquals(
            "Mandat fermé le 01 nov. 2026, au loyer de 240 000 FCFA / mois, 12 jours après son approbation.",
            page.selectFirst("[data-panel=CLOSED] .alert__title + p")?.text()?.replace('\u00a0', ' '),
        )
        assertNull(page.selectFirst("[data-end]"))
        assertEquals(listOf("done", "done", "done", "done"), page.select("[data-mandate-steps] li").map { it.attr("data-state") })
    }

    @Test
    fun cancelled() {
        val page = render(
            PENDING.copy(
                status = MandateStatus.CANCELLED,
                closedAt = date("2026-10-19T10:00:00Z"),
                cancellationCode = MandateCancellationCode.CHANGE_OF_PLANS,
                cancellationReason = "Il reste chez lui",
            ),
        )

        val panel = page.selectFirst("[data-panel=CANCELLED]")!!
        assertEquals("Le 19 oct. 2026 : changement de projet.", panel.select("p")[1].text())
        assertEquals("Il reste chez lui", panel.selectFirst("[data-cancellation-reason]")?.text())
        assertEquals("Annulé", page.selectFirst("[data-step=CLOSED] .mandate-steps__label")?.text())
        assertNull(page.selectFirst("[data-end]"))
    }

    @Test
    fun contact() {
        val page = render(PENDING.copy(preferredContactPeriod = com.wutsi.ndopify.listing.dto.ContactPeriodType.MORNING))

        val card = page.selectFirst(".contact-card")!!
        assertEquals("Le client", page.selectFirst("#contact-title")?.text())
        assertEquals("visually-hidden", page.selectFirst("#contact-title")?.className()) // For screen readers only
        // Right under the title, above the progress
        val contact = page.selectFirst("section[aria-labelledby=contact-title]")!!
        assertEquals("mandate-progress", contact.nextElementSiblings().first { it.tagName() == "section" }.className())
        assertEquals("Ray Sponsible · à appeler le matin", card.selectFirst("p")?.text())
        assertEquals("tel:+237699123456", card.selectFirst("a[data-contact=whatsapp]")?.attr("href"))
        assertEquals("+237 6 99 12 34 56", card.selectFirst("[data-contact-number]")?.text())
        assertEquals("https://wa.me/237699123456", card.selectFirst("a[data-contact-whatsapp]")?.attr("href"))
        assertEquals("btn btn--secondary btn--compact", card.selectFirst("a[data-contact-whatsapp]")?.className())
        assertEquals("mailto:ray@gmail.com", card.selectFirst("a[data-contact=email]")?.attr("href"))
    }

    @Test
    fun `contact unavailable`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(customerClient).get(any())

        val page = render(get("/mandates/201"))

        assertNull(page.selectFirst(".contact-card"))
        assertNotNull(page.selectFirst("[data-contact-unavailable]"))
        assertTrue(page.selectFirst("[data-panel=PENDING_APPROVAL]")!!.text().contains("Le mandat a été envoyé au client."))
    }

    @Test
    fun criteria() {
        val page = render(get("/mandates/201"))

        assertEquals("Sa recherche", page.selectFirst("#search-title")?.text())
        val key = page.select("[data-mandate-key-criteria] .review__item")
        assertEquals(
            listOf("Secteurs", "Budget", "Emménagement", "Type de bien", "Autres précisions"),
            key.map { it.selectFirst("dt")?.text() },
        )
        assertEquals("Maison, 3 chambres", key[3].selectFirst("dd")?.text())
        assertEquals("Voir tous les critères (3)", page.selectFirst("[data-mandate-more] summary")?.text())
        assertEquals(3, page.select("[data-mandate-criteria] .review__item").size)
    }

    @Test
    fun locked() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val page = render(get("/mandates/201"))

        assertEquals("Ce mandat n'est pas disponible", page.selectFirst("[data-mandate-locked] h1")?.text())
        assertNull(page.selectFirst("[data-mandate]"))
        assertEquals("/#mandates-title", page.selectFirst(".back-link a")?.attr("href"))
    }

    @Test
    fun `signed out - no user menu`() {
        SecurityContextHolder.clearContext()

        assertNull(render(get("/mandates/201")).selectFirst("nav.user-menu"))
    }

    // Send for approval, close, cancel

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "sent | success | Mandat envoyé à ray@gmail.com pour signature.",
            "resent | success | Mandat renvoyé à ray@gmail.com pour signature.",
            "closed | success | Mandat fermé : félicitations pour ce bail !",
            "cancelled | success | Mandat annulé. Il ne figure plus dans vos mandats en cours.",
            "saved | success | Mandat modifié.",
            "saved-unsent | success | Mandat modifié. L'ancienne version ne peut plus être signée : envoyez-le à nouveau pour que le client signe la nouvelle.",
            "changed | warning | Ce mandat a changé entre-temps : vérifiez son état avant de recommencer.",
            "error | error | Action impossible pour le moment. Réessayez dans quelques instants.",
        ],
    )
    fun outcome(outcome: String, style: String, text: String) {
        val flash = render(get("/mandates/201").flashAttr("outcome", outcome)).selectFirst("[data-mandate-flash]")!!

        assertEquals("alert alert--$style", flash.className())
        assertEquals("-1", flash.attr("tabindex"))
        assertEquals(text, flash.text())
    }

    @Test
    fun `saved - assigned`() {
        doReturn(MANDATE).whenever(mandateClient).get(201)

        assertEquals("Mandat modifié.", render(get("/mandates/201").flashAttr("outcome", "saved")).selectFirst("[data-mandate-flash]")?.text())
    }

    @Test
    fun `no outcome`() {
        assertNull(render(get("/mandates/201")).selectFirst("[data-mandate-flash]"))
    }

    @Test
    fun `close form`() {
        val page = render(APPROVED, "/mandates/201?end=close")

        assertTrue(page.selectFirst("[data-end-buttons]")!!.hasAttr("hidden"))
        val form = page.selectFirst("form[data-end-form=close]")!!
        assertFalse(form.hasAttr("hidden"))
        assertTrue(page.selectFirst("form[data-end-form=cancel]")!!.hasAttr("hidden"))
        assertEquals("/mandates/201/close", form.attr("action"))
        assertEquals("post", form.attr("method"))
        assertEquals("Fermer le mandat", form.selectFirst("h2")?.text())
        assertEquals("end-title", form.selectFirst("h2")?.id())
        assertEquals("Confirmer la fermeture", form.selectFirst("button[type=submit]")?.text())
        assertEquals("btn btn--primary", form.selectFirst("button[type=submit]")?.className())
        assertEquals("/mandates/201", form.selectFirst("a[data-end-back]")?.attr("href"))
        assertNotNull(form.selectFirst("input[name=finalPrice]"))
        assertNull(form.selectFirst("[name=cancellationCode]"))
        assertTrue(form.selectFirst("#finalPrice-error")!!.hasAttr("hidden"))
    }

    @Test
    fun `close form - invalid amount`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        val page = render(post("/mandates/201/close").param("finalPrice", "abc"))

        val form = page.selectFirst("form[data-end-form=close]")!!
        assertFalse(form.hasAttr("hidden"))
        assertEquals("abc", form.selectFirst("input[name=finalPrice]")?.attr("value"))
        assertEquals("true", form.selectFirst("input[name=finalPrice]")?.attr("aria-invalid"))
        assertFalse(form.selectFirst("#finalPrice-error")!!.hasAttr("hidden"))
        assertEquals("Indiquez le loyer convenu, en FCFA par mois.", form.selectFirst("#finalPrice-error")?.text())
    }

    @Test
    fun `cancel form`() {
        val page = render(get("/mandates/201?end=cancel"))

        assertNull(page.selectFirst("form[data-end-form=close]"))
        val form = page.selectFirst("form[data-end-form=cancel]")!!
        assertFalse(form.hasAttr("hidden"))
        assertEquals("/mandates/201/cancel", form.attr("action"))
        assertEquals("Annuler le mandat", form.selectFirst("h2")?.text())
        assertEquals("Confirmer l'annulation", form.selectFirst("button[type=submit]")?.text())
        assertEquals("btn btn--danger", form.selectFirst("button[type=submit]")?.className())
        assertEquals(
            listOf("FOUND_INDEPENDENTLY", "FOUND_VIA_OTHER_AGENT", "CHANGE_OF_PLANS", "UNREALISTIC_CRITERIA", "UNSATISFACTORY_SERVICE", "OTHER"),
            form.select("input[name=cancellationCode]").map { it.attr("value") },
        )
        assertNull(form.selectFirst("input[name=finalPrice]"))
    }

    @Test
    fun `cancel form - no code`() {
        val page = render(post("/mandates/201/cancel").param("cancellationReason", "Pourquoi pas"))

        val form = page.selectFirst("form[data-end-form=cancel]")!!
        assertEquals("Choisissez la raison de l'annulation.", form.selectFirst("#cancellationCode-error")?.text())
        assertFalse(form.selectFirst("#cancellationCode-error")!!.hasAttr("hidden"))
        assertEquals("Pourquoi pas", form.selectFirst("textarea[name=cancellationReason]")?.text())
    }

    @Test
    fun `cancel form - code kept`() {
        val page = render(post("/mandates/201/cancel").param("cancellationCode", "OTHER").param("cancellationReason", "x".repeat(501)))

        assertTrue(page.selectFirst("input[name=cancellationCode][value=OTHER]")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("#cancellationReason-error")!!.hasAttr("hidden"))
    }

    // Biens correspondants

    private val proposedAt = date("2026-10-28T10:00:00Z")

    private fun withMatches(vararg matches: com.wutsi.ndopify.match.dto.Match) {
        doReturn(matches.toList()).whenever(matchClient).search(any(), any(), any())
        doReturn(
            listOf(MatchFixtures.LISTING_A, MatchFixtures.LISTING_B, MatchFixtures.LISTING_C)
                .map { it.copy(cityId = DOUALA, neighborhoodId = BONAPRISO) },
        ).whenever(listingClient).searchByIds(any())
    }

    /**
     * A signed search with a requested visit on its ACCEPTED match (9001), a proposal awaiting an answer (9003), and
     * one refused and one unanswered match (to fill the outcomes line).
     */
    private fun withVisits(vararg visits: VisitSummary) {
        withMatches(
            MatchFixtures.MATCH_B.copy(mandateId = 201, status = MatchStatus.PROPOSED, proposedAt = proposedAt),
            MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.ACCEPTED, proposedAt = proposedAt),
            MatchFixtures.MATCH_C.copy(mandateId = 201, status = MatchStatus.REJECTED),
            MatchFixtures.MATCH_C.copy(id = 9005, mandateId = 201, status = MatchStatus.EXPIRED, proposedAt = proposedAt),
            MatchFixtures.MATCH_C.copy(id = 9006, mandateId = 201, status = MatchStatus.DISMISSED),
        )
        doReturn(visits.toList()).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)
    }

    @Test
    fun `visits - a requested visit stays in its match card`() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        withVisits(VisitFixtures.summary(VisitFixtures.requested().copy(mandateId = 201, matchId = 9001)))

        val page = render(APPROVED)

        val card = page.selectFirst("[data-match-id=9001]")!!
        assertNull(card.selectFirst("[data-match-tag]"))
        assertEquals("Visite demandée", card.selectFirst("[data-visit-kind]")?.text())
        assertEquals("pour le sam. 7 novembre 2026 à 10:30", card.selectFirst("[data-visit-when]")?.text())
        assertEquals("Il reste 42 h pour confirmer", card.selectFirst("[data-visit-timer]")?.text())
        assertEquals("Confirmer la visite", card.selectFirst("[data-visit-link]")?.text())
        assertEquals("/visits/7001", card.selectFirst("[data-visit-link]")?.attr("href"))
        assertEquals("Envoyé le 28 oct. 2026", card.selectFirst("[data-match-answer]")?.text())

        // The only actionable card's send button is secondary while a visit waits for an answer.
        val send = page.selectFirst("[data-matches-section] [data-match-send]")!!
        assertEquals("btn btn--secondary", send.className())

        val pending = page.selectFirst("[data-mandate-pending-visit]")!!
        assertEquals("1 visite à confirmer · 42 h — Appartement · 3 chambres · annonce n° 3012", pending.text())
        assertEquals("#match-9001", pending.attr("href"))

        assertEquals("À confirmer (1)", page.selectFirst("[data-matches-section] .assignments__group-title")?.text())
        assertTrue(page.select("[data-matches-section] .assignments__group-title").all { it.text().contains("(") })
        assertEquals("1 refusé · 1 sans réponse", page.selectFirst("[data-match-outcomes]")?.text())
        assertNull(page.selectFirst("section[data-mandate-visits]"))
        assertNull(page.selectFirst("[data-match-in-visit]"))
        assertNull(page.selectFirst("[data-listing-visits]"))
        assertNull(page.selectFirst("[data-mandate-visit-stats]"))
        assertTrue(page.selectFirst("[data-contact-whatsapp]")!!.hasClass("btn--secondary"))
    }

    @Test
    fun `visits - confirmed`() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        withVisits(VisitFixtures.summary(VisitFixtures.confirmed().copy(mandateId = 201, matchId = 9001)))

        val page = render(APPROVED)

        val card = page.selectFirst("[data-match-id=9001]")!!
        assertEquals("Visite confirmée", card.selectFirst("[data-visit-kind]")?.text())
        assertEquals("le sam. 7 novembre 2026 à 10:30", card.selectFirst("[data-visit-when]")?.text())
        assertNull(card.selectFirst("[data-visit-timer]"))
        assertEquals("Voir la visite", card.selectFirst("[data-visit-link]")?.text())
        assertEquals("Visites à venir (1)", page.selectFirst("[data-matches-section] .assignments__group-title")?.text())
        assertNull(page.selectFirst("[data-mandate-pending-visit]"))
    }

    @Test
    fun `visits unavailable`() {
        withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.ACCEPTED))
        doThrow(ResourceAccessException("timeout")).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

        val page = render(APPROVED)

        val section = page.selectFirst("section[data-matches-section]")!!
        assertEquals(
            "Les visites de ce mandat ne peuvent pas être affichées pour le moment.",
            section.selectFirst("[data-visits-unavailable]")?.text(),
        )
        // Without the visits, no match block is shown and no pending callout points at one.
        assertEquals(listOf("9001"), page.select("[data-match]").map { it.attr("data-match-id") })
        assertNull(page.selectFirst("[data-visit-kind]"))
        assertNull(page.selectFirst("[data-mandate-pending-visit]"))
    }

    @Test
    fun `matches and visits both unavailable - only the matches warning`() {
        doThrow(ResourceAccessException("down")).whenever(matchClient).search(any(), any(), any())
        doThrow(ResourceAccessException("timeout")).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

        val page = render(APPROVED)
        val section = page.selectFirst("section[data-matches-section]")!!

        assertEquals(
            "Les biens correspondants ne peuvent pas être affichés pour le moment.",
            section.selectFirst("[data-match-unavailable]")?.text(),
        )
        assertNull(section.selectFirst("[data-visits-unavailable]"))
    }

    @Test
    fun `no requested visit - no pending callout`() {
        withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201))

        val page = render(APPROVED)

        assertNull(page.selectFirst("[data-mandate-pending-visit]"))
        assertNull(page.selectFirst("[data-match-id=9001] [data-visit-kind]"))
        assertNull(page.selectFirst("section[data-mandate-visits]"))
    }

    @Test
    fun `not a search - no pending visit`() {
        val page = render(APPROVED.copy(mandateType = MandateType.LISTING))

        assertNull(page.selectFirst("[data-mandate-pending-visit]"))
        assertNull(page.selectFirst("section[data-mandate-visits]"))
        verify(visitClient, never()).search(any(), any(), any(), any())
    }

    @Test
    fun matches() {
        withMatches(
            MatchFixtures.MATCH_B.copy(mandateId = 201, status = MatchStatus.PROPOSED, proposedAt = proposedAt),
            MatchFixtures.MATCH_A.copy(mandateId = 201),
            MatchFixtures.MATCH_C.copy(mandateId = 201, status = MatchStatus.DISMISSED),
        )
        val search = APPROVED.copy(
            generatorSet = true,
            securedFenced = true,
            waterTank = true,
            furnitureType = MatchFixtures.MANDATE.furnitureType,
        )

        val page = render(search)

        val section = page.selectFirst("section[data-matches-section]")!!
        assertEquals("matches-title", section.attr("aria-labelledby"))
        assertEquals("Biens correspondants", section.selectFirst("#matches-title")?.text())
        val cards = section.select("[data-match]")
        assertEquals(listOf("9001", "9003"), cards.map { it.attr("data-match-id") })

        val card = cards[0]
        assertEquals("match-9001", card.id())
        assertEquals("match-9001-title", card.selectFirst("article")?.attr("aria-labelledby"))
        assertEquals("Appartement · 3 chambres", card.selectFirst("[data-match-title]")?.text())
        assertEquals("status-tag status-tag--info", card.selectFirst("[data-match-tag]")?.className())
        assertEquals("Nouveau", card.selectFirst("[data-match-tag]")?.text())
        val link = card.selectFirst("#match-9001-title a[data-match-link]")!!
        assertEquals("/listings/3012", link.attr("href"))
        assertEquals("_blank", link.attr("target"))
        assertEquals("3012", link.selectFirst("[data-match-ref]")?.text())
        assertEquals(
            listOf("Correspondance", "Quartier", "Loyer", "Disponibilité"),
            card.select("[data-match-facts] dt.visually-hidden").map { it.text() },
        )
        assertEquals(
            listOf("95\u00a0%", "Bonapriso", "240K\u00a0FCFA / mois"),
            card.select("[data-match-facts] dd").take(3).map { it.wholeText() },
        )
        // The rent, in bold: what the agent looks at first
        assertEquals("match__rent", card.select("[data-match-facts] dd")[2].className())
        val fit = card.select("[data-match-fit] li")
        assertEquals(7, fit.size)
        assertEquals("match__fit-item match__fit-item--full", fit[0].className())
        assertEquals("Oui : Dans le secteur recherché", fit[0].text())
        assertEquals("match__fit-item match__fit-item--full", fit[3].className())
        assertEquals("Oui : Appartement, comme demandé", fit[3].text())
        assertEquals("match__fit-item", fit[4].className())
        assertEquals("En partie : 2 équipements sur 3", fit[4].text())
        assertNull(card.selectFirst("[data-match-answer]"))

        val send = card.selectFirst("form[data-match-send-form]")!!
        assertEquals("/mandates/201/matches/9001/propose", send.attr("action"))
        assertEquals("post", send.attr("method"))
        assertEquals("Envoyer au client", send.selectFirst("button")?.text())
        assertEquals("btn btn--primary", send.selectFirst("button")?.className())
        assertEquals("au client", send.selectFirst("button .visually-hidden")?.text())
        assertEquals("/mandates/201?dismiss=9001#match-9001-title", card.selectFirst("a[data-match-dismiss]")?.attr("href"))
        assertFalse(card.selectFirst("[data-match-actions]")!!.hasAttr("hidden"))
        assertTrue(card.selectFirst("[data-match-confirm]")!!.hasAttr("hidden"))

        val proposed = cards[1]
        assertEquals("En attente", proposed.selectFirst("[data-match-tag]")?.text())
        assertEquals("Envoyé le 28 oct. 2026", proposed.selectFirst("[data-match-answer]")?.text())
        assertNull(proposed.selectFirst("[data-match-actions]"))
        assertNull(proposed.selectFirst("[data-match-confirm]"))

        assertEquals(
            "1 bien écarté : il ne vous sera plus proposé pour ce mandat.",
            section.selectFirst("[data-match-dismissed]")?.text(),
        )
        assertEquals("2 biens", searchStatsLabel(page))
        assertNull(section.selectFirst("[data-match-empty]"))
        assertNull(section.selectFirst("[data-match-unavailable]"))
    }

    @Test
    fun `matches - activity line with completed visits`() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        withMatches(
            MatchFixtures.MATCH_B.copy(mandateId = 201, status = MatchStatus.PROPOSED, proposedAt = proposedAt),
            MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.ACCEPTED),
        )
        doReturn(
            listOf(
                VisitFixtures.summary(VisitFixtures.requested().copy(mandateId = 201, matchId = 9001)),
                VisitFixtures.summary(VisitFixtures.completed().copy(id = 7002, mandateId = 201, matchId = 9003)),
            ),
        ).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

        val page = render(APPROVED)

        assertEquals("2 biens · 1 intéressé · 2 visites", searchStatsLabel(page))
    }

    @Test
    fun `matches - activity line with visits but no pool`() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.REJECTED))
        doReturn(
            listOf(VisitFixtures.summary(VisitFixtures.completed().copy(id = 7002, mandateId = 201, matchId = 9003))),
        ).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

        val section = render(APPROVED).selectFirst("section[data-matches-section]")!!

        assertEquals("1 visite", searchStatsLabel(section))
    }

    @Test
    fun `matches - dismiss confirmation open`() {
        withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201))

        val card = render(APPROVED, "/mandates/201?dismiss=9001").selectFirst("[data-match]")!!

        assertTrue(card.selectFirst("[data-match-actions]")!!.hasAttr("hidden"))
        val confirm = card.selectFirst("[data-match-confirm]")!!
        assertFalse(confirm.hasAttr("hidden"))
        assertEquals("Écarter ce bien ?", confirm.selectFirst(".alert__title")?.text())
        val form = confirm.selectFirst("form")!!
        assertEquals("/mandates/201/matches/9001/dismiss", form.attr("action"))
        assertEquals("post", form.attr("method"))
        assertEquals("/mandates/201#match-9001-title", confirm.selectFirst("a[data-match-dismiss-cancel]")?.attr("href"))
    }

    @Test
    fun `matches - several dismissed`() {
        withMatches(
            MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.DISMISSED),
            MatchFixtures.MATCH_B.copy(mandateId = 201, status = MatchStatus.DISMISSED),
        )

        val section = render(APPROVED).selectFirst("[data-matches-section]")!!

        assertEquals(
            "2 biens écartés : ils ne vous seront plus proposés pour ce mandat.",
            section.selectFirst("[data-match-dismissed]")?.text(),
        )
        assertNotNull(section.selectFirst("[data-match-empty]"))
    }

    @Test
    fun `matches - only refused ones`() {
        withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.REJECTED))

        val page = render(APPROVED)
        val section = page.selectFirst("[data-matches-section]")!!

        assertNull(section.selectFirst("[data-match-list]"))
        assertNull(section.selectFirst("[data-match-empty]"))
        assertEquals("1 refusé", section.selectFirst("[data-match-outcomes]")?.text())
        assertNull(section.ownerDocument()!!.selectFirst("[data-search-stats]"))
    }

    @Test
    fun `matches - empty`() {
        withMatches()

        val page = render(APPROVED)
        val section = page.selectFirst("[data-matches-section]")!!

        assertTrue(section.selectFirst("[data-match-empty]")!!.text().startsWith("Aucun de vos biens ne correspond"))
        assertNull(section.selectFirst("[data-match-list]"))
        assertNull(section.selectFirst("[data-match-dismissed]"))
        assertNull(section.ownerDocument()!!.selectFirst("[data-search-stats]"))
    }

    @Test
    fun `matches - unavailable`() {
        doThrow(ResourceAccessException("down")).whenever(matchClient).search(any(), any(), any())

        val page = render(APPROVED)

        val section = page.selectFirst("[data-matches-section]")!!
        assertEquals(
            "Les biens correspondants ne peuvent pas être affichés pour le moment.",
            section.selectFirst("[data-match-unavailable]")?.text(),
        )
        assertNull(section.selectFirst("[data-match-empty]"))
        assertNotNull(page.selectFirst("#contact-title"))
        assertNull(section.ownerDocument()!!.selectFirst("[data-search-stats]"))
    }

    @Test
    fun `matches - outcome`() {
        withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201))
        doReturn(APPROVED).whenever(mandateClient).get(201)

        val page = render(get("/mandates/201").flashAttr("match", "proposed").flashAttr("matchListing", 3012L))

        val flash = page.selectFirst("[data-matches-section] [data-match-flash]")!!
        assertEquals("alert alert--success", flash.className())
        assertEquals("status", flash.attr("role"))
        assertEquals("Annonce n° 3012 envoyée à ray@gmail.com.", flash.text())
        assertNull(page.selectFirst("[data-mandate-flash]"))
    }

    @Test
    fun `no matches section before signature`() {
        assertNull(render(get("/mandates/201")).selectFirst("[data-matches-section]"))
    }

    /** The activity line, in the approved panel of [element]'s page. */
    private fun searchStatsLabel(element: Element): String? =
        element.ownerDocument()!!.selectFirst("[data-panel=APPROVED] [data-search-stats] .visually-hidden")
            ?.nextElementSibling()?.text()

    // mandate.js

    @Test
    fun `script and hooks`() {
        val page = render(APPROVED)

        val script = page.selectFirst("script[src=/assets/js/mandate.js]")!!
        assertTrue(script.hasAttr("defer"))
        assertEquals("mandate", page.body().attr("data-page"))
        assertEquals(listOf("close", "cancel"), page.select("[data-end-open]").map { it.attr("data-end-open") })
        assertEquals(listOf("close", "cancel"), page.select("form[data-end-form]").map { it.attr("data-end-form") })
        assertEquals("end-form-close", page.selectFirst("[data-end-open=close]")?.attr("aria-controls"))
        assertEquals("end-form-close", page.selectFirst("form[data-end-form=close]")?.id())
        assertEquals(2, page.select("[data-end-back]").size)
    }
}
