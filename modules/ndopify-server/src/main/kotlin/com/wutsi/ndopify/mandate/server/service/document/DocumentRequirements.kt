package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType

/**
 * What a document of each [DocumentType] must say to be usable, read by [DocumentChecks]. A type without an extractor
 * ([DocumentType.UNKNOWN] and the financial documents #333 adds) has no requirements: it's never extracted.
 */
object DocumentRequirements {
    /**
     * [required] fields must be read with at least the minimum confidence; one of each [oneOf] group too;
     * [requiresExpiry] makes an unreadable expiry its own problem; [sides] must all be present among the pages.
     */
    data class Requirements(
        val required: Set<String> = emptySet(),
        val oneOf: List<Set<String>> = emptyList(),
        val requiresExpiry: Boolean = false,
        val sides: Set<String> = emptySet(),
    )

    /** What [type] must show; null for a type no extractor handles. */
    fun of(type: DocumentType): Requirements? = when (type) {
        DocumentType.NATIONAL_ID -> Requirements(
            required = setOf("number", "firstName", "lastName"),
            requiresExpiry = true,
            sides = setOf("FRONT", "BACK"),
        )
        DocumentType.PASSPORT -> Requirements(
            required = setOf("number", "firstName", "lastName"),
            requiresExpiry = true,
            sides = setOf("FRONT"),
        )
        DocumentType.LAND_TITLE,
        DocumentType.CERTIFICATE_OF_OWNERSHIP -> Requirements(required = setOf("titleNumber", "owners", "locality"))
        DocumentType.SURVEY_PLAN -> Requirements(required = setOf("parcelReference", "locality"))
        DocumentType.POWER_OF_ATTORNEY -> Requirements(required = setOf("grantor", "attorney", "purpose"))
        DocumentType.LEASE_CONTRACT,
        DocumentType.UTILITY_BILL,
        DocumentType.TAX_RECEIPT -> Requirements(
            required = setOf("holderName", "issueDate"),
            oneOf = listOf(setOf("address", "locality")),
        )
        DocumentType.OTHER -> Requirements(required = setOf("summary"))
        DocumentType.UNKNOWN,
        DocumentType.PAY_SLIP,
        DocumentType.BANK_PRE_APPROVAL,
        DocumentType.PROOF_OF_FUNDS -> null
    }
}
