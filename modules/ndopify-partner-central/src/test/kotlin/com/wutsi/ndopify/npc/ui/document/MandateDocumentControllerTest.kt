package com.wutsi.ndopify.npc.ui.document

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.DocumentField
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.MandateDocumentPage
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.UpdateMandateDocumentRequest
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateDocumentClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateDocumentClient.UploadedPage
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.Instant
import java.util.Date
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateDocumentControllerTest {
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
    private val page1 = MandateDocumentPage(id = 2001, index = 0)
    private val page2 = MandateDocumentPage(id = 2002, index = 1)
    private val document = MandateDocument(
        id = DOCUMENT_ID,
        mandateId = MANDATE_ID,
        type = DocumentType.LAND_TITLE,
        title = "Titre foncier n° 4521",
        status = DocumentStatus.EXTRACTED,
        pages = listOf(page1, page2),
        version = 3,
        createdAt = Date.from(Instant.parse("2026-11-25T10:00:00Z")),
        modifiedAt = Date.from(Instant.parse("2026-11-25T10:00:00Z")),
    )

    // Magic numbers are enough for Tika to tell the type from the bytes.
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10) + ByteArray(64)

    @BeforeEach
    fun setUp() {
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(TenantFixtures.TENANT).whenever(tenantContext).getTenant()
        doReturn(Date.from(Instant.parse("2026-11-26T10:00:00Z")).time).whenever(clock).millis()
        doReturn(mandate).whenever(mandateClient).get(MANDATE_ID)
        doReturn(mandate.copy(status = MandateStatus.CANCELLED)).whenever(mandateClient).get(311)
        doReturn(document).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        mvc = MockMvcBuilders.standaloneSetup(
            MandateDocumentController(currentAgent, mandateClient, documentClient, labels, tenantContext, clock),
        )
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun photo(bytes: ByteArray = jpeg, name: String = "IMG_2041.jpg") =
        MockMultipartFile("files", name, "image/jpeg", bytes)

    private fun replacement(bytes: ByteArray = jpeg) = MockMultipartFile("file", "page.jpg", "image/jpeg", bytes)

    private fun serverError(status: HttpStatus, code: String = "", parameter: String? = null): HttpClientErrorException {
        val ex = HttpClientErrorException(status)
        ex.setBodyConvertFunction {
            ErrorResponse(Error(code = code, parameter = parameter?.let { Parameter(name = it) }))
        }
        return ex
    }

    @Test
    fun `add form`() {
        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/new")).andReturn()

        assertEquals(MandateDocumentController.VIEW_NEW, result.modelAndView?.viewName)
        val model = model(result)
        assertEquals(
            listOf(
                DocumentType.NATIONAL_ID,
                DocumentType.PASSPORT,
                DocumentType.LAND_TITLE,
                DocumentType.CERTIFICATE_OF_OWNERSHIP,
                DocumentType.SURVEY_PLAN,
                DocumentType.POWER_OF_ATTORNEY,
            ),
            model["suggested"],
        )
        assertTrue((model["others"] as List<*>).last() == DocumentType.OTHER)
        assertEquals(AddDocumentForm(), model["form"])
        assertEquals(emptyMap<String, String>(), model["errors"])
        assertEquals(MANDATE_ID, (model["mandate"] as com.wutsi.ndopify.npc.ui.mandate.MandateView).id)
    }

    @Test
    fun `add - a 5 Mo page shows the size error`() {
        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents")
                    .file(photo(jpeg + ByteArray(5 * 1024 * 1024)))
                    .param("type", DocumentType.LAND_TITLE.name)
                    .param("title", "Titre foncier lot 12"),
            )
            .andReturn()

        assertEquals(
            "« IMG_2041.jpg » dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère.",
            (model(result)["errors"] as Map<*, *>)["files"],
        )
        assertEquals("Titre foncier lot 12", (model(result)["form"] as AddDocumentForm).title)
    }

    @Test
    fun `add form - closed mandate - changed`() {
        val result = mvc.perform(get("/mandates/311/documents/new")).andReturn()

        assertEquals("redirect:/mandates/311", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `add form - another agent's mandate - changed`() {
        doThrow(HttpClientErrorException.Forbidden::class).whenever(mandateClient).get(MANDATE_ID)

        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/new")).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `add - redirects to the mandate page with its title`() {
        doReturn(42L).whenever(documentClient).upload(any(), any(), any(), any())

        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents")
                    .file(photo())
                    .file(photo(name = "verso.png"))
                    .param("type", DocumentType.LAND_TITLE.name)
                    .param("title", "  Titre foncier lot 12  "),
            )
            .andReturn()

        val pages = argumentCaptor<List<UploadedPage>>()
        verify(documentClient).upload(any(), any(), any(), pages.capture())
        assertEquals(listOf("IMG_2041.jpg", "verso.png"), pages.firstValue.map { it.filename })
        assertEquals("image/jpeg", pages.firstValue.first().mimeType)
        assertEquals("redirect:/mandates/$MANDATE_ID#documents-title", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.ADDED, result.flashes()["documentOutcome"])
        assertEquals("Titre foncier lot 12", result.flashes()["documentTitle"])
    }

    @Test
    fun `add - a blank title takes the type's name back`() {
        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents")
                    .file(photo())
                    .param("type", DocumentType.PASSPORT.name)
                    .param("title", "   "),
            )
            .andReturn()

        verify(documentClient).upload(eq(MANDATE_ID), eq(DocumentType.PASSPORT), isNull(), any())
        assertEquals("Passeport", result.flashes()["documentTitle"])
    }

    @Test
    fun `add - no type - the form again, the title kept`() {
        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents").file(photo()).param("title", "Titre foncier"))
            .andReturn()

        assertEquals(MandateDocumentController.VIEW_NEW, result.modelAndView?.viewName)
        assertEquals(mapOf("type" to "Choisissez le type de document."), model(result)["errors"])
        assertEquals(AddDocumentForm(type = "", title = "Titre foncier"), model(result)["form"])
        verify(documentClient, never()).upload(any(), any(), any(), any())
    }

    @Test
    fun `add - no page`() {
        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents").param("type", DocumentType.PASSPORT.name))
            .andReturn()

        assertEquals(mapOf("files" to "Ajoutez au moins une page."), model(result)["errors"])
    }

    @Test
    fun `add - eleven pages`() {
        val request = multipart("/mandates/$MANDATE_ID/documents").param("type", DocumentType.PASSPORT.name)
        repeat(11) { request.file(photo(name = "p$it.jpg")) }

        val result = mvc.perform(request).andReturn()

        assertEquals(mapOf("files" to "10 pages au plus."), model(result)["errors"])
    }

    @Test
    fun `add - a title over 100 characters`() {
        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents")
                    .file(photo())
                    .param("type", DocumentType.PASSPORT.name)
                    .param("title", "a".repeat(101)),
            )
            .andReturn()

        assertEquals(mapOf("title" to "100 caractères au plus."), model(result)["errors"])
    }

    @Test
    fun `add - a pdf is sent with its mime type`() {
        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents")
                    .file(MockMultipartFile("files", "scan.pdf", "application/pdf", pdf()))
                    .param("type", DocumentType.PASSPORT.name),
            )
            .andReturn()

        val pages = argumentCaptor<List<UploadedPage>>()
        verify(documentClient).upload(any(), any(), anyOrNull(), pages.capture())
        assertEquals("application/pdf", pages.firstValue.single().mimeType)
        assertEquals("scan.pdf", pages.firstValue.single().filename)
        assertEquals("redirect:/mandates/$MANDATE_ID#documents-title", result.modelAndView?.viewName)
    }

    @Test
    fun `add - a pdf over 5 Mo shows the field error and calls nothing`() {
        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents")
                    .file(MockMultipartFile("files", "scan.pdf", "application/pdf", pdf() + ByteArray(5 * 1024 * 1024)))
                    .param("type", DocumentType.PASSPORT.name),
            )
            .andReturn()

        assertEquals(
            mapOf("files" to "« scan.pdf » dépasse 5 Mo : choisissez un PDF plus léger, ou envoyez ses pages en photos."),
            model(result)["errors"],
        )
        verify(documentClient, never()).upload(any(), any(), anyOrNull(), any())
    }

    @Test
    fun `add - the server's pdf codes map to the french pages error`() {
        val messages = mapOf(
            ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED to
                "Un PDF est protégé par un mot de passe : enregistrez-le sans protection, ou envoyez ses pages en " +
                "photos. Choisissez de nouveau les pages.",
            ErrorCode.MANDATE_DOCUMENT_PDF_UNREADABLE to
                "Un PDF est vide ou endommagé : envoyez ses pages en photos. Choisissez de nouveau les pages.",
            ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES to
                "Un PDF fait plus de 10 pages : envoyez ses pages en photos, 10 au plus, ou un PDF plus court. " +
                "Choisissez de nouveau les pages.",
        )
        for ((code, message) in messages) {
            val ex = HttpClientErrorException(HttpStatus.BAD_REQUEST)
            ex.setBodyConvertFunction { ErrorResponse(Error(code = code, parameter = Parameter(name = "file"))) }
            doThrow(ex).whenever(documentClient).upload(any(), any(), anyOrNull(), any())

            val result = mvc
                .perform(
                    multipart("/mandates/$MANDATE_ID/documents")
                        .file(MockMultipartFile("files", "scan.pdf", "application/pdf", pdf()))
                        .param("type", DocumentType.PASSPORT.name)
                        .param("title", "Titre"),
                )
                .andReturn()

            assertEquals(MandateDocumentController.VIEW_NEW, result.modelAndView?.viewName)
            // A `file` parameter always answers on the form's own field, the pages
            assertEquals(mapOf("files" to message), model(result)["errors"], code)
            assertEquals("Titre", (model(result)["form"] as AddDocumentForm).title)
        }
    }

    private fun pdf(): ByteArray = "%PDF-1.4\n".toByteArray() + ByteArray(64)

    @Test
    fun `add - over the multipart limit - the size error`() {
        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/new").param("error", "too-large")).andReturn()

        assertEquals(MandateDocumentController.VIEW_NEW, result.modelAndView?.viewName)
        assertEquals(mapOf("files" to MandateDocumentController.TOO_LARGE), model(result)["errors"])
    }

    @Test
    fun `add - a server 400 on a field`() {
        doThrow(serverError(HttpStatus.BAD_REQUEST, ErrorCode.HTTP_INVALID_PARAMETER, "title"))
            .whenever(documentClient).upload(any(), any(), any(), any())

        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents")
                    .file(photo())
                    .param("type", DocumentType.PASSPORT.name)
                    .param("title", "Titre"),
            )
            .andReturn()

        assertEquals(MandateDocumentController.VIEW_NEW, result.modelAndView?.viewName)
        assertNotNull((model(result)["errors"] as Map<*, *>)["title"])
        assertEquals("Titre", (model(result)["form"] as AddDocumentForm).title)
    }

    @Test
    fun `add - a server 400 without a field is on the pages`() {
        doThrow(serverError(HttpStatus.BAD_REQUEST, ErrorCode.HTTP_INVALID_PARAMETER, "file"))
            .whenever(documentClient).upload(any(), any(), anyOrNull(), any())

        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents").file(photo()).param("type", DocumentType.PASSPORT.name))
            .andReturn()

        assertNotNull((model(result)["errors"] as Map<*, *>)["files"])
    }

    @Test
    fun `add - the mandate closed meanwhile - changed`() {
        doThrow(serverError(HttpStatus.CONFLICT, ErrorCode.MANDATE_ALREADY_CLOSED))
            .whenever(documentClient).upload(any(), any(), anyOrNull(), any())

        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents").file(photo()).param("type", DocumentType.PASSPORT.name))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `add - the server fails - the form again`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .whenever(documentClient).upload(any(), any(), anyOrNull(), any())

        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents").file(photo()).param("type", DocumentType.PASSPORT.name))
            .andReturn()

        assertEquals(MandateDocumentController.VIEW_NEW, result.modelAndView?.viewName)
        assertNotNull(model(result)["error"])
    }

    @Test
    fun `add - the server is unreachable - the form again`() {
        doThrow(ResourceAccessException("down")).whenever(documentClient).upload(any(), any(), anyOrNull(), any())

        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents").file(photo()).param("type", DocumentType.PASSPORT.name))
            .andReturn()

        assertEquals(MandateDocumentController.VIEW_NEW, result.modelAndView?.viewName)
        assertNotNull(model(result)["error"])
    }

    @Test
    fun `document page`() {
        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).andReturn()

        assertEquals(MandateDocumentController.VIEW_DOCUMENT, result.modelAndView?.viewName)
        val view = model(result)["document"] as DocumentView
        assertEquals("Titre foncier n° 4521", view.title)
        assertEquals(
            listOf(
                "/mandates/301/documents/3103/pages/2001/preview",
                "/mandates/301/documents/3103/pages/2002/preview",
            ),
            view.pages.map { it.href },
        )
        assertEquals(RenameDocumentForm(title = "Titre foncier n° 4521", version = 3), model(result)["renameForm"])
        assertEquals(false, model(result)["conflict"])
        assertEquals(false, model(result)["deleting"])
    }

    @Test
    fun `document page - the reading of the document`() {
        doReturn(document.copy(fields = listOf(DocumentField("titleNumber", "4521"))))
            .whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).andReturn()

        val reading = model(result)["reading"] as DocumentReadingView
        assertEquals(listOf(DocumentFieldRowView("Numéro du titre", "4521")), reading.rows)
    }

    @Test
    fun `document page - delete confirmation open without JS`() {
        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID").param("delete", "1")).andReturn()

        assertEquals(true, model(result)["deleting"])
    }

    @Test
    fun `document page - a document of another mandate - changed`() {
        doThrow(serverError(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_DOCUMENT_NOT_FOUND))
            .whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `document page - a malformed id - changed`() {
        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/not-a-number")).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
    }

    @Test
    fun `document page - a replacement over the multipart limit shows its page's error`() {
        val result = mvc
            .perform(
                get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")
                    .param("replaceError", "size")
                    .param("page", page2.id.toString()),
            )
            .andReturn()

        assertEquals(mapOf(page2.id to MandateDocumentController.TOO_LARGE), model(result)["replaceErrors"])
    }

    @Test
    fun `document page - a closed mandate keeps it readable`() {
        doReturn(mandate.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(MANDATE_ID)

        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).andReturn()

        assertEquals(MandateDocumentController.VIEW_DOCUMENT, result.modelAndView?.viewName)
        assertFalse((model(result)["document"] as DocumentView).writable)
    }

    @Test
    fun `rename`() {
        val result = mvc
            .perform(
                post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename")
                    .param("title", "  Titre foncier lot 12 ")
                    .param("version", "3"),
            )
            .andReturn()

        verify(documentClient).rename(
            MANDATE_ID,
            DOCUMENT_ID,
            UpdateMandateDocumentRequest(title = "Titre foncier lot 12", version = 3),
        )
        assertEquals("redirect:/mandates/$MANDATE_ID/documents/$DOCUMENT_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.RENAMED, result.flashes()["outcome"])
    }

    @Test
    fun `rename - a blank title`() {
        val result = mvc
            .perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename").param("title", "  "))
            .andReturn()

        assertEquals(MandateDocumentController.VIEW_DOCUMENT, result.modelAndView?.viewName)
        assertEquals(mapOf("title" to "Indiquez un titre."), model(result)["renameErrors"])
        verify(documentClient, never()).rename(any(), any(), any())
    }

    @Test
    fun `rename - over 100 characters`() {
        val result = mvc
            .perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename").param("title", "a".repeat(101)))
            .andReturn()

        assertEquals(mapOf("title" to "100 caractères au plus."), model(result)["renameErrors"])
    }

    @Test
    fun `rename - changed since the page was loaded - the current title`() {
        doThrow(serverError(HttpStatus.CONFLICT, ErrorCode.MANDATE_DOCUMENT_VERSION_CONFLICT))
            .whenever(documentClient).rename(any(), any(), any())

        val result = mvc
            .perform(
                post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename")
                    .param("title", "Titre foncier lot 12")
                    .param("version", "2"),
            )
            .andReturn()

        assertEquals(MandateDocumentController.VIEW_DOCUMENT, result.modelAndView?.viewName)
        assertEquals(true, model(result)["conflict"])
        assertEquals("Titre foncier n° 4521", (model(result)["renameForm"] as RenameDocumentForm).title)
        assertEquals(3, (model(result)["renameForm"] as RenameDocumentForm).version)
    }

    @Test
    fun `rename - the mandate closed meanwhile - changed`() {
        doThrow(serverError(HttpStatus.CONFLICT, ErrorCode.MANDATE_ALREADY_CLOSED))
            .whenever(documentClient).rename(any(), any(), any())

        val result = mvc
            .perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename").param("title", "Titre"))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `rename - the document is gone - changed`() {
        doThrow(serverError(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_DOCUMENT_NOT_FOUND))
            .whenever(documentClient).rename(any(), any(), any())

        val result = mvc
            .perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename").param("title", "Titre"))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
    }

    @Test
    fun `rename - the server fails - the document page says so`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .whenever(documentClient).rename(any(), any(), any())

        val result = mvc
            .perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename").param("title", "Titre"))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID/documents/$DOCUMENT_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.FAILED, result.flashes()["outcome"])
        assertNotNull(result.flashes()["outcomeMessage"])
    }

    @Test
    fun `replace a page`() {
        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}").file(replacement()))
            .andReturn()

        val page = argumentCaptor<UploadedPage>()
        verify(documentClient).uploadReplacementPage(any(), any(), any(), page.capture())
        assertEquals("page.jpg", page.firstValue.filename)
        assertEquals("redirect:/mandates/$MANDATE_ID/documents/$DOCUMENT_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.REPLACED, result.flashes()["outcome"])
        assertEquals(2, result.flashes()["page"])
    }

    @Test
    fun `replace a page - no file`() {
        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}"))
            .andReturn()

        assertEquals(
            mapOf(page2.id to "Choisissez la nouvelle page : une photo ou un PDF."),
            model(result)["replaceErrors"],
        )
        verify(documentClient, never()).uploadReplacementPage(any(), any(), any(), any())
    }

    @Test
    fun `replace a page - a 6 Mo file`() {
        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}")
                    .file(replacement(jpeg + ByteArray(6 * 1024 * 1024))),
            )
            .andReturn()

        assertEquals(
            mapOf(page2.id to "Cette image dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère."),
            model(result)["replaceErrors"],
        )
    }

    @Test
    fun `replace a page - a page of another document - changed`() {
        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/9999").file(replacement()))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        verify(documentClient, never()).uploadReplacementPage(any(), any(), any(), any())
    }

    @Test
    fun `replace a page - the server refuses it - changed`() {
        doThrow(serverError(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_DOCUMENT_PAGE_NOT_FOUND))
            .whenever(documentClient).uploadReplacementPage(any(), any(), any(), any())

        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}").file(replacement()))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `delete`() {
        val result = mvc.perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/delete")).andReturn()

        verify(documentClient).delete(MANDATE_ID, DOCUMENT_ID)
        assertEquals("redirect:/mandates/$MANDATE_ID#documents-title", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.DELETED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `delete - the mandate closed meanwhile - changed`() {
        doThrow(serverError(HttpStatus.CONFLICT, ErrorCode.MANDATE_ALREADY_CLOSED))
            .whenever(documentClient).delete(any(), any())

        val result = mvc.perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/delete")).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `delete - the document is gone - changed`() {
        doThrow(serverError(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_DOCUMENT_NOT_FOUND))
            .whenever(documentClient).delete(any(), any())

        val result = mvc.perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/delete")).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
    }

    @Test
    fun `delete - the server fails - the mandate page says so`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .whenever(documentClient).delete(any(), any())

        val result = mvc.perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/delete")).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID#documents-title", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.FAILED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `not an agent - signed out`() {
        doReturn(null).whenever(currentAgent).get()

        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/new")).andReturn()

        assertEquals("redirect:/login", result.modelAndView?.viewName)
    }

    @Test
    fun `a malformed mandate id - the Espace agent`() {
        val result = mvc.perform(get("/mandates/not-a-number/documents/new")).andReturn()

        assertEquals("redirect:/", result.modelAndView?.viewName)
    }

    // The authenticated preview of a page: the image bytes, never a storage URL

    private val previewJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

    @Test
    fun `preview - the page's jpeg behind the agent session`() {
        doReturn(previewJpeg).whenever(documentClient).preview(MANDATE_ID, DOCUMENT_ID, page1.id)

        val result = mvc.perform(preview(page1.id)).andReturn()

        assertEquals(200, result.response.status)
        assertEquals("image/jpeg", result.response.contentType)
        assertEquals("inline; filename=\"mandat-301-page-1.jpg\"", result.response.getHeader("Content-Disposition"))
        assertEquals("private, max-age=300", result.response.getHeader("Cache-Control"))
        assertEquals("nosniff", result.response.getHeader("X-Content-Type-Options"))
        assertContentEquals(previewJpeg, result.response.contentAsByteArray)
        verify(documentClient).preview(MANDATE_ID, DOCUMENT_ID, page1.id)
    }

    @Test
    fun `preview - not signed in - signed out`() {
        doReturn(null).whenever(currentAgent).get()

        val result = mvc.perform(preview(page1.id)).andReturn()

        assertEquals("redirect:/login", result.modelAndView?.viewName)
        verify(documentClient, never()).preview(any(), any(), any())
    }

    @Test
    fun `preview - a cancelled mandate still serves its pages`() {
        doReturn(mandate.copy(status = MandateStatus.CANCELLED)).whenever(mandateClient).get(311)
        doReturn(document).whenever(documentClient).get(311, DOCUMENT_ID)
        doReturn(previewJpeg).whenever(documentClient).preview(311, DOCUMENT_ID, page1.id)

        val result = mvc.perform(preview(page1.id, 311)).andReturn()

        assertEquals(200, result.response.status)
        assertEquals("image/jpeg", result.response.contentType)
    }

    @Test
    fun `preview - a page that isn't the document's - 404`() {
        val result = mvc.perform(preview(999)).andReturn()

        assertEquals(404, result.response.status)
        verify(documentClient, never()).preview(any(), any(), any())
    }

    @Test
    fun `preview - the server refuses it - its status`() {
        for (status in listOf(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND)) {
            doThrow(HttpClientErrorException.create(status, status.reasonPhrase, HttpHeaders.EMPTY, null, null))
                .whenever(documentClient).preview(MANDATE_ID, DOCUMENT_ID, page1.id)

            val result = mvc.perform(preview(page1.id)).andReturn()

            assertEquals(status.value(), result.response.status, status.name)
        }
    }

    @Test
    fun `preview - the server fails or is unreachable - 502`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .whenever(documentClient).preview(MANDATE_ID, DOCUMENT_ID, page1.id)
        assertEquals(502, mvc.perform(preview(page1.id)).andReturn().response.status)

        doThrow(ResourceAccessException("down")).whenever(documentClient).preview(MANDATE_ID, DOCUMENT_ID, page1.id)
        assertEquals(502, mvc.perform(preview(page1.id)).andReturn().response.status)
    }

    private fun preview(pageId: Long, mandateId: Long = MANDATE_ID) =
        get("/mandates/$mandateId/documents/$DOCUMENT_ID/pages/$pageId/preview")

    private fun MvcResult.flashes(): Map<String, Any?> = flashMap

    // The JSON proxies of the direct upload (mandate-document.js)

    private fun json(request: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) =
        request.contentType(org.springframework.http.MediaType.APPLICATION_JSON)

    @Test
    fun `presign - JSON`() {
        doReturn(com.wutsi.ndopify.common.dto.PresignUploadResponse("https://s3/put", "mandate/301/documents/a.png"))
            .whenever(documentClient).presign(MANDATE_ID, "image/png")

        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/presign")).content("""{"contentType":"image/png"}"""),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.url").value("https://s3/put"))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.path")
                    .value("mandate/301/documents/a.png"),
            )
    }

    @Test
    fun `presign - not signed in - 401, never the sign-in redirect`() {
        doReturn(null).whenever(currentAgent).get()

        val result = mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/presign")).content("""{"contentType":"image/png"}"""),
        ).andReturn()

        assertEquals(401, result.response.status)
        assertNull(result.modelAndView?.viewName)
        verify(documentClient, never()).presign(any(), any())
    }

    @Test
    fun `presign - a closed mandate - 409`() {
        doReturn(mandate.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(MANDATE_ID)

        mvc.perform(json(post("/mandates/$MANDATE_ID/documents/presign")).content("""{"contentType":"image/png"}"""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict)

        verify(documentClient, never()).presign(any(), any())
    }

    @Test
    fun `presign - another agent's mandate - 404`() {
        doThrow(HttpClientErrorException.Forbidden::class).whenever(mandateClient).get(MANDATE_ID)

        mvc.perform(json(post("/mandates/$MANDATE_ID/documents/presign")).content("""{"contentType":"image/png"}"""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)
    }

    @Test
    fun `presign - the server refuses the type - its status and body`() {
        doThrow(serverError(HttpStatus.BAD_REQUEST, ErrorCode.HTTP_INVALID_PARAMETER, "contentType"))
            .whenever(documentClient).presign(any(), any())

        val result = mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/presign")).content("""{"contentType":"application/pdf"}"""),
        ).andReturn()

        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains(ErrorCode.HTTP_INVALID_PARAMETER))
    }

    @Test
    fun `presign - the server is unreachable - 502`() {
        doThrow(ResourceAccessException("down")).whenever(documentClient).presign(any(), any())

        mvc.perform(json(post("/mandates/$MANDATE_ID/documents/presign")).content("""{"contentType":"image/png"}"""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadGateway)
    }

    @Test
    fun `create - JSON`() {
        doReturn(42L).whenever(documentClient).create(any(), any())

        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/create"))
                .content("""{"type":"LAND_TITLE","title":"  Titre foncier lot 12  ","paths":["a","b"]}"""),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentId").value(42),
            )

        val request = argumentCaptor<com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest>()
        verify(documentClient).create(any(), request.capture())
        assertEquals("Titre foncier lot 12", request.firstValue.title)
    }

    @Test
    fun `create - eleven paths - 400, no call`() {
        val paths = (1..11).joinToString(",") { "\"p$it\"" }

        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/create"))
                .content("""{"type":"PASSPORT","paths":[$paths]}"""),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)

        verify(documentClient, never()).create(any(), any())
    }

    @Test
    fun `create - a duplicate path - 400, no call`() {
        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/create"))
                .content("""{"type":"PASSPORT","paths":["a","a"]}"""),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)

        verify(documentClient, never()).create(any(), any())
    }

    @Test
    fun `create - no path or no type - 400, no call`() {
        mvc.perform(json(post("/mandates/$MANDATE_ID/documents/create")).content("""{"type":"PASSPORT","paths":[]}"""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)
        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/create")).content("""{"type":"UNKNOWN","paths":["a"]}"""),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)

        verify(documentClient, never()).create(any(), any())
    }

    @Test
    fun `create - a title over 100 characters - 400, no call`() {
        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/create"))
                .content("""{"type":"PASSPORT","title":"${"a".repeat(101)}","paths":["a"]}"""),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)

        verify(documentClient, never()).create(any(), any())
    }

    @Test
    fun `saved - back to the mandate page with the new document's title`() {
        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/saved").param("documentId", DOCUMENT_ID.toString()))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID#documents-title", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.ADDED, result.flashes()["documentOutcome"])
        assertEquals("Titre foncier n° 4521", result.flashes()["documentTitle"])
    }

    @Test
    fun `saved - an unknown document - changed`() {
        doThrow(serverError(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_DOCUMENT_NOT_FOUND))
            .whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/saved").param("documentId", DOCUMENT_ID.toString()))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `presign a replacement page - JSON`() {
        doReturn(com.wutsi.ndopify.common.dto.PresignUploadResponse("https://s3/put", "mandate/301/documents/b.png"))
            .whenever(documentClient).presign(MANDATE_ID, "image/jpeg")

        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}/presign"))
                .content("""{"contentType":"image/jpeg"}"""),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.path").exists())
    }

    @Test
    fun `attach a replacement page - JSON`() {
        doReturn(page2.id).whenever(documentClient).replacePage(any(), any(), any(), any())

        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}/attach"))
                .content("""{"path":"mandate/301/documents/b.png"}"""),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.pageId").value(page2.id),
            )

        verify(documentClient).replacePage(MANDATE_ID, DOCUMENT_ID, page2.id, "mandate/301/documents/b.png")
    }

    @Test
    fun `attach - another document's page - 404`() {
        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/9999/attach"))
                .content("""{"path":"mandate/301/documents/b.png"}"""),
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)

        verify(documentClient, never()).replacePage(any(), any(), any(), any())
    }

    @Test
    fun `attach - not signed in - 401`() {
        doReturn(null).whenever(currentAgent).get()

        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}/attach"))
                .content("""{"path":"mandate/301/documents/b.png"}"""),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    fun `attach - the server refuses the page - its status and body`() {
        doThrow(serverError(HttpStatus.BAD_REQUEST, ErrorCode.HTTP_INVALID_PARAMETER, "file"))
            .whenever(documentClient).replacePage(any(), any(), any(), any())

        val result = mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}/attach"))
                .content("""{"path":"mandate/301/documents/b.png"}"""),
        ).andReturn()

        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains(ErrorCode.HTTP_INVALID_PARAMETER))
    }

    @Test
    fun `saved page - back to the document with its number`() {
        val result = mvc.perform(
            get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}/saved"),
        ).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID/documents/$DOCUMENT_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.REPLACED, result.flashes()["outcome"])
        assertEquals(2, result.flashes()["page"])
    }

    @Test
    fun `saved page - a page that isn't the document's - changed`() {
        val result = mvc.perform(
            get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/9999/saved"),
        ).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    // The uncovered error pages of the actions

    private fun bodied(status: HttpStatus, message: String, parameter: String): HttpClientErrorException {
        val ex = HttpClientErrorException(status)
        ex.setBodyConvertFunction {
            ErrorResponse(Error(code = "bad", message = message, parameter = Parameter(name = parameter)))
        }
        return ex
    }

    @Test
    fun `rename - the server refuses the title - the field's error`() {
        doThrow(bodied(HttpStatus.BAD_REQUEST, "Titre trop long", "title"))
            .whenever(documentClient).rename(any(), any(), any())

        val result = mvc
            .perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename").param("title", "Titre"))
            .andReturn()

        assertEquals(MandateDocumentController.VIEW_DOCUMENT, result.modelAndView?.viewName)
        assertEquals(mapOf("title" to "Titre trop long"), model(result)["renameErrors"])
    }

    @Test
    fun `rename - a conflict it can't read is a stale page`() {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { throw IllegalStateException("no body") }
        doThrow(ex).whenever(documentClient).rename(any(), any(), any())

        val result = mvc
            .perform(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/rename").param("title", "Titre"))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `replace a page - the server refuses the image - its page's error`() {
        doThrow(bodied(HttpStatus.BAD_REQUEST, "Format non accepté", "file"))
            .whenever(documentClient).uploadReplacementPage(any(), any(), any(), any())

        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}").file(replacement()))
            .andReturn()

        assertEquals(MandateDocumentController.VIEW_DOCUMENT, result.modelAndView?.viewName)
        assertEquals(
            mapOf(
                page2.id to "Cette page n'a pas pu être remplacée : vérifiez qu'il s'agit d'une photo lisible ou d'un " +
                    "PDF d'une page, non protégé.",
            ),
            model(result)["replaceErrors"],
        )
    }

    @Test
    fun `replace a page - a one-page pdf is accepted`() {
        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}")
                    .file(MockMultipartFile("file", "scan.pdf", "application/pdf", pdf())),
            )
            .andReturn()

        val page = argumentCaptor<UploadedPage>()
        verify(documentClient).uploadReplacementPage(any(), any(), any(), page.capture())
        assertEquals("application/pdf", page.firstValue.mimeType)
        assertEquals("redirect:/mandates/$MANDATE_ID/documents/$DOCUMENT_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.REPLACED, result.flashes()["outcome"])
    }

    @Test
    fun `replace a page - the server's multi-page pdf code opens the row`() {
        val ex = HttpClientErrorException(HttpStatus.BAD_REQUEST)
        ex.setBodyConvertFunction {
            ErrorResponse(
                Error(
                    code = ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES,
                    parameter = Parameter(name = "file"),
                ),
            )
        }
        doThrow(ex).whenever(documentClient).uploadReplacementPage(any(), any(), any(), any())

        val result = mvc
            .perform(
                multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}")
                    .file(MockMultipartFile("file", "scan.pdf", "application/pdf", pdf())),
            )
            .andReturn()

        assertEquals(MandateDocumentController.VIEW_DOCUMENT, result.modelAndView?.viewName)
        assertEquals(
            mapOf(page2.id to "Ce PDF fait plusieurs pages : envoyez un PDF d'une page, ou une photo."),
            model(result)["replaceErrors"],
        )
    }

    @Test
    fun `replace a page - the server fails - the document page says so`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .whenever(documentClient).uploadReplacementPage(any(), any(), any(), any())

        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}").file(replacement()))
            .andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID/documents/$DOCUMENT_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.FAILED, result.flashes()["outcome"])
    }

    @Test
    fun `attach - the server is unreachable - 502`() {
        doThrow(ResourceAccessException("down")).whenever(documentClient).replacePage(any(), any(), any(), any())

        mvc.perform(
            json(post("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}/attach"))
                .content("""{"path":"mandate/301/documents/b.png"}"""),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadGateway)
    }

    @Test
    fun `presign - a mandate the pages don't show - 404`() {
        doReturn(mandate.copy(status = MandateStatus.PENDING_ASSIGNMENT)).whenever(mandateClient).get(MANDATE_ID)

        mvc.perform(json(post("/mandates/$MANDATE_ID/documents/presign")).content("""{"contentType":"image/png"}"""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)

        verify(documentClient, never()).presign(any(), any())
    }

    @Test
    fun `presign - the server is unreachable when the mandate is read - 502`() {
        doThrow(ResourceAccessException("down")).whenever(mandateClient).get(MANDATE_ID)

        mvc.perform(json(post("/mandates/$MANDATE_ID/documents/presign")).content("""{"contentType":"image/png"}"""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadGateway)
    }

    @Test
    fun `document page - the server is unreachable - changed`() {
        doThrow(ResourceAccessException("down")).whenever(mandateClient).get(MANDATE_ID)

        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `document page - the server is unreachable when the document is read - changed`() {
        doThrow(ResourceAccessException("down")).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID")).andReturn()

        assertEquals("redirect:/mandates/$MANDATE_ID", result.modelAndView?.viewName)
        assertEquals(MandateDocumentController.CHANGED, result.flashes()["documentOutcome"])
    }

    @Test
    fun `add - a server 400 whose body can't be read falls back to the pages`() {
        val ex = HttpClientErrorException(HttpStatus.BAD_REQUEST)
        ex.setBodyConvertFunction { throw IllegalStateException("no body") }
        doThrow(ex).whenever(documentClient).upload(any(), any(), anyOrNull(), any())

        val result = mvc
            .perform(multipart("/mandates/$MANDATE_ID/documents").file(photo()).param("type", DocumentType.PASSPORT.name))
            .andReturn()

        assertNotNull((model(result)["errors"] as Map<*, *>)["files"])
    }
}
