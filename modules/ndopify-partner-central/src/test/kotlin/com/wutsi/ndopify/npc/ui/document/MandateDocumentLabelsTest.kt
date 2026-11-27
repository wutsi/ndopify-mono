package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.mandate.dto.DocumentSource
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MandateDocumentLabelsTest {
    @Test
    fun `type names`() {
        assertEquals(
            mapOf(
                DocumentType.NATIONAL_ID to "Carte nationale d'identité",
                DocumentType.PASSPORT to "Passeport",
                DocumentType.LAND_TITLE to "Titre foncier",
                DocumentType.CERTIFICATE_OF_OWNERSHIP to "Certificat de propriété",
                DocumentType.SURVEY_PLAN to "Plan de bornage",
                DocumentType.POWER_OF_ATTORNEY to "Procuration",
                DocumentType.LEASE_CONTRACT to "Contrat de bail",
                DocumentType.UTILITY_BILL to "Facture d'eau ou d'électricité",
                DocumentType.TAX_RECEIPT to "Quittance d'impôt",
                DocumentType.PAY_SLIP to "Bulletin de paie",
                DocumentType.BANK_PRE_APPROVAL to "Accord de principe bancaire",
                DocumentType.PROOF_OF_FUNDS to "Justificatif de fonds",
                DocumentType.OTHER to "Document",
            ),
            DocumentType.entries.filter { it != DocumentType.UNKNOWN }.associateWith { MandateDocumentLabels.type(it) },
        )
        assertEquals("Document", MandateDocumentLabels.type(DocumentType.UNKNOWN))
    }

    @Test
    fun `status tags`() {
        assertEquals(MandateRules.Tag("Reçu", ""), MandateDocumentLabels.status(DocumentStatus.UPLOADED))
        assertEquals(MandateRules.Tag("En cours d'analyse", "info"), MandateDocumentLabels.status(DocumentStatus.EXTRACTING))
        assertEquals(MandateRules.Tag("Analysé", "success"), MandateDocumentLabels.status(DocumentStatus.EXTRACTED))
        assertEquals(MandateRules.Tag("À corriger", "warning"), MandateDocumentLabels.status(DocumentStatus.FAILED))
        assertEquals(MandateRules.Tag("Reçu", ""), MandateDocumentLabels.status(DocumentStatus.UNKNOWN))
    }

    @Test
    fun `source names`() {
        assertEquals("Photos", MandateDocumentLabels.source(DocumentSource.IMAGE))
        assertEquals("PDF", MandateDocumentLabels.source(DocumentSource.PDF))
        assertEquals("Photos ou PDF", MandateDocumentLabels.source(DocumentSource.UNKNOWN))
    }

    @Test
    fun `suggested types of a listing`() {
        assertEquals(
            listOf(
                DocumentType.NATIONAL_ID,
                DocumentType.PASSPORT,
                DocumentType.LAND_TITLE,
                DocumentType.CERTIFICATE_OF_OWNERSHIP,
                DocumentType.SURVEY_PLAN,
                DocumentType.POWER_OF_ATTORNEY,
            ),
            MandateDocumentLabels.suggested(MandateType.LISTING),
        )
    }

    @Test
    fun `suggested types of a search`() {
        assertEquals(
            listOf(
                DocumentType.NATIONAL_ID,
                DocumentType.PASSPORT,
                DocumentType.PAY_SLIP,
                DocumentType.BANK_PRE_APPROVAL,
                DocumentType.PROOF_OF_FUNDS,
            ),
            MandateDocumentLabels.suggested(MandateType.SEARCH),
        )
    }

    @Test
    fun `other types of a listing`() {
        assertEquals(
            listOf(
                DocumentType.LEASE_CONTRACT,
                DocumentType.UTILITY_BILL,
                DocumentType.TAX_RECEIPT,
                DocumentType.PAY_SLIP,
                DocumentType.BANK_PRE_APPROVAL,
                DocumentType.PROOF_OF_FUNDS,
                DocumentType.OTHER,
            ),
            MandateDocumentLabels.others(MandateType.LISTING),
        )
    }

    @Test
    fun `other types of a search`() {
        val others = MandateDocumentLabels.others(MandateType.SEARCH)

        assertEquals(DocumentType.OTHER, others.last())
        assertEquals(
            listOf(
                DocumentType.LAND_TITLE,
                DocumentType.CERTIFICATE_OF_OWNERSHIP,
                DocumentType.SURVEY_PLAN,
                DocumentType.POWER_OF_ATTORNEY,
                DocumentType.LEASE_CONTRACT,
                DocumentType.UTILITY_BILL,
                DocumentType.TAX_RECEIPT,
                DocumentType.OTHER,
            ),
            others,
        )
        // Every type but UNKNOWN is offered once, on either mandate
        val listing = MandateDocumentLabels.suggested(MandateType.LISTING) + MandateDocumentLabels.others(MandateType.LISTING)
        assertEquals(DocumentType.entries.filter { it != DocumentType.UNKNOWN }.toSet(), listing.toSet())
        assertEquals(listing.size, listing.distinct().size)
    }
}
