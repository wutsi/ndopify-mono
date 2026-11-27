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
import com.wutsi.ndopify.mandate.dto.DocumentSource
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
import com.wutsi.ndopify.platform.image.PdfRasterizationException
import com.wutsi.ndopify.platform.image.PdfRasterizer
import com.wutsi.ndopify.platform.image.UnprocessableImageException
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.upload.DirectUploadService
import com.wutsi.ndopify.platform.util.MimeUtils
import com.wutsi.ndopify.refdata.dto.StorageType
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.time.Clock
import java.util.Date
import java.util.UUID

/**
 * The documents a mandate's agent attaches to it: identity cards, payslips, land titles... A document's files are
 * private: every page has a watermarked display copy ([MandateDocumentPreviewService]), generated when the page is
 * written and read through the preview endpoint; neither the original nor its storage path is ever handed out.
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
    private val pdfRasterizer: PdfRasterizer,
    private val previewService: MandateDocumentPreviewService,
    private val tx: TransactionTemplate,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateDocumentService::class.java)

        /** The most pages one document may have; a PDF's pages count one each, an image one. */
        const val MAX_PAGES = 10

        /** The most documents one mandate may hold, every type together. */
        const val MAX_DOCUMENTS = 5

        private const val PDF_MIME_TYPE = "application/pdf"
        private const val JPEG_MIME_TYPE = "image/jpeg"
        private val TIKA = Tika()

        /** The storage folder of a mandate's documents: the presign and the upload share it. */
        fun prefix(mandateId: Long) = "mandate/$mandateId/documents"
    }

    /** One stored page to write: the bytes of an image, or of one page rendered from a PDF. */
    private data class PageSource(val bytes: ByteArray, val mimeType: String)

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
     * The display copy of [page]: its watermarked JPEG, generated lazily when it isn't stored yet. A failure is a 500
     * [ErrorCode.HTTP_INTERNAL] ([MandateDocumentPreviewService.read]), never the original bytes.
     */
    fun preview(mandate: MandateEntity, page: MandateDocumentPageEntity): ByteArray =
        previewService.read(mandate.id ?: -1, page.path, page.storageType, page.mimeType)

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
     * [ErrorCode.MANDATE_ALREADY_CLOSED] (closed or cancelled), 400
     * [ErrorCode.MANDATE_DOCUMENT_LIMIT_REACHED] when the mandate already holds [MAX_DOCUMENTS], then 400
     * [ErrorCode.HTTP_INVALID_PARAMETER]: `type`
     * UNKNOWN; `title` longer than 100 characters after trim; `paths` empty or with duplicates; more than
     * [MAX_PAGES] paths → 400 [ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES] on `paths`; a path a page already has;
     * then each `verify(path, prefix(mandateId))` (path outside the mandate's folder, missing object, over 5 MB,
     * bytes not a JPEG/PNG/WEBP or not matching the extension). Saved through [saveLocked]: the document UPLOADED with
     * the trimmed title (or the type's label when blank) and pages indexed 0..n-1 in the given order with the default
     * storage's type. A failed save deletes every new object no page references.
     */
    fun create(mandateId: Long, request: CreateMandateDocumentRequest): MandateDocumentEntity {
        ensureWritable(mandateId)
        ensureDocumentSlot(mandateId)

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
        if (paths.size > MAX_PAGES) {
            throw pageCap("paths")
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
            source = DocumentSource.IMAGE,
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
            // The display copy of every page, before any row references it: an image that can't be previewed refuses
            // the whole document (fail closed, never an unviewable page)
            paths.forEachIndexed { index, path -> previewService.generate(mandateId, storage, path, mimeTypes[index]) }
            saveLocked(mandateId) {
                // The authoritative slot check, under the mandate's lock: a concurrent create may have taken the
                // last one since the pre-check above.
                ensureDocumentSlot(mandateId)
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
        } catch (ex: UnprocessableImageException) {
            deleteUnreferenced(mandateId, paths, storage.type())
            throw invalidImage("path")
        } catch (ex: Exception) {
            deleteUnreferenced(mandateId, paths, storage.type())
            throw ex
        }
    }

    /**
     * Creates a document from files posted as multipart, storing each one at a new path under the mandate's folder.
     * Same checks as [create], plus `files` empty → 400 (`files`), and each file checked before it's stored (outside
     * any transaction). A JPEG / PNG / WEBP is one page; a PDF is rendered to one JPEG page per PDF page, in the PDF's
     * order, at its own place in the files and never stored. A refused file, or a store that throws, deletes the files
     * stored before it: they are fresh UUIDs no page references, so no reference check is needed.
     *
     * The add holds either images or exactly one PDF: two PDFs, or images and a PDF, are refused before any file is
     * stored, and the document records the kind it was created from (`source` IMAGE or PDF). Every add is capped at
     * [MAX_PAGES] pages once a PDF is expanded (an image counts one).
     *
     * Errors, on top of [create]'s: the mandate already holds [MAX_DOCUMENTS] → 400
     * [ErrorCode.MANDATE_DOCUMENT_LIMIT_REACHED]; images and a PDF, or two PDFs → 400
     * [ErrorCode.MANDATE_DOCUMENT_MIXED_SOURCES] on `files`; a password-protected PDF → 400
     * [ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED], an empty or corrupt one → 400
     * [ErrorCode.MANDATE_DOCUMENT_PDF_UNREADABLE], both on `file`; an add whose expansion takes it over [MAX_PAGES]
     * pages → 400 [ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES] on `files` (on `file` when one PDF alone has more
     * pages).
     */
    fun upload(mandateId: Long, type: DocumentType, title: String?, files: List<MultipartFile>): MandateDocumentEntity {
        ensureWritable(mandateId)
        ensureDocumentSlot(mandateId)

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

        // The kind of the whole add, read from the bytes before anything is stored: one PDF, or only images.
        val mimeTypes = files.map { file -> TIKA.detect(file.bytes) }
        val pdfs = mimeTypes.count { it == PDF_MIME_TYPE }
        val images = mimeTypes.count { it in DirectUploadService.IMAGE_MIME_TYPES }
        if (pdfs > 1 || (pdfs == 1 && images > 0)) {
            throw mixedSources("files")
        }
        val source = if (pdfs == 1) DocumentSource.PDF else DocumentSource.IMAGE

        // Storage I/O outside any transaction. A later file refused (or a store that throws) deletes the pages already
        // stored: fresh UUIDs, so no page can reference them.
        val storage = storageProvider.get()
        val prefix = prefix(mandateId)
        val now = Date(clock.millis())
        val pages = mutableListOf<MandateDocumentPageEntity>()
        val storedPaths = mutableListOf<String>()
        try {
            files.forEachIndexed { index, file ->
                val mimeType = mimeTypes[index]
                val fileSources = pageSources(file, mimeType, "file")
                // The page cap counts every add, not only a PDF's expansion: an image is one page.
                if (pages.size + fileSources.size > MAX_PAGES) {
                    throw pageCap("files")
                }
                fileSources.forEach { source ->
                    val path = "$prefix/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(source.mimeType)}"
                    storage.store(path, source.bytes.inputStream(), source.mimeType)
                    storedPaths.add(path)
                    previewService.generate(mandateId, storage, path, source.mimeType, original = source.bytes)
                    pages.add(
                        MandateDocumentPageEntity(
                            pageIndex = pages.size,
                            path = path,
                            storageType = storage.type(),
                            mimeType = source.mimeType,
                            createdAt = now,
                            modifiedAt = now,
                        ),
                    )
                }
            }
        } catch (ex: PdfRasterizationException) {
            cleanup(mandateId, storedPaths, storage.type())
            throw pdfError(ex.reason, "file")
        } catch (ex: UnprocessableImageException) {
            cleanup(mandateId, storedPaths, storage.type())
            throw invalidImage("file")
        } catch (ex: Exception) {
            cleanup(mandateId, storedPaths, storage.type())
            throw ex
        }

        val document = MandateDocumentEntity(
            mandateId = mandateId,
            type = type,
            title = trimmedTitle ?: MandateDocumentLabels.title(type),
            status = DocumentStatus.UPLOADED,
            source = source,
            createdAt = now,
            modifiedAt = now,
        )

        return try {
            saveLocked(mandateId) {
                // The authoritative slot check, under the mandate's lock: a concurrent create may have taken the
                // last one since the pre-check above (the stored pages are removed by the catch below).
                ensureDocumentSlot(mandateId)
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
     * `verify(path, prefix(mandateId))` (see [create]); a path that isn't the document's kind → 400
     * [ErrorCode.MANDATE_DOCUMENT_MIXED_SOURCES] on `path`; a page over [MAX_PAGES] → 400
     * [ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES] on `path`. Saved through [saveLocked] at
     * `findMaxPageIndex + 1`, the document reset. A failed save deletes the new object unless a page references it.
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
        ensureKind(document, mimeType, "path")
        ensureCanAddPages(document, 1, "path")
        previewOrRefuse(mandateId, storage, path, mimeType, null, "path")
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
                ensureCanAddPages(document, 1, "path")
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
     * Same checks as [addPage], the file checked before it's stored, outside any transaction. An image adds one page;
     * a PDF adds one page per PDF page, in the PDF's order, rendered to JPEGs and appended from the current last page
     * index. Returns the first added page (the only one for an image).
     *
     * Errors, on top of [addPage]'s: a file that isn't the document's kind → 400
     * [ErrorCode.MANDATE_DOCUMENT_MIXED_SOURCES] on `file`; a password-protected PDF → 400
     * [ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED], an empty or corrupt one → 400
     * [ErrorCode.MANDATE_DOCUMENT_PDF_UNREADABLE], a PDF over [MAX_PAGES] pages → 400
     * [ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES], all on `file`; the document's pages plus the added ones over
     * [MAX_PAGES] → 400 [ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES] on `file`, checked under the lock too.
     */
    fun uploadPage(mandateId: Long, documentId: Long, file: MultipartFile): MandateDocumentPageEntity {
        val mandate = loadWritable(mandateId)
        val document = findDocument(mandate, documentId)
        ensureNotClosed(mandate)

        // Storage I/O outside any transaction
        val mimeType = TIKA.detect(file.bytes)
        ensureKind(document, mimeType, "file")
        val storage = storageProvider.get()
        val sources = try {
            pageSources(file, mimeType, "file")
        } catch (ex: PdfRasterizationException) {
            throw pdfError(ex.reason, "file")
        }
        ensureCanAddPages(document, sources.size, "file")
        val now = Date(clock.millis())
        val storedPaths = mutableListOf<String>()
        val pages = mutableListOf<MandateDocumentPageEntity>()
        try {
            sources.forEach { source ->
                val path = "${prefix(mandateId)}/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(source.mimeType)}"
                storage.store(path, source.bytes.inputStream(), source.mimeType)
                storedPaths.add(path)
                previewService.generate(mandateId, storage, path, source.mimeType, original = source.bytes)
                pages.add(
                    MandateDocumentPageEntity(
                        documentId = document.id ?: -1,
                        pageIndex = 0,
                        path = path,
                        storageType = storage.type(),
                        mimeType = source.mimeType,
                        createdAt = now,
                        modifiedAt = now,
                    ),
                )
            }
        } catch (ex: PdfRasterizationException) {
            cleanup(mandateId, storedPaths, storage.type())
            throw pdfError(ex.reason, "file")
        } catch (ex: UnprocessableImageException) {
            cleanup(mandateId, storedPaths, storage.type())
            throw invalidImage("file")
        } catch (ex: Exception) {
            cleanup(mandateId, storedPaths, storage.type())
            throw ex
        }

        return try {
            saveLocked(mandateId) {
                // The authoritative page-cap check: a concurrent add may have filled the document since the pre-check.
                ensureCanAddPages(document, sources.size, "file")
                // The base index under the lock: a concurrent add must not give two pages the same index.
                val base = (pageDao.findMaxPageIndex(document.id ?: -1) ?: -1) + 1
                val saved = pages.mapIndexed { index, page -> pageDao.save(page.copy(pageIndex = base + index)) }
                documentDao.save(resetExtraction(document))
                saved.first()
            }
        } catch (ex: Exception) {
            deleteUnreferenced(mandateId, storedPaths, storage.type())
            throw ex
        }
    }

    /**
     * Replaces a page's file with one already uploaded to the storage, keeping its `pageIndex`. Errors, in order: 404
     * [ErrorCode.MANDATE_NOT_FOUND], 403 [ErrorCode.MANDATE_ACCESS_DENIED], 404 [ErrorCode.MANDATE_DOCUMENT_NOT_FOUND],
     * 404 [ErrorCode.MANDATE_DOCUMENT_PAGE_NOT_FOUND], 409 [ErrorCode.MANDATE_ALREADY_CLOSED]; the page already at
     * [path] is returned as is (idempotent); a [path] another page has → 400 [ErrorCode.HTTP_INVALID_PARAMETER]
     * (`path`); then `verify(path, prefix(mandateId))`; a path that isn't the document's kind → 400
     * [ErrorCode.MANDATE_DOCUMENT_MIXED_SOURCES] on `path`. Saved through [saveLocked]: the page keeps its `pageIndex`
     * and
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
        ensureKind(document, mimeType, "path")
        previewOrRefuse(mandateId, storage, path, mimeType, null, "path")
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
        deletePageObjects(mandateId, oldPath, oldStorageType)
        return saved
    }

    /**
     * Replaces a page's file with one posted as multipart, storing it at a new path under the mandate's folder. Same
     * checks as [replacePage], the file checked before it's stored, outside any transaction. An image, or a PDF of
     * exactly one page rendered to a JPEG, replaces the page and keeps its `pageIndex`.
     *
     * Errors, on top of [replacePage]'s: a file that isn't the document's kind → 400
     * [ErrorCode.MANDATE_DOCUMENT_MIXED_SOURCES] on `file`; a password-protected PDF → 400
     * [ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED], an empty or corrupt one → 400
     * [ErrorCode.MANDATE_DOCUMENT_PDF_UNREADABLE], a PDF of more than one page → 400
     * [ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES], all on `file`. A refused file leaves the page, its object and
     * its preview untouched. No page-count check: an over-limit document's page is still replaceable.
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
        val mimeType = TIKA.detect(file.bytes)
        ensureKind(document, mimeType, "file")
        val storage = storageProvider.get()
        val source = try {
            pageSources(file, mimeType, "file", maxPages = 1).single()
        } catch (ex: PdfRasterizationException) {
            throw pdfError(ex.reason, "file")
        }
        val path = "${prefix(mandateId)}/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(source.mimeType)}"
        try {
            storage.store(path, source.bytes.inputStream(), source.mimeType)
            previewService.generate(mandateId, storage, path, source.mimeType, original = source.bytes)
        } catch (ex: UnprocessableImageException) {
            cleanup(mandateId, listOf(path), storage.type())
            throw invalidImage("file")
        } catch (ex: Exception) {
            cleanup(mandateId, listOf(path), storage.type())
            throw ex
        }
        val oldPath = page.path
        val oldStorageType = page.storageType
        val replaced = page.copy(
            path = path,
            storageType = storage.type(),
            mimeType = source.mimeType,
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
        deletePageObjects(mandateId, oldPath, oldStorageType)
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
        deletePageObjects(mandateId, oldPath, oldStorageType)
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
        pages.forEach { page -> deletePageObjects(mandateId, page.path, page.storageType) }
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
     * committed during the storage I/O wins).
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

    /**
     * Refuses a page of the other kind on [document]: an `IMAGE` document takes a photo, a `PDF` document a PDF, an
     * `UNKNOWN` one either. A file that is neither is left to [pageSources], which refuses it as a bad parameter.
     */
    private fun ensureKind(document: MandateDocumentEntity, mimeType: String?, field: String) {
        val pdf = mimeType == PDF_MIME_TYPE
        val image = mimeType in DirectUploadService.IMAGE_MIME_TYPES
        val accepted = when (document.source) {
            DocumentSource.IMAGE -> !pdf
            DocumentSource.PDF -> !image
            DocumentSource.UNKNOWN -> true
        }
        if (!accepted) {
            throw mixedSources(field)
        }
    }

    /** Refuses [count] new pages when [document] would then hold more than [MAX_PAGES] (under the caller's lock). */
    private fun ensureCanAddPages(document: MandateDocumentEntity, count: Int, field: String) {
        val current = pageDao.findByDocumentIdOrderByPageIndex(document.id ?: -1).size
        if (current + count > MAX_PAGES) {
            throw pageCap(field)
        }
    }

    /**
     * Refuses a document when [mandateId] already holds [MAX_DOCUMENTS]: 400 [ErrorCode.MANDATE_DOCUMENT_LIMIT_REACHED],
     * no parameter and no value (the cap is not a field of the request).
     */
    private fun ensureDocumentSlot(mandateId: Long) {
        if (documentDao.countByMandateId(mandateId) >= MAX_DOCUMENTS) {
            throw limitReached()
        }
    }

    private fun limitReached() = BadRequestException(Error(code = ErrorCode.MANDATE_DOCUMENT_LIMIT_REACHED))

    /** Images and a PDF, or two PDFs, in one document: 400 on [field], no parameter value to leak a file name. */
    private fun mixedSources(field: String) = BadRequestException(
        Error(
            code = ErrorCode.MANDATE_DOCUMENT_MIXED_SOURCES,
            parameter = Parameter(name = field, type = ParameterType.PARAMETER_TYPE_PAYLOAD),
        ),
    )

    /** A create or add that would take the document over [MAX_PAGES] pages: 400 on [field], no value. */
    private fun pageCap(field: String) = BadRequestException(
        Error(
            code = ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES,
            parameter = Parameter(name = field, type = ParameterType.PARAMETER_TYPE_PAYLOAD),
        ),
    )

    private fun invalid(name: String, value: Any?) = BadRequestException(
        Error(
            code = ErrorCode.HTTP_INVALID_PARAMETER,
            parameter = Parameter(name = name, type = ParameterType.PARAMETER_TYPE_PAYLOAD, value = value),
        ),
    )

    /**
     * The pages of one posted file, in order: an image is one page, a PDF as many pages as it has, each rendered to a
     * JPEG ([maxPages] at most). Errors, all on [field]: neither an image nor a PDF, or a PDF over 5 MB → 400
     * [ErrorCode.HTTP_INVALID_PARAMETER]; a PDF that can't be expanded → [PdfRasterizationException], which the caller
     * words through [pdfError].
     */
    private fun pageSources(
        file: MultipartFile,
        mimeType: String,
        field: String,
        maxPages: Int = MAX_PAGES,
    ): List<PageSource> = when {
        mimeType == PDF_MIME_TYPE -> {
            if (file.size > DirectUploadService.MAX_BYTES) {
                throw invalidFile(field)
            }
            pdfRasterizer.rasterize(file.bytes, maxPages).map { page -> PageSource(page, JPEG_MIME_TYPE) }
        }

        mimeType in DirectUploadService.IMAGE_MIME_TYPES -> listOf(PageSource(file.bytes, directUpload.check(file)))
        else -> throw invalidFile(field)
    }

    /**
     * A refused PDF, as a 400 whose [reason] picks the code. No message and no value: neither a file name nor a path of
     * the PDF may reach the error body or the logs.
     */
    private fun pdfError(reason: PdfRasterizationException.Reason, field: String) = BadRequestException(
        Error(
            code = when (reason) {
                PdfRasterizationException.Reason.ENCRYPTED -> ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED
                PdfRasterizationException.Reason.TOO_MANY_PAGES -> ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES
                PdfRasterizationException.Reason.UNREADABLE,
                PdfRasterizationException.Reason.EMPTY,
                -> ErrorCode.MANDATE_DOCUMENT_PDF_UNREADABLE
            },
            parameter = Parameter(name = field, type = ParameterType.PARAMETER_TYPE_PAYLOAD),
        ),
    )

    /** A file that is neither an image nor a PDF, or a PDF over 5 MB: 400 on [field], with no value to leak its name. */
    private fun invalidFile(field: String) = BadRequestException(
        Error(
            code = ErrorCode.HTTP_INVALID_PARAMETER,
            parameter = Parameter(name = field, type = ParameterType.PARAMETER_TYPE_PAYLOAD),
        ),
    )

    /**
     * An image that can't be previewed: 400 [ErrorCode.HTTP_INVALID_PARAMETER] on [name], with no path (or file name)
     * in the value or the message, so nothing of the page leaks into the error body or the logs.
     */
    private fun invalidImage(name: String) = BadRequestException(
        Error(
            code = ErrorCode.HTTP_INVALID_PARAMETER,
            message = "The image cannot be previewed.",
            parameter = Parameter(name = name, type = ParameterType.PARAMETER_TYPE_PAYLOAD),
        ),
    )

    /** After a failed save: deletes each new object (and its preview) no page references. */
    private fun deleteUnreferenced(mandateId: Long, paths: List<String>, storageType: StorageType) {
        paths.forEach { path ->
            if (pageDao.findByPath(path) == null) {
                deletePageObjects(mandateId, path, storageType)
            }
        }
    }

    /** After a refused file, before any page references it: deletes the stored original and its preview. */
    private fun cleanup(mandateId: Long, paths: List<String>, storageType: StorageType) {
        paths.forEach { path -> deletePageObjects(mandateId, path, storageType) }
    }

    /**
     * Generates the preview of a page's new file, or refuses the page: an image that can't be previewed is deleted
     * (with its original) and is a 400 [ErrorCode.HTTP_INVALID_PARAMETER] on [fieldname].
     */
    private fun previewOrRefuse(
        mandateId: Long,
        storage: StorageService,
        path: String,
        mimeType: String?,
        original: ByteArray?,
        fieldname: String,
    ) {
        try {
            previewService.generate(mandateId, storage, path, mimeType, original)
        } catch (ex: UnprocessableImageException) {
            cleanup(mandateId, listOf(path), storage.type())
            throw invalidImage(fieldname)
        } catch (ex: Exception) {
            cleanup(mandateId, listOf(path), storage.type())
            throw ex
        }
    }

    /** Deletes a page's original and its preview; the preview's failure doesn't make the object deletion fail. */
    private fun deletePageObjects(mandateId: Long, path: String, storageType: StorageType) {
        deleteQuietly(mandateId, path, storageType)
        previewService.deleteQuietly(mandateId, path, storageType)
    }

    /**
     * Deletes an object no row references any more (a legacy page's object is in the default storage); a failure is
     * logged with the mandate id and the exception class only — never the path, which is private.
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
            LOGGER.warn(">>> Mandate#$mandateId - could not delete a page object (${ex.javaClass.name})")
        }
    }
}
