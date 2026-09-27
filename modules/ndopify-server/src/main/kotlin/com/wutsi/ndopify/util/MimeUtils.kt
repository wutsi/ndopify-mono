package com.wutsi.ndopify.util

object MimeUtils {
    fun getExtensionFromMimeType(contentType: String?): String {
        if (contentType == null) return ".bin" // Default for unknown data

        // Remove any parameters like charset (e.g., "image/jpeg; charset=UTF-8")
        val pureMimeType = contentType.split(";")[0].trim().lowercase()

        return when (pureMimeType) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "application/pdf" -> ".pdf"
            "text/html" -> ".html"
            "text/plain" -> ".txt"
            "application/json" -> ".json"
            else -> ".bin"
        }
    }

    fun getMimeTypeFromExtension(extension: String?): String {
        if (extension == null) return "application/octet-stream" // Default for unknown data

        // Normalize extension: remove leading dot, trim, and convert to lowercase
        val normalizedExtension = extension.trim().lowercase().trimStart('.')

        return when (normalizedExtension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "html", "htm" -> "text/html"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }
}
