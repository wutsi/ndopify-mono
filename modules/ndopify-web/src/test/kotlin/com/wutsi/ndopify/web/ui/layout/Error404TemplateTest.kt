package com.wutsi.ndopify.web.ui.layout

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Renders `templates/error/404.html` with a real Thymeleaf engine, without a tenant. */
class Error404TemplateTest {
    @Controller
    class FakeController {
        @GetMapping("/")
        fun page(): String = "error/404"
    }

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(FakeController())
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

    private fun render(): Document =
        Jsoup.parse(mvc.perform(get("/")).andReturn().response.contentAsString)

    @Test
    fun `Ndopify shell without a tenant`() {
        val page = render()

        assertEquals("Page introuvable — Ndopify", page.title())
        assertEquals("noindex, nofollow", page.selectFirst("meta[name=robots]")?.attr("content"))
        assertEquals("/assets/images/icon.png", page.selectFirst("link[rel=icon]")?.attr("href"))
        assertEquals("/assets/images/apple-touch-icon.png", page.selectFirst("link[rel=apple-touch-icon]")?.attr("href"))
        assertEquals("Ndopify", page.selectFirst("meta[name=apple-mobile-web-app-title]")?.attr("content"))
        assertEquals("/assets/dist/app.min.css", page.selectFirst("link[rel=stylesheet]")?.attr("href"))
        assertEquals(1, page.select("link[rel=stylesheet]").size)
        val script = page.selectFirst("script[src=/assets/dist/app.min.js]")
        assertNotNull(script)
        assertTrue(script.hasAttr("defer"))
        assertEquals(1, page.select("script[src^=/assets/]").size)
        assertEquals("/assets/images/logo.png", page.selectFirst("header img")?.attr("src"))
        assertEquals("Ndopify", page.selectFirst("header img")?.attr("alt"))
        assertEquals("/assets/images/icon.png", page.selectFirst("footer img")?.attr("src"))
        assertTrue(page.select(".site-nav").isEmpty())
        assertTrue(page.select(".site-footer nav").isEmpty())
        assertEquals(
            "© 2026 Ndopify. Logements vérifiés. Locations fiables.",
            page.selectFirst(".site-footer small")?.text(),
        )
    }
}
