package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.DocumentField
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateDocumentReadingViewsTest {
    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 25)
        const val WAITING_UPLOADED =
            "Le document est reçu : sa lecture va commencer. Rien à faire, elle se lance d'elle-même ; ses données " +
                "apparaîtront ici."
        const val WAITING_EXTRACTING =
            "L'IA lit le document. Ses données apparaîtront ici dès qu'elle aura terminé : rien à faire."
        const val ALREADY_EXPIRED = "Elle était déjà expirée quand l'IA l'a lue."
        const val EXPIRED_SINCE = "Elle a expiré depuis sa lecture."
        const val FIND_A_VALID_ONE =
            "Ajoutez une pièce en cours de validité : remplacez ses pages par celles de la nouvelle pièce, ou " +
                "supprimez-la et ajoutez la nouvelle."
        const val CLOSED = "Mandat fermé : cette pièce reste consultable telle quelle."
        const val CANCELLED = "Mandat annulé : cette pièce reste consultable telle quelle."
        const val PROBLEM_OTHER =
            "Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour relancer la lecture."
        const val PAGE_MISSING =
            "Il manque une page du document, son verso par exemple. Remplacez la page manquante par une photo, ou " +
                "supprimez le document et ajoutez-le avec toutes ses pages."
        const val LOW_CONFIDENCE =
            "L'IA n'est pas sûre d'un champ obligatoire. Vérifiez-le sur la pièce : si la photo est floue, " +
                "remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau."
    }

    private val labels = DocumentFieldLabels(
        ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
        },
    )

    private val mandate = Mandate(
        id = 301,
        status = MandateStatus.APPROVED,
        mandateType = MandateType.LISTING,
        listingType = ListingType.SALE,
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

    private fun doc(
        status: DocumentStatus = DocumentStatus.EXTRACTED,
        fields: List<DocumentField> = landTitleFields,
        problemCodes: List<String> = emptyList(),
        issuedAt: LocalDate? = LocalDate.of(2019, 6, 14),
        expiresAt: LocalDate? = null,
    ) = MandateDocument(
        id = 3103,
        mandateId = 301,
        type = DocumentType.LAND_TITLE,
        title = "Titre foncier n° 4521",
        status = status,
        fields = fields,
        problemCodes = problemCodes,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
    )

    private fun reading(document: MandateDocument, on: Mandate = mandate): DocumentReadingView =
        MandateDocumentReadingViews.of(on, document, FORMATTER, labels, TODAY)

    @Test
    fun `an extracted document shows its fields in the server's order`() {
        val view = reading(doc())

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
            view.rows.map { it.label },
        )
        assertEquals(
            listOf("4521", "Rodrigue Essomba, Carine Mbarga", "Wouri", "Douala 5e", "Bonapriso", "500 m²", "14 juin 2019"),
            view.rows.map { it.value },
        )
        assertNull(view.waiting)
        assertNull(view.expiry)
        assertTrue(view.problems.isEmpty())
        assertFalse(view.noData)
    }

    @Test
    fun `an unknown key reads the generic label with its value`() {
        val view = reading(doc(fields = landTitleFields + DocumentField("position", "Chargée de clientèle")))

        assertEquals(DocumentFieldRowView("Autre donnée", "Chargée de clientèle"), view.rows.last())
    }

    @Test
    fun `the currency is the amounts' unit, never a row`() {
        val view = reading(
            doc(
                fields = listOf(
                    DocumentField("currency", "XAF"),
                    DocumentField("amount", "12500000"),
                ),
            ),
        )

        assertEquals(listOf(DocumentFieldRowView("Montant", "12,5M\u00A0FCFA")), view.rows)
    }

    @Test
    fun `a null or blank value has no row`() {
        val view = reading(
            doc(
                fields = listOf(
                    DocumentField("titleNumber", "4521"),
                    DocumentField("owners", null),
                    DocumentField("department", "   "),
                    DocumentField("locality", "Bonapriso"),
                ),
            ),
        )

        assertEquals(listOf("Numéro du titre", "Localité"), view.rows.map { it.label })
    }

    @Test
    fun `a waiting document says the reading happens on its own`() {
        val uploaded = reading(doc(status = DocumentStatus.UPLOADED, fields = emptyList()))
        assertEquals(WAITING_UPLOADED, uploaded.waiting)
        assertTrue(uploaded.rows.isEmpty())
        assertFalse(uploaded.noData)

        val extracting = reading(doc(status = DocumentStatus.EXTRACTING, fields = emptyList()))
        assertEquals(WAITING_EXTRACTING, extracting.waiting)
        assertTrue(extracting.rows.isEmpty())
        assertFalse(extracting.noData)
    }

    @Test
    fun `an extracted document without fields says no data was read`() {
        val view = reading(doc(fields = emptyList()))

        assertTrue(view.noData)
        assertTrue(view.rows.isEmpty())
        assertTrue(view.problems.isEmpty())
        assertNull(view.waiting)
    }

    @Test
    fun `a failed document shows one instruction per code, in the server's order`() {
        val view = reading(
            doc(
                status = DocumentStatus.FAILED,
                fields = emptyList(),
                problemCodes = listOf("DOCUMENT_PAGE_MISSING", "DOCUMENT_FIELD_LOW_CONFIDENCE"),
            ),
        )

        assertEquals(listOf(PAGE_MISSING, LOW_CONFIDENCE), view.problems)
        assertTrue(view.rows.isEmpty())
        assertFalse(view.noData)
    }

    @Test
    fun `an unknown code reads the generic instruction`() {
        val view = reading(doc(status = DocumentStatus.FAILED, fields = emptyList(), problemCodes = listOf("DOCUMENT_BACK_MISSING")))

        assertEquals(listOf(PROBLEM_OTHER), view.problems)
    }

    @Test
    fun `a failed document without a usable code still says what to do`() {
        val view = reading(doc(status = DocumentStatus.FAILED, fields = emptyList(), problemCodes = emptyList()))

        assertEquals(listOf(PROBLEM_OTHER), view.problems)
    }

    @Test
    fun `document expired before today shows the notice and its way out`() {
        val view = reading(
            doc(expiresAt = LocalDate.of(2025, 1, 12), problemCodes = listOf("DOCUMENT_EXPIRED")),
        )

        assertEquals(DocumentExpiryView("12 janv. 2025", ALREADY_EXPIRED, FIND_A_VALID_ONE), view.expiry)
        // DOCUMENT_EXPIRED is a warning, never an instruction
        assertTrue(view.problems.isEmpty())
        assertFalse(view.noData)
    }

    @Test
    fun `a document expired since its reading says so`() {
        val view = reading(doc(expiresAt = LocalDate.of(2025, 1, 12)))

        assertEquals(EXPIRED_SINCE, view.expiry?.since)
    }

    @Test
    fun `a document expiring today or later shows no notice`() {
        assertNull(reading(doc(expiresAt = TODAY)).expiry)
        assertNull(reading(doc(expiresAt = LocalDate.of(2026, 12, 1))).expiry)
        assertNull(reading(doc(expiresAt = null)).expiry)
    }

    @Test
    fun `a closed or cancelled mandate keeps the notice readable, without a way out`() {
        val cancelled = reading(doc(expiresAt = LocalDate.of(2025, 1, 12)), on = mandate.copy(status = MandateStatus.CANCELLED))
        assertEquals(CANCELLED, cancelled.expiry?.wayOut)

        val closed = reading(doc(expiresAt = LocalDate.of(2025, 1, 12)), on = mandate.copy(status = MandateStatus.CLOSED))
        assertEquals(CLOSED, closed.expiry?.wayOut)
    }

    @Test
    fun `a waiting document has no field row even when the payload carries some`() {
        val view = reading(doc(status = DocumentStatus.EXTRACTING))

        assertNotNull(view.waiting)
        assertTrue(view.rows.isEmpty())
    }
}
