package com.wutsi.ndopify.web.ui.home

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

/** Renders `templates/louer.html` (today's renter page, moved to `/louer`) with a real Thymeleaf engine. */
class LouerTemplateTest {
    /** Stands for [HomeController]: puts the given attributes in the model and renders `louer`. */
    @Controller
    class FakeController {
        var attributes: Map<String, Any?> = emptyMap()

        @GetMapping("/louer")
        fun page(model: Model): String {
            attributes.forEach { (key, value) -> model.addAttribute(key, value) }
            return HomeController.RENTER_VIEW
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

    private val findPropertyUrl = "https://partner.ndopify.cm/find-property?type=RENTAL"

    private fun render(vararg attributes: Pair<String, Any?>): Document {
        controller.attributes = mapOf(*attributes)
        return Jsoup.parse(mvc.perform(get("/louer")).andReturn().response.contentAsString)
    }

    @Test
    fun `the renter page keeps its rental wording`() {
        val page = render("tenant" to tenant, "findPropertyUrl" to findPropertyUrl)

        assertEquals("Confiez votre recherche de logement à un agent vérifié", page.selectFirst("h1")?.text())
        assertEquals(
            "Ndopify — Confiez votre recherche de logement à un agent vérifié",
            page.title(),
        )
        assertEquals("https://www.ndopify.cm/louer", page.selectFirst("link[rel=canonical]")?.attr("href"))
        val text = page.text()
        assertTrue(text.contains("1 mois"), text)
        assertTrue(text.contains("à la signature du bail"), text)
        assertTrue(text.contains("Lancer ma recherche"))
    }

    @Test
    fun `every call to action opens find-property`() {
        val page = render("tenant" to tenant, "findPropertyUrl" to findPropertyUrl)

        val ctas = page.select("a.btn-primary")
        assertTrue(ctas.size >= 3, ctas.size.toString())
        assertTrue(ctas.all { it.text() == "Lancer ma recherche" })
        assertTrue(ctas.all { it.attr("href") == findPropertyUrl })
    }

    @Test
    fun `the switch offers the buyer page`() {
        val page = render("tenant" to tenant, "findPropertyUrl" to findPropertyUrl)

        assertEquals(listOf("/louer", "/acheter"), page.select("nav.goal-switch a").map { it.attr("href") })
        assertEquals("Louer", page.selectFirst("nav.goal-switch a[aria-current=page]")?.text())
        assertEquals("Louer", page.selectFirst(".site-nav__list a[aria-current=page]")?.text())
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
