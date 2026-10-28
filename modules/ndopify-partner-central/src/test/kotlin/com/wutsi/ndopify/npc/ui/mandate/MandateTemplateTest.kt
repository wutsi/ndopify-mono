package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
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
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
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
                    locationClient,
                    tenantContext,
                    clock,
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
    fun `pending approval`() {
        val page = render(get("/mandates/201"))

        assertEquals("Locataire · Maison — Ndopify Partner Central", page.title())
        assertEquals("Locataire · Maison", page.selectFirst("h1")?.text())
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

        assertEquals("Le bailleur", page.selectFirst("#contact-title")?.text())
        assertEquals("Son bien", page.selectFirst("#search-title")?.text())
        assertTrue(page.text().contains("Présentez le bien aux locataires et organisez les visites."))
    }

    @Test
    fun `approved - landlord without listing`() {
        val page = render(APPROVED.copy(mandateType = MandateType.LISTING))

        val box = page.selectFirst("[data-panel=APPROVED] [data-listing-actions]")!!
        assertTrue(box.selectFirst("[data-listing-text]")!!.text().startsWith("Visitez le bien"))
        val form = box.selectFirst("form[data-listing-create-form]")!!
        assertEquals("/mandates/201/listing", form.attr("action"))
        assertEquals("post", form.attr("method"))
        assertEquals("Créer l'annonce", form.selectFirst("button[data-listing-create]")?.text())
        assertNull(box.selectFirst("a[data-listing-link]"))
    }

    @Test
    fun `approved - landlord with listing`() {
        doReturn(ListingViewsTest.LISTING).whenever(listingClient).get(501)

        val page = render(APPROVED.copy(mandateType = MandateType.LISTING, listingId = 501))

        val box = page.selectFirst("[data-listing-actions]")!!
        val link = box.selectFirst("a[data-listing-link]")!!
        assertEquals("/listings/501", link.attr("href"))
        assertEquals("Continuer l'annonce", link.text())
        assertEquals("btn btn--primary", link.className())
        assertTrue(box.selectFirst("[data-listing-text]")!!.text().startsWith("Annonce en brouillon : encore à faire"))
        assertNull(box.selectFirst("form[data-listing-create-form]"))
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
        assertEquals("Ray Sponsible · à appeler le matin", card.selectFirst("p")?.text())
        assertEquals("tel:+237699123456", card.selectFirst("a[data-contact=whatsapp]")?.attr("href"))
        assertEquals("+237 6 99 12 34 56", card.selectFirst("[data-contact-number]")?.text())
        assertEquals("https://wa.me/237699123456", card.selectFirst("a[data-contact-whatsapp]")?.attr("href"))
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
