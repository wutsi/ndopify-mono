package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentPageEntity
import com.wutsi.ndopify.mandate.server.service.MandateDocumentService
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.platform.image.ImageResizeException
import com.wutsi.ndopify.platform.image.ImageResizer
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.StorageType
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.math.max

/**
 * Reads a mandate document in the background: `UPLOADED → EXTRACTING → EXTRACTED | FAILED`, with the typed fields
 * ([DocumentData]) and the problem codes ([DocumentProblemCodes]) #331 shows to the agent.
 *
 * No transaction is open during the storage I/O or the vision call: the claim and every save are short transactions
 * holding the document's write lock, and the final save is `@Version`-ed, so a page change that lands during the
 * extraction wins and the stale result is dropped. Every page is re-encoded by [ImageResizer] — the model never gets
 * the stored original. An unreadable page is `DOCUMENT_UNREADABLE` without any model call and without a retry; only a
 * technical failure (the model times out or errors) is retried, at most `max-attempts` times in the run.
 *
 * Never logs an extracted value: only ids, the type and status, the attempt and the problem codes.
 */
@Service
class MandateDocumentExtractionService(
    private val mandateService: MandateService,
    private val documentService: MandateDocumentService,
    private val documentDao: MandateDocumentRepository,
    private val pageDao: MandateDocumentPageRepository,
    private val storageProvider: StorageServiceProvider,
    private val extractors: DocumentDataExtractorProvider,
    private val resizer: ImageResizer,
    private val checks: DocumentChecks,
    private val codec: DocumentDataCodec,
    private val tenantService: TenantService,
    private val clock: Clock,
    private val tx: TransactionTemplate,
    private val logger: KVLogger,
    @param:Value("\${ndopify.mandate.document.extract.batch-size}") private val batchSize: Int,
    @param:Value("\${ndopify.mandate.document.extract.max-attempts}") private val maxAttempts: Int,
    @param:Value("\${ndopify.mandate.document.extract.in-progress-timeout-minutes}") private val inProgressTimeoutMinutes: Long,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateDocumentExtractionService::class.java)

        /** The largest page the extraction reads: the upload refuses more, a legacy object is truncated to it. */
        private const val MAX_PAGE_BYTES = 5 * 1024 * 1024
    }

    /**
     * The extraction of one tenant's due documents: first the ones an interrupted run left `EXTRACTING` for more than
     * the timeout, then the oldest `UPLOADED` ones, up to `batch-size`. A document that fails doesn't stop the others.
     * Returns how many documents were processed, extracted or failed (the types without an extractor aren't selected).
     */
    fun extractDue(timeZone: String): Int {
        recoverInterrupted()
        var count = 0
        documentDao.findByTypeInAndStatusOrderByIdAsc(
            types = extractors.types(),
            status = DocumentStatus.UPLOADED,
            pageable = OffsetLimitPageRequest(0, batchSize),
        ).forEach { document ->
                try {
                    if (extract(document, timeZone, force = false)) {
                        count++
                    }
                } catch (ex: Exception) {
                    // A document failing must not prevent the others to be extracted
                    LOGGER.warn(">>> MandateDocument#${document.id} - extraction failed (${ex.javaClass.name})")
                }
            }
        return count
    }

    /**
     * The ops re-run of one document, synchronously, whatever its status. Errors, in order: 404
     * [ErrorCode.MANDATE_NOT_FOUND], 404 [ErrorCode.MANDATE_DOCUMENT_NOT_FOUND] (another mandate's too), 400
     * [ErrorCode.HTTP_INVALID_PARAMETER] (`type`) for a type without an extractor.
     */
    fun extract(mandateId: Long, documentId: Long) {
        val mandate = mandateService.findById(mandateId)
        val document = documentService.findDocument(mandate, documentId)
        if (!extractors.supports(document.type)) {
            throw invalid("type", document.type.name)
        }
        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant in context")
        val tenant = tenantService.findById(tenantId)
        extract(document, tenant.timeZone, force = true)
    }

    /**
     * Sends every document of [type] but the `EXTRACTING` ones back to `UPLOADED`, with its data, dates and codes
     * cleared, so the next job run reads it again; returns how many were requeued. A type without an extractor is a 400
     * [ErrorCode.HTTP_INVALID_PARAMETER] (`type`): there is nothing to re-run for it.
     */
    fun requeueByType(type: DocumentType): Int {
        if (DocumentRequirements.of(type) == null) {
            throw invalid("type", type.name)
        }
        return documentDao.findByTypeAndStatusNot(type, DocumentStatus.EXTRACTING).count { document ->
            try {
                tx.execute {
                    // Re-read under the lock: its status or its pages may have changed since it was listed
                    val locked = documentDao.findByIdForUpdate(document.id!!)
                    if (locked != null && locked.status != DocumentStatus.EXTRACTING) {
                        documentDao.save(reset(locked, DocumentStatus.UPLOADED))
                        true
                    } else {
                        false
                    }
                } == true
            } catch (ex: ObjectOptimisticLockingFailureException) {
                false
            }
        }
    }

    /**
     * Extracts [document]. Returns true when it was processed (whatever the outcome), false when it was skipped: a type
     * without an extractor, a document already `EXTRACTING`, or not `UPLOADED` without [force].
     */
    private fun extract(document: MandateDocumentEntity, timeZone: String, force: Boolean): Boolean {
        if (!extractors.supports(document.type)) {
            return false
        }

        val claimed = claim(document, force) ?: return false
        val pages = pageDao.findByDocumentIdOrderByPageIndex(claimed.id!!)
        val files = mutableListOf<File>()
        try {
            try {
                pages.forEach { page ->
                    val jpeg = resizer.resize(ByteArrayInputStream(read(page)))
                    val file = File.createTempFile("mandate-document-", ".jpg")
                    // Registered before the write, so a failed write still has it deleted in the finally
                    files.add(file)
                    file.writeBytes(jpeg)
                }
            } catch (ex: ImageResizeException) {
                // A valid answer: the page can't be read, a retry won't change it
                return fail(claimed, listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE), 0)
            } catch (ex: Exception) {
                LOGGER.warn(">>> MandateDocument#${claimed.id} - page read failed (${ex.javaClass.name})")
                return fail(claimed, listOf(DocumentProblemCodes.EXTRACTION_FAILED), 0)
            }

            val (data, attempts) = extract(claimed, files)
                ?: return fail(claimed, listOf(DocumentProblemCodes.EXTRACTION_FAILED), maxAttempts)
            val codes = checks.check(claimed.type, data)
            val issuedAt = data.issuedAt()
            val expiresAt = data.expiresAt()
            val expired = expiresAt != null && expiresAt.isBefore(today(timeZone))
            return if (codes.isEmpty()) {
                complete(claimed, data, issuedAt, expiresAt, expired, attempts)
            } else {
                fail(claimed, codes, attempts)
            }
        } finally {
            files.forEach { file -> file.delete() }
        }
    }

    /**
     * [type]'s extractor over [images], retried [maxAttempts] times on a technical failure. The data and the attempt
     * count, or null once every attempt threw.
     */
    private fun extract(document: MandateDocumentEntity, images: List<File>): Pair<DocumentData, Int>? {
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            try {
                return extractors.get(document.type)!!.extract(document.type, images) to attempt
            } catch (ex: Exception) {
                // Never the exception's message: it may carry the model's output
                LOGGER.warn(">>> MandateDocument#${document.id} - extraction attempt $attempt failed (${ex.javaClass.name})")
            }
        }
        return null
    }

    /** UPLOADED → EXTRACTING with everything cleared, under the lock; null when another run or a page change won. */
    private fun claim(document: MandateDocumentEntity, force: Boolean): MandateDocumentEntity? = try {
        tx.execute {
            val locked = documentDao.findByIdForUpdate(document.id!!)
            if (locked == null || locked.status == DocumentStatus.EXTRACTING || (!force && locked.status != DocumentStatus.UPLOADED)) {
                null
            } else {
                documentDao.save(reset(locked, DocumentStatus.EXTRACTING))
            }
        }
    } catch (ex: ObjectOptimisticLockingFailureException) {
        null
    }

    /** The extraction's result: the data, the dates and the expiry warning, saved under the document's version. */
    private fun complete(
        document: MandateDocumentEntity,
        data: DocumentData,
        issuedAt: LocalDate?,
        expiresAt: LocalDate?,
        expired: Boolean,
        attempt: Int,
    ): Boolean {
        val codes = if (expired) listOf(DocumentProblemCodes.DOCUMENT_EXPIRED) else emptyList()
        return save(
            document.copy(
                status = DocumentStatus.EXTRACTED,
                data = codec.write(data),
                dataVersion = codec.version(),
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                problemCodes = codes,
                modifiedAt = now(),
            ),
            codes,
            attempt,
        )
    }

    /** The extraction's failure: only the problem codes are kept. */
    private fun fail(document: MandateDocumentEntity, codes: List<String>, attempt: Int): Boolean = save(
        document.copy(
            status = DocumentStatus.FAILED,
            data = null,
            dataVersion = null,
            issuedAt = null,
            expiresAt = null,
            problemCodes = codes,
            modifiedAt = now(),
        ),
        codes,
        attempt,
    )

    /**
     * Saves the extraction's outcome, false when a page change won: its newer version makes the save fail, and the
     * document keeps the `UPLOADED` state the change set.
     */
    private fun save(document: MandateDocumentEntity, codes: List<String>, attempt: Int = 0): Boolean = try {
        tx.execute { documentDao.save(document) }
        log(document, codes, attempt)
        true
    } catch (ex: ObjectOptimisticLockingFailureException) {
        LOGGER.info(">>> MandateDocument#${document.id} - a page change won, the extraction is dropped")
        false
    }

    /** Sends back to `UPLOADED` the documents left `EXTRACTING` after an interrupted run. */
    private fun recoverInterrupted() {
        val cutoff = Date(clock.millis() - inProgressTimeoutMinutes * 60_000)
        documentDao.findByStatusAndModifiedAtBefore(DocumentStatus.EXTRACTING, cutoff).forEach { document ->
            try {
                tx.execute {
                    val locked = documentDao.findByIdForUpdate(document.id!!)
                    if (locked != null && locked.status == DocumentStatus.EXTRACTING && locked.modifiedAt.before(cutoff)) {
                        LOGGER.warn(">>> MandateDocument#${locked.id} - extraction interrupted, sent back to UPLOADED")
                        documentDao.save(reset(locked, DocumentStatus.UPLOADED))
                    }
                }
            } catch (ex: ObjectOptimisticLockingFailureException) {
                // A page change or another run won: nothing to recover
            }
        }
    }

    /** The page's bytes from its own storage (the default one for a legacy `UNKNOWN` page), at most 5 MB. */
    private fun read(page: MandateDocumentPageEntity): ByteArray {
        val storage = if (page.storageType == StorageType.UNKNOWN) {
            storageProvider.get()
        } else {
            storageProvider.get(page.storageType)
        }
        return storage.readPrefix(page.path, MAX_PAGE_BYTES)
    }

    /** The document cleared of its extraction, in [status]. */
    private fun reset(document: MandateDocumentEntity, status: DocumentStatus): MandateDocumentEntity = document.copy(
        status = status,
        data = null,
        dataVersion = null,
        issuedAt = null,
        expiresAt = null,
        problemCodes = emptyList(),
        modifiedAt = now(),
    )

    /** The tenant's today: [timeZone]'s calendar date for the server's [clock]. */
    private fun today(timeZone: String): LocalDate = LocalDate.now(clock.withZone(zone(timeZone)))

    private fun zone(timeZone: String): ZoneId = try {
        ZoneId.of(timeZone)
    } catch (ex: Exception) {
        clock.zone
    }

    private fun now(): Date = Date(clock.millis())

    /** Only ids, type, status, attempt and codes: an extracted value never reaches the logs. */
    private fun log(document: MandateDocumentEntity, codes: List<String>, attempt: Int) {
        logger.add("mandate_document_id", document.id)
        logger.add("document_type", document.type.name)
        logger.add("document_status", document.status.name)
        logger.add("attempt", max(attempt, 1).toLong())
        if (codes.isNotEmpty()) {
            logger.add("problem_codes", codes)
        }
    }

    private fun invalid(name: String, value: Any?) = BadRequestException(
        Error(
            code = ErrorCode.HTTP_INVALID_PARAMETER,
            parameter = Parameter(name = name, type = ParameterType.PARAMETER_TYPE_QUERY, value = value),
        ),
    )
}
