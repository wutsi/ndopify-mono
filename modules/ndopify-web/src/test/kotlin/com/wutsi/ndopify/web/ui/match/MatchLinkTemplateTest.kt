package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.refdata.dto.Tenant
import org.jsoup.Jsoup
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
import kotlin.test.assertNull

/** Renders `templates/match-link.html` with a real Thymeleaf engine. */
class MatchLinkTemplateTest {
    @Controller
    class FakeController {
        var reason: String = "expired"
        var tenant: Tenant = Tenant()

        @GetMapping("/")
        fun page(model: Model): String {
            model.addAttribute("tenant", tenant)
            model.addAttribute("reason", reason)
            return MatchController.VIEW_LINK
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

    private fun render(reason: String, tenant: Tenant = Tenant(name = "Ndopify Cameroun", logoUrl = "https://cdn.ndopify.cm/logo.png")) =
        controller.let {
            it.reason = reason
            it.tenant = tenant
            Jsoup.parse(mvc.perform(get("/")).andReturn().response.contentAsString)
        }

    @Test
    fun expired() {
        val page = render("expired")

        assertEquals("Ce lien a expiré. Demandez à votre agent de vous renvoyer la proposition.", page.selectFirst("main p")?.text())
        assertEquals("https://cdn.ndopify.cm/logo.png", page.selectFirst("header img")?.attr("src"))
        assertEquals("Ndopify Cameroun", page.selectFirst("header img")?.attr("alt"))
        assertEquals("noindex, nofollow", page.selectFirst("meta[name=robots]")?.attr("content"))
    }

    @Test
    fun notYours() {
        val page = render("not-yours")

        assertEquals("Ce lien a été envoyé à une autre personne.", page.selectFirst("main p")?.text())
    }

    @Test
    fun `Ndopify's logo when the tenant has none`() {
        val page = render("expired", Tenant(logoUrl = null))

        assertEquals("/assets/images/logo.png", page.selectFirst("header img")?.attr("src"))
        assertNull(page.selectFirst("main a[href*=token]"))
    }
}
