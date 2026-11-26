package com.wutsi.ndopify.mandate.dto

/** One document of a mandate, with its pages and extraction. */
data class GetMandateDocumentResponse(val document: MandateDocument = MandateDocument())
