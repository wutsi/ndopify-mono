package com.wutsi.ndopify.mandate.dto

import java.util.Date

/**
 * One image page of a [MandateDocument], [index] 0-based in the document. [url] is presigned and short-lived: the
 * page's storage path is never handed out.
 */
data class MandateDocumentPage(
    val id: Long = -1,
    val index: Int = 0,
    val contentType: String? = null,
    val url: String = "",
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
