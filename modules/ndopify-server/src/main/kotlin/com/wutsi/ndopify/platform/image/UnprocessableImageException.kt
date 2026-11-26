package com.wutsi.ndopify.platform.image

/**
 * The bytes can't be turned into a display copy: not an image, an unsupported format, or more pixels than
 * [ImagePreviewService.MAX_PIXELS]. Callers fail closed (refuse the write, or a 500 at read): the original bytes are
 * never the answer.
 */
class UnprocessableImageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
