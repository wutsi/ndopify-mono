package com.wutsi.ndopify.npc.ui.layout

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Renders the `templates/error/` pages, which Spring Boot's error controller picks by status, with a real Thymeleaf. */
class ErrorTemplateTest {
    /** Stands for Spring Boot's error controller, which renders `error/{status}`. */
    @Controller
    class ErrorPageController {
        @GetMapping("/error-page/{status}")
        fun page(@PathVariable status: Int): String = "error/$status"
    }

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ErrorPageController())
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

    private fun render(status: Int): Document =
        Jsoup.parse(mvc.perform(get("/error-page/$status")).andReturn().response.contentAsString)

    @Test
    fun forbidden() {
        val page = render(403)

        assertEquals("Accès refusé", page.selectFirst("[data-error=403] h1")?.text())
        assertEquals("/", page.selectFirst(".cta-row a")?.attr("href"))
        assertNull(page.selectFirst(".user-menu"))
    }

    @Test
    fun `not found`() {
        val page = render(404)

        assertEquals("Page introuvable", page.selectFirst("[data-error=404] h1")?.text())
        assertEquals("/", page.selectFirst(".cta-row a")?.attr("href"))
        assertNull(page.selectFirst(".user-menu"))
    }
}
