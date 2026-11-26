package com.wutsi.ndopify.mandate.dto

/** One field the extraction read on a [MandateDocument] — its [key], its [value] as read, and how sure it was. */
data class DocumentField(val key: String = "", val value: String? = null, val confidence: Double? = null)
