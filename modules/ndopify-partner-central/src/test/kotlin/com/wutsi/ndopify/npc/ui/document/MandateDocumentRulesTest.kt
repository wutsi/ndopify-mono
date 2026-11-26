package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.mandate.dto.DocumentSource
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateDocumentRulesTest {
    private fun page(
        filename: String = "IMG_2041.jpg",
        size: Long = 1024,
        mimeType: String? = "image/jpeg",
    ) = SniffedPage(filename, size, mimeType, ByteArray(0))

    private fun form(type: String = DocumentType.NATIONAL_ID.name, title: String = "") = AddDocumentForm(type, title)

    @Test
    fun `writable while the mandate is active`() {
        for (status in listOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL, MandateStatus.APPROVED)) {
            assertTrue(MandateDocumentRules.writable(Mandate(status = status)), status.name)
        }
        for (status in listOf(MandateStatus.CLOSED, MandateStatus.CANCELLED)) {
            assertFalse(MandateDocumentRules.writable(Mandate(status = status)), status.name)
        }
    }

    @Test
    fun `a valid add`() {
        assertEquals(emptyMap(), MandateDocumentRules.validateAdd(form(title = "Titre foncier"), listOf(page())))
    }

    @Test
    fun `a type is required`() {
        assertEquals(
            mapOf("type" to "Choisissez le type de document."),
            MandateDocumentRules.validateAdd(form(type = ""), listOf(page())),
        )
        assertEquals(
            mapOf("type" to "Choisissez le type de document."),
            MandateDocumentRules.validateAdd(form(type = "UNKNOWN"), listOf(page())),
        )
        assertEquals(
            mapOf("type" to "Choisissez le type de document."),
            MandateDocumentRules.validateAdd(form(type = "NOT_A_TYPE"), listOf(page())),
        )
    }

    @Test
    fun `a page is required`() {
        assertEquals(
            mapOf("files" to "Ajoutez au moins une page."),
            MandateDocumentRules.validateAdd(form(), emptyList()),
        )
    }

    @Test
    fun `ten pages at most`() {
        assertEquals(emptyMap(), MandateDocumentRules.validateAdd(form(), List(10) { page("p$it.jpg") }))
        assertEquals(
            mapOf("files" to "10 pages au plus."),
            MandateDocumentRules.validateAdd(form(), List(11) { page("p$it.jpg") }),
        )
    }

    @Test
    fun `a title of 100 characters at most, after trimming`() {
        assertEquals(emptyMap(), MandateDocumentRules.validateAdd(form(title = "a".repeat(100)), listOf(page())))
        assertEquals(emptyMap(), MandateDocumentRules.validateAdd(form(title = "  " + "a".repeat(100) + "  "), listOf(page())))
        assertEquals(
            mapOf("title" to "100 caractères au plus."),
            MandateDocumentRules.validateAdd(form(title = "a".repeat(101)), listOf(page())),
        )
    }

    @Test
    fun `a page is an image or a pdf of 5 Mo at most`() {
        assertEquals(emptyMap(), MandateDocumentRules.validateAdd(form(), listOf(page(size = 5 * 1024 * 1024L))))
        assertEquals(
            mapOf("files" to "« IMG_2041.jpg » dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère."),
            MandateDocumentRules.validateAdd(form(), listOf(page(size = 5 * 1024 * 1024L + 1))),
        )
        assertEquals(
            mapOf(
                "files" to "« IMG_2041.jpg » n'est ni une photo ni un PDF : choisissez une photo JPEG, PNG ou WEBP, " +
                    "ou un PDF.",
            ),
            MandateDocumentRules.validateAdd(form(), listOf(page(mimeType = "image/gif"))),
        )
        assertEquals(
            mapOf(
                "files" to "« IMG_2041.jpg » n'est ni une photo ni un PDF : choisissez une photo JPEG, PNG ou WEBP, " +
                    "ou un PDF.",
            ),
            MandateDocumentRules.validateAdd(form(), listOf(page(mimeType = null))),
        )
    }

    @Test
    fun `an add accepts a pdf`() {
        assertEquals(
            emptyMap(),
            MandateDocumentRules.validateAdd(form(), listOf(page(filename = "scan.pdf", mimeType = "application/pdf"))),
        )
    }

    @Test
    fun `an add's pdf over 5 Mo is refused with the pdf message`() {
        assertEquals(
            mapOf(
                "files" to "« scan.pdf » dépasse 5 Mo : choisissez un PDF plus léger, ou envoyez ses pages en photos.",
            ),
            MandateDocumentRules.validateAdd(
                form(),
                listOf(page(filename = "scan.pdf", size = 5 * 1024 * 1024L + 1, mimeType = "application/pdf")),
            ),
        )
    }

    @Test
    fun `the first page's problem is the one shown`() {
        val errors = MandateDocumentRules.validateAdd(
            form(),
            listOf(page("ok.jpg"), page("big.jpg", 6 * 1024 * 1024L), page("gif.gif", mimeType = "image/gif")),
        )

        assertEquals(
            "« big.jpg » dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère.",
            errors.getValue("files"),
        )
    }

    @Test
    fun `a rename title is required and of 100 characters at most`() {
        assertEquals(mapOf("title" to "Indiquez un titre."), MandateDocumentRules.validateTitle("   "))
        assertEquals(mapOf("title" to "100 caractères au plus."), MandateDocumentRules.validateTitle("a".repeat(101)))
        assertEquals(emptyMap(), MandateDocumentRules.validateTitle("Titre foncier"))
    }

    @Test
    fun `a replacement page`() {
        assertEquals("Choisissez la nouvelle page : une photo ou un PDF.", MandateDocumentRules.validatePage(null))
        assertNull(MandateDocumentRules.validatePage(page()))
        assertNull(MandateDocumentRules.validatePage(page(filename = "scan.pdf", mimeType = "application/pdf")))
        assertEquals(MandateDocumentRules.NOT_A_PAGE, MandateDocumentRules.validatePage(page(mimeType = "image/gif")))
        assertEquals(
            MandateDocumentRules.PDF_TOO_LARGE,
            MandateDocumentRules.validatePage(
                page(filename = "scan.pdf", size = 5 * 1024 * 1024L + 1, mimeType = "application/pdf"),
            ),
        )
        assertEquals(
            "Cette image dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère.",
            MandateDocumentRules.validatePage(page(size = 5 * 1024 * 1024L + 1)),
        )
    }

    @Test
    fun `a pdf is recognized by its mime type`() {
        assertTrue(MandateDocumentRules.isPdf(page(mimeType = "application/pdf")))
        assertFalse(MandateDocumentRules.isPdf(page()))
    }

    @Test
    fun `the posted type`() {
        assertEquals(DocumentType.LAND_TITLE, MandateDocumentRules.type("LAND_TITLE"))
        assertEquals(DocumentType.LAND_TITLE, MandateDocumentRules.type(" LAND_TITLE "))
        assertNull(MandateDocumentRules.type(null))
        assertNull(MandateDocumentRules.type(""))
        assertNull(MandateDocumentRules.type("UNKNOWN"))
        assertNull(MandateDocumentRules.type("NOT_A_TYPE"))
    }

    @Test
    fun `every type is accepted on either mandate`() {
        for (type in MandateType.entries) {
            val mandate = Mandate(mandateType = type, status = MandateStatus.ASSIGNED)
            assertTrue(MandateDocumentRules.writable(mandate))
        }
    }

    @Test
    fun `at most 5 documents per mandate`() {
        assertEquals(5, MandateDocumentRules.MAX_DOCUMENTS)
        assertFalse(MandateDocumentRules.atLimit(4))
        assertTrue(MandateDocumentRules.atLimit(5))
        assertTrue(MandateDocumentRules.atLimit(6))
    }

    @Test
    fun `a mixed selection is photos and a PDF, or two PDFs`() {
        val photo = page("a.jpg")
        val pdf = page("a.pdf", mimeType = "application/pdf")
        assertTrue(MandateDocumentRules.mixed(listOf(photo, pdf)))
        assertTrue(MandateDocumentRules.mixed(listOf(pdf, page("b.pdf", mimeType = "application/pdf"))))
        assertFalse(MandateDocumentRules.mixed(listOf(photo, page("b.jpg"))))
        assertFalse(MandateDocumentRules.mixed(listOf(pdf)))
    }

    @Test
    fun `the kind of a selection`() {
        assertEquals(DocumentSource.IMAGE, MandateDocumentRules.source(listOf(page(), page("b.png", mimeType = "image/png"))))
        assertEquals(DocumentSource.PDF, MandateDocumentRules.source(listOf(page("a.pdf", mimeType = "application/pdf"))))
        // Mixed lists are refused before the kind is used; the function never throws on one.
        assertEquals(
            DocumentSource.PDF,
            MandateDocumentRules.source(listOf(page(), page("a.pdf", mimeType = "application/pdf"))),
        )
    }

    @Test
    fun `a mixed selection is refused before the file count`() {
        val pages = List(10) { index -> page("p$index.jpg") } +
            page("scan.pdf", mimeType = "application/pdf")

        assertEquals(
            mapOf("files" to MandateDocumentRules.MIXED_SOURCES),
            MandateDocumentRules.validateAdd(form(), pages),
        )
    }

    @Test
    fun `a replacement keeps the document's kind`() {
        val photo = page()
        val pdf = page("scan.pdf", mimeType = "application/pdf")
        assertNull(MandateDocumentRules.validatePage(pdf, DocumentSource.PDF))
        assertEquals(MandateDocumentRules.mixedReplace(DocumentSource.PDF), MandateDocumentRules.validatePage(photo, DocumentSource.PDF))
        assertNull(MandateDocumentRules.validatePage(photo, DocumentSource.IMAGE))
        assertEquals(
            MandateDocumentRules.mixedReplace(DocumentSource.IMAGE),
            MandateDocumentRules.validatePage(pdf, DocumentSource.IMAGE),
        )
        assertNull(MandateDocumentRules.validatePage(photo, DocumentSource.UNKNOWN))
        assertNull(MandateDocumentRules.validatePage(pdf, DocumentSource.UNKNOWN))
    }

    @Test
    fun `the accepted file types of a document's kind`() {
        assertEquals("application/pdf", MandateDocumentRules.accept(DocumentSource.PDF))
        assertEquals("image/jpeg,image/png,image/webp", MandateDocumentRules.accept(DocumentSource.IMAGE))
        assertEquals(
            "image/jpeg,image/png,image/webp,application/pdf",
            MandateDocumentRules.accept(DocumentSource.UNKNOWN),
        )
    }

    @Test
    fun `the replacement copy of a kind`() {
        assertEquals("1", "1")
        assertTrue(MandateDocumentRules.replaceHint(DocumentSource.PDF).startsWith("PDF d'une page"))
        assertTrue(MandateDocumentRules.replaceHint(DocumentSource.IMAGE).startsWith("Photo JPEG"))
        assertTrue(MandateDocumentRules.replaceHint(DocumentSource.UNKNOWN).contains("ou PDF d'une page"))
        assertEquals("Choisissez un PDF d'une page pour cette page.", MandateDocumentRules.replaceRequired(DocumentSource.PDF))
        assertEquals("Choisissez une photo pour cette page.", MandateDocumentRules.replaceRequired(DocumentSource.IMAGE))
    }

    @Test
    fun `the cap notice`() {
        assertEquals("Un mandat peut avoir 5 documents au plus.", MandateDocumentRules.DOCUMENT_LIMIT)
    }
}
