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
import kotlin.test.assertTrue

/** Renders `templates/owner.html` with a real Thymeleaf engine. */
class OwnerTemplateTest {
    /** Stands for [OwnerController]: puts the given attributes in the model and renders `owner`. */
    @Controller
    class FakeController {
        var attributes: Map<String, Any?> = emptyMap()

        @GetMapping("/owner")
        fun page(model: Model): String {
            attributes.forEach { (key, value) -> model.addAttribute(key, value) }
            return OwnerController.VIEW
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

    private fun render(vararg attributes: Pair<String, Any?>): Document {
        controller.attributes = mapOf(*attributes)
        return Jsoup.parse(mvc.perform(get("/owner")).andReturn().response.contentAsString)
    }

    @Test
    fun `tenant logo and icon`() {
        val page = render("tenant" to tenant, "offerPropertyUrl" to "https://partner.ndopify.cm/offer-property")

        assertEquals("https://cdn.ndopify.cm/logo.png", page.selectFirst("header img")?.attr("src"))
        assertEquals("Ndopify Cameroun", page.selectFirst("header img")?.attr("alt"))
        assertEquals("https://cdn.ndopify.cm/icon.png", page.selectFirst("footer img")?.attr("src"))
        assertEquals("https://cdn.ndopify.cm/icon.png", page.selectFirst("link[rel=icon]")?.attr("href"))
        assertEquals("https://cdn.ndopify.cm/icon.png", page.selectFirst("link[rel=apple-touch-icon]")?.attr("href"))
        assertEquals("https://cdn.ndopify.cm/logo.png", page.selectFirst("meta[property=og:image]")?.attr("content"))
        assertEquals("https://www.ndopify.cm/owner", page.selectFirst("link[rel=canonical]")?.attr("href"))
        // Thymeleaf's JavaScript inlining escapes "/" as "\/", which is still the same JSON string
        val ld = page.selectFirst("script[type=application/ld+json]")!!.data().replace("\\/", "/")
        assertTrue(ld.contains("\"logo\": \"https://cdn.ndopify.cm/logo.png\""))
        assertTrue(ld.contains("\"name\": \"Ndopify Cameroun\""))
    }

    @Test
    fun `Ndopify's logo and icon when the tenant has none`() {
        val page = render("tenant" to tenant.copy(logoUrl = null, iconUrl = ""))

        assertEquals("/assets/images/logo.png", page.selectFirst("header img")?.attr("src"))
        assertEquals("/assets/images/icon.png", page.selectFirst("footer img")?.attr("src"))
        assertEquals("/assets/images/icon.png", page.selectFirst("link[rel=icon]")?.attr("href"))
    }

    @Test
    fun `every call to action opens offer-property`() {
        val page = render("tenant" to tenant, "offerPropertyUrl" to "https://partner.ndopify.cm/offer-property")

        val ctas = page.select(".cta-row a")
        assertEquals(4, ctas.size)
        assertTrue(ctas.all { it.attr("href") == "https://partner.ndopify.cm/offer-property" })
    }

    @Test
    fun `no call to action without offer-property URL`() {
        val page = render("tenant" to tenant)

        assertTrue(page.select(".cta-row").isEmpty())
    }

    @Test
    fun `no link to the prototype`() {
        val page = render("tenant" to tenant, "offerPropertyUrl" to "https://partner.ndopify.cm/offer-property")

        assertTrue(page.select("[href$=.html], [src$=.html]").isEmpty())
    }
}
