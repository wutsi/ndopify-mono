package com.wutsi.ndopify.esignature.ui.verify

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/verify.html` with a real Thymeleaf engine. */
class VerifyTemplateTest {
    /** Stands for [VerifyController]: puts the given attributes in the model and renders `verify`. */
    @Controller
    class FakeController {
        var attributes: Map<String, Any?> = emptyMap()

        @GetMapping("/verify/{id}/{hash}")
        fun page(model: Model): String {
            attributes.forEach { (key, value) -> model.addAttribute(key, value) }
            return VerifyController.VIEW
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

    private fun render(view: VerifyView, vararg extra: Pair<String, Any?>): Document {
        controller.attributes = mapOf("view" to view) + extra
        return Jsoup.parse(mvc.perform(get("/verify/c-1/abc")).andReturn().response.contentAsString)
    }

    private val hash = "9f2c4e7a1b8d03f65e2a7c9b4d1e8f0a3c6b5d2e9f1a4c7b0d3e6f9a2c5b8e1d"
    private val valid = VerifyView(
        valid = true,
        contractNumber = "MNDT-2026-000042",
        signerName = "Ray Sponsible",
        signedAt = "18 octobre 2026 à 14:32",
        signatureHash = hash,
        signedContentUrl = "https://storage.ndopify.cm/contracts/MNDT-2026-000042-abc.pdf",
    )

    @Test
    fun valid() {
        val page = render(valid)

        assertEquals("Vérification de la signature", page.selectFirst("h1")?.text())
        assertEquals("Cette signature est valide.", page.selectFirst("#valid-title")?.text())
        assertNull(page.selectFirst("#invalid-title"))
        val values = page.select("dl.review dd").map { it.text() }
        assertEquals(listOf("MNDT-2026-000042", "Ray Sponsible", "18 octobre 2026 à 14:32", hash), values)
        assertEquals(hash, page.selectFirst(".hash")?.text())
        assertEquals(valid.signedContentUrl, page.selectFirst("a[download]")?.attr("href"))
    }

    @Test
    fun `valid without pdf`() {
        val page = render(valid.copy(signedContentUrl = null))

        assertNotNull(page.selectFirst("#valid-title"))
        assertNull(page.selectFirst("a[download]"))
        assertNull(page.selectFirst(".cta-row"))
    }

    @Test
    fun invalid() {
        val page = render(VerifyView(valid = false))

        assertEquals("Cette signature n'est pas valide.", page.selectFirst("#invalid-title")?.text())
        assertNull(page.selectFirst("dl.review"))
        assertNull(page.selectFirst("#valid-title"))
        assertNull(page.selectFirst("a[download]"))
    }

    @Test
    fun `tenant logo`() {
        val tenant = Tenant(name = "Ndopify Cameroun", logoUrl = "https://cdn.ndopify.cm/logo.png")

        assertEquals("https://cdn.ndopify.cm/logo.png", render(valid, "tenant" to tenant).selectFirst("header img")?.attr("src"))
        assertEquals("/assets/images/logo.png", render(valid, "tenant" to null).selectFirst("header img")?.attr("src"))
    }

    @Test
    fun `no script`() {
        val page = render(valid)

        assertTrue(page.select("script").isEmpty())
        assertEquals("noindex, nofollow", page.selectFirst("meta[name=robots]")?.attr("content"))
        assertEquals("no-referrer", page.selectFirst("meta[name=referrer]")?.attr("content"))
    }
}
