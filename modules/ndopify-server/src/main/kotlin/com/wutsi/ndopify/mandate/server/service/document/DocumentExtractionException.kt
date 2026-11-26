package com.wutsi.ndopify.mandate.server.service.document

/**
 * The vision model couldn't read a document: its answer was empty or a technical failure (timeout, model error). The
 * caller retries a technical failure in the run and ends `EXTRACTION_FAILED`; an answer the model did produce but
 * couldn't read is a `valid = false` [DocumentData], never this exception.
 */
class DocumentExtractionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
