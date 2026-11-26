package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.DocumentSource
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.MandateDocumentPage
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateDocumentViewsTest {
    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 25)
    }

    private val mandate = Mandate(
        id = 301,
        status = MandateStatus.APPROVED,
        mandateType = MandateType.LISTING,
        listingType = ListingType.SALE,
    )

    private fun date(value: String): Date = Date.from(Instant.parse(value))

    private fun doc(
        id: Long = 3103,
        type: DocumentType = DocumentType.LAND_TITLE,
        title: String = "Titre foncier n° 4521",
        status: DocumentStatus = DocumentStatus.EXTRACTED,
        createdAt: String = "2026-11-25T10:00:00Z",
        pages: List<MandateDocumentPage> = listOf(
            MandateDocumentPage(id = 1, index = 0),
            MandateDocumentPage(id = 2, index = 1),
        ),
        version: Long = 3,
    ) = MandateDocument(
        id = id,
        mandateId = 301,
        type = type,
        title = title,
        status = status,
        pages = pages,
        version = version,
        createdAt = date(createdAt),
        modifiedAt = date(createdAt),
    )

    @Test
    fun section() {
        val section = MandateDocumentViews.section(
            mandate,
            listOf(
                doc(id = 1, createdAt = "2026-11-20T10:00:00Z"),
                doc(id = 2, createdAt = "2026-11-25T10:00:00Z", type = DocumentType.NATIONAL_ID, title = "Carte nationale d'identité"),
            ),
            FORMATTER,
            TODAY,
        )

        assertFalse(section.unavailable)
        assertTrue(section.writable)
        assertEquals(listOf(2L, 1L), section.rows.map { it.id })
        assertEquals(
            DocumentRowView(
                id = 2,
                typeLabel = "Carte nationale d'identité",
                title = null,
                status = MandateRules.Tag("Analysé", "success"),
                addedOn = "25 nov. 2026",
                pageCount = 2,
                href = "/mandates/301/documents/2",
            ),
            section.rows[0],
        )
        assertEquals("Titre foncier n° 4521", section.rows[1].title)
        assertEquals("Titre foncier", section.rows[1].typeLabel)
        assertEquals("Ajoutez la pièce d'identité du vendeur", section.emptyHint.take(38))
        assertEquals(
            "Les pièces d'identité et de propriété du vendeur. Privées : jamais publiées ni montrées à un autre agent.",
            section.intro,
        )
    }

    @Test
    fun `section - the count against the cap`() {
        val three = MandateDocumentViews.section(mandate, List(3) { doc(id = it.toLong()) }, FORMATTER, TODAY)
        assertEquals(3, three.count)
        assertFalse(three.atLimit)

        val five = MandateDocumentViews.section(mandate, List(5) { doc(id = it.toLong()) }, FORMATTER, TODAY)
        assertEquals(5, five.count)
        assertTrue(five.atLimit)

        val unavailable = MandateDocumentViews.section(mandate, null, FORMATTER, TODAY)
        assertEquals(0, unavailable.count)
        assertFalse(unavailable.atLimit)
    }

    @Test
    fun `section - the tenant's date format, year included`() {
        val formatter = TenantFormatter(TENANT.copy(dateFormat = "dd/MM/yyyy"))

        val section = MandateDocumentViews.section(mandate, listOf(doc()), formatter, TODAY)

        assertEquals("25/11/2026", section.rows.single().addedOn)
    }

    @Test
    fun `section - unavailable`() {
        val section = MandateDocumentViews.section(mandate, null, FORMATTER, TODAY)

        assertTrue(section.unavailable)
        assertTrue(section.rows.isEmpty())
    }

    @Test
    fun `section - empty`() {
        val section = MandateDocumentViews.section(mandate, emptyList(), FORMATTER, TODAY)

        assertFalse(section.unavailable)
        assertTrue(section.rows.isEmpty())
    }

    @Test
    fun `section - read-only once closed or cancelled`() {
        for (status in listOf(MandateStatus.CLOSED, MandateStatus.CANCELLED)) {
            val section = MandateDocumentViews.section(mandate.copy(status = status), emptyList(), FORMATTER, TODAY)
            assertFalse(section.writable, status.name)
        }
    }

    @Test
    fun `section - the hint and the intro follow the kind of mandate`() {
        val rentalListing = MandateDocumentViews.section(
            mandate.copy(listingType = ListingType.RENTAL),
            emptyList(),
            FORMATTER,
            TODAY,
        )
        assertTrue(rentalListing.emptyHint.contains("du bailleur"), rentalListing.emptyHint)
        assertTrue(rentalListing.intro.contains("du bailleur"))

        val saleSearch = MandateDocumentViews.section(
            mandate.copy(mandateType = MandateType.SEARCH, listingType = ListingType.SALE),
            emptyList(),
            FORMATTER,
            TODAY,
        )
        assertTrue(saleSearch.emptyHint.contains("de l'acheteur"), saleSearch.emptyHint)
        assertTrue(saleSearch.intro.contains("de financement de l'acheteur"), saleSearch.intro)

        val rentalSearch = MandateDocumentViews.section(
            mandate.copy(mandateType = MandateType.SEARCH, listingType = ListingType.RENTAL),
            emptyList(),
            FORMATTER,
            TODAY,
        )
        assertTrue(rentalSearch.emptyHint.contains("du client"), rentalSearch.emptyHint)
        assertTrue(rentalSearch.intro.contains("de financement du client"), rentalSearch.intro)
    }

    @Test
    fun `section - an expired document is marked, a valid one is not`() {
        val section = MandateDocumentViews.section(
            mandate,
            listOf(
                doc(id = 1, createdAt = "2026-11-20T10:00:00Z").copy(expiresAt = LocalDate.of(2025, 1, 12)),
                doc(id = 2, createdAt = "2026-11-25T10:00:00Z").copy(expiresAt = TODAY),
                doc(id = 3, createdAt = "2026-11-24T10:00:00Z"),
            ),
            FORMATTER,
            TODAY,
        )

        assertEquals(listOf(2L, 3L, 1L), section.rows.map { it.id })
        assertEquals(listOf(false, false, true), section.rows.map { it.expired })
    }

    @Test
    fun document() {
        val view = MandateDocumentViews.document(mandate, doc(), FORMATTER)

        assertEquals(3103, view.id)
        assertEquals(301, view.mandateId)
        assertEquals("Titre foncier", view.typeLabel)
        assertEquals("Titre foncier n° 4521", view.title)
        assertEquals(MandateRules.Tag("Analysé", "success"), view.status)
        assertEquals("25 nov. 2026", view.addedOn)
        assertEquals(3, view.version)
        assertTrue(view.writable)
        assertEquals(listOf(1, 2), view.pages.map { it.number })
        assertEquals(
            listOf(
                "/mandates/301/documents/3103/pages/1/preview",
                "/mandates/301/documents/3103/pages/2/preview",
            ),
            view.pages.map { it.href },
        )
    }

    @Test
    fun `document - its kind and the replacement it accepts`() {
        val pdf = MandateDocumentViews.document(mandate, doc().copy(source = DocumentSource.PDF), FORMATTER)
        assertEquals(DocumentSource.PDF, pdf.source)
        assertEquals("PDF", pdf.sourceLabel)
        assertEquals("application/pdf", pdf.replaceAccept)
        assertEquals(MandateDocumentRules.replaceHint(DocumentSource.PDF), pdf.replaceHint)
        assertEquals(MandateDocumentRules.replaceRequired(DocumentSource.PDF), pdf.replaceRequired)

        val image = MandateDocumentViews.document(mandate, doc().copy(source = DocumentSource.IMAGE), FORMATTER)
        assertEquals("Photos", image.sourceLabel)
        assertEquals("image/jpeg,image/png,image/webp", image.replaceAccept)

        val legacy = MandateDocumentViews.document(mandate, doc().copy(source = DocumentSource.UNKNOWN), FORMATTER)
        assertEquals("Photos ou PDF", legacy.sourceLabel)
        assertEquals("image/jpeg,image/png,image/webp,application/pdf", legacy.replaceAccept)
    }

    @Test
    fun `document - its title is the type's name, not repeated`() {
        val view = MandateDocumentViews.document(
            mandate,
            doc(type = DocumentType.NATIONAL_ID, title = "Carte nationale d'identité"),
            FORMATTER,
        )

        assertNull(view.typeLabel)
        assertEquals("Carte nationale d'identité", view.title)
    }

    @Test
    fun `document - pages in index order`() {
        val view = MandateDocumentViews.document(
            mandate,
            doc(
                pages = listOf(
                    MandateDocumentPage(id = 2, index = 1),
                    MandateDocumentPage(id = 1, index = 0),
                ),
            ),
            FORMATTER,
        )

        assertEquals(listOf(1, 2), view.pages.map { it.number })
        assertEquals(listOf(1L, 2L), view.pages.map { it.id })
    }

    @Test
    fun `document - read-only once closed`() {
        val view = MandateDocumentViews.document(mandate.copy(status = MandateStatus.CLOSED), doc(), FORMATTER)

        assertFalse(view.writable)
    }
}
