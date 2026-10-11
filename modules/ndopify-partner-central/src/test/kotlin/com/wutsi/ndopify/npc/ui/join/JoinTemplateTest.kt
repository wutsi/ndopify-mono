package com.wutsi.ndopify.npc.ui.join

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import com.wutsi.ndopify.refdata.dto.Tenant
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Renders `templates/rejoindre.html` through [JoinController] with a real Thymeleaf engine, to catch template
 * mistakes (expressions, conditions, pre-filled values) that [JoinControllerTest] can't see.
 */
class JoinTemplateTest {
    private val agentClient = mock<NdAgentClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    private val locations = listOf(
        Location(id = 2220957, name = "Yaoundé", type = LocationType.CITY, country = "CM"),
        Location(id = 1001, parentId = 2220957, name = "Bastos", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 1002, parentId = 2220957, name = "Essos", type = LocationType.NEIGHBORHOOD, country = "CM"),
    )

    private val complete = PendingJoin(
        firstName = "Ray",
        lastName = "Sponsible",
        email = "ray@gmail.com",
        whatsappNumber = "+237670000001",
        experienceLevel = ExperienceLevel.JUNIOR,
        agentType = AgentType.INDEPENDANT,
        cityId = 2220957,
        neighborhoodIds = listOf(1001, 1002),
        mobileMoneyNumber = "+237670000002",
    )

    @BeforeEach
    fun setUp() {
        doReturn(Tenant(id = 1, countryCode = "CM")).whenever(tenantContext).getTenant()
        whenever(locationClient.search(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<SearchLocationRequest>(0)
            SearchLocationResponse(locations = locations.filter { request.ids.contains(it.id) })
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
        mvc = MockMvcBuilders.standaloneSetup(JoinController(agentClient, locationClient, tenantContext))
            .setViewResolvers(viewResolver)
            .build()
        session = MockHttpSession()
    }

    @Test
    fun identity() {
        val page = render(get("/rejoindre"))

        assertEquals("Étape 1 sur 5", page.selectFirst("[data-progress-label]")?.text())
        assertEquals("/rejoindre/identite", form(page).attr("action"))
        assertEquals(listOf("identite"), page.select("[data-step]").map { it.attr("data-step") })
        assertEquals("/login", page.selectFirst("[data-prev]")?.attr("href"))
        assertEquals("Suivant", page.selectFirst("[data-next]")?.text())
        assertNull(page.selectFirst("input[name=retour]"))
        assertTrue(page.select(".field__error").all { it.hasAttr("hidden") })
    }

    @Test
    fun identityWithErrors() {
        val page = render(
            post("/rejoindre/identite")
                .param("firstName", "Ray")
                .param("lastName", "R2D2")
                .param("email", "ray@gmail.com")
                .param("whatsappNumber", "+237670000001"),
        )

        assertEquals("Ray", page.selectFirst("#firstName")?.`val`())
        assertEquals("R2D2", page.selectFirst("#lastName")?.`val`())
        assertEquals("true", page.selectFirst("#lastName")?.attr("aria-invalid"))
        assertTrue(page.selectFirst("#firstName")!!.attr("aria-invalid").isEmpty())
        assertTrue(page.selectFirst("#firstName-error")!!.hasAttr("hidden"))
        val error = page.selectFirst("#lastName-error")!!
        assertTrue(!error.hasAttr("hidden"))
        assertEquals(
            "Utilisez uniquement des lettres, des espaces, des tirets ou des apostrophes.",
            error.selectFirst("[data-error-text]")?.text(),
        )
    }

    @Test
    fun identityReturningToReview() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val page = render(get("/rejoindre?etape=identite&retour=revue"))

        assertEquals("revue", page.selectFirst("input[name=retour]")?.`val`())
        assertEquals("Revenir à la revue", page.selectFirst("[data-next]")?.text())
        assertEquals("/rejoindre?etape=revue", page.selectFirst("[data-prev]")?.attr("href"))
        assertEquals("ray@gmail.com", page.selectFirst("#email")?.`val`())
        assertEquals("+237670000001", page.selectFirst("#whatsappNumber")?.`val`())
    }

    @Test
    fun experience() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val page = render(get("/rejoindre?etape=experience"))

        assertEquals("Étape 2 sur 5", page.selectFirst("[data-progress-label]")?.text())
        assertEquals("/rejoindre/experience", form(page).attr("action"))
        assertEquals(listOf("JUNIOR"), page.select("input[name=experienceLevel][checked]").map { it.`val`() })
        assertEquals(listOf("INDEPENDANT"), page.select("input[name=agentType][checked]").map { it.`val`() })
        assertEquals("/rejoindre", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun sector() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val page = render(get("/rejoindre?etape=secteur"))

        assertEquals("/rejoindre/secteur", form(page).attr("action"))
        assertEquals("/locations", form(page).attr("data-locations-url"))
        assertEquals("2220957", page.selectFirst("#cityId option[selected]")?.`val`())
        assertEquals("Yaoundé", page.selectFirst("#cityId option[selected]")?.text())
        assertEquals(listOf("1001", "1002"), page.select("#neighborhoodIds option[selected]").map { it.`val`() })
        assertEquals("2 sur 3 sélectionnés", page.selectFirst("[data-neighborhood-counter]")?.text())
        assertTrue(page.selectFirst("[data-neighborhoods-empty]")!!.hasAttr("hidden"))
        assertTrue(!page.selectFirst("[data-group=neighborhoodIds]")!!.hasAttr("hidden"))
        assertEquals("/rejoindre?etape=experience", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun sectorWithoutCity() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(cityId = null, neighborhoodIds = emptyList()))

        val page = render(get("/rejoindre?etape=secteur"))

        assertEquals(listOf(""), page.select("#cityId option").map { it.`val`() })
        assertTrue(page.select("#neighborhoodIds option").isEmpty())
        assertEquals("0 sur 3 sélectionné", page.selectFirst("[data-neighborhood-counter]")?.text())
        assertTrue(!page.selectFirst("[data-neighborhoods-empty]")!!.hasAttr("hidden"))
        assertTrue(page.selectFirst("[data-group=neighborhoodIds]")!!.hasAttr("hidden"))
    }

    @Test
    fun payment() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(mobileMoneyNumber = "+237670000001"))

        val page = render(get("/rejoindre?etape=paiement"))

        assertEquals("/rejoindre/paiement", form(page).attr("action"))
        assertEquals("+237670000001", page.selectFirst("#mobileMoneyNumber")?.`val`())
        assertTrue(page.selectFirst("#sameAsWhatsapp")!!.hasAttr("checked"))
        assertEquals("+237670000001", page.selectFirst("[data-whatsapp-number]")?.text())
        assertEquals("/rejoindre?etape=secteur", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun paymentWithForeignWhatsapp() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(whatsappNumber = "+15145550000"))

        val page = render(get("/rejoindre?etape=paiement"))

        assertNull(page.selectFirst("[data-same-as-whatsapp]"))
        assertEquals("+237670000002", page.selectFirst("#mobileMoneyNumber")?.`val`())
    }

    @Test
    fun review() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val page = render(get("/rejoindre?etape=revue"))

        assertEquals("Étape 5 sur 5", page.selectFirst("[data-progress-label]")?.text())
        assertEquals("/rejoindre/revue", form(page).attr("action"))
        assertEquals(
            mapOf(
                "name" to "Ray Sponsible",
                "email" to "ray@gmail.com",
                "whatsappNumber" to "+237670000001",
                "experienceLevel" to "1 à 3 ans",
                "agentType" to "Indépendant",
                "cityId" to "Yaoundé",
                "neighborhoodIds" to "Bastos, Essos",
                "mobileMoneyNumber" to "+237670000002",
            ),
            page.select("[data-review-value]").associate { it.attr("data-review-value") to it.text() },
        )
        assertEquals(
            "/rejoindre?etape=secteur&retour=revue",
            page.select(".review__edit").map { it.attr("href") }[5],
        )
        assertNull(page.selectFirst("[data-submit-error]"))
    }

    @Test
    fun reviewAgentAlreadyExists() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)
        val conflict = HttpClientErrorException(HttpStatus.CONFLICT)
        conflict.setBodyConvertFunction { ErrorResponse(Error(code = ErrorCode.AGENT_ALREADY_EXISTS)) }
        doThrow(conflict).whenever(agentClient).create(any())

        val page = render(post("/rejoindre/revue"))

        val alert = assertNotNull(page.selectFirst("[data-submit-error]"))
        assertEquals("Un compte utilise déjà cette adresse email.", alert.selectFirst(".alert__title")?.text())
        assertEquals(
            listOf("/login", "/rejoindre?etape=identite&retour=revue"),
            alert.select("a").map { it.attr("href") },
        )
    }

    @Test
    fun reviewRejected() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)
        doThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST)).whenever(agentClient).create(any())

        val page = render(post("/rejoindre/revue"))

        val alert = assertNotNull(page.selectFirst("[data-submit-error]"))
        assertTrue(alert.select("a").isEmpty())
    }

    @Test
    fun confirmation() {
        session.setAttribute(JoinedAgent.SESSION_ATTRIBUTE, JoinedAgent(email = "ray@gmail.com"))

        val page = render(get("/rejoindre?etape=confirmation"))

        assertEquals("Candidature envoyée — Ndopify Partner Central", page.title())
        assertEquals("ra•••@gmail.com", page.selectFirst("[data-joined-email]")?.text())
        assertNull(page.selectFirst("[data-progress-block]"))
        assertNull(page.selectFirst("form"))
        assertEquals("/login", page.selectFirst(".cta-row a")?.attr("href"))
    }

    private fun render(request: MockHttpServletRequestBuilder): Document {
        val response = mvc.perform(request.session(session)).andReturn().response
        assertEquals(200, response.status)
        return Jsoup.parse(response.getContentAsString(Charsets.UTF_8))
    }

    private fun form(page: Document) = page.select("form[data-join-form]").single()
}
