package com.wutsi.ndopify.mandate.dto

/**
 * Creates a document of a mandate from files already uploaded: [paths] are the `path`s of the
 * `PresignUploadResponse`s, in page order. [title] defaults to the type's own French label when blank.
 */
data class CreateMandateDocumentRequest(
    val type: DocumentType = DocumentType.UNKNOWN,
    val title: String? = null,
    val paths: List<String> = emptyList(),
)
