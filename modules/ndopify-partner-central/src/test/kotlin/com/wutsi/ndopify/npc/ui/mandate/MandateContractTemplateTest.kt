package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.web.core.service.TenantContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/mandate/contract.html` through [MandateContractController] with a real Thymeleaf engine. */
class MandateContractTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val contractClient = mock<NdContractClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        doReturn(TenantFixtures.TENANT).whenever(tenantContext).getTenant()
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(MANDATE).whenever(mandateClient).get(201)
        doReturn(CUSTOMER).whenever(customerClient).get(CUSTOMER.id!!)

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
                MandateContractController(
                    currentAgent,
                    mandateClient,
                    contractClient,
                    customerClient,
                    tenantContext,
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

    private fun text(element: org.jsoup.nodes.Element?): String? = element?.text()?.replace('\u00a0', ' ')

    private fun withContract() {
        doReturn(PENDING).whenever(mandateClient).get(201)
        doReturn(CONTRACT.copy(durationMonths = 3, visitFee = Money(5000, "XAF"))).whenever(contractClient).get("c-201")
    }

    @Test
    fun `the page loads no page script`() {
        withContract()

        val page = render("/mandates/201/contract")

        assertEquals(listOf("/assets/js/partner-central.js"), page.select("script[src]").map { it.attr("src") })
        assertEquals("mandate-contract", page.body().attr("data-page"))
    }

    @Test
    fun `the terms are pre-filled and the send says sent`() {
        val page = render("/mandates/201/contract")

        assertEquals("Envoyer le mandat au client", page.selectFirst("h1")?.text())
        assertEquals("/mandates/201/contract", page.selectFirst("form[data-contract-form]")?.attr("action"))
        assertEquals(12, page.select("select[name=durationMonths] option").size)
        assertEquals("6", page.selectFirst("select[name=durationMonths] option[selected]")?.attr("value"))
        assertEquals("0", page.selectFirst("input[name=visitFee]")?.attr("value"))
        assertEquals("FCFA", page.selectFirst(".input-unit__suffix")?.text())
        assertEquals("Envoyer au client", page.selectFirst("button[data-contract-submit]")?.text())
        assertEquals("/mandates/201", page.selectFirst(".back-link a")?.attr("href"))
    }

    @Test
    fun `a sent mandate pre-fills its contract and says resend`() {
        withContract()

        val page = render("/mandates/201/contract")

        assertEquals("3", page.selectFirst("select[name=durationMonths] option[selected]")?.attr("value"))
        assertEquals("5000", page.selectFirst("input[name=visitFee]")?.attr("value"))
        assertEquals("Renvoyer", page.selectFirst("button[data-contract-submit]")?.text())
        assertTrue(text(page.selectFirst(".hint"))!!.contains("ray@gmail.com"))
        assertTrue(text(page.selectFirst(".hint"))!!.contains("7 jours"))
        assertTrue(text(page.selectFirst(".hint"))!!.contains("Ndopify ne perçoit aucun paiement."))
    }

    @Test
    fun `an API-set duration is offered as is`() {
        doReturn(PENDING).whenever(mandateClient).get(201)
        doReturn(CONTRACT.copy(durationMonths = 24)).whenever(contractClient).get("c-201")

        val page = render("/mandates/201/contract")

        assertEquals("24", page.selectFirst("select[name=durationMonths] option[selected]")?.attr("value"))
        assertEquals(13, page.select("select[name=durationMonths] option").size)
    }

    @Test
    fun `a first send has no signature deadline yet`() {
        val page = render("/mandates/201/contract")

        assertTrue(text(page.selectFirst(".hint"))!!.contains("Ndopify ne perçoit aucun paiement."))
        assertTrue(!text(page.selectFirst(".hint"))!!.contains("jours"))
        assertNull(page.selectFirst("[data-duration-error]"))
    }

    @Test
    fun `the errors show under their fields`() {
        withContract()

        val page = render(post("/mandates/201/contract").param("durationMonths", "13").param("visitFee", "abc"))

        assertEquals("Choisissez une durée de 1 à 12 mois.", text(page.selectFirst("[data-duration-error]")))
        assertEquals(
            "Indiquez un montant en chiffres, ou 0 pour aucun frais de visite.",
            text(page.selectFirst("[data-visit-fee-error]")),
        )
        // The agent's answers are kept
        assertEquals("abc", page.selectFirst("input[name=visitFee]")?.attr("value"))
    }
}
