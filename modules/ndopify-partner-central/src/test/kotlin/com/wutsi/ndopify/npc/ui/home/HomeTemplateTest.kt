package com.wutsi.ndopify.npc.ui.home

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.assignment.AssignmentController
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.ASSIGNMENT
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.party.dto.KycStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(AssignmentViewsTest.NOW).whenever(clock).millis()
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(MANDATE).whenever(mandateClient).get(101)

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
                    locationClient,
                    tenantContext,
                    clock
                ),
                AssignmentController(assignmentClient),
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

    @Test
    fun offer() {
        val page = render(get("/"))

        val card = page.selectFirst("[data-assignment]")!!
        assertEquals("assignment-7", card.id())
        assertEquals("7", card.attr("data-assignment-id"))
        assertEquals((AssignmentViewsTest.NOW + 52 * 60_000L).toString(), card.attr("data-expires-at"))
        assertEquals("Recherche de Location", card.selectFirst("h3")?.text())
        assertEquals("52 min", card.selectFirst("[data-timer-value]")?.text())
        assertEquals("Secteurs", card.selectFirst("[data-fact=sector]")?.previousElementSibling()?.text())
        assertEquals("Bonapriso, Akwa", card.selectFirst("[data-fact=sector]")?.text())
        assertEquals("150K FCFA par mois", card.selectFirst("[data-fact=price]")?.wholeText())
        assertEquals("Dans moins d'1 mois", card.selectFirst("[data-fact=moving]")?.text())
        assertEquals("/assignments/7/accept", card.selectFirst("form[data-accept-form]")?.attr("action"))
        assertEquals("post", card.selectFirst("form[data-accept-form]")?.attr("method"))
        assertEquals("/?decline=7#assignment-7", card.selectFirst("a[data-decline]")?.attr("href"))
        assertFalse(card.selectFirst("[data-actions]")!!.hasAttr("hidden"))
        assertTrue(card.selectFirst("[data-decline-confirm]")!!.hasAttr("hidden"))
        assertEquals(8, card.select(".review__item").size)
        assertEquals("Gardien", card.select(".review__item").last()?.selectFirst(".review__value")?.text())
        assertFalse(page.text().contains("677")) // the mandate's comments
        assertTrue(page.selectFirst("[data-assignments-empty]")!!.hasAttr("hidden"))
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

        assertNotNull(page.selectFirst("#assignments-title"))
        assertNull(page.selectFirst("[data-assignment]"))
        assertFalse(page.selectFirst("[data-assignments-empty]")!!.hasAttr("hidden"))
    }

    @Test
    fun `offers unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(assignmentClient).pending(any())

        val page = render(get("/"))

        assertNotNull(page.selectFirst("[data-assignments-unavailable]"))
        assertTrue(page.selectFirst("[data-assignments-empty]")!!.hasAttr("hidden"))
    }

    @Test
    fun `not verified - no section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()
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
}
