package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.BadRequestException
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
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycImageEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.event.KycCaseCompletedEvent
import com.wutsi.ndopify.party.server.mail.KycMailet
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.util.MimeUtils
import com.wutsi.ndopify.refdata.dto.KycErrorCode
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
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(KycService::class.java)
        private val MAX_RETRIES = 3
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
                status = KycStatus.PENDING,
                createdAt = now,
                modifiedAt = now,
            )
        )
    }

    fun upload(
        id: String,
        identificationType: IdentificationType,
        imageType: KycImageType,
        file: MultipartFile
    ): KycImageEntity {
        ensureImage(file)

        // Images can only be added or replaced while the case is PENDING: once submitted, the documents under
        // review (or already reviewed) must not change.
        val case = findById(id)
        ensureStatusIsPending(case)
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
        val path = "kyc/${case.id}/images/${image.id}.$extension"
        val storage = storageProvider.get()
        storage.store(path, file.inputStream, mimeType)

        return tx.execute {
            kycImageDao.save(
                image.copy(
                    path = path,
                    storageType = storage.type(),
                    mimeType = mimeType,
                    modifiedAt = now,
                )
            )
        }
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
     * Sends a case for review: PENDING → SUBMITTED. Its documents are frozen from then on, and the offline
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
     * 1. [claim] the case (SUBMITTED → IN_PROGRESS), in a short transaction;
     * 2. run the verifiers, outside any transaction: they return their results unsaved;
     * 3. [complete] the case with these results in a short transaction, or [retry] it if a verifier failed.
     */
    fun verify(id: String): KycCaseEntity {
        val case = tx.execute { claim(id) }

        val verifications = try {
            LOGGER.info(">>> KYC#$id - Verifying identification, selfie and payment methods")
            // Checked before the other verifiers (no need to call them), and here rather than in complete(): failing
            // there would leave the case IN_PROGRESS
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
            return tx.execute { retry(findById(id), KycErrorCode.GATEWAY_ERROR, ex.message) }
        }

        return tx.execute { complete(id, verifications) }
    }

    /**
     * Sends back for verification the cases left IN_PROGRESS for more than [inProgressTimeoutMinutes]: their
     * verification was interrupted between [claim] and [complete] (e.g. the server stopped). Counted as a failed
     * attempt, so a case whose verification keeps getting interrupted ends up REQUIRES_MANUAL_REVIEW.
     */
    fun recoverInterrupted(): List<KycCaseEntity> {
        val cutoff = Date(clock.millis() - inProgressTimeoutMinutes * 60_000)
        return dao.findByStatusAndModifiedAtBefore(KycStatus.IN_PROGRESS, cutoff)
            .mapNotNull { case -> tx.execute { recoverInterrupted(case.id, cutoff) } }
    }

    private fun recoverInterrupted(id: String, cutoff: Date): KycCaseEntity? {
        // Checked again under the lock: the verification may have completed since the case was listed
        val case = dao.findForUpdateById(id) ?: return null
        if (case.status != KycStatus.IN_PROGRESS || !case.modifiedAt.before(cutoff)) {
            return null
        }

        LOGGER.warn(">>> KYC#$id - Verification interrupted, IN_PROGRESS since ${case.modifiedAt}")
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
        // Locked, so that the job and the endpoint can't both claim the case: the 2nd waits, then sees IN_PROGRESS
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
                status = KycStatus.IN_PROGRESS,
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
                status = if (retries < MAX_RETRIES) KycStatus.SUBMITTED else KycStatus.REQUIRES_MANUAL_REVIEW,
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
        val verifier = verifierProvider.get(verificationType)
        return verifier?.verify(case) ?: emptyList()
    }

    private fun ensureStatusIsPending(case: KycCaseEntity) {
        if (case.status != KycStatus.PENDING) {
            throw BadRequestException(
                error = Error(code = ErrorCode.KYC_CASE_NOT_PENDING)
            )
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
