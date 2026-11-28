package com.wutsi.ndopify.mandate.server.service

/** The reason codes of an ownership verdict; plain strings, like `DocumentProblemCodes` (no DTO release per code). */
object OwnershipReasonCodes {
    const val ID_MISSING = "OWNERSHIP_ID_MISSING"
    const val ID_UNUSABLE = "OWNERSHIP_ID_UNUSABLE"
    const val ID_EXPIRED = "OWNERSHIP_ID_EXPIRED"
    const val ID_NAME_MISMATCH = "OWNERSHIP_ID_NAME_MISMATCH"
    const val DOCUMENT_MISSING = "OWNERSHIP_DOCUMENT_MISSING"
    const val DOCUMENT_UNUSABLE = "OWNERSHIP_DOCUMENT_UNUSABLE"
    const val DOCUMENT_NAME_MISMATCH = "OWNERSHIP_DOCUMENT_NAME_MISMATCH"
    const val PROPERTY_MISMATCH = "OWNERSHIP_PROPERTY_MISMATCH"
}
