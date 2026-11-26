package com.wutsi.ndopify.mandate.dto

/**
 * Where a document stands in the extraction pipeline. There is no `EXPIRED`: whether a document is still valid is
 * [MandateDocument.expiresAt] against the reader's clock, not a status (#327).
 */
enum class DocumentStatus {
    UNKNOWN,
    UPLOADED,
    EXTRACTING,
    EXTRACTED,
    FAILED,
}
