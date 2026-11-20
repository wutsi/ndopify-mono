package com.wutsi.ndopify.platform.storage.upload

import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.util.MimeUtils
import org.apache.tika.Tika
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Lets a browser upload a file straight to the storage, so its bytes never go through a dyno: [presign] hands out a
 * short-lived PUT URL for a new path under a prefix, and [verify] checks what was uploaded there before the caller
 * attaches it. No business rule here: the calling domain checks who may upload and when, and records the file.
 */
@Service
class DirectUploadService(
    private val storageProvider: StorageServiceProvider,
    @param:Value("\${ndopify.storage.upload-url-ttl-seconds}") private val ttl: Int,
) {
    companion object {
        val IMAGE_MIME_TYPES = listOf("image/jpeg", "image/png", "image/webp")
        const val MAX_BYTES = 5L * 1024 * 1024
        const val PREFIX_BYTES = 4096
        private val TIKA = Tika()
    }

    /**
     * A PUT URL for `<prefix>/<uuid>.<ext>`, signed for [contentType]. An empty response (HTTP 200) when the storage
     * can't presign (local): the caller falls back to the multipart upload.
     * Errors: 400 `HTTP_INVALID_PARAMETER` (`contentType`) when not JPEG, PNG or WEBP.
     */
    fun presign(prefix: String, contentType: String?): PresignUploadResponse {
        val mimeType = contentType?.split(";")?.first()?.trim()?.lowercase()
        if (mimeType !in IMAGE_MIME_TYPES) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    message = "$contentType is not a JPEG, PNG or WEBP image.",
                    parameter = Parameter(
                        name = "contentType",
                        type = ParameterType.PARAMETER_TYPE_PAYLOAD,
                        value = contentType,
                    ),
                )
            )
        }

        val path = "$prefix/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(mimeType)}"
        val url = storageProvider.get().presignedUploadUrl(path, mimeType!!, ttl)
            ?: return PresignUploadResponse()
        return PresignUploadResponse(url.toString(), path)
    }

    /**
     * Checks the file uploaded at [path] and returns its MIME type, detected from its bytes. Errors, in order, all
     * 400 `HTTP_INVALID_PARAMETER` (`path`): a path [presign] wouldn't have issued under [prefix]; no object; more than
     * [MAX_BYTES]; bytes that aren't JPEG/PNG/WEBP or don't match the path's extension. The object is deleted on the
     * last two, so a rejected file doesn't stay in the storage.
     */
    fun verify(path: String, prefix: String): String {
        if (!path.matches(Regex("^${Regex.escape(prefix)}/[0-9a-f-]{36}\\.(jpg|png|webp)$"))) {
            throw invalid(path, "$path is not a path issued for $prefix.")
        }

        val storage = storageProvider.get()
        val objectMetadata = storage.metadata(path)
            ?: throw invalid(path, "No file was uploaded at $path.")

        if (objectMetadata.contentLength > MAX_BYTES) {
            storage.delete(path)
            throw invalid(path, "The file at $path is larger than ${MAX_BYTES / (1024 * 1024)} MB.")
        }

        val mimeType = TIKA.detect(storage.readPrefix(path, PREFIX_BYTES))
        if (mimeType !in IMAGE_MIME_TYPES || MimeUtils.getExtensionFromMimeType(mimeType) != path.substringAfterLast('.')) {
            storage.delete(path)
            throw invalid(path, "The file at $path is not a JPEG, PNG or WEBP image.")
        }
        return mimeType
    }

    private fun invalid(path: String, message: String) = BadRequestException(
        error = Error(
            code = ErrorCode.HTTP_INVALID_PARAMETER,
            message = message,
            parameter = Parameter(
                name = "path",
                type = ParameterType.PARAMETER_TYPE_PAYLOAD,
                value = path,
            ),
        )
    )
}
