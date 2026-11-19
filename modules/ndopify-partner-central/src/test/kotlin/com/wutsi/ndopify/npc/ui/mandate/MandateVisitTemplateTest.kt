package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.APPROVED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.match.MatchFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures
import com.wutsi.ndopify.npc.ui.visit.VisitRules
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.web.core.service.TenantContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Renders `templates/mandate/visit.html` through [MandateVisitController] with a real Thymeleaf engine. */
class MandateVisitTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val matchClient = mock<NdMatchClient>()
    private val visitClient = mock<NdVisitClient>()
    private val listingClient = mock<NdListingClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    private val match = MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.ACCEPTED)
    private val second = MatchFixtures.MATCH_C.copy(id = 9002, mandateId = 201, status = MatchStatus.ACCEPTED)

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        doReturn(TenantFixtures.TENANT).whenever(tenantContext).getTenant()
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(NOW).whenever(clock).millis()
        doReturn(APPROVED).whenever(mandateClient).get(201)
        doReturn(listOf(match, second)).whenever(matchClient).search(listOf(201), listOf(MatchStatus.ACCEPTED))
        doReturn(emptyList<VisitSummary>())
            .whenever(visitClient).searchByMatches(AGENT.id, VisitRules.OPEN, listOf(match.id, second.id))
        doReturn(
            listOf(
                MatchFixtures.LISTING_A.copy(title = "Nice apartment"),
                MatchFixtures.LISTING_C.copy(title = "Small apartment"),
            ),
        ).whenever(listingClient).searchByIds(any())
        doReturn(CUSTOMER).whenever(customerClient).get(555)

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
                MandateVisitController(
                    currentAgent,
                    mandateClient,
                    matchClient,
                    visitClient,
                    listingClient,
                    customerClient,
                    tenantContext,
                    clock,
                ),
            )
            .setControllerAdvice(SignedInAdvice(), HttpClientErrorHandler(mock()))
            .setViewResolvers(viewResolver)
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun render(request: org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<*>): Document =
        Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)

    @Test
    fun `the page, its pickers and its two buttons`() {
        val page = render(get("/mandates/201/visits"))

        assertEquals("Planifier la visite — Ndopify Partner Central", page.title())
        assertEquals("Planifier la visite", page.selectFirst("h1")?.text())
        val intro = page.selectFirst(".mandate__meta")!!
        assertTrue(intro.text().contains("Mandat n° 201"))
        assertTrue(intro.text().contains("Ray Sponsible"))
        assertNotNull(page.selectFirst("nav.user-menu"))
        assertEquals("/mandates/201", page.selectFirst(".back-link a")?.attr("href"))

        // The accepted properties, one option each; the browser picks the first until the agent chooses
        val property = page.selectFirst("select#matchId")!!
        assertEquals(listOf("9001", "9002"), property.select("option").map { it.attr("value") })
        assertEquals("Nice apartment — annonce n° 3012", property.select("option")[0].text())
        assertEquals(0, property.select("option[selected]").size)

        // The 7 days and the 25 times of the tenant's slots
        assertEquals(7, page.select("select#date option").size)
        assertEquals("2026-10-19", page.select("select#date option")[0].attr("value"))
        assertEquals(25, page.select("select#time option").size)
        assertEquals("08:00", page.select("select#time option")[0].attr("value"))
        assertEquals("8h00", page.select("select#time option")[0].text())
        assertEquals(listOf("ON_SITE", "VIDEO"), page.select("select#type option").drop(1).map { it.attr("value") })
        assertEquals("Sur place", page.select("select#type option")[1].text())

        // The form posts without any script
        assertEquals("/mandates/201/visits", page.selectFirst("form[data-visit-form]")?.attr("action"))
        assertEquals("Annuler", page.selectFirst("form a.btn--outline")?.text())
        assertEquals("Confirmer la visite", page.selectFirst("button[data-visit-submit]")?.text())
        assertEquals(0, page.select("script[src]").count { it.attr("src").contains("visit.js") })
        // The shared header script still opens the account menu, as on every signed-in page
        assertNotNull(page.selectFirst("script[src*='partner-central.js']"))
    }

    @Test
    fun `the errors are shown under their field, with the agent's answers kept`() {
        val page = render(
            post("/mandates/201/visits")
                .param("matchId", "9999")
                .param("date", "2026-10-19")
                .param("time", "10:30")
                .param("type", "ON_SITE"),
        )

        assertEquals("Choisissez le bien accepté par le client.", page.selectFirst("[data-match-error]")?.text())
        assertTrue(page.selectFirst("select#matchId")!!.attr("aria-invalid") == "true")
        assertEquals("2026-10-19", page.selectFirst("select#date option[selected]")?.attr("value"))
        assertEquals("10:30", page.selectFirst("select#time option[selected]")?.attr("value"))
        assertTrue(page.selectFirst("select#type option[value=ON_SITE]")!!.hasAttr("selected"))
        assertFalse(page.selectFirst("[data-match-error]")!!.hasAttr("hidden"))
    }
}
