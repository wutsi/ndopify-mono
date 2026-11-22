package com.wutsi.ndopify.web.ui.visit

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
import kotlin.test.assertTrue

/** Renders `templates/visit-link.html` with a real Thymeleaf engine. */
class VisitLinkTemplateTest {
    @Controller
    class FakeController {
        var reason: String = "expired"
        var tenant: Tenant = Tenant()

        @GetMapping("/")
        fun page(model: Model): String {
            model.addAttribute("tenant", tenant)
            model.addAttribute("reason", reason)
            return VisitController.VIEW_LINK
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

    private fun render(
        reason: String,
        tenant: Tenant = Tenant(
            name = "Ndopify Cameroun",
            logoUrl = "https://cdn.ndopify.cm/logo.png",
            iconUrl = "https://cdn.ndopify.cm/icon.png",
        ),
    ): Document {
        controller.reason = reason
        controller.tenant = tenant
        return Jsoup.parse(mvc.perform(get("/")).andReturn().response.contentAsString)
    }

    @Test
    fun expired() {
        val page = render("expired")

        assertEquals(
            "Ce lien a expiré. Demandez à votre agent de vous renvoyer le lien de la visite.",
            page.selectFirst("main p")?.text(),
        )
        assertEquals("Votre visite — Ndopify", page.title())
        assertEquals("https://cdn.ndopify.cm/logo.png", page.selectFirst("header img")?.attr("src"))
        assertEquals("Ndopify Cameroun", page.selectFirst("header img")?.attr("alt"))
        assertEquals("https://cdn.ndopify.cm/icon.png", page.selectFirst("link[rel=icon]")?.attr("href"))
        assertEquals("noindex, nofollow", page.selectFirst("meta[name=robots]")?.attr("content"))
        assertTrue(page.select(".site-nav").isEmpty())
        assertTrue(page.select(".site-footer").isEmpty())
    }

    @Test
    fun notYours() {
        val page = render("not-yours")

        assertEquals("Ce lien a été envoyé à une autre personne.", page.selectFirst("main p")?.text())
    }

    @Test
    fun `Ndopify's logo when the tenant has none`() {
        val page = render("expired", Tenant(logoUrl = null, iconUrl = ""))

        assertEquals("/assets/images/logo.png", page.selectFirst("header img")?.attr("src"))
        assertEquals("/assets/images/icon.png", page.selectFirst("link[rel=icon]")?.attr("href"))
    }
}
