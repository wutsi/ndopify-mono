package com.wutsi.ndopify.npc.ui.agent

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.SearchListingResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.config.Parallel
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.agent.AgentProfileFixtures.LISTINGS
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.web.core.service.TenantContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.ResourceAccessException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Renders `templates/agent.html` through [AgentProfileController] with a real Thymeleaf engine. */
class AgentProfileTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val agentClient = mock<NdAgentClient>()
    private val listingClient = mock<NdListingClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var parallel: Parallel
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(ProfileFixtures.AGENT).whenever(currentAgent).get()
        doReturn(AgentProfileFixtures.AGENT).whenever(agentClient).get(30)
        doReturn(SearchListingResponse(listings = LISTINGS, total = 6)).whenever(listingClient).searchByAgent(any(), any(), any())
        doReturn(emptyList<ListingImage>()).whenever(listingClient).images(any())
        parallel = Parallel()

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
                AgentProfileController(
                    currentAgent,
                    agentClient,
                    listingClient,
                    locationClient,
                    tenantContext,
                    parallel,
                ),
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

    private fun render(id: String = "30", page: Int? = null): Document {
        val request = get("/agent?id=$id")
        page?.let { request.param("page", it.toString()) }
        return Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)
    }

    @Test
    fun profile() {
        val page = render()

        assertEquals("Clarisse Mbarga — Ndopify Partner Central", page.title())
        assertEquals("Clarisse Mbarga", page.selectFirst("h1")?.text())
        assertEquals("Vérifié", page.selectFirst(".agent-badge")?.text())
        assertEquals("Indépendant · Douala", page.selectFirst(".agent-hero__meta")?.text())
        assertEquals("Membre depuis mars 2023", page.selectFirst(".agent-hero__since")?.text())
        assertEquals(
            listOf("Bonapriso", "Akwa"),
            page.select(".agent-sectors__chip").map { it.text() },
        )
        assertEquals("https://wa.me/237699123456", page.selectFirst(".agent-hero__contact")?.attr("href"))
        assertEquals(
            listOf("Appartement 2 chambres", "Studio", "Maison 3 chambres"),
            page.select(".agent-listing__title").map { it.text() },
        )
        assertEquals("/listings/5001", page.selectFirst(".agent-listing")?.attr("href"))
        assertEquals("Page 1 sur 2", page.selectFirst(".agent-pagination__page")?.text())
        assertEquals("/agent?id=30&page=2", page.selectFirst("[data-listings-next]")?.attr("href"))
        assertNotNull(page.selectFirst("button.agent-pagination__button[disabled]"))
        assertNull(page.selectFirst("script[src=/assets/js/agent.js]"))
    }

    @Test
    fun `profile - the page's chrome`() {
        val page = render()

        assertEquals("agent", page.body().attr("data-page"))
        assertEquals("/assets/css/partner-central.css", page.selectFirst("link[href=/assets/css/partner-central.css]")?.attr("href"))
        assertEquals("/assets/css/agent.css", page.selectFirst("link[href=/assets/css/agent.css]")?.attr("href"))
        assertNotNull(page.selectFirst("script[src=/assets/js/partner-central.js]"))
        assertNotNull(page.selectFirst("nav.user-menu"))
        assertEquals("/", page.selectFirst(".agent-back a")?.attr("href"))
        assertEquals("← Retour à l'espace agent", page.selectFirst(".agent-back a")?.text())
    }

    @Test
    fun `missing - the panel, no profile`() {
        doReturn(null).whenever(agentClient).get(99)

        val page = render("99")

        assertEquals("Agent introuvable — Ndopify Partner Central", page.title())
        assertEquals("Agent introuvable", page.selectFirst(".alert__title")?.text())
        assertNull(page.selectFirst("article[data-agent]"))
        assertEquals("/", page.selectFirst(".alert a")?.attr("href"))
    }

    @Test
    fun `missing with a back - the panel returns to the Espace agent`() {
        doReturn(null).whenever(agentClient).get(99)

        val page = Jsoup.parse(
            mvc.perform(get("/agent?id=99&back=/listings/501")).andReturn().response.contentAsString,
        )

        assertEquals("/listings/501", page.selectFirst(".agent-back a")?.attr("href"))
        assertEquals("← Retour", page.selectFirst(".agent-back a")?.text())
        assertEquals("/", page.selectFirst(".alert a")?.attr("href"))
        assertEquals("Retour à l'espace agent", page.selectFirst(".alert a")?.text())
    }

    @Test
    fun `no listings - no section nor pager`() {
        doReturn(SearchListingResponse()).whenever(listingClient).searchByAgent(any(), any(), any())

        val page = render()

        assertNull(page.selectFirst("#listings"))
        assertNull(page.selectFirst(".agent-pagination"))
    }

    @Test
    fun `listings unavailable - the hint, no cards`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).searchByAgent(any(), any(), any())

        val page = render()

        assertNotNull(page.selectFirst("#listings"))
        assertEquals("Annonces indisponibles pour le moment. Réessayez plus tard.", page.selectFirst("#listings .alert")?.text())
        assertNull(page.selectFirst(".agent-listing"))
        assertNull(page.selectFirst(".agent-pagination"))
    }

    @Test
    fun `page 2 - its cards and pager`() {
        val second = LISTINGS.mapIndexed { index, listing -> listing.copy(id = 5004L + index) }
        doReturn(SearchListingResponse(listings = second, total = 6)).whenever(listingClient).searchByAgent(30, 3, 3)

        val page = render(page = 2)

        assertEquals("/listings/5004", page.selectFirst(".agent-listing")?.attr("href"))
        assertEquals("Page 2 sur 2", page.selectFirst(".agent-pagination__page")?.text())
        assertEquals("/agent?id=30&page=1", page.selectFirst("[data-listings-prev]")?.attr("href"))
        assertNull(page.selectFirst("[data-listings-next]"))
        assertNotNull(page.selectFirst("button.agent-pagination__button[disabled]"))
    }
}
