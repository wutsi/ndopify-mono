package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.InternalErrorException
import com.wutsi.ndopify.platform.image.ImagePreviewService
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.StorageType
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The display copy of a mandate page: the resized, watermarked JPEG a browser may see, stored next to the private
 * original at `<path>.preview-v1.jpg` in the page's own storage (the default one for a legacy `UNKNOWN` page). No
 * database column: the path is derived, and a page created before this feature gets its preview on its first view.
 *
 * Generation happens at write time ([generate], called by [MandateDocumentService]) and lazily in [read], under a
 * per-path in-process lock so a local storage reader never sees a half-written file. A generation or storage failure
 * at read fails closed: a 500 whose log names the mandate id and the exception class only, never a path or a URL.
 */
@Service
class MandateDocumentPreviewService(
    private val storageProvider: StorageServiceProvider,
    private val imagePreview: ImagePreviewService,
    private val tenantService: TenantService,
) {
    companion object {
        const val PREVIEW_SUFFIX = ".preview-v1.jpg"
        private const val CONTENT_TYPE = "image/jpeg"
        private const val LOCKS = 64 // striped locks: one per path hash, no unbounded map to leak

        private val LOGGER = LoggerFactory.getLogger(MandateDocumentPreviewService::class.java)
        private val LOCK_STRIPES = Array(LOCKS) { Any() }

        /** The derived path of a page's preview; bump the suffix (v2...) when the pipeline's output changes. */
        fun previewPath(path: String): String = path + PREVIEW_SUFFIX
    }

    /**
     * Builds the preview of the original at [path] (or of its [original] bytes when given), stores it next to the
     * original and returns it. Propagates [com.wutsi.ndopify.platform.image.UnprocessableImageException] and a
     * storage failure to the caller, which decides what to refuse.
     */
    fun generate(
        mandateId: Long,
        storage: StorageService,
        path: String,
        mimeType: String?,
        original: ByteArray? = null,
    ): ByteArray {
        val source = original ?: readObject(storage, path)
        val preview = imagePreview.preview(source, mimeType, watermark(mandateId))
        storage.store(previewPath(path), ByteArrayInputStream(preview), CONTENT_TYPE)
        return preview
    }

    /**
     * The preview of a page: the stored one when it exists, else generated once and stored first. A generation or
     * storage failure is a 500 [ErrorCode.HTTP_INTERNAL] without the original cause, so no path reaches a log.
     */
    fun read(mandateId: Long, path: String, storageType: StorageType, mimeType: String?): ByteArray {
        val storage = storageFor(storageType)
        synchronized(lock(path)) {
            try {
                if (storage.metadata(previewPath(path)) != null) {
                    return readObject(storage, previewPath(path))
                }
                return generate(mandateId, storage, path, mimeType)
            } catch (ex: Exception) {
                LOGGER.error(">>> Mandate#$mandateId - page preview failed (${ex.javaClass.name})")
                throw InternalErrorException(Error(ErrorCode.HTTP_INTERNAL))
            }
        }
    }

    /** Deletes a page's preview; a failure is logged, never fatal (the page or document is already gone). */
    fun deleteQuietly(mandateId: Long, path: String, storageType: StorageType) {
        try {
            storageFor(storageType).delete(previewPath(path))
        } catch (ex: Exception) {
            LOGGER.warn(">>> Mandate#$mandateId - could not delete a page preview (${ex.javaClass.name})")
        }
    }

    private fun storageFor(storageType: StorageType): StorageService =
        if (storageType == StorageType.UNKNOWN) {
            storageProvider.get()
        } else {
            storageProvider.get(storageType)
        }

    private fun lock(path: String): Any = LOCK_STRIPES[(path.hashCode() and Int.MAX_VALUE) % LOCKS]

    private fun readObject(storage: StorageService, path: String): ByteArray {
        val out = ByteArrayOutputStream()
        storage.get(path, out)
        return out.toByteArray()
    }

    private fun watermark(mandateId: Long): String {
        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant in context")
        return "${tenantService.findById(tenantId).name} - Mandat n° $mandateId"
    }
}
