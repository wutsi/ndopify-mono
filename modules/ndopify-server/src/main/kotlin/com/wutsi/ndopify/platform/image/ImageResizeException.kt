package com.wutsi.ndopify.platform.image

/**
 * The bytes can't be turned into the vision copy: not an image, an unsupported format, or more pixels than
 * [ResizeOptions.maxSourcePixels]. The caller fails closed (the document is `DOCUMENT_UNREADABLE`), never sends the
 * original nor a partial copy to the model.
 */
class ImageResizeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
