package com.wutsi.ndopify.web.ui.buyer

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders `templates/acheter.html` (the buyer landing page) with a real Thymeleaf engine. */
class BuyerTemplateTest {
    /** Stands for [BuyerController]: puts the given attributes in the model and renders `acheter`. */
    @Controller
    class FakeController {
        var attributes: Map<String, Any?> = emptyMap()

        @GetMapping("/acheter")
        fun page(model: Model): String {
            attributes.forEach { (key, value) -> model.addAttribute(key, value) }
            return BuyerController.VIEW
        }
    }

    private val controller = FakeController()
    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setViewResolvers(
            ThymeleafViewResolver().apply {
                templateEngine = SpringTemplateEngine().apply {
                    setTemplateResolver(
                        ClassLoaderTemplateResolver().apply {
                            prefix = "templates/"
                            suffix = ".html"
                            characterEncoding = "UTF-8"
                        },
                    )
                }
                characterEncoding = "UTF-8"
            },
        )
        .build()

    private val tenant = Tenant(
        id = 1,
        name = "Ndopify Cameroun",
        logoUrl = "https://cdn.ndopify.cm/logo.png",
        iconUrl = "https://cdn.ndopify.cm/icon.png",
        partnerCentralUrl = "https://partner.ndopify.cm",
        publicPortalUrl = "https://www.ndopify.cm",
    )

    private val findPropertyUrl = "https://partner.ndopify.cm/find-property?type=SALE"

    /** A tenant whose money is not in FCFA: the proof lines must follow it, never a hard-coded currency. */
    private val euroTenant = tenant.copy(
        currencyCode = "EUR",
        currencySymbol = "EUR",
        monetaryFormat = "#,###,##0 EUR",
        numberFormat = "#,###,##0",
    )

    private val zeroAmount = TenantFormatter(euroTenant).money(0)

    private fun render(vararg attributes: Pair<String, Any?>): Document {
        controller.attributes = mapOf(*attributes)
        return Jsoup.parse(mvc.perform(get("/acheter")).andReturn().response.contentAsString)
    }

    @Test
    fun `the page sells a verified agent to a buyer`() {
        val page = render("tenant" to tenant, "findPropertyUrl" to findPropertyUrl, "zeroAmount" to zeroAmount)

        assertEquals("Confiez votre projet d'achat à un agent vérifié", page.selectFirst("h1")?.text())
        assertEquals("Ndopify — Confiez votre projet d'achat à un agent vérifié", page.title())
        assertTrue(
            page.selectFirst("meta[name=description]")!!.attr("content")
                .startsWith("Maison, appartement ou terrain : décrivez votre projet d'achat"),
        )
        assertEquals("https://www.ndopify.cm/acheter", page.selectFirst("link[rel=canonical]")?.attr("href"))
        assertEquals("Acheter", page.selectFirst(".site-nav__list a[aria-current=page]")?.text())
        assertEquals(listOf("/louer", "/acheter"), page.select("nav.goal-switch a").map { it.attr("href") })
    }

    @Test
    fun `the proof lines carry the tenant's formatted zero`() {
        val page = render("tenant" to euroTenant, "findPropertyUrl" to findPropertyUrl, "zeroAmount" to zeroAmount)

        assertEquals("0\u00A0EUR", zeroAmount)
        assertEquals(listOf("100 %", "0 EUR", "Sur place"), page.select(".proof-stats strong").map { it.text() })
        assertFalse(page.text().contains("0 FCFA"))
    }

    @Test
    fun `every call to action opens the sale wizard`() {
        val page = render("tenant" to tenant, "findPropertyUrl" to findPropertyUrl, "zeroAmount" to zeroAmount)

        val ctas = page.select("a.btn-primary")
        assertTrue(ctas.size >= 3, ctas.size.toString())
        assertTrue(ctas.all { it.text() == "Lancer ma recherche" })
        assertTrue(ctas.all { it.attr("href") == findPropertyUrl })
        assertTrue(ctas.all { it.attr("href").endsWith("/find-property?type=SALE") })
    }

    @Test
    fun `the page promises only what holds`() {
        val text = render("tenant" to tenant, "findPropertyUrl" to findPropertyUrl).text()

        listOf(
            "un même agent traite les locations et les ventes",
            "un pourcentage du prix de vente",
            "Ndopify vérifie l'agent, pas les documents du bien",
            "Ndopify ne perçoit aucun paiement",
        ).forEach { assertTrue(text.contains(it), it) }
        listOf("affichés nulle part", "spécialiste", "off-market", "catalogue")
            .forEach { assertFalse(text.contains(it), it) }
    }

    @Test
    fun `no call to action without find-property URL`() {
        val page = render("tenant" to tenant)

        assertTrue(page.select(".cta-row").isEmpty())
        assertFalse(page.text().contains("Lancer ma recherche"))
    }

    @Test
    fun `no link to the prototype`() {
        val page = render("tenant" to tenant, "findPropertyUrl" to findPropertyUrl)

        assertTrue(page.select("[href$=.html], [src$=.html]").isEmpty())
    }
}
