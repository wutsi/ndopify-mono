package com.wutsi.ndopify.mandate.dto

import java.time.LocalDate
import java.util.Date

/**
 * A document attached to a mandate — an identity card, a payslip, a land title... — with its [pages] and, once
 * extracted, the [fields] read on it and the [problemCodes] the extraction reported. It is never part of [Mandate]
 * nor [MandateSummary]: only the document endpoints of a mandate return it.
 */
data class MandateDocument(
    val id: Long = -1,
    val mandateId: Long = -1,
    val type: DocumentType = DocumentType.UNKNOWN,
    val title: String = "",
    val status: DocumentStatus = DocumentStatus.UNKNOWN,

    /** The kind of files it was created from; [DocumentSource.UNKNOWN] for a document written before it was recorded. */
    val source: DocumentSource = DocumentSource.UNKNOWN,
    val problemCodes: List<String> = emptyList(),
    val fields: List<DocumentField> = emptyList(),
    val issuedAt: LocalDate? = null,
    val expiresAt: LocalDate? = null,
    val pages: List<MandateDocumentPage> = emptyList(),
    val version: Long = 0,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
