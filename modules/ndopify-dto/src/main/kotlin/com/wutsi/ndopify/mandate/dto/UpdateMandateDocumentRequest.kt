package com.wutsi.ndopify.mandate.dto

/** Renames a document of a mandate: [version] is the one the client read, null to not check it. */
data class UpdateMandateDocumentRequest(val title: String = "", val version: Long? = null)
