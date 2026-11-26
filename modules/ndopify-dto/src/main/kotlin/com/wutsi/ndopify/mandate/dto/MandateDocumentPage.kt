package com.wutsi.ndopify.mandate.dto

import java.util.Date

/**
 * One image page of a [MandateDocument], [index] 0-based in the document. Its file is private: no URL and no storage
 * path are handed out. The caller builds the page's authenticated preview route from the mandate, document and page
 * ids.
 */
data class MandateDocumentPage(
    val id: Long = -1,
    val index: Int = 0,
    val contentType: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
