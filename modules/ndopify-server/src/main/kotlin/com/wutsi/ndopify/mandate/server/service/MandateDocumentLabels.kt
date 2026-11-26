package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.mandate.dto.DocumentType

/** The French label a document of a given type gets when its title is left blank. */
object MandateDocumentLabels {
    fun title(type: DocumentType): String = when (type) {
        DocumentType.NATIONAL_ID -> "Carte nationale d'identité"
        DocumentType.PASSPORT -> "Passeport"
        DocumentType.LAND_TITLE -> "Titre foncier"
        DocumentType.CERTIFICATE_OF_OWNERSHIP -> "Certificat de propriété"
        DocumentType.SURVEY_PLAN -> "Plan de bornage"
        DocumentType.POWER_OF_ATTORNEY -> "Procuration"
        DocumentType.LEASE_CONTRACT -> "Contrat de bail"
        DocumentType.UTILITY_BILL -> "Facture d'eau ou d'électricité"
        DocumentType.TAX_RECEIPT -> "Quittance d'impôt"
        DocumentType.PAY_SLIP -> "Bulletin de paie"
        DocumentType.BANK_PRE_APPROVAL -> "Accord de principe bancaire"
        DocumentType.PROOF_OF_FUNDS -> "Justificatif de fonds"
        DocumentType.OTHER,
        DocumentType.UNKNOWN,
            -> "Document"
    }
}
