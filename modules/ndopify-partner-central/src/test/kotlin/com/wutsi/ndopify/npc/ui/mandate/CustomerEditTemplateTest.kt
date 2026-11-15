package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/mandate/customer-edit.html` through [CustomerEditController] with a real Thymeleaf engine. */
class CustomerEditTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(mock(), tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(NOW).whenever(clock).millis()
        doReturn(MANDATE).whenever(mandateClient).get(201)
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
            .standaloneSetup(CustomerEditController(currentAgent, mandateClient, customerClient, tenantContext, clock))
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
    fun `form - pre-filled`() {
        val page = render(get("/mandates/201/customer/edit"))

        assertEquals("Modifier les coordonnées du client — Mandat n° 201 — Ndopify Partner Central", page.title())
        assertEquals("Coordonnées du client", page.selectFirst("h1")?.text())
        assertEquals("Mandat n° 201 · Locataire · Maison", page.selectFirst(".mandate__meta")?.text())
        assertEquals("Ray", page.selectFirst("#firstName")?.attr("value"))
        assertEquals("Sponsible", page.selectFirst("#lastName")?.attr("value"))
        assertEquals("ray@gmail.com", page.selectFirst("#email")?.attr("value"))
        assertEquals("+237699123456", page.selectFirst("#whatsappNumber")?.attr("value"))
        // Without JS the field posts as is; mandate.js moves the name to the hidden input intl-tel-input syncs
        assertEquals("whatsappNumber", page.selectFirst("#whatsappNumber")?.attr("name"))
        assertEquals("/mandates/201", page.selectFirst(".back-link a")?.attr("href"))
        assertEquals("/mandates/201/customer/edit", page.selectFirst("[data-customer-form]")?.attr("action"))
        assertNotNull(page.selectFirst("[data-customer-submit]"))
        assertNull(page.selectFirst("[data-customer-locked]"))
        assertNull(page.selectFirst("[data-customer-resend]"))
        assertTrue(page.selectFirst("[data-customer-hint]")!!.text().contains("tous les mandats"))
    }

    @Test
    fun `form - pending approval says the mandate comes back to assigned`() {
        doReturn(PENDING).whenever(mandateClient).get(201)

        val page = render(get("/mandates/201/customer/edit"))

        assertNotNull(page.selectFirst("[data-customer-resend]"))
        assertTrue(page.selectFirst("[data-customer-hint]")!!.text().contains("tous les mandats"))
    }

    @Test
    fun `form - errors`() {
        val page = render(
            post("/mandates/201/customer/edit")
                .param("firstName", "")
                .param("lastName", "Sponsible")
                .param("email", "ray@gmail.com")
                .param("whatsappNumber", "+237699123456"),
        )

        assertEquals("Saisissez le prénom.", page.selectFirst("#firstName-error [data-error-text]")?.text())
        assertEquals("true", page.selectFirst("#firstName")?.attr("aria-invalid"))
        assertTrue(page.selectFirst("#lastName-error")?.hasAttr("hidden") ?: true)
        assertEquals("", page.selectFirst("#firstName")?.attr("value"))
    }

    @Test
    fun `form - locked unavailable`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val page = render(get("/mandates/201/customer/edit"))

        assertEquals(
            "Ce mandat n'est pas disponible",
            page.selectFirst("[data-customer-locked] h1")?.text(),
        )
        assertNull(page.selectFirst("[data-customer-form]"))
    }

    @Test
    fun `form - locked closed`() {
        doReturn(MANDATE.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(201)

        val page = render(get("/mandates/201/customer/edit"))

        assertEquals(
            "Ce mandat ne permet plus de corriger les coordonnées",
            page.selectFirst("[data-customer-locked] h1")?.text(),
        )
        assertNull(page.selectFirst("[data-customer-form]"))
    }
}
