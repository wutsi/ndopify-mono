package com.wutsi.ndopify.npc.ui.listing

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.COMPLETE
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.IMAGES
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.LISTING
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.SENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.web.core.service.TenantContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/listing.html` through [ListingController] with a real Thymeleaf engine. */
class ListingTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val listingClient = mock<NdListingClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val matchClient = mock<NdMatchClient>()
    private val agentClient = mock<NdAgentClient>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(LISTING).whenever(listingClient).get(501)
        doReturn(IMAGES).whenever(listingClient).images(501)

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
                ListingController(
                    currentAgent,
                    listingClient,
                    mandateClient,
                    locationClient,
                    tenantContext,
                    matchClient,
                    agentClient,
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

    private fun render(listing: Listing? = null, outcome: String? = null, match: String? = null): Document {
        listing?.let { doReturn(it).whenever(listingClient).get(501) }
        val request = get("/listings/501")
        outcome?.let { request.flashAttr(ListingController.OUTCOME, it) }
        match?.let { request.param("match", it) }
        return Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)
    }

    @Test
    fun draft() {
        val page = render()

        assertEquals("Annonce · Appartement, Bonapriso — Ndopify Partner Central", page.title())
        assertEquals("Annonce · Appartement, Bonapriso", page.selectFirst("h1")?.text())
        assertEquals("status-tag status-tag--info", page.selectFirst(".mandate-intro .status-tag")?.className())
        assertEquals("Brouillon", page.selectFirst(".mandate-intro .status-tag")?.text())
        assertEquals("Mandat n° 203 · créée le 20 oct. 2026", page.selectFirst(".mandate__meta")?.text())
        assertEquals("/mandates/203", page.selectFirst(".back-link a")?.attr("href"))
        assertEquals("← Retour au mandat", page.selectFirst(".back-link a")?.text())
        assertNotNull(page.selectFirst("nav.user-menu"))
        assertNull(page.selectFirst("[data-listing-locked]"))
        assertNull(page.selectFirst("[data-listing-flash]"))
        assertEquals(listOf("DRAFT"), page.select("[data-listing-panel]").map { it.attr("data-listing-panel") })
        assertEquals("listing", page.body().attr("data-page"))
        assertNotNull(page.selectFirst("script[src=/assets/js/listing.js]"))
        assertNull(page.selectFirst("[data-listing-stats]"))
        assertNull(page.selectFirst("[data-listing-share]"))
    }

    @Test
    fun checklist() {
        val page = render()

        assertEquals("1 sections sur 6 vérifiées", page.selectFirst("[data-checklist-progress]")?.text())
        val rows = page.select("[data-checklist] > li")
        assertEquals(7, rows.size)
        assertEquals(
            listOf("location", "gps", "property", "amenities", "terms", "photos", "remarks").map { "/listings/501/$it" },
            rows.map { it.selectFirst("a.checklist__row")?.attr("href") },
        )
        val location = rows[0]
        assertEquals("Emplacement", location.selectFirst("[data-section-title]")?.text())
        assertEquals("Vérifié", location.selectFirst("[data-section-state]")?.text())
        assertEquals("status-tag status-tag--success", location.selectFirst("[data-section-state]")?.className())
        assertEquals("Bonapriso · Derrière l'école publique", location.selectFirst("[data-section-summary]")?.text())
        assertEquals("À compléter", rows[5].selectFirst("[data-section-state]")?.text())
    }

    @Test
    fun `send - incomplete`() {
        val page = render()

        val form = page.selectFirst("form[data-listing-send-form]")!!
        assertEquals("/listings/501/verify", form.attr("action"))
        assertEquals("post", form.attr("method"))
        val submit = form.selectFirst("button[data-listing-submit]")!!
        assertEquals("Envoyer pour vérification", submit.text())
        assertEquals("true", submit.attr("aria-disabled"))
        assertEquals("listing-missing", submit.attr("aria-describedby"))
        assertEquals(
            "Encore à faire : Position GPS, Le bien, Eau, électricité, accès, Loyer et conditions, " +
                "la photo refusée, 3 photos.",
            page.selectFirst("#listing-missing")?.text(),
        )
    }

    @Test
    fun `send - complete`() {
        val page = render(COMPLETE)

        val submit = page.selectFirst("button[data-listing-submit]")!!
        assertTrue(!submit.hasAttr("aria-disabled"))
        assertEquals("Tout est vérifié : vous pouvez envoyer l'annonce.", page.selectFirst("#listing-missing")?.text())
    }

    @Test
    fun sent() {
        val page = render(COMPLETE.copy(status = ListingStatus.UNDER_VERIFICATION, modifiedAt = SENT))

        assertEquals("En vérification", page.selectFirst(".mandate-intro .status-tag")?.text())
        assertEquals(listOf("SENT"), page.select("[data-listing-panel]").map { it.attr("data-listing-panel") })
        assertTrue(page.selectFirst("[data-listing-panel=SENT]")!!.text().contains("Envoyée le 22 oct. 2026."))
        assertNull(page.selectFirst("[data-listing-send]"))
        assertEquals(7, page.select("a.checklist__row").size)
    }

    @Test
    fun published() {
        val page = render(
            COMPLETE.copy(
                status = ListingStatus.PUBLISHED,
                publishedAt = SENT,
                title = "Appartement 2 chambres à louer à Bonapriso",
                summary = "Appartement de 85 m² à Bonapriso.",
                url = "/douala/bonapriso/listing/501/appartement",
            ),
        )

        assertTrue(page.select("[data-listing-panel]").isEmpty())
        assertEquals("Publiée le 22 oct. 2026", page.selectFirst("[data-listing-tag]")?.text())
        assertNull(page.selectFirst("[data-generated-title]"))
        assertNull(page.selectFirst("[data-listing-public-url]"))
        assertNull(page.selectFirst("[data-listing-share]"))
        assertNull(page.selectFirst("[data-listing-send]"))
        assertNull(page.selectFirst("[data-listing-stats]"))
    }

    @Test
    fun `published - gallery, about and sheet`() {
        val page = render(
            COMPLETE.copy(
                status = ListingStatus.PUBLISHED,
                publishedAt = SENT,
                heroImageId = 2,
                description = "Un appartement lumineux.\n\nLibre en novembre.",
            ),
        )

        val gallery = page.selectFirst("[data-listing-gallery]")!!
        assertEquals("https://cdn/2.jpg", gallery.selectFirst("[data-photo-link] img")?.attr("src"))
        assertEquals("Voir les 2 photos", gallery.selectFirst("[data-photo-open]")?.text())
        assertEquals("https://cdn/2.jpg", gallery.selectFirst("[data-photo-open]")?.attr("href"))
        assertNotNull(page.selectFirst("[data-listing-about]"))
        assertEquals(
            "Un appartement lumineux.\n\nLibre en novembre.",
            page.selectFirst("[data-listing-about] .listing-description")?.wholeText(),
        )
        assertNotNull(page.selectFirst("[data-listing-details]"))
        assertNotNull(page.selectFirst("[data-listing-sheet]"))
        assertEquals("2 chambres", page.selectFirst("[data-listing-facts] li")?.text())
    }

    @Test
    fun `published - more than five photos are in the viewer, five in the grid`() {
        val images = (1L..7L).map { ListingImage(id = it, url = "https://cdn/$it.jpg", status = ImageStatus.VERIFIED) }
        doReturn(images).whenever(listingClient).images(501)
        val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT))

        assertEquals(7, page.select("[data-photo-link]").size)
        assertEquals(2, page.select("[data-photo-link].listing-gallery__item--extra").size)
        assertEquals("Voir les 7 photos", page.selectFirst("[data-photo-open]")?.text())
    }

    @Test
    fun `published - images unavailable leaves the sheet, without a gallery`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).images(501)
        val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT))

        assertNull(page.selectFirst("[data-listing-gallery]"))
        assertNotNull(page.selectFirst("[data-listing-details]"))
    }

    @Test
    fun `published owner - no gps, no access, no private block`() {
        val page = render(
            COMPLETE.copy(
                status = ListingStatus.PUBLISHED,
                publishedAt = SENT,
                latitude = null,
                longitude = null,
                accessDirections = null,
            ),
        )

        assertNull(page.selectFirst(".listing-private"))
        assertNull(page.selectFirst(".listing-place__note"))
    }

    @Test
    fun `published - owner keeps the public link, share, stats and private location`() {
        doReturn(TENANT.copy(publicPortalUrl = "https://ndopify.com")).whenever(tenantContext).getTenant()
        doReturn(mapOf(501L to ListingStats(501, 2, 1, 2))).whenever(listingClient).stats(listOf(501L))
        val page = render(
            COMPLETE.copy(
                status = ListingStatus.PUBLISHED,
                publishedAt = SENT,
                url = "/douala/bonapriso/listing/501/appartement",
                accessDirections = "Porte de droite",
            ),
        )

        assertNotNull(page.selectFirst("[data-listing-public-url]"))
        assertNotNull(page.selectFirst("[data-listing-share]"))
        assertEquals(
            "2 personnes intéressées · 3 visites",
            page.selectFirst("[data-listing-stats] > span > span:last-child")?.text(),
        )
        assertNotNull(page.selectFirst(".listing-private"))
        assertTrue(page.selectFirst(".listing-private")!!.text().contains("4.03350, 9.69420"))
        assertTrue(page.selectFirst(".listing-private")!!.text().contains("Porte de droite"))
    }

    @Test
    fun `published - verification is collapsed`() {
        val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT))

        val details = page.selectFirst("[data-listing-verification]")!!
        assertFalse(details.hasAttr("open"))
        assertEquals("published", details.attr("data-mode"))
        assertEquals("Vérification de l'annonce", details.selectFirst("summary")?.text())
        assertEquals("Sections vérifiées", details.selectFirst("h2")?.text())
    }

    @Test
    fun `draft - verification stays open and the checklist is the page`() {
        val details = render().selectFirst("[data-listing-verification]")!!

        assertTrue(details.hasAttr("open"))
        assertEquals("edit", details.attr("data-mode"))
        assertEquals(7, render().select("a.checklist__row").size)
        assertNull(render().selectFirst("[data-listing-gallery]"))
    }

    @Test
    fun `co-broker - no owner-only block, agent card instead`() {
        doReturn(
            COMPLETE.copy(
                status = ListingStatus.PUBLISHED,
                agentId = 999,
                publishedAt = SENT,
                description = "Public",
                url = "/douala/bonapriso/listing/501/appartement",
            ),
        ).whenever(listingClient).get(501)
        doReturn(Match(id = 9009, mandateId = 204, listingId = 501, coBrokerage = true, status = MatchStatus.ACTIVE))
            .whenever(matchClient).get(9009)
        doReturn(Mandate(id = 204, agentId = AGENT.id, mandateType = MandateType.SEARCH, status = MandateStatus.APPROVED))
            .whenever(mandateClient).get(204)
        doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

        val page = render(match = "9009")

        assertNull(page.selectFirst("[data-listing-public-url]"))
        assertNull(page.selectFirst("[data-listing-share]"))
        assertNull(page.selectFirst("[data-listing-stats]"))
        assertNull(page.selectFirst("[data-listing-verification]"))
        assertNull(page.selectFirst(".listing-private"))
        assertEquals("/mandates/204#matches-title", page.selectFirst(".back-link a")?.attr("href"))
        assertEquals("← Retour aux biens correspondants", page.selectFirst(".back-link a")?.text())
        assertNotNull(page.selectFirst("[data-listing-agent]"))
        assertNotNull(page.selectFirst("[data-listing-origin]"))
        assertNull(page.selectFirst("[data-listing-meta]"))
        assertNotNull(page.selectFirst("[data-listing-gallery]"))
        assertNotNull(page.selectFirst("[data-listing-details]"))
    }

    @Test
    fun `published - activity line`() {
        doReturn(mapOf(501L to ListingStats(501, 2, 1, 2))).whenever(listingClient).stats(listOf(501L))

        val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT))

        assertEquals(
            "2 personnes intéressées · 3 visites",
            page.selectFirst("[data-listing-stats] > span > span:last-child")?.text(),
        )
    }

    @Test
    fun `published - no proposals (they are on the mandate page)`() {
        assertNull(render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT)).selectFirst("[data-listing-proposals]"))
    }

    @Test
    fun `draft - no proposals`() {
        assertNull(render().selectFirst("[data-listing-proposals]"))
    }

    @Test
    fun `published - public url`() {
        doReturn(TENANT.copy(publicPortalUrl = "https://ndopify.com")).whenever(tenantContext).getTenant()

        val page = render(
            COMPLETE.copy(
                status = ListingStatus.PUBLISHED,
                publishedAt = SENT,
                url = "/douala/bonapriso/listing/501/appartement",
            ),
        )

        assertEquals(
            "https://ndopify.com/douala/bonapriso/listing/501/appartement",
            page.selectFirst("[data-listing-public-url]")?.attr("href"),
        )
    }

    @Test
    fun `published - share button`() {
        doReturn(TENANT.copy(publicPortalUrl = "https://ndopify.com")).whenever(tenantContext).getTenant()

        val page = render(
            COMPLETE.copy(
                status = ListingStatus.PUBLISHED,
                publishedAt = SENT,
                url = "/douala/bonapriso/listing/501/appartement",
            ),
        )

        val button = page.selectFirst("[data-listing-share]")!!
        assertEquals("https://ndopify.com/douala/bonapriso/listing/501/appartement", button.attr("data-share-url"))
        assertEquals("Annonce · Appartement, Bonapriso", button.attr("data-share-title"))
        assertEquals("Partager", button.text())
        assertTrue(button.hasAttr("hidden"))
        assertNotNull(page.selectFirst("[data-share-status]"))
    }

    @Test
    fun `closed - public url`() {
        doReturn(TENANT.copy(publicPortalUrl = "https://ndopify.com")).whenever(tenantContext).getTenant()

        listOf(ListingStatus.RENTED, ListingStatus.SOLD).forEach { status ->
            val page = render(
                COMPLETE.copy(
                    status = status,
                    url = "/douala/bonapriso/listing/501/appartement",
                ),
            )

            assertEquals(
                "https://ndopify.com/douala/bonapriso/listing/501/appartement",
                page.selectFirst("[data-listing-public-url]")?.attr("href"),
            )
            assertEquals(
                "https://ndopify.com/douala/bonapriso/listing/501/appartement",
                page.selectFirst("[data-listing-share]")?.attr("data-share-url"),
            )
        }
    }

    @Test
    fun `sent back - to fix`() {
        val page = render(
            COMPLETE.copy(
                status = ListingStatus.DRAFT,
                errorCodes = listOf(ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD),
                verifiedAt = SENT,
            ),
        )

        assertEquals("À corriger", page.selectFirst(".mandate-intro .status-tag")?.text())
        assertEquals(listOf("TO_FIX"), page.select("[data-listing-panel]").map { it.attr("data-listing-panel") })
        assertEquals("Annonce à corriger", page.selectFirst("[data-listing-panel=TO_FIX] .alert__title")?.text())
        assertEquals(
            listOf("La position GPS relevée n'est pas dans le quartier indiqué."),
            page.select("[data-rejection-reasons] li").map { it.text() },
        )
        assertEquals(
            "À corriger",
            page.select("a.checklist__row").first { it.attr("href") == "/listings/501/gps" }
                .selectFirst("[data-section-state]")?.text(),
        )
        assertNotNull(page.selectFirst("form[data-listing-send-form]"))
    }

    @Test
    fun outcome() {
        val page = render(outcome = ListingController.SENT)

        assertEquals(1, page.select("[data-listing-flash]").size)
        val flash = page.selectFirst("[data-listing] > [data-listing-flash]")!!
        assertEquals("page-intro mandate-intro", flash.previousElementSibling()?.className())
        assertEquals("alert alert--success", flash.className())
        assertEquals("status", flash.attr("role"))
        assertEquals("Annonce envoyée. Nous vous préviendrons dès sa publication.", flash.text())
    }

    @Test
    fun `outcome - error`() {
        val flash = render(outcome = ListingController.FAILED).selectFirst("[data-listing-flash]")!!

        assertEquals("alert alert--error", flash.className())
        assertEquals("alert", flash.attr("role"))
    }

    @Test
    fun `locked - with outcome`() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val page = render(outcome = ListingController.CHANGED)

        assertNotNull(page.selectFirst("[data-listing-locked]"))
        assertEquals(
            "Cette annonce a changé entre-temps : vérifiez son état avant de recommencer.",
            page.selectFirst("[data-listing-flash]")?.text(),
        )
    }

    @Test
    fun unavailable() {
        doThrow(HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)).whenever(listingClient).get(501)

        val page = render()

        assertNull(page.selectFirst("[data-listing-locked]"))
        assertEquals(
            "Annonce indisponible pour le moment Réessayez dans quelques instants.",
            page.selectFirst("[data-listing-unavailable]")?.text(),
        )
        assertEquals("/#mandates-title", page.selectFirst(".back-link a")?.attr("href"))
    }

    @Test
    fun pending() {
        val page = render(COMPLETE.copy(status = ListingStatus.PENDING))

        assertEquals("En attente", page.selectFirst(".mandate-intro .status-tag")?.text())
        assertTrue(page.select("[data-listing-panel]").isEmpty())
    }

    @Test
    fun locked() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(listingClient).get(501)

        val page = render()

        assertEquals("Annonce — Ndopify Partner Central", page.title())
        assertEquals("Cette annonce n'est pas disponible", page.selectFirst("[data-listing-locked] h1")?.text())
        assertEquals("/#mandates-title", page.selectFirst(".back-link a")?.attr("href"))
        assertNull(page.selectFirst("[data-listing]"))
    }
}
