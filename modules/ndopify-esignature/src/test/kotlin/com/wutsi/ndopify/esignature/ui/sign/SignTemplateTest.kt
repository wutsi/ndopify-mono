package com.wutsi.ndopify.esignature.ui.sign

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/sign.html` with a real Thymeleaf engine, to catch template mistakes the controller tests can't see. */
class SignTemplateTest {
    /** Stands for [SignController]: puts the given attributes in the model and renders `sign`. */
    @Controller
    class FakeController {
        var attributes: Map<String, Any?> = emptyMap()

        @GetMapping("/sign/{id}")
        fun page(model: Model): String {
            attributes.forEach { (key, value) -> model.addAttribute(key, value) }
            return SignController.VIEW
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

    private fun render(view: SignView, vararg extra: Pair<String, Any?>): Document {
        controller.attributes = mapOf("view" to view, "contractPartyId" to "cp-1") + extra
        return Jsoup.parse(mvc.perform(get("/sign/cp-1")).andReturn().response.contentAsString)
    }

    private val details = SignView(
        step = SignView.STEP_SIGN,
        contractType = "Mandat de recherche",
        contractNumber = "MNDT-2026-000042",
        senderName = "Marie Ngo Biyong",
        deadline = "25 octobre 2026",
        signerName = "Ray Sponsible",
        signerFirstName = "Ray",
        signDate = "18 octobre 2026",
        contentHtml = "<h3>Mandat</h3><p>Le Mandant confie…</p>",
        canSign = true,
    )

    private fun blocked(reason: String, senderName: String? = "Marie Ngo Biyong") =
        SignView(
            step = SignView.STEP_BLOCKED,
            blocked = reason,
            contractType = "Mandat de recherche",
            contractNumber = "MNDT-2026-000042",
            senderName = senderName,
            deadline = "25 octobre 2026",
        )

    @Test
    fun `intro asks for email and shows no contract`() {
        val page = render(SignView(step = SignView.STEP_INTRO), "email" to "ray@gmail.com")

        val email = page.selectFirst("form[action=/sign/cp-1/otp] input[type=email]")
        assertNotNull(email)
        assertEquals("email", email.attr("autocomplete"))
        assertTrue(email.hasAttr("required"))
        assertEquals("ray@gmail.com", email.attr("value"))
        assertEquals("Adresse e-mail", page.selectFirst("label[for=${email.id()}]")?.text())
        assertEquals("S'authentifier", page.selectFirst("button[type=submit]")?.text())
        assertEquals("Étape 1 sur 3", page.selectFirst("[data-progress-label]")?.text())
        assertFalse(page.html().contains("MNDT-"))
        assertNull(page.selectFirst("dl.review"))
        assertNull(page.selectFirst("[data-step=code]"))
        assertNull(page.selectFirst("[data-previous]"))
        assertTrue(page.text().contains("Ce message ne vous était pas destiné ?"))
    }

    @Test
    fun `intro shows the error`() {
        val page = render(SignView(step = SignView.STEP_INTRO), "error" to "Oups")

        assertEquals("Oups", page.selectFirst("[role=alert]")?.text())
        assertEquals("true", page.selectFirst("input[type=email]")?.attr("aria-invalid"))
    }

    @Test
    fun `code step has one time code field`() {
        val page = render(
            SignView(step = SignView.STEP_CODE, maskedEmail = "ra•••@gmail.com", codeExpiresAt = "14 h 35"),
            "error" to "Code incorrect.",
            "code" to "123456",
        )

        val code = page.selectFirst("form[action=/sign/cp-1/verify] input[name=code]")
        assertNotNull(code)
        assertEquals("one-time-code", code.attr("autocomplete"))
        assertEquals("numeric", code.attr("inputmode"))
        assertFalse(code.hasAttr("maxlength"))
        assertEquals("123456", code.attr("value"))
        assertEquals("true", code.attr("aria-invalid"))
        assertEquals("Code incorrect.", page.selectFirst("#code-error[role=alert]")?.text())
        assertTrue(page.text().contains("ra•••@gmail.com"))
        assertTrue(page.text().contains("14 h 35"))
        assertEquals("Étape 2 sur 3", page.selectFirst("[data-progress-label]")?.text())
        assertEquals("/sign/cp-1", page.selectFirst("a[data-previous]")?.attr("href"))
        assertNull(page.selectFirst("input[type=email]"))
    }

    @Test
    fun `code step resends through its own form`() {
        val page = render(
            SignView(step = SignView.STEP_CODE, maskedEmail = "ra•••@gmail.com", codeExpiresAt = "14 h 35"),
            "resent" to true,
        )

        val form = page.selectFirst("form[action=/sign/cp-1/otp]")
        assertNotNull(form)
        assertEquals("true", form.selectFirst("input[name=resend]")?.attr("value"))
        assertEquals(form.id(), page.selectFirst("button[data-resend]")?.attr("form"))
        assertEquals("Un nouveau code a été envoyé. Il remplace le précédent.", page.selectFirst("[role=status]")?.text())
        assertNull(page.selectFirst("#code-error"))
    }

    @Test
    fun `sign step shows details and content`() {
        val page = render(details)

        assertEquals("Étape 3 sur 3", page.selectFirst("[data-progress-label]")?.text())
        val review = page.select("dl.review").first { it.attr("aria-label") != "Votre signature" }
        assertTrue(review.text().contains("Mandat de recherche"))
        assertTrue(review.text().contains("Marie Ngo Biyong"))
        assertTrue(review.text().contains("MNDT-2026-000042"))
        assertTrue(review.text().contains("25 octobre 2026"))
        assertEquals("Le Mandant confie…", page.selectFirst("article.contract p")?.text())
        assertTrue(page.selectFirst("dl.signature")!!.text().contains("Ray Sponsible"))
        assertTrue(page.selectFirst("dl.signature")!!.text().contains("18 octobre 2026"))

        val form = page.selectFirst("form[action=/sign/cp-1]")
        assertNotNull(form)
        assertEquals("yes", form.selectFirst("input[type=checkbox][name=consent]")?.attr("value"))
        assertEquals("Signer électroniquement", form.selectFirst("button[type=submit]")?.text())
        assertNull(form.selectFirst("button[aria-disabled]"))
    }

    @Test
    fun `sign step omits the sender when unknown and shows the error`() {
        val page = render(details.copy(senderName = null), "error" to "Cochez la case pour signer le contrat.")

        assertFalse(page.text().contains("Envoyé par"))
        assertEquals("Cochez la case pour signer le contrat.", page.select("p[role=alert]").last()?.text())
        assertEquals("true", page.selectFirst("input[name=consent]")?.attr("aria-invalid"))
    }

    @Test
    fun `sign step without content has no button`() {
        val page = render(details.copy(contentHtml = null, canSign = false))

        assertNull(page.selectFirst("article.contract"))
        assertTrue(page.text().contains("Le contrat ne peut pas être affiché pour le moment. Réessayez dans quelques instants."))
        assertNull(page.selectFirst("input[name=consent]"))
        assertNull(page.selectFirst("button[type=submit]"))
    }

    private fun assertBlocked(page: Document, title: String) {
        assertEquals(title, page.selectFirst(".alert__title")?.text())
        assertNull(page.selectFirst("form[action=/sign/cp-1]"))
        assertNull(page.selectFirst("input[name=consent]"))
        assertNull(page.selectFirst(".progress"))
        assertEquals(1, page.select(".alert").size)
    }

    @Test
    fun `signed link`() {
        val page = render(blocked(SignView.BLOCKED_SIGNED))

        assertBlocked(page, "Vous avez déjà signé ce contrat.")
        assertTrue(page.text().contains("Il n'y a rien d'autre à faire."))
        assertFalse(page.text().contains("copie"))
    }

    @Test
    fun `expired link names the agent`() {
        val page = render(blocked(SignView.BLOCKED_EXPIRED))

        assertBlocked(page, "Le délai de signature de ce contrat est dépassé.")
        assertTrue(page.text().contains("Contactez votre agent, Marie Ngo Biyong."))
        assertNull(page.selectFirst("a[href*=wa.me]"))
    }

    @Test
    fun `expired link without sender`() {
        val page = render(blocked(SignView.BLOCKED_EXPIRED, senderName = null))

        assertBlocked(page, "Le délai de signature de ce contrat est dépassé.")
        assertTrue(page.text().contains("Contactez la personne qui vous l'a envoyé."))
    }

    @Test
    fun `cancelled link`() {
        val page = render(blocked(SignView.BLOCKED_CANCELLED))

        assertBlocked(page, "Ce contrat a été annulé.")
        assertTrue(page.text().contains("Contactez votre agent, Marie Ngo Biyong."))
    }

    @Test
    fun `not required link`() {
        val page = render(blocked(SignView.BLOCKED_NOT_REQUIRED))

        assertBlocked(page, "Votre signature n'est pas requise pour ce contrat.")
        assertTrue(page.text().contains("Il n'y a rien à faire de votre côté."))
    }

    @Test
    fun `unavailable link`() {
        val page = render(blocked(SignView.BLOCKED_UNAVAILABLE))

        assertBlocked(page, "Ce contrat n'est pas disponible.")
        assertTrue(page.text().contains("Contactez la personne qui vous l'a envoyé."))
    }

    @Test
    fun `not yours link has the logout form`() {
        val page = render(SignView(step = SignView.STEP_BLOCKED, blocked = SignView.BLOCKED_NOT_YOURS))

        assertEquals("Ce contrat n'a pas été envoyé à cette adresse.", page.selectFirst(".alert__title")?.text())
        assertTrue(page.text().contains("Identifiez-vous avec l'adresse e-mail à laquelle vous avez reçu ce lien."))
        assertEquals("Utiliser une autre adresse", page.selectFirst("form[action=/sign/cp-1/logout] button")?.text())
        assertNull(page.selectFirst("input[name=consent]"))
        assertFalse(page.html().contains("MNDT-"))
    }

    @Test
    fun `done has no copy promise`() {
        val page = render(
            SignView(
                step = SignView.STEP_DONE,
                contractNumber = "MNDT-2026-000042",
                signerFirstName = "Ray",
                signedAt = "18 octobre 2026 à 14 h 32",
            ),
        )

        assertEquals("Votre signature est enregistrée", page.selectFirst("#done-title")?.text())
        assertTrue(page.text().contains("Merci, Ray."))
        assertTrue(page.text().contains("MNDT-2026-000042"))
        assertTrue(page.text().contains("18 octobre 2026 à 14 h 32"))
        assertTrue(page.text().contains("Vous n'avez rien d'autre à faire."))
        assertFalse(page.text().contains("copie"))
        assertNull(page.selectFirst(".progress"))
        assertNull(page.selectFirst("form"))
    }

    @Test
    fun `header has logo only`() {
        val page = render(SignView(step = SignView.STEP_INTRO))

        assertNotNull(page.selectFirst("header img[alt=Ndopify]"))
        assertNull(page.selectFirst("nav"))
        assertFalse(page.html().contains("wa.me"))
    }

    @Test
    fun `text is escaped and the contract is markup`() {
        val page = render(
            details.copy(senderName = "<script>alert(1)</script>", contentHtml = "<p class=\"x\">Contenu</p>"),
            "error" to "<script>alert(2)</script>",
        )

        assertNull(page.selectFirst("script:not([src])"))
        assertTrue(page.text().contains("<script>alert(1)</script>"))
        assertTrue(page.text().contains("<script>alert(2)</script>"))
        assertEquals("Contenu", page.selectFirst("article.contract p.x")?.text())
    }
}
