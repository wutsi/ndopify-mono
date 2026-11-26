package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.UpdateMandateDocumentRequest
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentPageEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.upload.DirectUploadService
import com.wutsi.ndopify.platform.util.MimeUtils
import com.wutsi.ndopify.refdata.dto.StorageType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.time.Clock
import java.util.Date
import java.util.UUID

/**
 * The documents a mandate's agent attaches to it: identity cards, payslips, land titles... A document's files are
 * private, never served from a public URL: each page is read through a short-lived presigned URL ([pageUrl]), and its
 * storage path is never handed out.
 */
@Service
class MandateDocumentService(
    private val mandateService: MandateService,
    private val mandateDao: MandateRepository,
    private val access: MandateAccessService,
    private val documentDao: MandateDocumentRepository,
    private val pageDao: MandateDocumentPageRepository,
    private val storageProvider: StorageServiceProvider,
    private val directUpload: DirectUploadService,
    private val tx: TransactionTemplate,
    private val clock: Clock,

    @param:Value("\${ndopify.mandate.document.url-ttl-seconds}") private val urlTTL: Int,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateDocumentService::class.java)

        /** The storage folder of a mandate's documents: the presign and the upload share it. */
        fun prefix(mandateId: Long) = "mandate/$mandateId/documents"
    }

    /**
     * The documents of a mandate, newest first.
     */
    fun search(mandateId: Long): List<MandateDocumentEntity> = documentDao.findByMandateIdOrderByIdDesc(mandateId)

    /**
     * A mandate's document, 404 [ErrorCode.MANDATE_DOCUMENT_NOT_FOUND] when unknown or when [documentId]'s mandate is
     * not [mandate].
     */
    fun findDocument(mandate: MandateEntity, documentId: Long): MandateDocumentEntity {
        val document = documentDao.findById(documentId).orElse(null)
        if (document == null || document.mandateId != mandate.id) {
            throw NotFoundException(Error(code = ErrorCode.MANDATE_DOCUMENT_NOT_FOUND))
        }
        return document
    }

    /**
     * A page of [document], 404 [ErrorCode.MANDATE_DOCUMENT_PAGE_NOT_FOUND] when unknown or when [pageId]'s document is
     * not [document].
     */
    fun findPage(document: MandateDocumentEntity, pageId: Long): MandateDocumentPageEntity {
        val page = pageDao.findById(pageId).orElse(null)
        if (page == null || page.documentId != document.id) {
            throw NotFoundException(Error(code = ErrorCode.MANDATE_DOCUMENT_PAGE_NOT_FOUND))
        }
        return page
    }

    /**
     * The pages of each document, keyed by document id, ordered by page index.
     */
    fun findPages(documentIds: Collection<Long>): Map<Long, List<MandateDocumentPageEntity>> {
        if (documentIds.isEmpty()) {
            return emptyMap()
        }
        return pageDao.findByDocumentIdInOrderByPageIndex(documentIds)
            .groupBy { page -> page.documentId }
    }

    /**
     * A short-lived URL to read [page]. A legacy page with no storage type was written to the default storage.
     */
    fun pageUrl(page: MandateDocumentPageEntity): String {
        val storage = if (page.storageType == StorageType.UNKNOWN) {
            storageProvider.get()
        } else {
            storageProvider.get(page.storageType)
        }
        return storage.generatePresignedUrl(page.path, urlTTL).toString()
    }

    /**
     * A short-lived URL to upload a page straight to the storage. Errors, in order: 404 [ErrorCode.MANDATE_NOT_FOUND],
     * 403 [ErrorCode.MANDATE_ACCESS_DENIED], 409 [ErrorCode.MANDATE_ALREADY_CLOSED], then 400
     * [ErrorCode.HTTP_INVALID_PARAMETER] (`contentType`, by [DirectUploadService.presign]) for a type that isn't JPEG,
     * PNG or WEBP. No URL when the storage can't presign (local): the caller falls back to [upload].
     */
    fun presign(mandateId: Long, contentType: String?): PresignUploadResponse {
        ensureWritable(mandateId)
        return directUpload.presign(prefix(mandateId), contentType)
    }

    /**
     * Creates a document from files already uploaded straight to the storage ([CreateMandateDocumentRequest.paths], in
     * page order). Errors, in order: 404 [ErrorCode.MANDATE_NOT_FOUND], 403 [ErrorCode.MANDATE_ACCESS_DENIED], 409
     * [ErrorCode.MANDATE_ALREADY_CLOSED] (closed or cancelled), then 400 [ErrorCode.HTTP_INVALID_PARAMETER]: `type`
     * UNKNOWN; `title` longer than 100 characters after trim; `paths` empty or with duplicates; a path a page already
     * has; then each `verify(path, prefix(mandateId))` (path outside the mandate's folder, missing object, over 5 MB,
     * bytes not a JPEG/PNG/WEBP or not matching the extension). Saved through [saveLocked]: the document UPLOADED with
     * the trimmed title (or the type's label when blank) and pages indexed 0..n-1 in the given order with the default
     * storage's type. A failed save deletes every new object no page references.
     */
    fun create(mandateId: Long, request: CreateMandateDocumentRequest): MandateDocumentEntity {
        ensureWritable(mandateId)

        val type = request.type
        if (type == DocumentType.UNKNOWN) {
            throw invalid("type", type.name)
        }
        val title = request.title?.trim()?.ifEmpty { null }
        if (title != null && title.length > 100) {
            throw invalid("title", title)
        }
        val paths = request.paths
        if (paths.isEmpty()) {
            throw invalid("paths", "")
        }
        if (paths.distinct().size != paths.size) {
            throw invalid("paths", paths.joinToString(","))
        }
        val prefix = prefix(mandateId)
        paths.forEach { path ->
            if (pageDao.findByPath(path) != null) {
                throw invalid("path", path)
            }
        }

        // Storage I/O outside any transaction
        val storage = storageProvider.get()
        val mimeTypes = paths.map { path -> directUpload.verify(path, prefix) }
        val now = Date(clock.millis())

        val document = MandateDocumentEntity(
            mandateId = mandateId,
            type = type,
            title = title ?: MandateDocumentLabels.title(type),
            status = DocumentStatus.UPLOADED,
            createdAt = now,
            modifiedAt = now,
        )
        val pages = paths.mapIndexed { index, path ->
            MandateDocumentPageEntity(
                pageIndex = index,
                path = path,
                storageType = storage.type(),
                mimeType = mimeTypes[index],
                createdAt = now,
                modifiedAt = now,
            )
        }

        return try {
            saveLocked(mandateId) {
                // The authoritative path check, under the mandate's lock: a concurrent create may have committed the
                // same path since the pre-lock check above (a UNIQUE(path) violation would otherwise surface as a 500).
                paths.forEach { path ->
                    if (pageDao.findByPath(path) != null) {
                        throw invalid("path", path)
                    }
                }
                val saved = documentDao.save(document)
                pages.forEach { page -> pageDao.save(page.copy(documentId = saved.id!!)) }
                saved
            }
        } catch (ex: Exception) {
            deleteUnreferenced(mandateId, paths, storage.type())
            throw ex
        }
    }

    /**
     * Creates a document from files posted as multipart, storing each one at a new path under the mandate's folder.
     * Same checks as [create], plus `files` empty → 400 (`files`), and each file checked by [DirectUploadService.check]
     * before it's stored (outside any transaction).
     */
    fun upload(mandateId: Long, type: DocumentType, title: String?, files: List<MultipartFile>): MandateDocumentEntity {
        ensureWritable(mandateId)

        if (type == DocumentType.UNKNOWN) {
            throw invalid("type", type.name)
        }
        val trimmedTitle = title?.trim()?.ifEmpty { null }
        if (trimmedTitle != null && trimmedTitle.length > 100) {
            throw invalid("title", trimmedTitle)
        }
        if (files.isEmpty()) {
            throw invalid("files", "")
        }

        // Storage I/O outside any transaction
        val storage = storageProvider.get()
        val prefix = prefix(mandateId)
        val now = Date(clock.millis())
        val pages = files.mapIndexed { index, file ->
            val mimeType = directUpload.check(file)
            val path = "$prefix/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(mimeType)}"
            storage.store(path, file.inputStream, mimeType)
            MandateDocumentPageEntity(
                pageIndex = index,
                path = path,
                storageType = storage.type(),
                mimeType = mimeType,
                createdAt = now,
                modifiedAt = now,
            )
        }

        val document = MandateDocumentEntity(
            mandateId = mandateId,
            type = type,
            title = trimmedTitle ?: MandateDocumentLabels.title(type),
            status = DocumentStatus.UPLOADED,
            createdAt = now,
            modifiedAt = now,
        )

        return try {
            saveLocked(mandateId) {
                // No path re-check: the paths are freshly generated UUIDs, so two requests can never collide.
                val saved = documentDao.save(document)
                pages.forEach { page -> pageDao.save(page.copy(documentId = saved.id!!)) }
                saved
            }
        } catch (ex: Exception) {
            deleteUnreferenced(mandateId, pages.map { it.path }, storage.type())
            throw ex
        }
    }

    /**
     * Renames a document. Errors, in order: 404 [ErrorCode.MANDATE_NOT_FOUND], 403 [ErrorCode.MANDATE_ACCESS_DENIED],
     * 404 [ErrorCode.MANDATE_DOCUMENT_NOT_FOUND], 409 [ErrorCode.MANDATE_ALREADY_CLOSED], 409
     * [ErrorCode.MANDATE_DOCUMENT_VERSION_CONFLICT] when [request]'s version isn't the entity's, then 400
     * [ErrorCode.HTTP_INVALID_PARAMETER] (`title`) blank or longer than 100 characters after trim. Only the title and
     * [MandateDocumentEntity.modifiedAt] change: the status, pages and extracted data are kept. Saved through
     * [saveLocked].
     */
    fun rename(mandateId: Long, documentId: Long, request: UpdateMandateDocumentRequest): MandateDocumentEntity {
        val mandate = loadWritable(mandateId)
        val document = findDocument(mandate, documentId)
        ensureNotClosed(mandate)

        if (request.version != null && request.version != document.version) {
            throw ConflictException(Error(ErrorCode.MANDATE_DOCUMENT_VERSION_CONFLICT))
        }
        val title = request.title.trim()
        if (title.isEmpty() || title.length > 100) {
            throw invalid("title", title)
        }

        return saveLocked(mandateId) {
            documentDao.save(document.copy(title = title, modifiedAt = Date(clock.millis())))
        }
    }

    /**
     * Adds a page already uploaded to the storage to [documentId]'s document. Errors, in order: 404
     * [ErrorCode.MANDATE_NOT_FOUND], 403 [ErrorCode.MANDATE_ACCESS_DENIED], 404 [ErrorCode.MANDATE_DOCUMENT_NOT_FOUND],
     * 409 [ErrorCode.MANDATE_ALREADY_CLOSED]; a page of this document already at [path] is returned as is (idempotent,
     * nothing else changes); a [path] another page has → 400 [ErrorCode.HTTP_INVALID_PARAMETER] (`path`); then
     * `verify(path, prefix(mandateId))` (see [create]). Saved through [saveLocked] at `findMaxPageIndex + 1`, the
     * document reset. A failed save deletes the new object unless a page references it.
     */
    fun addPage(mandateId: Long, documentId: Long, path: String): MandateDocumentPageEntity {
        val mandate = loadWritable(mandateId)
        val document = findDocument(mandate, documentId)
        ensureNotClosed(mandate)

        val existing = pageDao.findByPath(path)
        if (existing != null) {
            if (existing.documentId == document.id) {
                return existing
            }
            throw invalid("path", path)
        }

        // Storage I/O outside any transaction
        val storage = storageProvider.get()
        val mimeType = directUpload.verify(path, prefix(mandateId))
        val page = MandateDocumentPageEntity(
            documentId = document.id ?: -1,
            pageIndex = 0,
            path = path,
            storageType = storage.type(),
            mimeType = mimeType,
            createdAt = Date(clock.millis()),
            modifiedAt = Date(clock.millis()),
        )

        return try {
            saveLocked(mandateId) {
                // The authoritative path check, under the mandate's lock: a concurrent add may have committed since
                // the pre-check above (which only saves the storage I/O of [DirectUploadService.verify]).
                val current = pageDao.findByPath(path)
                if (current != null) {
                    if (current.documentId == document.id) {
                        return@saveLocked current
                    }
                    throw invalid("path", path)
                }
                val saved = pageDao.save(page.copy(pageIndex = (pageDao.findMaxPageIndex(document.id ?: -1) ?: -1) + 1))
                documentDao.save(resetExtraction(document))
                saved
            }
        } catch (ex: Exception) {
            deleteUnreferenced(mandateId, listOf(path), storage.type())
            throw ex
        }
    }

    /**
     * Adds a page posted as multipart to [documentId]'s document, storing it at a new path under the mandate's folder.
     * Same checks as [addPage], the file checked by [DirectUploadService.check] and stored outside any transaction.
     */
    fun uploadPage(mandateId: Long, documentId: Long, file: MultipartFile): MandateDocumentPageEntity {
        val mandate = loadWritable(mandateId)
        val document = findDocument(mandate, documentId)
        ensureNotClosed(mandate)

        // Storage I/O outside any transaction
        val storage = storageProvider.get()
        val mimeType = directUpload.check(file)
        val path = "${prefix(mandateId)}/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(mimeType)}"
        storage.store(path, file.inputStream, mimeType)
        val page = MandateDocumentPageEntity(
            documentId = document.id ?: -1,
            pageIndex = 0,
            path = path,
            storageType = storage.type(),
            mimeType = mimeType,
            createdAt = Date(clock.millis()),
            modifiedAt = Date(clock.millis()),
        )

        return try {
            saveLocked(mandateId) {
                val saved = pageDao.save(page.copy(pageIndex = (pageDao.findMaxPageIndex(document.id ?: -1) ?: -1) + 1))
                documentDao.save(resetExtraction(document))
                saved
            }
        } catch (ex: Exception) {
            deleteUnreferenced(mandateId, listOf(path), storage.type())
            throw ex
        }
    }

    /**
     * Replaces a page's file with one already uploaded to the storage, keeping its `pageIndex`. Errors, in order: 404
     * [ErrorCode.MANDATE_NOT_FOUND], 403 [ErrorCode.MANDATE_ACCESS_DENIED], 404 [ErrorCode.MANDATE_DOCUMENT_NOT_FOUND],
     * 404 [ErrorCode.MANDATE_DOCUMENT_PAGE_NOT_FOUND], 409 [ErrorCode.MANDATE_ALREADY_CLOSED]; the page already at
     * [path] is returned as is (idempotent); a [path] another page has → 400 [ErrorCode.HTTP_INVALID_PARAMETER]
     * (`path`); then `verify(path, prefix(mandateId))`. Saved through [saveLocked]: the page keeps its `pageIndex` and
     * gets `path` / `storageType` / `mimeType`, the document reset; after the commit the old object is deleted with the
     * old page's own storage type (default storage when `UNKNOWN`). A failed save deletes the new object unless a page
     * references it.
     */
    fun replacePage(mandateId: Long, documentId: Long, pageId: Long, path: String): MandateDocumentPageEntity {
        val mandate = loadWritable(mandateId)
        val document = findDocument(mandate, documentId)
        val page = findPage(document, pageId)
        ensureNotClosed(mandate)

        val existing = pageDao.findByPath(path)
        if (existing != null) {
            if (existing.id == page.id) {
                return existing
            }
            throw invalid("path", path)
        }

        // Storage I/O outside any transaction
        val storage = storageProvider.get()
        val mimeType = directUpload.verify(path, prefix(mandateId))
        val oldPath = page.path
        val oldStorageType = page.storageType
        val replaced = page.copy(
            path = path,
            storageType = storage.type(),
            mimeType = mimeType,
            modifiedAt = Date(clock.millis()),
        )

        val saved = try {
            saveLocked(mandateId) {
                // The authoritative path check, under the mandate's lock: a concurrent write may have committed since
                // the pre-check above.
                val current = pageDao.findByPath(path)
                if (current != null) {
                    if (current.id == page.id) {
                        return@saveLocked current
                    }
                    throw invalid("path", path)
                }
                val s = pageDao.save(replaced)
                documentDao.save(resetExtraction(document))
                s
            }
        } catch (ex: Exception) {
            deleteUnreferenced(mandateId, listOf(path), storage.type())
            throw ex
        }
        deleteQuietly(mandateId, oldPath, oldStorageType)
        return saved
    }

    /**
     * Replaces a page's file with one posted as multipart, storing it at a new path under the mandate's folder. Same
     * checks as [replacePage], the file checked by [DirectUploadService.check] and stored outside any transaction.
     */
    fun uploadReplacementPage(
        mandateId: Long,
        documentId: Long,
        pageId: Long,
        file: MultipartFile,
    ): MandateDocumentPageEntity {
        val mandate = loadWritable(mandateId)
        val document = findDocument(mandate, documentId)
        val page = findPage(document, pageId)
        ensureNotClosed(mandate)

        // Storage I/O outside any transaction
        val storage = storageProvider.get()
        val mimeType = directUpload.check(file)
        val path = "${prefix(mandateId)}/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(mimeType)}"
        storage.store(path, file.inputStream, mimeType)
        val oldPath = page.path
        val oldStorageType = page.storageType
        val replaced = page.copy(
            path = path,
            storageType = storage.type(),
            mimeType = mimeType,
            modifiedAt = Date(clock.millis()),
        )

        val saved = try {
            saveLocked(mandateId) {
                val s = pageDao.save(replaced)
                documentDao.save(resetExtraction(document))
                s
            }
        } catch (ex: Exception) {
            deleteUnreferenced(mandateId, listOf(path), storage.type())
            throw ex
        }
        deleteQuietly(mandateId, oldPath, oldStorageType)
        return saved
    }

    /**
     * Deletes a page of a document. Errors, in order: 404 [ErrorCode.MANDATE_NOT_FOUND], 403
     * [ErrorCode.MANDATE_ACCESS_DENIED], 404 [ErrorCode.MANDATE_DOCUMENT_NOT_FOUND], 404
     * [ErrorCode.MANDATE_DOCUMENT_PAGE_NOT_FOUND], 409 [ErrorCode.MANDATE_ALREADY_CLOSED]; the document's only page → 400
     * [ErrorCode.MANDATE_DOCUMENT_LAST_PAGE]. The row is deleted in [saveLocked], the other pages keep their `pageIndex`,
     * the document reset; after the commit the object is deleted with the page's own storage type (default storage when
     * `UNKNOWN`). A failed deletion is logged only.
     */
    fun deletePage(mandateId: Long, documentId: Long, pageId: Long) {
        val mandate = loadWritable(mandateId)
        val document = findDocument(mandate, documentId)
        val page = findPage(document, pageId)
        ensureNotClosed(mandate)

        val oldPath = page.path
        val oldStorageType = page.storageType

        saveLocked(mandateId) {
            // The last-page check under the mandate's lock: two concurrent deletes of a 2-page document must not both
            // pass (a close committed during the storage I/O is already refused here too).
            if (pageDao.findByDocumentIdOrderByPageIndex(document.id!!).size == 1) {
                throw BadRequestException(Error(ErrorCode.MANDATE_DOCUMENT_LAST_PAGE))
            }
            pageDao.delete(page)
            documentDao.save(resetExtraction(document))
        }
        deleteQuietly(mandateId, oldPath, oldStorageType)
    }

    /**
     * Deletes a document and all its pages. Errors, in order: 404 [ErrorCode.MANDATE_NOT_FOUND], 403
     * [ErrorCode.MANDATE_ACCESS_DENIED], 404 [ErrorCode.MANDATE_DOCUMENT_NOT_FOUND], 409
     * [ErrorCode.MANDATE_ALREADY_CLOSED]. The pages then the document are deleted in [saveLocked]; after the commit
     * every page's object is deleted with its own storage type (default storage when `UNKNOWN`). A failed deletion is
     * logged only.
     */
    fun delete(mandateId: Long, documentId: Long) {
        val mandate = loadWritable(mandateId)
        val document = findDocument(mandate, documentId)
        ensureNotClosed(mandate)

        val pages = saveLocked(mandateId) {
            // Re-read the pages under the lock: a page added concurrently after the document was loaded must be
            // deleted too (deleting the document while a page still references it would violate the page FK).
            val pages = pageDao.findByDocumentIdOrderByPageIndex(document.id!!)
            pageDao.deleteAll(pages)
            documentDao.delete(document)
            pages
        }
        pages.forEach { page -> deleteQuietly(mandateId, page.path, page.storageType) }
    }

    /**
     * The mandate the caller may add documents to: 404 [ErrorCode.MANDATE_NOT_FOUND], 403
     * [ErrorCode.MANDATE_ACCESS_DENIED] but for its agent (or an admin), 409 [ErrorCode.MANDATE_ALREADY_CLOSED] once
     * closed or cancelled.
     */
    private fun ensureWritable(mandateId: Long): MandateEntity {
        val mandate = loadWritable(mandateId)
        ensureNotClosed(mandate)
        return mandate
    }

    /**
     * The mandate the caller may write to: 404 [ErrorCode.MANDATE_NOT_FOUND], 403 [ErrorCode.MANDATE_ACCESS_DENIED].
     * No closed check here: a document/page write must find its document or page first (404) before the 409
     * [ErrorCode.MANDATE_ALREADY_CLOSED].
     */
    private fun loadWritable(mandateId: Long): MandateEntity {
        val mandate = mandateService.findById(mandateId)
        access.ensureCanUpdate(mandate)
        return mandate
    }

    /**
     * Runs [block] in a transaction holding the mandate's lock, re-checking that it isn't closed or cancelled (a close
     * committed during the storage I/O wins). Shared with Task 4.
     */
    private fun <T> saveLocked(mandateId: Long, block: () -> T): T = tx.execute {
        val mandate = mandateDao.findByIdForUpdate(mandateId)
            ?: throw NotFoundException(Error(code = ErrorCode.MANDATE_NOT_FOUND))
        ensureNotClosed(mandate)
        block()
    }!!

    private fun ensureNotClosed(mandate: MandateEntity) {
        if (mandate.status == MandateStatus.CLOSED || mandate.status == MandateStatus.CANCELLED) {
            throw ConflictException(Error(ErrorCode.MANDATE_ALREADY_CLOSED))
        }
    }

    /** The document after a page change: UPLOADED with its extracted fields cleared. */
    private fun resetExtraction(document: MandateDocumentEntity): MandateDocumentEntity = document.copy(
        status = DocumentStatus.UPLOADED,
        problemCodes = emptyList(),
        data = null,
        dataVersion = null,
        issuedAt = null,
        expiresAt = null,
        modifiedAt = Date(clock.millis()),
    )

    private fun invalid(name: String, value: Any?) = BadRequestException(
        Error(
            code = ErrorCode.HTTP_INVALID_PARAMETER,
            parameter = Parameter(name = name, type = ParameterType.PARAMETER_TYPE_PAYLOAD, value = value),
        ),
    )

    /** After a failed save: deletes each new object no page references (a concurrent save of the same path). */
    private fun deleteUnreferenced(mandateId: Long, paths: List<String>, storageType: StorageType) {
        paths.forEach { path ->
            if (pageDao.findByPath(path) == null) {
                deleteQuietly(mandateId, path, storageType)
            }
        }
    }

    /**
     * Deletes an object no row references any more (a legacy page's object is in the default storage); a failure is
     * logged, not fatal.
     */
    private fun deleteQuietly(mandateId: Long, path: String, storageType: StorageType) {
        val storage = if (storageType == StorageType.UNKNOWN) {
            storageProvider.get()
        } else {
            storageProvider.get(storageType)
        }
        try {
            storage.delete(path)
        } catch (ex: Exception) {
            LOGGER.warn(">>> Mandate#$mandateId - Could not delete $path", ex)
        }
    }
}
