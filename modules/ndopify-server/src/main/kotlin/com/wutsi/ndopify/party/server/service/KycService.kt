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
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycImageEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.util.MimeUtils
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.net.URL
import java.time.Clock
import java.util.Date

@Service
class KycService(
    @param:Value("\${ndopify.party.kyc.image.url-ttl-seconds}") val imageUrlTTL: Int,

    private val dao: KycCaseRepository,
    private val partyService: PartyService,
    private val kycImageDao: KycImageRepository,
    private val verifierProvider: KycCaseVerifierProvider,
    private val storageProvider: StorageServiceProvider,
    private val paymentMethodService: PaymentMethodService,
    private val mailet: KycMailet,
    private val clock: Clock,
    private val logger: KVLogger,
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
        return dao.findAll(spec, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
            .drop(request.offset)
            .take(request.limit)
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

    @Transactional
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

        return kycImageDao.save(
            image.copy(
                path = path,
                storageType = storage.type(),
                mimeType = mimeType,
                modifiedAt = now,
            )
        )
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
     */
    @Transactional
    fun verify(id: String): KycCaseEntity {
        val case = findById(id)
        return verify(case)
    }

    @Transactional
    fun verify(case: KycCaseEntity): KycCaseEntity {
        ensureStatusIsSubmitted(case)
        try {
            startVerification(case)
            val updatedCase = verifyIdentification(case)
            verifyPaymentMethods(updatedCase)

            return updatedCase
        } catch (ex: Exception) {
            LOGGER.error(">>> KYC#${case.id} - Verification failed", ex)
            val retries = (case.retries ?: 0) + 1
            return dao.save(
                case.copy(
                    status = if (retries < MAX_RETRIES) KycStatus.PENDING else KycStatus.REQUIRES_MANUAL_REVIEW,
                    retries = retries,
                    errorCode = KycErrorCode.GATEWAY_ERROR,
                    errorMessage = ex.message,
                    modifiedAt = Date(clock.millis()),
                )
            )
        }
    }

    fun sendStatusEmail(case: KycCaseEntity) {
        mailet.send(case)
    }

    private fun verifyIdentification(case: KycCaseEntity): KycCaseEntity {
        // ID
        LOGGER.info(">>> KYC#${case.id} - Verifying identification")
        val idVerification = verify(case, KycVerificationType.IDENTIFICATION).first()

        // Selfie
        LOGGER.info(">>> KYC#${case.id} - Verifying selfie")
        val selfieVerification = verify(case, KycVerificationType.SELFIE).firstOrNull()

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

        return updatedCase
    }

    private fun verifyPaymentMethods(case: KycCaseEntity): KycCaseEntity {
        LOGGER.info(">>> KYC case#${case.id} - Verifying payment methods")
        val paymentVerifications = verify(case, KycVerificationType.PAYMENT_METHOD)

        // Update the payment methods KYC status
        paymentVerifications.forEach { verification ->
            val paymentMethod = verification.paymentMethod
            if (paymentMethod != null && paymentMethod.status == PaymentMethodStatus.PENDING_VERIFICATION) {
                val status = if (verification.status == KycStatus.VERIFIED) {
                    PaymentMethodStatus.VERIFIED
                } else {
                    PaymentMethodStatus.REJECTED
                }
                paymentMethodService.updateStatus(verification.paymentMethod, status)
            }
        }

        return case
    }

    private fun verify(case: KycCaseEntity, verificationType: KycVerificationType): List<KycVerificationEntity> {
        val verifier = verifierProvider.get(verificationType)
        return verifier?.verify(case) ?: emptyList()
    }

    private fun startVerification(case: KycCaseEntity) {
        LOGGER.info(">>> KYC#${case.id} - Starting verification")
        dao.save(
            case.copy(
                status = KycStatus.IN_PROGRESS,
                errorCode = null,
                errorMessage = null,
                modifiedAt = Date(clock.millis()),
            )
        )
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
