package com.wutsi.ndopify.npc.ui.document

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.DocumentField
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.MandateDocumentPage
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateDocumentClient
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.ScriptFixtures
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.web.core.service.TenantContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/mandate/document-new.html` and `templates/mandate/document.html` with a real Thymeleaf engine. */
class MandateDocumentTemplateTest {
    companion object {
        const val MANDATE_ID = 301L
        const val DOCUMENT_ID = 3103L
    }

    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val documentClient = mock<NdMandateDocumentClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private val labels = DocumentFieldLabels(
        ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
        },
    )
    private lateinit var mvc: MockMvc

    private val mandate = Mandate(
        id = MANDATE_ID,
        status = MandateStatus.APPROVED,
        mandateType = MandateType.LISTING,
        listingType = ListingType.SALE,
    )
    private val document = MandateDocument(
        id = DOCUMENT_ID,
        mandateId = MANDATE_ID,
        type = DocumentType.LAND_TITLE,
        title = "Titre foncier n° 4521",
        status = DocumentStatus.EXTRACTED,
        pages = listOf(
            MandateDocumentPage(id = 2001, index = 0),
            MandateDocumentPage(id = 2002, index = 1),
        ),
        version = 3,
        createdAt = Date.from(Instant.parse("2026-11-25T10:00:00Z")),
        modifiedAt = Date.from(Instant.parse("2026-11-25T10:00:00Z")),
    )

    private val landTitleFields = listOf(
        DocumentField("titleNumber", "4521", 0.97),
        DocumentField("owners", "Rodrigue Essomba, Carine Mbarga", 0.93),
        DocumentField("department", "Wouri", 0.95),
        DocumentField("arrondissement", "Douala 5e", 0.91),
        DocumentField("locality", "Bonapriso", 0.94),
        DocumentField("surface", "500", 0.88),
        DocumentField("issueDate", "2019-06-14", 0.96),
    )

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(TenantFixtures.TENANT).whenever(tenantContext).getTenant()
        doReturn(Date.from(Instant.parse("2026-11-26T10:00:00Z")).time).whenever(clock).millis()
        doReturn(mandate).whenever(mandateClient).get(MANDATE_ID)
        doReturn(document).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val engine = SpringTemplateEngine().apply {
            setTemplateResolver(
                ClassLoaderTemplateResolver().apply {
                    prefix = "templates/"
                    suffix = ".html"
                    characterEncoding = "UTF-8"
                },
            )
        }
        val viewResolver = ThymeleafViewResolver().apply {
            templateEngine = engine
            characterEncoding = "UTF-8"
        }
        mvc = MockMvcBuilders
            .standaloneSetup(
                MandateDocumentController(currentAgent, mandateClient, documentClient, labels, tenantContext, clock),
            )
            .setControllerAdvice(SignedInAdvice(), HttpClientErrorHandler(mock()))
            .setViewResolvers(viewResolver)
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun render(request: org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<*>): Document =
        Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)

    @Test
    fun `add form`() {
        val page = render(get("/mandates/$MANDATE_ID/documents/new"))
        ScriptFixtures.write("mandate-document-new", page)

        assertEquals("Ajouter un document au mandat 301 — Ndopify Partner Central", page.title())
        assertEquals("Ajouter un document", page.selectFirst("h1")?.text())
        assertTrue(page.text().contains("Mandat n° 301"))

        // The suggested types first, then every other type
        val groups = page.select("select[name=type] optgroup")
        assertEquals(listOf("Conseillés pour ce mandat", "Autres types"), groups.map { it.attr("label") })
        assertEquals(
            listOf("NATIONAL_ID", "PASSPORT", "LAND_TITLE", "CERTIFICATE_OF_OWNERSHIP", "SURVEY_PLAN", "POWER_OF_ATTORNEY"),
            groups[0].select("option").map { it.attr("value") },
        )
        assertEquals("OTHER", groups[1].select("option").last()?.attr("value"))
        assertEquals("Titre foncier", groups[0].select("option[value=LAND_TITLE]").text())

        val files = page.selectFirst("input[name=files]")!!
        assertTrue(files.hasAttr("multiple"))
        assertEquals("image/jpeg,image/png,image/webp,application/pdf", files.attr("accept"))
        val hint = page.selectFirst("#pages-hint")!!.text()
        assertTrue(hint.contains("JPEG, PNG, WEBP ou PDF"), hint)
        assertTrue(hint.contains("5 Mo par fichier"), hint)
        assertTrue(hint.contains("Les pages d'un PDF"), hint)
        assertEquals("0 / 100", page.selectFirst("[data-title-counter]")?.text())
        assertEquals("/mandates/301/documents", page.selectFirst("form[data-new-form]")?.attr("action"))

        // With JS: "Prendre une photo" stays images-only, "Choisir des fichiers" takes the PDFs too
        val pickers = page.select("[data-page-input]")
        assertEquals(2, pickers.size)
        assertEquals(
            "image/jpeg,image/png,image/webp",
            page.selectFirst("label:contains(Prendre une photo) input")?.attr("accept"),
        )
        assertEquals(
            "image/jpeg,image/png,image/webp,application/pdf",
            page.selectFirst("label:contains(Choisir des fichiers) input")?.attr("accept"),
        )
        assertEquals("Aucun fichier choisi.", page.selectFirst("[data-pages-count]")?.text())
    }

    @Test
    fun `add form - over the multipart limit`() {
        val page = render(get("/mandates/$MANDATE_ID/documents/new").param("error", "too-large"))

        val error = page.selectFirst("#pages-error")!!
        assertFalse(error.hasAttr("hidden"))
        assertEquals(
            "Ce fichier dépasse 5 Mo : choisissez un fichier plus léger.",
            error.selectFirst("[data-error-text]")?.text(),
        )
    }

    @Test
    fun `add form - the type and the title kept, the error on its field`() {
        val page = Jsoup.parse(
            mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .multipart("/mandates/$MANDATE_ID/documents")
                    .param("type", DocumentType.PASSPORT.name)
                    .param("title", "Mon passeport"),
            ).andReturn().response.contentAsString,
        )

        assertEquals("selected", page.selectFirst("option[value=PASSPORT]")?.attr("selected"))
        assertEquals("Mon passeport", page.selectFirst("input[name=title]")?.attr("value"))
        assertEquals("Ajoutez au moins une page.", page.selectFirst("#pages-error [data-error-text]")?.text())
    }

    @Test
    fun `add form - no meta to keep on a fresh page`() {
        val page = render(get("/mandates/$MANDATE_ID/documents/new"))

        assertNull(page.selectFirst("option[selected]"))
        assertTrue(page.selectFirst("input[name=title]")?.attr("value").isNullOrEmpty())
        assertTrue(page.selectFirst("#pages-error")!!.hasAttr("hidden"))
    }

    @Test
    fun document() {
        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))
        ScriptFixtures.write("mandate-document", page)

        // No image, no storage URL and no page path: only the Partner Central preview route of the Voir links
        assertTrue(page.select("[data-document] img").isEmpty())
        assertFalse(page.html().contains("mandate/301/documents/"))
        assertFalse(page.html().contains("https://s3"))
        assertFalse(page.html().contains("signature"))
        assertFalse(page.html().contains("presigned"))
        assertEquals("Titre foncier n° 4521 — Ndopify Partner Central", page.title())
        assertEquals("Titre foncier n° 4521", page.selectFirst("h1")?.text())
        assertEquals("Analysé", page.selectFirst("[data-document-status]")?.text()?.replace("Statut : ", ""))
        assertEquals("Titre foncier", page.selectFirst("[data-document-type]")?.text())
        assertTrue(page.selectFirst("[data-document-meta]")!!.text().contains("Ajouté le 25 nov. 2026"))
        assertTrue(page.selectFirst("[data-document-meta]")!!.text().startsWith("Titre foncier · "))

        val rows = page.select(".doc-page")
        assertEquals(listOf("Page 1", "Page 2"), rows.map { it.selectFirst("[data-page-caption]")?.text() })
        val links = rows.map { it.selectFirst("a.doc-page__view") }
        assertEquals(
            listOf(
                "/mandates/301/documents/3103/pages/2001/preview",
                "/mandates/301/documents/3103/pages/2002/preview",
            ),
            links.map { it?.attr("href") },
        )
        assertEquals(listOf("_blank", "_blank"), links.map { it?.attr("target") })
        assertEquals(listOf("noopener", "noopener"), links.map { it?.attr("rel") })

        val forms = page.select(".doc-page form")
        assertEquals(2, forms.size)
        assertEquals(
            "/mandates/301/documents/3103/pages/2001",
            forms[0].attr("action"),
        )
        assertEquals(
            "Photo JPEG, PNG ou WEBP, ou PDF d'une page, 5 Mo au plus. Le fichier prend la place de cette page.",
            forms[0].selectFirst(".field__hint")?.text(),
        )
        assertEquals(
            "Nouveau fichier de la page 1",
            forms[0].selectFirst("label.field__label")?.text(),
        )
        assertEquals("image/jpeg,image/png,image/webp,application/pdf", forms[0].selectFirst("input[type=file]")?.attr("accept"))
        assertEquals("file", forms[0].selectFirst("input[type=file]")?.attr("name"))

        assertEquals("3", page.selectFirst("form[data-rename-form] input[name=version]")?.attr("value"))
        assertEquals("Titre foncier n° 4521", page.selectFirst("#documentTitle")?.attr("value"))
        assertEquals("/mandates/301/documents/3103?delete=1", page.selectFirst("[data-delete-open]")?.attr("href"))
        assertTrue(page.selectFirst("[data-delete-confirm]")!!.hasAttr("hidden"))
        assertNull(page.selectFirst("[data-rename-conflict]"))
    }

    @Test
    fun `document - nothing names the AI`() {
        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))

        assertEquals(
            "Un document d'un mandat : ce qui en a été lu, ses problèmes, ses pages.",
            page.selectFirst("meta[name=description]")?.attr("content"),
        )
        val html = page.html()
        assertFalse(Regex("""\bIA\b""").containsMatchIn(html), "the page names the AI")
        assertFalse(Regex("""\bAI\b""").containsMatchIn(html), "the page names the AI")
        assertFalse(html.contains("intelligence artificielle", ignoreCase = true), "the page names the AI")
    }

    @Test
    fun `document - no image, one upload date, no size`() {
        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))

        assertEquals(1, Regex("Ajouté le").findAll(page.text()).count())
        assertEquals(2, page.select("[data-page-caption]").size)
    }

    @Test
    fun `document - the delete confirmation open without JS`() {
        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID").param("delete", "1"))

        val panel = page.selectFirst("[data-delete-confirm]")!!
        assertTrue(panel.text().contains("Supprimer ce document ?"))
        assertTrue(panel.text().contains("Ses 2 pages sont supprimées"))
        assertEquals("/mandates/301/documents/3103/delete", panel.selectFirst("form")?.attr("action"))
        assertEquals("true", page.selectFirst("[data-delete-open]")?.attr("aria-expanded"))
    }

    @Test
    fun `document - renamed`() {
        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID").flashAttr("outcome", "renamed"))

        assertEquals("Titre du document enregistré.", page.selectFirst("[data-document-flash]")?.text())
        assertEquals("alert alert--success", page.selectFirst("[data-document-flash]")?.className())
    }

    @Test
    fun `document - an action that couldn't run`() {
        val page = render(
            get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID").flashAttr("outcome", "error")
                .flashAttr("outcomeMessage", "Le titre n'a pas pu être enregistré. Réessayez dans quelques instants."),
        )

        assertEquals("alert alert--error", page.selectFirst("[data-document-flash]")?.className())
        assertEquals(
            "Le titre n'a pas pu être enregistré. Réessayez dans quelques instants.",
            page.selectFirst("[data-document-flash]")?.text(),
        )
    }

    @Test
    fun `document - a page replaced`() {
        val page = render(
            get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")
                .flashAttr("outcome", "replaced")
                .flashAttr("page", 2),
        )

        assertEquals("Page 2 remplacée.", page.selectFirst("[data-document-flash]")?.text())
    }

    @Test
    fun `document - the conflict shows the current title`() {
        doThrow(conflict()).whenever(documentClient).rename(any(), any(), any())

        val page = Jsoup.parse(
            mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename")
                    .param("title", "Mon titre")
                    .param("version", "2"),
            ).andReturn().response.contentAsString,
        )

        assertNotNull(page.selectFirst("[data-rename-conflict]"))
        assertEquals("Titre foncier n° 4521", page.selectFirst("#documentTitle")?.attr("value"))
        assertEquals("3", page.selectFirst("form[data-rename-form] input[name=version]")?.attr("value"))
    }

    @Test
    fun `document - a replacement error on its page`() {
        val page = Jsoup.parse(
            mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/2002"),
            ).andReturn().response.contentAsString,
        )

        val row = page.select(".doc-page")[1]
        assertEquals("open", row.selectFirst("details")?.attr("open"))
        assertEquals("Choisissez la nouvelle page : une photo ou un PDF.", row.selectFirst("[data-error-text]")?.text())
        assertFalse(page.select(".doc-page")[0].selectFirst("details")!!.hasAttr("open"))
    }

    @Test
    fun `document - the fields the AI read`() {
        doReturn(document.copy(fields = landTitleFields)).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))
        val reading = page.selectFirst("[data-document-reading]")!!

        assertEquals("Données lues", reading.selectFirst("h2")?.text())
        assertTrue(reading.select(".alert--warning").isEmpty())
        assertTrue(reading.select(".hint").isEmpty())
        assertEquals(
            listOf(
                "Numéro du titre",
                "Propriétaires",
                "Département",
                "Arrondissement",
                "Localité",
                "Surface",
                "Date de délivrance",
            ),
            reading.select(".doc-data .review__term").map { it.text() },
        )
        assertEquals(
            listOf("4521", "Rodrigue Essomba, Carine Mbarga", "Wouri", "Douala 5e", "Bonapriso", "500 m²", "14 juin 2019"),
            reading.select(".doc-data .review__value").map { it.text().replace('\u00A0', ' ').replace('\u202F', ' ') },
        )
        assertTrue(
            reading.selectFirst(".doc-reading__intro")!!.text().replace('\u00A0', ' ')
                .startsWith("Lues automatiquement sur la pièce : vérifiez-les sur l'original."),
        )
        // No confidence, no raw JSON
        assertFalse(reading.text().contains("0.9"))
        assertFalse(page.html().contains("\"confidence\""))
    }

    @Test
    fun `document - an unknown key and the amounts' currency`() {
        doReturn(
            document.copy(
                fields = listOf(
                    DocumentField("currency", "XAF"),
                    DocumentField("amount", "12500000"),
                    DocumentField("position", "Chargée de clientèle"),
                ),
            ),
        ).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val reading = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).selectFirst("[data-document-reading]")!!

        assertEquals(listOf("Montant", "Autre donnée"), reading.select(".doc-data .review__term").map { it.text() })
        assertEquals(
            listOf("12,5M FCFA", "Chargée de clientèle"),
            reading.select(".doc-data .review__value").map { it.text().replace('\u00A0', ' ') },
        )
    }

    @Test
    fun `document - waiting for the reading`() {
        for ((status, text) in listOf(
            DocumentStatus.UPLOADED to
                "Le document est reçu : sa lecture va commencer. Rien à faire, elle se lance d'elle-même ; ses données " +
                "apparaîtront ici.",
            DocumentStatus.EXTRACTING to
                "La lecture du document est en cours. Ses données apparaîtront ici dès qu'elle sera terminée : rien à faire.",
        )) {
            doReturn(document.copy(status = status, fields = emptyList())).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

            val reading = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).selectFirst("[data-document-reading]")!!

            assertEquals(text, reading.selectFirst("[data-reading-waiting]")?.text())
            assertNull(reading.selectFirst(".doc-data"))
            assertNull(reading.selectFirst(".doc-problems"))
        }
    }

    @Test
    fun `document - an extracted document without a field says no data was read`() {
        doReturn(document.copy(fields = emptyList())).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))
        val reading = page.selectFirst("[data-document-reading]")!!

        assertTrue(
            reading.selectFirst("[data-reading-empty]")!!.text().replace('\u00A0', ' ')
                .startsWith("Aucune donnée n'a été lue sur ce document. Vérifiez la pièce elle-même : elle reste consultable."),
        )
        assertNull(reading.selectFirst(".doc-data"))
        assertEquals("Analysé", page.selectFirst("[data-document-status]")?.text()?.replace("Statut : ", ""))
    }

    @Test
    fun `document - a failed reading shows one instruction per code`() {
        doReturn(
            document.copy(
                status = DocumentStatus.FAILED,
                fields = emptyList(),
                problemCodes = listOf("DOCUMENT_PAGE_MISSING", "DOCUMENT_FIELD_LOW_CONFIDENCE"),
            ),
        ).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val reading = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).selectFirst("[data-document-reading]")!!

        assertEquals(
            listOf(
                "Il manque une page du document, son verso par exemple. Remplacez la page manquante par une photo, ou " +
                    "supprimez le document et ajoutez-le avec toutes ses pages.",
                "Un champ obligatoire n'a pas pu être lu avec certitude. Vérifiez-le sur la pièce : si la photo est " +
                    "floue, remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau.",
            ),
            reading.select(".doc-problems li").map { it.text() },
        )
        assertNull(reading.selectFirst(".doc-data"))
    }

    @Test
    fun `document - DOCUMENT_EXPIRED is the notice, never an instruction`() {
        doReturn(
            document.copy(
                problemCodes = listOf("DOCUMENT_EXPIRED"),
                expiresAt = LocalDate.of(2025, 1, 12),
            ),
        ).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val reading = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).selectFirst("[data-document-reading]")!!

        assertNull(reading.selectFirst(".doc-problems"))
        assertNotNull(reading.selectFirst("[data-reading-expired]"))
    }

    @Test
    fun `document - an expired piece shows the notice and its way out`() {
        doReturn(document.copy(problemCodes = listOf("DOCUMENT_EXPIRED"), expiresAt = LocalDate.of(2025, 1, 12)))
            .whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))
        val notice = page.selectFirst("[data-reading-expired]")!!

        assertEquals("Pièce expirée le 12 janv. 2025", notice.selectFirst("[data-expired-title]")?.text())
        assertEquals("Elle était déjà expirée lors de sa lecture.", notice.selectFirst("[data-expired-when]")?.text())
        assertTrue(
            notice.selectFirst("[data-expired-action]")!!.text().replace('\u00A0', ' ')
                .startsWith("Ajoutez une pièce en cours de validité : remplacez ses pages par celles de la nouvelle pièce"),
        )
        // The status pill stays Analysé: an expiry is not a status
        assertEquals("Analysé", page.selectFirst("[data-document-status]")?.text()?.replace("Statut : ", ""))
    }

    @Test
    fun `document - expired since its reading`() {
        doReturn(document.copy(expiresAt = LocalDate.of(2025, 1, 12))).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val notice = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).selectFirst("[data-reading-expired]")!!

        assertEquals("Elle a expiré depuis sa lecture.", notice.selectFirst("[data-expired-when]")?.text())
    }

    @Test
    fun `document - no notice when the piece is valid or has no expiry`() {
        for (expiresAt in listOf(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 1), null)) {
            doReturn(document.copy(expiresAt = expiresAt)).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

            val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))

            assertNull(page.selectFirst("[data-reading-expired]"), "$expiresAt")
        }
    }

    @Test
    fun `document - a cancelled mandate keeps the notice readable, without a way out`() {
        doReturn(mandate.copy(status = MandateStatus.CANCELLED)).whenever(mandateClient).get(MANDATE_ID)
        doReturn(document.copy(fields = landTitleFields, expiresAt = LocalDate.of(2025, 1, 12)))
            .whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))

        assertEquals(
            "Mandat annulé : cette pièce reste consultable telle quelle.",
            page.selectFirst("[data-expired-action]")?.text(),
        )
        assertEquals(7, page.select(".doc-data .review__item").size)
        assertTrue(page.select(".doc-page details").isEmpty())
        assertNull(page.selectFirst("[data-document-manage]"))
    }

    private fun conflict(): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction {
            com.wutsi.ndopify.error.dto.ErrorResponse(
                com.wutsi.ndopify.error.dto.Error(code = com.wutsi.ndopify.error.dto.ErrorCode.MANDATE_DOCUMENT_VERSION_CONFLICT),
            )
        }
        return ex
    }

    @Test
    fun `document - read-only on a closed mandate`() {
        doReturn(mandate.copy(status = MandateStatus.CANCELLED)).whenever(mandateClient).get(MANDATE_ID)

        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))

        assertNotNull(page.selectFirst("[data-document-readonly]"))
        assertEquals(2, page.select("a.doc-page__view").size)
        assertTrue(page.select(".doc-page details").isEmpty())
        assertNull(page.selectFirst("[data-document-manage]"))
        assertTrue(page.select("[data-document] form").isEmpty())
    }
}
