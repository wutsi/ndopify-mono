package com.wutsi.ndopify.npc.ui.visit

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.CUSTOMER
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.LISTING
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.checkedIn
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.completed
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.confirmed
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.date
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.requested
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.window
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.core.service.TenantContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/visit.html` through [VisitController] with a real Thymeleaf engine. */
class VisitTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val visitClient = mock<NdVisitClient>()
    private val listingClient = mock<NdListingClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        doReturn(requested()).whenever(visitClient).get(7001)
        doReturn(LISTING).whenever(listingClient).get(3012)
        doReturn(CUSTOMER).whenever(customerClient).get(300)

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
                VisitController(
                    currentAgent,
                    visitClient,
                    listingClient,
                    customerClient,
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

    private fun render(visit: Visit): Document {
        doReturn(visit).whenever(visitClient).get(7001)
        return render(get("/visits/7001"))
    }

    private fun panel(page: Document, name: String) = page.selectFirst("[data-panel=$name]")

    @Test
    fun `requested panel`() {
        val page = render(requested())
        val panel = panel(page, "REQUESTED")!!

        assertEquals("Visite de Sandrine Eboa", page.selectFirst(".visit-intro h1")?.text())
        assertEquals("À confirmer", page.selectFirst(".visit-intro .status-tag")?.text())
        assertEquals("post", panel.selectFirst("form[data-schedule-form]")?.attr("method"))
        assertEquals("/visits/7001/schedule", panel.selectFirst("form[data-schedule-form]")?.attr("action"))
        assertEquals(7, panel.select("select[name=date] option").size)
        assertEquals(25, panel.select("select[name=time] option").size)
        assertEquals("2026-11-07", panel.selectFirst("select[name=date] option[selected]")?.attr("value"))
        assertEquals("10:30", panel.selectFirst("select[name=time] option[selected]")?.attr("value"))
        assertEquals(listOf("ON_SITE", "VIDEO"), panel.select("input[name=type]").map { it.attr("value") })
        assertEquals("/visits/7001/cancel", panel.selectFirst("form[data-cancel-form]")?.attr("action"))
        assertTrue(panel.text().contains("Je viendrai avec mon mari."))
    }

    @Test
    fun `confirmed panel`() {
        val page = render(confirmed())
        val panel = panel(page, "CONFIRMED")!!

        assertEquals("Confirmée", page.selectFirst(".visit-intro .status-tag")?.text())
        assertTrue(panel.text().contains("Rue Njo-Njo, portail bleu, Derrière la pharmacie"))
        assertNotNull(panel.selectFirst("[data-map-link]"))
        assertNotNull(panel.selectFirst("form[data-cancel-form]"))
        assertNull(panel.selectFirst("form[data-checkin-form]"))
        assertTrue(panel.text().contains("sam. 7 novembre 2026 à 09:30"))
    }

    @Test
    fun `window panel`() {
        val page = render(window())
        val panel = panel(page, "WINDOW")!!

        assertEquals("À démarrer", page.selectFirst(".visit-intro .status-tag")?.text())
        val checkIn = panel.selectFirst("form[data-checkin-form]")!!
        assertEquals("/visits/7001/check-in", checkIn.attr("action"))
        assertNotNull(checkIn.selectFirst("input[name=code]"))
        assertEquals(3, checkIn.select("input[data-position-input]").size)
        assertEquals("/visits/7001/no-show", panel.selectFirst("form[data-noshow-form]")?.attr("action"))
        assertEquals("/visits/7001/cancel", panel.selectFirst("form[data-cancel-form]")?.attr("action"))
    }

    @Test
    fun `window - before the no-show`() {
        val page = render(window().copy(scheduledAt = Date(VisitFixtures.NOW - 5 * 60_000)))
        val panel = panel(page, "WINDOW")!!

        assertNull(panel.selectFirst("[data-noshow-form]"))
    }

    @Test
    fun `window - code locked`() {
        doReturn(window()).whenever(visitClient).get(7001)
        val session = MockHttpSession().apply { setAttribute("npc.visit.code-locked.7001", true) }

        val panel = panel(render(get("/visits/7001").session(session)), "WINDOW")!!

        assertNull(panel.selectFirst("form[data-checkin-form]"))
        assertNotNull(panel.selectFirst("[data-code-locked]"))
    }

    @Test
    fun `checked in`() {
        val page = render(checkedIn().copy(checkedInAt = Date(VisitFixtures.NOW - 5 * 60_000)))
        val panel = panel(page, "CHECKED_IN")!!

        assertEquals("En cours", page.selectFirst(".visit-intro .status-tag")?.text())
        val form = panel.selectFirst("form[data-checkout-form]")!!
        assertEquals("/visits/7001/check-out", form.attr("action"))
        assertEquals("2000", form.selectFirst("textarea[name=comment]")?.attr("maxlength"))
        assertEquals(3, form.select("input[data-position-input]").size)
        assertEquals("true", form.selectFirst("[data-checkout-submit]")?.attr("aria-disabled"))
        assertTrue(panel.text().contains("10 min après son démarrage"))
    }

    @Test
    fun `completed panel`() {
        val page = render(completed())
        val panel = panel(page, "COMPLETED")!!

        assertEquals("Terminée", page.selectFirst(".visit-intro .status-tag")?.text())
        assertTrue(panel.text().contains("30 min"))
        assertTrue(panel.text().contains("Client intéressé, reviendra avec son épouse."))
        assertEquals("Pas intéressé", panel.selectFirst("[data-feedback-tag]")?.text())
        assertTrue(panel.text().contains("trop cher"))
        assertTrue(panel.text().contains("Un peu cher pour le quartier."))
        assertNull(panel.selectFirst("form"))
    }

    @Test
    fun cancelled() {
        val page = render(
            confirmed().copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = VisitParty.AGENT,
                cancelledAt = date("2026-11-05T06:00:00Z"),
                cancellationReason = "Indisponible.",
            ),
        )
        val panel = panel(page, "CANCELLED")!!

        assertTrue(panel.text().contains("Visite annulée"))
        assertTrue(panel.text().contains("Vous avez annulé la visite le jeu. 5 novembre 2026 à 07:00."))
        assertTrue(panel.text().contains("« Indisponible. »"))
        assertTrue(panel.text().contains("sam. 7 novembre 2026 à 10:30"))
    }

    @Test
    fun expired() {
        val panel = panel(render(requested().copy(status = VisitStatus.EXPIRED)), "EXPIRED")!!

        assertTrue(panel.text().contains("elle a expiré le"))
    }

    @Test
    fun `no show`() {
        val page = render(
            confirmed().copy(
                status = VisitStatus.NO_SHOW,
                type = VisitType.VIDEO,
                noShowParty = VisitParty.CUSTOMER,
                noShowReason = VisitNoShowReason.CUSTOMER_ABSENT,
            ),
        )
        val panel = panel(page, "NO_SHOW")!!

        assertTrue(panel.text().contains("Le client ne répond pas"))
        assertTrue(panel.text().contains("Déclaré par vous : l'absence du client est enregistrée."))
    }

    @Test
    fun `locked - not yours`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(visitClient).get(7001)

        val page = render(get("/visits/7001"))

        assertNotNull(page.selectFirst("[data-visit-locked]"))
        assertNull(page.selectFirst("[data-panel]"))
        assertTrue(page.text().contains("Cette visite n'est pas disponible"))
    }

    @Test
    fun `locked - unavailable`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(visitClient).get(7001)

        val page = render(get("/visits/7001"))

        assertTrue(page.text().contains("ne peut pas être affichée"))
    }

    @Test
    fun flash() {
        val page = render(get("/visits/7001").flashAttr(VisitController.OUTCOME, VisitController.CONFIRMED))

        val alert = page.selectFirst(".alert--success")!!
        assertTrue(alert.text().startsWith("Visite confirmée pour le "))
    }

    @Test
    fun `code locked flash`() {
        val page = render(get("/visits/7001").flashAttr(VisitController.OUTCOME, VisitController.CODE_LOCKED))

        assertNotNull(page.selectFirst(".alert--warning"))
        assertNull(page.selectFirst("form[data-checkin-form]"))
    }

    @Test
    fun `no code`() {
        val page = render(requested().copy(code = "4827"))

        assertFalse(page.text().contains("4827"))
    }

    @Test
    fun `every action is a form`() {
        val requested = panel(render(requested()), "REQUESTED")!!
        assertEquals("post", requested.selectFirst("form[data-schedule-form]")?.attr("method"))
        assertEquals("post", requested.selectFirst("form[data-cancel-form]")?.attr("method"))

        val window = panel(render(window()), "WINDOW")!!
        assertEquals("post", window.selectFirst("form[data-checkin-form]")?.attr("method"))
        assertEquals("post", window.selectFirst("form[data-noshow-form]")?.attr("method"))

        val checkedIn = panel(render(checkedIn()), "CHECKED_IN")!!
        assertEquals("post", checkedIn.selectFirst("form[data-checkout-form]")?.attr("method"))
    }
}
