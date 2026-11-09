package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest
import com.wutsi.ndopify.npc.ui.match.MatchFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import com.wutsi.ndopify.web.core.service.TenantContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.ResourceAccessException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/mandate/close.html` through [MandateCloseController] with a real Thymeleaf engine. */
class MandateCloseTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val matchClient = mock<NdMatchClient>()
    private val listingClient = mock<NdListingClient>()
    private val locationClient = mock<NdLocationClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    private val answered = Date.from(Instant.parse("2026-10-29T10:00:00Z"))

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        doReturn(TenantFixtures.TENANT).whenever(tenantContext).getTenant()
        doReturn(ProfileFixtures.AGENT).whenever(currentAgent).get()
        doReturn(MandateViewsTest.APPROVED).whenever(mandateClient).get(201)
        whenever(locationClient.search(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<SearchLocationRequest>(0)
            SearchLocationResponse(locations = MatchFixtures.LOCATIONS.values.filter { request.ids.contains(it.id) })
        }

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
                MandateCloseController(
                    currentAgent,
                    mandateClient,
                    matchClient,
                    listingClient,
                    locationClient,
                    customerClient,
                    tenantContext,
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

    private fun render(url: String): Document = render(get(url))

    /** A search's two listings, ACCEPTED by the client, and their listings. */
    private fun withListings() {
        doReturn(
            listOf(
                MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.ACCEPTED, answeredAt = answered),
                MatchFixtures.MATCH_C.copy(mandateId = 201, status = MatchStatus.ACCEPTED, answeredAt = answered),
            ),
        ).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))
        doReturn(listOf(MatchFixtures.LISTING_A, MatchFixtures.LISTING_C))
            .whenever(listingClient).searchByIds(listOf(3012L, 3027L))
    }

    /** A listing's two clients, ACCEPTED, and their searches and customers. */
    private fun withClients() {
        doReturn(MandateViewsTest.APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, ProfileFixtures.AGENT.id)
        doReturn(ListingViewsTest.COMPLETE.copy(status = ListingStatus.PUBLISHED, cityId = 100, neighborhoodId = 1001))
            .whenever(listingClient).get(501)
        doReturn(
            listOf(
                Match(id = 9002, mandateId = 204, listingId = 501, status = MatchStatus.ACCEPTED, answeredAt = answered),
                Match(id = 9005, mandateId = 207, listingId = 501, status = MatchStatus.ACCEPTED, answeredAt = answered),
            ),
        ).whenever(matchClient).searchByListings(listOf(501L), listOf(MatchStatus.ACCEPTED))
        doReturn(ListingViewsTest.SEARCHES).whenever(mandateClient).searchByIds(listOf(204L, 207L), ProfileFixtures.AGENT.id)
        doReturn(ListingViewsTest.CUSTOMERS).whenever(customerClient).search(listOf(14L, 17L))
    }

    private fun text(element: Element?): String? = element?.text()?.replace('\u00a0', ' ')

    @Test
    fun `listing mandate - the clients and the outside option`() {
        withClients()

        val page = render("/mandates/201/close")

        assertEquals("mandate-close", page.body().attr("data-page"))
        assertEquals("FCFA", page.body().attr("data-currency"))
        assertEquals("Fermer le mandat", page.selectFirst("h1")?.text())
        assertEquals("Propriétaire · Maison · mandat n° 201", text(page.selectFirst(".mandate__meta")))
        assertEquals(listOf("Client", "Loyer", "Confirmation"), page.select(".close-steps__item > span:last-child").map { it.text() })
        assertEquals(
            "Avec quel client le bien a-t-il été loué ?",
            text(page.selectFirst("fieldset[aria-describedby=counterpart-error] legend")),
        )
        assertEquals(listOf("9002", "9005", "outside"), page.select("input[name=counterpart]").map { it.attr("value") })
        val choices = page.select("label.choice").map { text(it) }
        assertTrue(choices.any { it?.contains("Sandrine Eboa") == true }, choices.toString())
        assertTrue(choices.any { it?.contains("Carine Ndongo") == true }, choices.toString())
        assertTrue(text(page.selectFirst("label.close-client-none"))!!.contains("Loué à un autre client"))
        assertEquals("/mandates/201", page.selectFirst(".back-link a")?.attr("href"))
    }

    @Test
    fun `search mandate - the listings, their references and rents`() {
        withListings()

        val page = render("/mandates/201/close")

        assertEquals("Locataire · Maison · mandat n° 201", text(page.selectFirst(".mandate__meta")))
        assertEquals(listOf("Bien", "Loyer", "Confirmation"), page.select(".close-steps__item > span:last-child").map { it.text() })
        assertEquals(
            "Sur quel bien la recherche s'est-elle conclue ?",
            text(page.selectFirst("fieldset[aria-describedby=counterpart-error] legend")),
        )
        assertEquals(listOf("9001", "9002", "outside"), page.select("input[name=counterpart]").map { it.attr("value") })
        val choices = page.select("label.choice").map { text(it) }
        assertTrue(choices.any { it?.contains("annonce n° 3012") == true }, choices.toString())
        assertTrue(choices.any { it?.contains("annonce n° 3027") == true }, choices.toString())
        assertTrue(choices.any { it?.contains("240K FCFA / mois") == true }, choices.toString())
        assertTrue(choices.any { it?.contains("200K FCFA / mois") == true }, choices.toString())
        assertTrue(text(page.selectFirst("label.close-client-none"))!!.contains("Un bien hors de vos annonces"))
    }

    @Test
    fun `no candidates - the price step without a counterpart fieldset`() {
        doReturn(emptyList<Match>()).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))

        val page = render("/mandates/201/close")

        assertNull(page.selectFirst("input[name=counterpart]"))
        assertNotNull(page.selectFirst("input[name=finalPrice]"))
        assertEquals("250000", page.selectFirst("input[name=finalPrice]")?.attr("value"))
        assertEquals(listOf("Loyer", "Confirmation"), page.select(".close-steps__item > span:last-child").map { it.text() })
        val form = page.selectFirst("form[action=/mandates/201/close/price]")!!
        assertEquals("post", form.attr("method"))
    }

    @Test
    fun `confirm - the recap and the final button`() {
        withListings()
        doReturn(MandateViewsTest.CUSTOMER).whenever(customerClient).get(555)

        val page = render(
            get("/mandates/201/close?step=confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9001, priceAmount = 240000)),
        )

        assertEquals("Appartement · 3 chambres, Bonapriso — annonce n° 3012", text(page.selectFirst("[data-recap-property]")))
        assertEquals("Ray Sponsible", text(page.selectFirst("[data-recap-client]")))
        assertEquals("240 000 FCFA / mois", text(page.selectFirst("[data-recap-rent]")))
        assertEquals(
            "À la confirmation : le mandat n° 201 est fermé, l'annonce est retirée du marché et le mandat du bailleur est fermé. Cette action est définitive.",
            text(page.selectFirst("[data-recap-note]")),
        )
        val form = page.selectFirst("form[action=/mandates/201/close/confirm]")!!
        assertEquals("post", form.attr("method"))
        assertEquals("Confirmer la fermeture", form.selectFirst("button[type=submit]")?.text())
        assertEquals("btn btn--primary", form.selectFirst("button[type=submit]")?.className())
        assertEquals("/mandates/201/close?step=price", page.selectFirst("[data-close-back]")?.attr("href"))
    }

    @Test
    fun `listing confirm - the client's name`() {
        withClients()

        val page = render(
            get("/mandates/201/close?step=confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9002, priceAmount = 150000)),
        )

        assertEquals("Appartement · 2 chambres, Bonapriso — annonce n° 501", text(page.selectFirst("[data-recap-property]")))
        assertEquals("Sandrine Eboa", text(page.selectFirst("[data-recap-client]")))
        assertEquals("150 000 FCFA / mois", text(page.selectFirst("[data-recap-rent]")))
        assertEquals(
            "À la confirmation : le mandat n° 201 est fermé, l'annonce est retirée du marché et la recherche du client est fermée. Cette action est définitive.",
            text(page.selectFirst("[data-recap-note]")),
        )
    }

    @Test
    fun `confirm - outside closure`() {
        doReturn(emptyList<Match>()).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))
        doReturn(MandateViewsTest.CUSTOMER).whenever(customerClient).get(555)

        val page = render(
            get("/mandates/201/close?step=confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(outside = true, priceAmount = 240000)),
        )

        assertEquals("Un bien hors de vos annonces", text(page.selectFirst("[data-recap-property]")))
        assertEquals("Ray Sponsible", text(page.selectFirst("[data-recap-client]")))
        assertEquals("240 000 FCFA / mois", text(page.selectFirst("[data-recap-rent]")))
        assertEquals(
            "À la confirmation : le mandat n° 201 est fermé. Aucune annonce n'est retirée du marché. Cette action est définitive.",
            text(page.selectFirst("[data-recap-note]")),
        )
    }

    @Test
    fun `confirm - no candidates closes only the search`() {
        doReturn(emptyList<Match>()).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))
        doReturn(MandateViewsTest.CUSTOMER).whenever(customerClient).get(555)

        val page = render(
            get("/mandates/201/close?step=confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(priceAmount = 240000)),
        )

        assertEquals("Un bien", text(page.selectFirst("[data-recap-property]")))
        assertEquals("Ray Sponsible", text(page.selectFirst("[data-recap-client]")))
        assertEquals(
            "À la confirmation : le mandat n° 201 est fermé. Aucune annonce n'est retirée du marché. Cette action est définitive.",
            text(page.selectFirst("[data-recap-note]")),
        )
    }

    @Test
    fun `confirm - a stored match whose candidates cannot be read`() {
        doThrow(ResourceAccessException("down")).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))
        doReturn(MandateViewsTest.CUSTOMER).whenever(customerClient).get(555)

        val page = render(
            get("/mandates/201/close?step=confirm")
                .sessionAttr(PendingMandateClose.SESSION_ATTRIBUTE, PendingMandateClose(matchId = 9001, priceAmount = 240000)),
        )

        assertEquals(
            "À la confirmation : le mandat n° 201 est fermé, l'annonce est retirée du marché et le mandat du bailleur est fermé. Cette action est définitive.",
            text(page.selectFirst("[data-recap-note]")),
        )
    }

    @Test
    fun `counterpart error - the message and aria-invalid`() {
        withListings()

        val page = render(post("/mandates/201/close/counterpart").param("counterpart", "9999"))

        assertEquals("true", page.selectFirst("fieldset[aria-describedby=counterpart-error]")?.attr("aria-invalid"))
        assertFalse(page.selectFirst("#counterpart-error")!!.hasAttr("hidden"))
        assertEquals(
            "Choisissez une contrepartie dans la liste, ou « hors de vos annonces ».",
            text(page.selectFirst("#counterpart-error")),
        )
    }

    @Test
    fun `price error - the message and aria-invalid`() {
        doReturn(emptyList<Match>()).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))

        val page = render(post("/mandates/201/close/price").param("finalPrice", "abc"))

        assertEquals("abc", page.selectFirst("input[name=finalPrice]")?.attr("value"))
        assertEquals("true", page.selectFirst("input[name=finalPrice]")?.attr("aria-invalid"))
        assertFalse(page.selectFirst("#finalPrice-error")!!.hasAttr("hidden"))
        assertEquals("Indiquez le loyer convenu, en FCFA par mois.", text(page.selectFirst("#finalPrice-error")))
    }

    @Test
    fun `locked - the locked message and nothing of the wizard`() {
        doReturn(MandateViewsTest.APPROVED.copy(agentId = 999)).whenever(mandateClient).get(201)

        val page = render("/mandates/201/close")

        assertEquals("Ce mandat n'est pas disponible", page.selectFirst("[data-close-locked] h1")?.text())
        assertNotNull(page.selectFirst("[data-close-locked]"))
        assertNull(page.selectFirst("[data-close-page]"))
        assertNull(page.selectFirst("input[name=counterpart]"))
        assertEquals("/#mandates-title", page.selectFirst(".back-link a")?.attr("href"))
    }

    @Test
    fun `candidates unavailable - the note and only the outside option`() {
        doThrow(ResourceAccessException("down")).whenever(matchClient).search(listOf(201L), listOf(MatchStatus.ACCEPTED))

        val page = render("/mandates/201/close")

        assertEquals(
            "Les biens correspondants ne peuvent pas être affichés pour le moment.",
            text(page.selectFirst("[data-counterpart-unavailable]")),
        )
        assertEquals(listOf("outside"), page.select("input[name=counterpart]").map { it.attr("value") })
    }

    @Test
    fun `conflict - the flash message`() {
        withListings()

        val page = render(get("/mandates/201/close?step=counterpart").flashAttr(MandateCloseController.CONFLICT, true))

        assertNotNull(page.selectFirst("[data-close-conflict]"))
        assertTrue(text(page.selectFirst("[data-close-conflict]"))!!.contains("n'est plus disponible"))
    }
}
