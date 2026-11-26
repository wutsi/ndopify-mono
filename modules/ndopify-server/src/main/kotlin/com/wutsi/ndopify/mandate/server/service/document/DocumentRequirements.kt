package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType

/**
 * What a document of each [DocumentType] must say to be usable, read by [DocumentChecks]. A type without an extractor
 * ([DocumentType.UNKNOWN]) has no requirements: it's never extracted.
 */
object DocumentRequirements {
    /**
     * [required] fields must be read with at least the minimum confidence; one of each [oneOf] group too;
     * [requiresExpiry] makes an unreadable expiry its own problem; [sides] must all be present among the pages.
     * [expiryField] is the required field that holds the expiry date, when there is one: it's checked by the expiry
     * rule alone, never as a generic field too.
     */
    data class Requirements(
        val required: Set<String> = emptySet(),
        val oneOf: List<Set<String>> = emptyList(),
        val requiresExpiry: Boolean = false,
        val sides: Set<String> = emptySet(),
        val expiryField: String? = null,
    )

    /** What [type] must show; null for a type no extractor handles. */
    fun of(type: DocumentType): Requirements? = when (type) {
        DocumentType.NATIONAL_ID -> Requirements(
            required = setOf("number", "firstName", "lastName"),
            requiresExpiry = true,
            sides = setOf("FRONT", "BACK"),
            expiryField = "expiryDate",
        )
        DocumentType.PASSPORT -> Requirements(
            required = setOf("number", "firstName", "lastName"),
            requiresExpiry = true,
            sides = setOf("FRONT"),
            expiryField = "expiryDate",
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
        DocumentType.PAY_SLIP -> Requirements(required = setOf("employeeName", "payMonth", "netSalary", "currency"))
        DocumentType.BANK_PRE_APPROVAL -> Requirements(
            required = setOf("bankName", "beneficiaries", "amount", "currency", "validityDate"),
            requiresExpiry = true,
            expiryField = "validityDate",
        )
        DocumentType.PROOF_OF_FUNDS -> Requirements(
            required = setOf("accountHolder", "amount", "currency", "statementDate"),
        )
        DocumentType.OTHER -> Requirements(required = setOf("summary"))
        DocumentType.UNKNOWN -> null
    }
}
