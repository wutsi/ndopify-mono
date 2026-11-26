package com.wutsi.ndopify.mandate.dto

/**
 * The files a document was created from: images ([IMAGE]), or one PDF rendered to image pages ([PDF]). [UNKNOWN] is
 * a document written before the kind was recorded: it accepts either kind on a page-level write.
 */
enum class DocumentSource {
    UNKNOWN,
    IMAGE,
    PDF,
}
