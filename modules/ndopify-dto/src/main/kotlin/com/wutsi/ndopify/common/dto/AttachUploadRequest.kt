package com.wutsi.ndopify.common.dto

/** Attaches the file uploaded at [path] (from a [PresignUploadResponse]) to the resource of the endpoint. */
data class AttachUploadRequest(val path: String = "")
