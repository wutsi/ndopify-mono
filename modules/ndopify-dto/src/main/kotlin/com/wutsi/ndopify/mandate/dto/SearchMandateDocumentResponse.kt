package com.wutsi.ndopify.mandate.dto

/** The documents of a mandate, newest first. */
data class SearchMandateDocumentResponse(val documents: List<MandateDocument> = emptyList())
