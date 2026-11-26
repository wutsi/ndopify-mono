package com.wutsi.ndopify.mandate.server.service.document

/**
 * The codes of the problems an extraction reports, stored in the document's `problem_codes` as plain strings (no DTO
 * change). Any code but [DOCUMENT_EXPIRED] makes the document `FAILED`; [DOCUMENT_EXPIRED] is a warning, the document
 * stays `EXTRACTED` so the agent is told at once.
 */
object DocumentProblemCodes {
    /** The model recognized another type than the declared one; the document must be deleted and re-added. */
    const val DOCUMENT_TYPE_MISMATCH = "DOCUMENT_TYPE_MISMATCH"

    /** The model couldn't read the document (or it couldn't be decoded): a new page may fix it, a retry won't. */
    const val DOCUMENT_UNREADABLE = "DOCUMENT_UNREADABLE"

    /** A side the type needs is missing from the pages (an ID's back, a passport's data page). */
    const val DOCUMENT_PAGE_MISSING = "DOCUMENT_PAGE_MISSING"

    /** A required field is missing, or was read under `ndopify.mandate.document.extract.min-confidence`. */
    const val DOCUMENT_FIELD_LOW_CONFIDENCE = "DOCUMENT_FIELD_LOW_CONFIDENCE"

    /** A document whose expiry date is required can't be read (an identity document, a bank pre-approval). */
    const val EXPIRY_DATE_UNREADABLE = "EXPIRY_DATE_UNREADABLE"

    /**
     * Every attempt of the run failed technically (timeout, model error). The document stays `FAILED`: a page change
     * resets it to `UPLOADED`, and the per-document ops re-run reads it again.
     */
    const val EXTRACTION_FAILED = "EXTRACTION_FAILED"

    /** The warning of a readable document whose expiry date is past: `EXTRACTED` still. */
    const val DOCUMENT_EXPIRED = "DOCUMENT_EXPIRED"
}
