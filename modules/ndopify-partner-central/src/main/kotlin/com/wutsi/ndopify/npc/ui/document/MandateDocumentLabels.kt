package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.mandate.dto.DocumentSource
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.mandate.MandateRules

/**
 * The words of a mandate's documents, as the approved prototype words them: the French names of ndopify-server's
 * `MandateDocumentLabels` (kept here, since Partner Central has no build-time dependency on the server), the statuses,
 * and the types the picker suggests for the mandate. The suggestion is only a hint: every type can be chosen on any
 * mandate.
 */
object MandateDocumentLabels {
    private val TYPES: Map<DocumentType, String> = linkedMapOf(
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
    )

    /** The statuses, as the mandate's pills: the same [MandateRules.Tag] style the rest of the page uses. */
    private val STATUSES: Map<DocumentStatus, MandateRules.Tag> = mapOf(
        DocumentStatus.UPLOADED to MandateRules.Tag("Reçu", ""),
        DocumentStatus.EXTRACTING to MandateRules.Tag("En cours d'analyse", "info"),
        DocumentStatus.EXTRACTED to MandateRules.Tag("Analysé", "success"),
        DocumentStatus.FAILED to MandateRules.Tag("À corriger", "warning"),
    )

    /** The types that fit a landlord's listing: their identity and the property's papers. */
    private val LISTING_TYPES = listOf(
        DocumentType.NATIONAL_ID,
        DocumentType.PASSPORT,
        DocumentType.LAND_TITLE,
        DocumentType.CERTIFICATE_OF_OWNERSHIP,
        DocumentType.SURVEY_PLAN,
        DocumentType.POWER_OF_ATTORNEY,
    )

    /** The types that fit a client's search: their identity and their financing. */
    private val SEARCH_TYPES = listOf(
        DocumentType.NATIONAL_ID,
        DocumentType.PASSPORT,
        DocumentType.PAY_SLIP,
        DocumentType.BANK_PRE_APPROVAL,
        DocumentType.PROOF_OF_FUNDS,
    )

    /** A type's French name; [DocumentType.UNKNOWN] is never offered, and reads as the generic "Document". */
    fun type(type: DocumentType): String = TYPES[type] ?: TYPES.getValue(DocumentType.OTHER)

    /** The kind pill: the files the document was created from; a legacy one accepts either. */
    fun source(source: DocumentSource): String = when (source) {
        DocumentSource.IMAGE -> "Photos"
        DocumentSource.PDF -> "PDF"
        DocumentSource.UNKNOWN -> "Photos ou PDF"
    }

    /** An unknown status reads as "Reçu": the extraction pipeline's states (#330) come later. */
    fun status(status: DocumentStatus): MandateRules.Tag =
        STATUSES[status] ?: STATUSES.getValue(DocumentStatus.UPLOADED)

    /** The types the picker lists first, by the kind of mandate; a SEARCH's when the type isn't one. */
    fun suggested(mandateType: MandateType): List<DocumentType> =
        if (mandateType == MandateType.LISTING) LISTING_TYPES else SEARCH_TYPES

    /** Every other type, under the picker's "Autres types", in enum order with [DocumentType.OTHER] last. */
    fun others(mandateType: MandateType): List<DocumentType> {
        val suggested = suggested(mandateType).toSet()
        return TYPES.keys
            .filter { it != DocumentType.UNKNOWN && it != DocumentType.OTHER && it !in suggested }
            .plus(DocumentType.OTHER)
    }
}
