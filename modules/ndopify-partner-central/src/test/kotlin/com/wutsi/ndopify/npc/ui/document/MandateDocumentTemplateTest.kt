package com.wutsi.ndopify.npc.ui.document

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingType
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
            MandateDocumentPage(id = 2001, index = 0, url = "https://s3/page-1?signature=abc"),
            MandateDocumentPage(id = 2002, index = 1, url = "https://s3/page-2?signature=def"),
        ),
        version = 3,
        createdAt = Date.from(Instant.parse("2026-11-25T10:00:00Z")),
        modifiedAt = Date.from(Instant.parse("2026-11-25T10:00:00Z")),
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
                MandateDocumentController(currentAgent, mandateClient, documentClient, tenantContext, clock),
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
        assertEquals("image/jpeg,image/png,image/webp", files.attr("accept"))
        assertTrue(page.text().contains("JPEG, PNG ou WEBP, 5 Mo par page, 10 pages au plus."))
        assertEquals("0 / 100", page.selectFirst("[data-title-counter]")?.text())
        assertEquals("/mandates/301/documents", page.selectFirst("form[data-new-form]")?.attr("action"))
    }

    @Test
    fun `add form - over the multipart limit`() {
        val page = render(get("/mandates/$MANDATE_ID/documents/new").param("error", "too-large"))

        val error = page.selectFirst("#pages-error")!!
        assertFalse(error.hasAttr("hidden"))
        assertEquals(MandateDocumentController.TOO_LARGE, error.selectFirst("[data-error-text]")?.text())
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

        // No image anywhere, and no page path: only the presigned URL of the Voir links
        assertTrue(page.select("[data-document] img").isEmpty())
        assertFalse(page.html().contains("mandate/301/documents/"))
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
            listOf("https://s3/page-1?signature=abc", "https://s3/page-2?signature=def"),
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
        assertTrue(forms[0].text().contains("JPEG, PNG ou WEBP, 5 Mo au plus."))
        assertEquals("file", forms[0].selectFirst("input[type=file]")?.attr("name"))

        assertEquals("3", page.selectFirst("form[data-rename-form] input[name=version]")?.attr("value"))
        assertEquals("Titre foncier n° 4521", page.selectFirst("#documentTitle")?.attr("value"))
        assertEquals("/mandates/301/documents/3103?delete=1", page.selectFirst("[data-delete-open]")?.attr("href"))
        assertTrue(page.selectFirst("[data-delete-confirm]")!!.hasAttr("hidden"))
        assertNull(page.selectFirst("[data-rename-conflict]"))
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
        assertEquals("Choisissez une image.", row.selectFirst("[data-error-text]")?.text())
        assertFalse(page.select(".doc-page")[0].selectFirst("details")!!.hasAttr("open"))
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
