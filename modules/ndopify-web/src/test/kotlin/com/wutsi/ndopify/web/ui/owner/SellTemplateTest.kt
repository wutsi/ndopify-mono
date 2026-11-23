package com.wutsi.ndopify.web.ui.owner

import com.wutsi.ndopify.refdata.dto.Tenant
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

/** Renders `templates/owner/sell.html` (the seller landing page) with a real Thymeleaf engine. */
class SellTemplateTest {
    /** Stands for [OwnerController]: puts the given attributes in the model and renders `owner/sell`. */
    @Controller
    class FakeController {
        var attributes: Map<String, Any?> = emptyMap()

        @GetMapping("/owner/sell")
        fun page(model: Model): String {
            attributes.forEach { (key, value) -> model.addAttribute(key, value) }
            return OwnerController.SELL_VIEW
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

    private val offerPropertyUrl = "https://partner.ndopify.cm/offer-property?type=SALE"

    private fun render(vararg attributes: Pair<String, Any?>): Document {
        controller.attributes = mapOf(*attributes)
        return Jsoup.parse(mvc.perform(get("/owner/sell")).andReturn().response.contentAsString)
    }

    @Test
    fun `the page sells a verified agent to a seller`() {
        val page = render("tenant" to tenant, "offerPropertyUrl" to offerPropertyUrl)

        assertEquals("Confiez la vente de votre bien à un agent vérifié", page.selectFirst("h1")?.text())
        assertEquals("Ndopify — Confiez la vente de votre bien à un agent vérifié", page.title())
        assertTrue(
            page.selectFirst("meta[name=description]")!!.attr("content")
                .startsWith("Maison, appartement ou terrain : proposez votre bien"),
        )
        assertEquals("https://www.ndopify.cm/owner/sell", page.selectFirst("link[rel=canonical]")?.attr("href"))
        assertEquals("Propriétaires", page.selectFirst(".site-nav__list a[aria-current=page]")?.text())
        assertEquals(listOf("/owner", "/owner/sell"), page.select("nav.goal-switch a").map { it.attr("href") })
        assertEquals("Vendre", page.selectFirst("nav.goal-switch a[aria-current=page]")?.text())
    }

    @Test
    fun `every call to action opens the sale wizard`() {
        val page = render("tenant" to tenant, "offerPropertyUrl" to offerPropertyUrl)

        val ctas = page.select("a.btn-primary")
        assertTrue(ctas.size >= 3, ctas.size.toString())
        assertTrue(ctas.all { it.text() == "Proposer mon bien" })
        assertTrue(ctas.all { it.attr("href") == offerPropertyUrl })
        assertTrue(ctas.all { it.attr("href").endsWith("/offer-property?type=SALE") })
    }

    @Test
    fun `the page promises only what holds`() {
        val text = render("tenant" to tenant, "offerPropertyUrl" to offerPropertyUrl).text()

        listOf(
            "du prix de vente versé directement à vous",
            "un pourcentage du prix de vente, écrit dans le mandat",
            "chez le notaire",
            "Ndopify ne les contrôle pas",
            "Ndopify ne perçoit aucun paiement",
        ).forEach { assertTrue(text.contains(it), it) }
        listOf("spécialiste", "titre foncier vérifié").forEach { assertFalse(text.contains(it), it) }
    }

    @Test
    fun `no call to action without offer-property URL`() {
        val page = render("tenant" to tenant)

        assertTrue(page.select(".cta-row").isEmpty())
        assertFalse(page.text().contains("Proposer mon bien"))
    }

    @Test
    fun `no link to the prototype`() {
        val page = render("tenant" to tenant, "offerPropertyUrl" to offerPropertyUrl)

        assertTrue(page.select("[href$=.html], [src$=.html]").isEmpty())
    }
}
