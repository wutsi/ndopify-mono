package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dto.CreateKycCaseRequest
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.party.server.dao.KycImageRepository
import com.wutsi.ndopify.party.server.dao.KycImageRow
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycImageEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.event.KycCaseCompletedEvent
import com.wutsi.ndopify.party.server.mail.KycMailet
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.upload.DirectUploadService
import com.wutsi.ndopify.platform.util.MimeUtils
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import com.wutsi.ndopify.refdata.dto.StorageType
import com.wutsi.ndopify.security.server.service.CallerService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.hibernate.Hibernate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.net.URL
import java.time.Clock
import java.util.Date

@Service
class KycService(
    @param:Value("\${ndopify.party.kyc.image.url-ttl-seconds}") val imageUrlTTL: Int,
    @param:Value("\${ndopify.party.kyc.verify.in-progress-timeout-minutes}") val inProgressTimeoutMinutes: Long,

    private val dao: KycCaseRepository,
    private val partyService: PartyService,
    private val kycImageDao: KycImageRepository,
    private val verifierProvider: KycCaseVerifierProvider,
    private val storageProvider: StorageServiceProvider,
    private val paymentMethodService: PaymentMethodService,
    private val verificationDao: KycVerificationRepository,
    private val tx: TransactionTemplate,
    private val mailet: KycMailet,
    private val clock: Clock,
    private val logger: KVLogger,
    private val eventPublisher: ApplicationEventPublisher,
    private val callerService: CallerService,
    private val directUpload: DirectUploadService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(KycService::class.java)
        private val MAX_RETRIES = 3

        /** The folder of a case's photos: the multipart upload and the direct upload share it. */
        fun prefix(caseId: String) = "kyc/$caseId/images"
    }

    fun findById(id: String): KycCaseEntity {
        return dao.findById(id).orElseThrow {
            NotFoundException(
                error = Error(
                    code = ErrorCode.KYC_CASE_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
        }
    }

    fun findImageById(id: String): KycImageEntity {
        return kycImageDao.findById(id)
            .orElseThrow {
                NotFoundException(
                    error = Error(
                        code = ErrorCode.KYC_IMAGE_NOT_FOUND,
                        message = "Image#$id not found",
                    )
                )
            }
    }

    fun search(request: SearchKycCaseRequest): List<KycCaseEntity> {
        val spec = Specification<KycCaseEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            request.partyId?.let { partyId ->
                predicates.add(cb.equal(root.get<PartyEntity>("party").get<Long>("id"), partyId))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<KycStatus>("status").`in`(request.statuses))
            }

            cb.and(*predicates.toTypedArray())
        }

        // Newest first: a party's current case is the one it opened last (a rejected case is followed by a new one).
        val sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))
        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, sort)).content
    }

    @Transactional
    fun create(request: CreateKycCaseRequest): KycCaseEntity {
        val party = partyService.findById(request.partyId)
        return create(party, request.identificationType)
    }

    @Transactional
    fun create(party: PartyEntity, identificationType: IdentificationType): KycCaseEntity {
        val now = Date(clock.millis())

        // Case
        return dao.save(
            KycCaseEntity(
                party = party,
                identificationType = identificationType,
                status = KycStatus.PENDING_VERIFICATION,
                createdAt = now,
                modifiedAt = now,
            )
        )
    }

    /**
     * Stores a KYC photo posted as multipart, replacing the case's image for that (identification type, image type).
     * Errors, in order: 404 KYC_CASE_NOT_FOUND (including another tenant's case), 403 HTTP_ACCESS_DENIED but for the
     * case's own party, 400 KYC_CASE_NOT_PENDING (once submitted, the documents under review must not change), then
     * 400 KYC_IMAGE_INVALID_MIME_TYPE. The row is saved by [saveImage]: a case submitted during the store keeps its
     * rows, but a photo stored at the row's own path (`{imageId}.{ext}`, the same extension) has already been
     * overwritten, since the multipart upload writes in place. The replaced object is deleted after the save when it is
     * at another path (a direct upload, another extension); a failed deletion is logged, not fatal.
     */
    fun upload(
        id: String,
        identificationType: IdentificationType,
        imageType: KycImageType,
        file: MultipartFile
    ): KycImageEntity {
        val case = findById(id)
        ensureOwnCase(case)
        ensureStatusIsPending(case)
        ensureImage(file)

        val now = Date(clock.millis())
        val image = kycImageDao.findByKycCaseAndIdentificationTypeAndImageType(
            case, identificationType, imageType
        ) ?: KycImageEntity(
            kycCase = case,
            identificationType = identificationType,
            imageType = imageType,
            createdAt = now,
            modifiedAt = now,
        )

        // Store
        val mimeType = file.contentType
        val extension = MimeUtils.getExtensionFromMimeType(mimeType)
        val path = "${prefix(case.id)}/${image.id}.$extension"
        val storage = storageProvider.get()
        storage.store(path, file.inputStream, mimeType)

        val (saved, previous) = try {
            saveImage(case, image, path, storage.type(), mimeType, now, inPlace = true)
        } catch (ex: Exception) {
            deleteUnreferenced(case, path) { storage.delete(path) }
            throw ex
        }
        deletePrevious(case, previous, path)
        return saved
    }

    /**
     * A short-lived URL to upload a KYC photo straight to the storage, for the case's own party. Errors, in order: 404
     * KYC_CASE_NOT_FOUND, 403 HTTP_ACCESS_DENIED but for the case's party, 400 KYC_CASE_NOT_PENDING, then 400
     * HTTP_INVALID_PARAMETER (by [DirectUploadService.presign]) for a type that isn't JPEG, PNG or WEBP. No URL when the
     * storage can't presign (local): the caller falls back to [upload].
     */
    fun presign(
        id: String,
        identificationType: IdentificationType,
        imageType: KycImageType,
        contentType: String?,
    ): PresignUploadResponse {
        val case = findById(id)
        ensureOwnCase(case)
        ensureStatusIsPending(case)
        return directUpload.presign(prefix(case.id), contentType)
    }

    /**
     * Attaches a KYC photo uploaded straight to the storage, replacing the case's image for that (identification type,
     * image type): one image per step. Errors, in order: 404 KYC_CASE_NOT_FOUND, 403 HTTP_ACCESS_DENIED but for the
     * case's party, 400 KYC_CASE_NOT_PENDING, 400 HTTP_INVALID_PARAMETER for a path another image of the case already
     * has, then [DirectUploadService.verify]'s 400s. Idempotent on [path]: the image already recorded for it is
     * returned. The row is saved by [saveImage]: a case submitted during the verification is left as it was, and the
     * uploaded object is deleted whenever the save fails. The replaced object is deleted after the new row is saved,
     * with its own storage; a failed deletion is logged, not fatal.
     */
    fun attach(
        id: String,
        identificationType: IdentificationType,
        imageType: KycImageType,
        path: String,
    ): KycImageEntity {
        val case = findById(id)
        ensureOwnCase(case)
        ensureStatusIsPending(case)

        val existing = kycImageDao.findByKycCaseAndIdentificationTypeAndImageType(case, identificationType, imageType)
        if (existing != null && existing.path == path) {
            return existing
        }
        ensurePathNotUsed(case, path, existing?.id)

        // Storage I/O outside the transaction, like upload()
        val mimeType = directUpload.verify(path, prefix(case.id))
        val storage = storageProvider.get()
        val now = Date(clock.millis())
        val image = existing ?: KycImageEntity(
            kycCase = case,
            identificationType = identificationType,
            imageType = imageType,
            createdAt = now,
            modifiedAt = now,
        )
        val (saved, previous) = try {
            saveImage(case, image, path, storage.type(), mimeType, now, inPlace = false)
        } catch (ex: Exception) {
            // Submitted meanwhile, or the save failed
            deleteUnreferenced(case, path) { storage.delete(path) }
            throw ex
        }
        deletePrevious(case, previous, path)
        return saved
    }

    /**
     * Saves [image] at [path], in a transaction holding the case's lock: the case must still be PENDING_VERIFICATION
     * (a submit committed during the storage I/O wins), no other image of the case may have [path], and the step's
     * image is read again from the database, so that concurrent writes on one step update a single row and each
     * replaced object is deleted once. [inPlace] (the multipart upload) rewrites the row's own path, so the row is
     * always saved; otherwise a row already at [path] (a concurrent attach) is returned as is. Returns the saved image
     * and the step's row as it was before the save (null when there was none).
     */
    private fun saveImage(
        case: KycCaseEntity,
        image: KycImageEntity,
        path: String,
        storageType: StorageType,
        mimeType: String?,
        now: Date,
        inPlace: Boolean,
    ): Pair<KycImageEntity, KycImageRow?> = tx.execute {
        ensureStillPending(case)
        val current = kycImageDao.findRow(case.id, image.identificationType.name, image.imageType.name)
        if (!inPlace && current?.getPath() == path) {
            return@execute kycImageDao.findById(current.getId()).orElseThrow() to null
        }
        ensurePathNotUsed(case, path, current?.getId())

        val base = current?.let { row -> kycImageDao.findById(row.getId()).orElseThrow() } ?: image
        kycImageDao.save(
            base.copy(
                path = path,
                storageType = storageType,
                mimeType = mimeType,
                modifiedAt = now,
            )
        ) to current
    }!!

    /** Deletes the object of the row [saveImage] replaced, with its own storage, unless it is at [path]. */
    private fun deletePrevious(case: KycCaseEntity, previous: KycImageRow?, path: String) {
        val oldPath = previous?.getPath() ?: return
        if (oldPath == path) return

        val storageType = StorageType.entries.firstOrNull { it.name == previous.getStorageType() } ?: StorageType.UNKNOWN
        deleteQuietly(case, oldPath) { storageProvider.get(storageType).delete(oldPath) }
    }

    fun getImageUrl(imageId: String): URL {
        val image = findImageById(imageId)
        if (image.path == null) {
            throw NotFoundException(
                error = Error(
                    code = ErrorCode.KYC_IMAGE_NO_CONTENT,
                    message = "Image#$imageId has no content"
                )
            )
        }
        val storage = storageProvider.get(image.storageType)
        return storage.generatePresignedUrl(image.path, imageUrlTTL)
    }

    /**
     * Sends a case for review: PENDING_VERIFICATION → SUBMITTED. Its documents are frozen from then on, and the offline
     * verification (see [verify]) picks it up. Requires the document's front, and its back for two-sided
     * documents (see [requiredImages]). The selfie is optional.
     */
    @Transactional
    fun submit(id: String): KycCaseEntity {
        val case = findById(id)
        ensureStatusIsPending(case)
        ensureIdentificationImages(case)

        return dao.save(
            case.copy(
                status = KycStatus.SUBMITTED,
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    /**
     * Verifies a SUBMITTED case. Never triggered by the UI: run offline, on the cases sent for review.
     *
     * Runs in 3 steps, so that no DB transaction is open during the verifiers' I/O (storage, LLM, MoMo):
     * 1. [claim] the case (SUBMITTED → UNDER_VERIFICATION), in a short transaction;
     * 2. run the verifiers, outside any transaction: they return their results unsaved;
     * 3. [complete] the case with these results in a short transaction, or [retry] it if a verifier failed.
     */
    fun verify(id: String): KycCaseEntity {
        val case = tx.execute { claim(id) }

        val verifications = try {
            // Checked before the other verifiers (no need to call them), and here rather than in complete(): failing
            // there would leave the case UNDER_VERIFICATION
            val idVerifications = verify(case, KycVerificationType.IDENTIFICATION)
            check(idVerifications.isNotEmpty()) { "Case#$id has no identification verification" }

            idVerifications +
                verify(case, KycVerificationType.SELFIE) +
                try { // Payment method failure should not break the KYC since its not used for assessing the party's status
                    // TODO: Remove this from KYC?
                    verify(case, KycVerificationType.PAYMENT_METHOD)
                } catch (ex: Exception) {
                    LOGGER.warn(">>> KYC#$id - PaymentMethod verification failed", ex)
                    emptyList()
                }
        } catch (ex: Exception) {
            LOGGER.error(">>> KYC#$id - Verification failed", ex)
            return tx.execute {
                retry(
                    case = findById(id),
                    errorCode = KycErrorCode.GATEWAY_ERROR,
                    errorMessage = "${ex::class.java.name}: ${ex.message}"
                )
            }
        }

        return tx.execute { complete(id, verifications) }
    }

    /**
     * Sends back for verification the cases left UNDER_VERIFICATION for more than [inProgressTimeoutMinutes]: their
     * verification was interrupted between [claim] and [complete] (e.g. the server stopped). Counted as a failed
     * attempt, so a case whose verification keeps getting interrupted ends up REQUIRES_MANUAL_VERIFICATION.
     */
    fun recoverInterrupted(): List<KycCaseEntity> {
        val cutoff = Date(clock.millis() - inProgressTimeoutMinutes * 60_000)
        return dao.findByStatusAndModifiedAtBefore(KycStatus.UNDER_VERIFICATION, cutoff)
            .mapNotNull { case -> tx.execute { recoverInterrupted(case.id, cutoff) } }
    }

    private fun recoverInterrupted(id: String, cutoff: Date): KycCaseEntity? {
        // Checked again under the lock: the verification may have completed since the case was listed
        val case = dao.findForUpdateById(id) ?: return null
        if (case.status != KycStatus.UNDER_VERIFICATION || !case.modifiedAt.before(cutoff)) {
            return null
        }

        LOGGER.warn(">>> KYC#$id - Verification interrupted, UNDER_VERIFICATION since ${case.modifiedAt}")
        return retry(case, KycErrorCode.INTERRUPTED, "Verification interrupted")
    }

    /**
     * Sends the status email of a resolved case. Called by `KycEventListener` on its own thread, with
     * `TenantContext` set: the transaction gives the mailet a session to load the case's party.
     */
    @Transactional
    fun sendStatusEmail(id: String) {
        val case = findById(id)
        if (case.status == KycStatus.VERIFIED || case.status == KycStatus.REJECTED) {
            mailet.send(case)
        }
    }

    private fun claim(id: String): KycCaseEntity {
        // Locked, so that the job and the endpoint can't both claim the case: the 2nd waits, then sees UNDER_VERIFICATION
        val case = dao.findForUpdateById(id)
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorCode.KYC_CASE_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
        ensureStatusIsSubmitted(case)

        LOGGER.info(">>> KYC#$id - Starting verification")
        return dao.save(
            case.copy(
                status = KycStatus.UNDER_VERIFICATION,
                errorCode = null,
                errorMessage = null,
                modifiedAt = Date(clock.millis()),
            )
        ).also { claimed ->
            // The verifiers read the images outside any session: load them while it's open
            Hibernate.initialize(claimed.images)
        }
    }

    private fun complete(id: String, verifications: List<KycVerificationEntity>): KycCaseEntity {
        val case = findById(id) // Fresh copy: the one the verifiers worked on is detached
        val saved = verifications.map { verification -> verificationDao.save(verification.copy(case = case)) }

        val updatedCase = updateCaseStatus(case, saved)
        updatePaymentMethodStatus(saved)
        return updatedCase
    }

    /** [case] must be a fresh copy, loaded in the current transaction, not to overwrite it with a stale one. */
    private fun retry(case: KycCaseEntity, errorCode: String, errorMessage: String?): KycCaseEntity {
        val retries = (case.retries ?: 0) + 1
        return dao.save(
            case.copy(
                status = if (retries < MAX_RETRIES) KycStatus.SUBMITTED else KycStatus.REQUIRES_MANUAL_VERIFICATION,
                retries = retries,
                errorCode = errorCode,
                errorMessage = errorMessage,
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    private fun updateCaseStatus(case: KycCaseEntity, verifications: List<KycVerificationEntity>): KycCaseEntity {
        val idVerification = verifications.first { verification ->
            verification.type == KycVerificationType.IDENTIFICATION
        }
        val selfieVerification = verifications.firstOrNull { verification ->
            verification.type == KycVerificationType.SELFIE
        }

        // KYC status
        val status =
            if (idVerification.status == KycStatus.VERIFIED && (selfieVerification == null || selfieVerification.status == KycStatus.VERIFIED)) {
                KycStatus.VERIFIED
            } else {
                KycStatus.REJECTED
            }
        val score = if (selfieVerification != null) {
            ((idVerification.score ?: 0) + (selfieVerification.score ?: 0)) / 2
        } else {
            idVerification.score
        }
        val errorCode = if (idVerification.status != KycStatus.VERIFIED) {
            KycErrorCode.IDENTIFICATION_REJECTED
        } else if (selfieVerification != null && selfieVerification.status != KycStatus.VERIFIED) {
            KycErrorCode.SELFIE_REJECTED
        } else {
            null
        }
        logger.add("kyc_case_status", status)
        logger.add("kyc_case_score", score)
        logger.add("kyc_case_full_name", case.party.fullName())
        val updatedCase = dao.save(
            case.copy(
                status = status,
                score = score,
                errorCode = errorCode,
                modifiedAt = Date(clock.millis()),
            )
        )

        // Update party KYC status
        LOGGER.info(">>> KYC#${case.id} - Updating party KYC status to $status")
        partyService.updateKyStatus(case.party, status)

        // KycEventListener runs after commit, AgentKycEventListener inside this transaction
        eventPublisher.publishEvent(
            KycCaseCompletedEvent(
                caseId = case.id,
                tenantId = case.tenantId!!,
                partyId = case.party.id!!,
                status = status,
            )
        )
        return updatedCase
    }

    private fun updatePaymentMethodStatus(verifications: List<KycVerificationEntity>) {
        verifications
            .filter { verification -> verification.type == KycVerificationType.PAYMENT_METHOD }
            .forEach { verification ->
                val paymentMethod = verification.paymentMethod
                if (paymentMethod != null && paymentMethod.status == PaymentMethodStatus.PENDING_VERIFICATION) {
                    val status = if (verification.status == KycStatus.VERIFIED) {
                        PaymentMethodStatus.VERIFIED
                    } else {
                        PaymentMethodStatus.REJECTED
                    }
                    paymentMethodService.updateStatus(paymentMethod, status)
                }
            }
    }

    private fun verify(case: KycCaseEntity, verificationType: KycVerificationType): List<KycVerificationEntity> {
        LOGGER.info(">>> KYC#${case.id} - Verifcation: $verificationType")

        val verifier = verifierProvider.get(verificationType)
        return verifier?.verify(case) ?: emptyList()
    }

    /** The case's own party only: no admin bypass, a KYC document is personal. */
    private fun ensureOwnCase(case: KycCaseEntity) {
        val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
        if (partyId == null || partyId != case.party.id) {
            throw ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
        }
    }

    private fun ensureStatusIsPending(case: KycCaseEntity) {
        if (case.status != KycStatus.PENDING_VERIFICATION) {
            throw notPending()
        }
    }

    private fun notPending() = BadRequestException(
        error = Error(code = ErrorCode.KYC_CASE_NOT_PENDING)
    )

    /**
     * Within the image's save transaction: the case is locked and still PENDING_VERIFICATION, so a submit committed
     * during the storage I/O (before the save) wins, and one started after it waits for the save.
     */
    private fun ensureStillPending(case: KycCaseEntity) {
        val status = dao.findStatusForUpdateById(case.id)
        // Compared by name: a name the enum no longer has isn't pending either (no valueOf exception)
        if (status != KycStatus.PENDING_VERIFICATION.name) {
            throw notPending()
        }
    }

    /**
     * A path already recorded by an image of the case other than [imageId] is refused: two rows would share one object, which the
     * replacement of either deletes.
     */
    private fun ensurePathNotUsed(case: KycCaseEntity, path: String, imageId: String?) {
        if (kycImageDao.findByKycCaseAndPath(case, path).any { it.id != imageId }) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    message = "$path is already the path of another image of Case#${case.id}.",
                    parameter = Parameter(
                        name = "path",
                        type = ParameterType.PARAMETER_TYPE_PAYLOAD,
                        value = path,
                    ),
                )
            )
        }
    }

    /**
     * After a failed save: deletes the new object at [path], unless a row of the case records it (a concurrent write
     * of the same path, or the multipart upload's in-place rewrite of the row's own object).
     */
    private fun deleteUnreferenced(case: KycCaseEntity, path: String, delete: () -> Unit) {
        if (kycImageDao.findByKycCaseAndPath(case, path).isEmpty()) {
            deleteQuietly(case, path, delete)
        }
    }

    /** Deletes an object no row references any more; a failure is logged, not fatal. */
    private fun deleteQuietly(case: KycCaseEntity, path: String, delete: () -> Unit) {
        try {
            delete()
        } catch (ex: Exception) {
            LOGGER.warn(">>> KYC#${case.id} - Could not delete the image $path", ex)
        }
    }

    /** The sides of the identification document must be uploaded before submitting. The selfie is optional. */
    private fun ensureIdentificationImages(case: KycCaseEntity) {
        val uploaded = case.images.filter { image -> image.path != null }.map { image -> image.imageType }
        val missing = requiredImages(case.identificationType).filter { type -> type !in uploaded }
        if (missing.isNotEmpty()) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE,
                    message = "Case#${case.id} (${case.identificationType}) is missing: ${missing.joinToString()}",
                )
            )
        }
    }

    /** Two-sided documents need their front and back; the others (e.g. a passport) only their front. */
    private fun requiredImages(type: IdentificationType): List<KycImageType> = when (type) {
        IdentificationType.NATIONAL_ID,
        IdentificationType.DRIVER_LICENSE,
        IdentificationType.RESIDENT_CARD,
            -> listOf(KycImageType.FRONT, KycImageType.BACK)

        else -> listOf(KycImageType.FRONT)
    }

    private fun ensureStatusIsSubmitted(case: KycCaseEntity) {
        if (case.status != KycStatus.SUBMITTED) {
            throw BadRequestException(
                error = Error(code = ErrorCode.KYC_CASE_NOT_SUBMITTED)
            )
        }
    }

    private fun ensureImage(file: MultipartFile) {
        val mimeType = file.contentType
        if (mimeType?.startsWith("image/") != true) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.KYC_IMAGE_INVALID_MIME_TYPE,
                    message = "${file.originalFilename} is a $mimeType - This is not an image file.",
                )
            )
        }
    }
}
