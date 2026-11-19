package com.wutsi.ndopify.common.dto

/** Asks for a short-lived URL to upload a file of [contentType] straight to the storage. */
data class PresignUploadRequest(val contentType: String? = null)
