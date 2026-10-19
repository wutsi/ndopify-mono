package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.ACCEPTED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.APPROVED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/mandate/edit.html` through [MandateController] with a real Thymeleaf engine. */
class MandateEditTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val contractClient = mock<NdContractClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val assignmentClient = mock<NdAssignmentClient>()
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

    private fun render(mandate: Mandate): Document {
        doReturn(mandate).whenever(mandateClient).get(201)
        return render(get("/mandates/201/edit"))
    }

    @Test
    fun form() {
        val page = render(MANDATE.copy(waterWell = true, version = 3))

        assertEquals("Modifier le mandat 201 — Ndopify Partner Central", page.title())
        assertEquals("Modifier le mandat", page.selectFirst("h1")?.text())
        assertEquals("Locataire · Maison · mandat n° 201", page.selectFirst(".mandate__meta")?.text())
        assertEquals("/mandates/201", page.selectFirst(".back-link a")?.attr("href"))
        assertNotNull(page.selectFirst("nav.user-menu"))
        assertNull(page.selectFirst("[data-edit-resend]"))

        val form = page.selectFirst("form[data-edit-form]")!!
        assertEquals("/mandates/201/edit", form.attr("action"))
        assertEquals("3", form.selectFirst("input[type=hidden][name=version]")?.attr("value"))
        assertEquals("post", form.attr("method"))
        assertEquals(
            listOf("sector", "property", "amenities", "budget", "comments"),
            form.select("details.edit-group").map { it.attr("data-edit-group") },
        )
        assertTrue(form.select("details.edit-group").none { it.hasAttr("open") })

        // Sector: the city is read-only; the chosen neighbourhoods, for slim-select to enhance.
        assertEquals("Douala", form.selectFirst(".edit-readonly")?.text())
        assertNull(form.selectFirst("[name=cityId]"))
        val select = form.selectFirst("select[name=neighborhoodIds]")!!
        assertTrue(select.hasAttr("multiple"))
        assertEquals(DOUALA.toString(), select.attr("data-city-id"))
        assertEquals(listOf(BONAPRISO.toString(), AKWA.toString()), select.select("option[selected]").map { it.attr("value") })
        assertEquals("Bonapriso, Akwa", form.selectFirst("[data-edit-group=sector] [data-group-value]")?.text())

        // Property: every follow-up question without JS, the types tagged with their categories.
        assertTrue(form.selectFirst("input[name=propertyCategory][value=RESIDENTIAL]")!!.hasAttr("checked"))
        assertTrue(form.selectFirst("input[name=propertyType][value=HOUSE]")!!.hasAttr("checked"))
        assertEquals("RESIDENTIAL COMMERCIAL", form.selectFirst("input[name=propertyType][value=LAND]")?.parent()?.attr("data-categories"))
        assertNotNull(form.selectFirst("input[name=propertyType][value=ANY]"))
        assertTrue(form.selectFirst("input[name=rooms][value=3]")!!.hasAttr("checked"))
        assertTrue(form.selectFirst("input[name=furnitureType][value=ANY]")!!.hasAttr("checked"))
        assertTrue(form.selectFirst("input[name=parkings][value=ANY]")!!.hasAttr("checked"))
        assertNotNull(form.selectFirst("input[name=area]"))
        assertEquals("Surface minimale", form.selectFirst("label[for=area] span")?.text())

        assertTrue(form.selectFirst("input[name=amenities][value=waterWell]")!!.hasAttr("checked"))
        assertFalse(form.selectFirst("input[name=amenities][value=waterMeter]")!!.hasAttr("checked"))
        assertEquals(14, form.select("input[name=amenities]").size)

        assertEquals("Budget et emménagement", form.selectFirst("[data-edit-group=budget] .edit-group__title")?.text())
        assertEquals("Budget mensuel", form.selectFirst("label[for=priceAmount]")?.text())
        assertEquals("250 000", form.selectFirst("input[name=priceAmount]")?.attr("value")?.replace('\u00a0', ' '))
        assertTrue(form.selectFirst("input[name=movingPeriod][value=WITHIN_3_MONTHS]")!!.hasAttr("checked"))
        assertEquals("Près d'une école", form.selectFirst("textarea[name=comments]")?.text())
        assertEquals("/mandates/201", form.selectFirst(".form__actions a")?.attr("href"))
        assertEquals("Enregistrer", form.selectFirst(".form__actions button[type=submit]")?.text())
    }

    @Test
    fun `pending approval - notice`() {
        val page = render(PENDING)

        assertEquals(
            "Ce mandat attend la signature du client : après vos modifications, renvoyez-le-lui pour qu'il signe la nouvelle version.",
            page.selectFirst("[data-edit-resend]")?.text()?.replace('\u00a0', ' '),
        )
    }

    @Test
    fun landlord() {
        val page = render(MANDATE.copy(mandateType = MandateType.LISTING))

        val form = page.selectFirst("form[data-edit-form]")!!
        assertTrue(form.select("input[value=ANY]").isEmpty())
        assertEquals("Surface", form.selectFirst("label[for=area] span")?.text())
        assertEquals("Loyer et disponibilité", form.selectFirst("[data-edit-group=budget] .edit-group__title")?.text())
        assertEquals("Loyer mensuel", form.selectFirst("label[for=priceAmount]")?.text())
        assertEquals("Disponibilité du bien", form.selectFirst("fieldset[data-group=movingPeriod] legend")?.text())
        assertEquals("Ce dont le bien dispose.", form.selectFirst("[data-edit-group=amenities] .edit-group__body > p")?.text())
    }

    @Test
    fun errors() {
        val page = render(
            post("/mandates/201/edit")
                .param("propertyCategory", "RESIDENTIAL")
                .param("propertyType", "APARTMENT")
                .param("furnitureType", "ANY")
                .param("priceAmount", "abc")
                .param("movingPeriod", "WITHIN_1_MONTH"),
        )

        val form = page.selectFirst("form[data-edit-form]")!!
        assertTrue(form.selectFirst("[data-edit-group=property]")!!.hasAttr("open"))
        assertTrue(form.selectFirst("[data-edit-group=budget]")!!.hasAttr("open"))
        assertFalse(form.selectFirst("[data-edit-group=sector]")!!.hasAttr("open"))
        assertEquals("Indiquez le nombre de chambres.", form.selectFirst("#rooms-error")?.text())
        assertFalse(form.selectFirst("#rooms-error")!!.hasAttr("hidden"))
        assertEquals("abc", form.selectFirst("input[name=priceAmount]")?.attr("value"))
        assertEquals("true", form.selectFirst("input[name=priceAmount]")?.attr("aria-invalid"))
        assertTrue(form.selectFirst("#area-error")!!.hasAttr("hidden"))
    }

    @Test
    fun `save error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).update(any(), any())

        val page = render(
            post("/mandates/201/edit")
                .param("propertyCategory", "RESIDENTIAL")
                .param("propertyType", "HOUSE")
                .param("rooms", "3")
                .param("furnitureType", "ANY")
                .param("priceAmount", "150000")
                .param("movingPeriod", "WITHIN_1_MONTH"),
        )

        assertEquals(
            "Enregistrement impossible pour le moment. Réessayez dans quelques instants.",
            page.selectFirst("[data-edit-error]")?.text(),
        )
    }

    @Test
    fun `locked - approved`() {
        val page = render(APPROVED)

        assertEquals("Ce mandat ne peut plus être modifié", page.selectFirst("[data-edit-locked] h1")?.text())
        assertEquals("Un mandat approuvé par le client ne se modifie plus.", page.selectFirst("[data-edit-locked] p")?.text())
        assertNull(page.selectFirst("form[data-edit-form]"))
        assertEquals("/mandates/201", page.selectFirst(".back-link a")?.attr("href"))
    }

    @Test
    fun `locked - unavailable`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val page = render(get("/mandates/201/edit"))

        assertEquals("Ce mandat n'est pas disponible", page.selectFirst("[data-edit-locked] h1")?.text())
        assertEquals(
            "Vous ne pouvez modifier que vos mandats, tant qu'ils ne sont pas approuvés par le client.",
            page.selectFirst("[data-edit-locked] p")?.text(),
        )
    }

    // mandate.js

    @Test
    fun `script and hooks`() {
        val page = render(MANDATE)

        assertTrue(page.selectFirst("script[src=/assets/js/mandate.js]")!!.hasAttr("defer"))
        assertNotNull(page.selectFirst("script[src*=slim-select@4.3.0]"))
        assertNotNull(page.selectFirst("link[href*=slim-select@4.3.0]"))
        assertEquals("mandate-edit", page.body().attr("data-page"))
        val form = page.selectFirst("form[data-edit-form]")!!
        assertEquals("/locations", form.attr("data-locations-url"))
        assertEquals(12, form.select("[data-categories]").size)
        assertEquals(5, form.select("[data-edit-group] [data-group-value]").size)
        assertEquals(DOUALA.toString(), form.selectFirst("[data-city-id]")?.attr("data-city-id"))
        assertNotNull(form.selectFirst("[data-group=rooms]"))
        assertNotNull(form.selectFirst("[data-group=furnitureType]"))
        assertNotNull(form.selectFirst("[data-group=parkings]"))
    }
}
