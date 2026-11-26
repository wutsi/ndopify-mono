package com.wutsi.ndopify.npc.ui.document

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
    fun `a page is an image of 5 Mo at most`() {
        assertEquals(emptyMap(), MandateDocumentRules.validateAdd(form(), listOf(page(size = 5 * 1024 * 1024L))))
        assertEquals(
            mapOf("files" to "« IMG_2041.jpg » dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère."),
            MandateDocumentRules.validateAdd(form(), listOf(page(size = 5 * 1024 * 1024L + 1))),
        )
        assertEquals(
            mapOf("files" to "« IMG_2041.jpg » n'est pas une image JPEG, PNG ou WEBP."),
            MandateDocumentRules.validateAdd(form(), listOf(page(mimeType = "image/gif"))),
        )
        assertEquals(
            mapOf("files" to "« IMG_2041.jpg » n'est pas une image JPEG, PNG ou WEBP."),
            MandateDocumentRules.validateAdd(form(), listOf(page(mimeType = null))),
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
        assertEquals("Choisissez une image.", MandateDocumentRules.validatePage(null))
        assertNull(MandateDocumentRules.validatePage(page()))
        assertEquals(
            "« IMG_2041.jpg » n'est pas une image JPEG, PNG ou WEBP.",
            MandateDocumentRules.validatePage(page(mimeType = "application/pdf")),
        )
        assertEquals(
            "Cette image dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère.",
            MandateDocumentRules.validatePage(page(size = 5 * 1024 * 1024L + 1)),
        )
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
}
