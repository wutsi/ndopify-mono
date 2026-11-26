package com.wutsi.ndopify.platform.image

/**
 * The bytes can't be turned into page images. [reason] tells the caller which refusal to answer: a PDF that needs a
 * password, one that can't be read or has no page, or one with more pages than the caller allows.
 */
class PdfRasterizationException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Reason { ENCRYPTED, UNREADABLE, EMPTY, TOO_MANY_PAGES }
}
