package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.server.service.kyc.KycInfo
import com.wutsi.ndopify.party.server.service.kyc.KycInfoExtractorIdentification
import org.springframework.stereotype.Service
import java.io.File
import java.time.ZoneOffset

/**
 * Reads a national ID or a passport, through the KYC identification extractor (same prompt and schema [KycInfo]) so
 * that the identity fields are read the same way for KYC and for a mandate document.
 *
 * A driver's license, resident card or tax id recognized on the pages is kept as [DocumentData.detectedType] — the
 * caller reports the mismatch. An empty answer (nothing read, no reason) is a [DocumentExtractionException].
 */
@Service
class IdentityDocumentDataExtractor(
    private val kyc: KycInfoExtractorIdentification,
) : DocumentDataExtractor {
    override val types = setOf(DocumentType.NATIONAL_ID, DocumentType.PASSPORT)

    override fun extract(type: DocumentType, images: List<File>): DocumentData {
        val info = kyc.extract(images)
        if (isEmpty(info)) {
            throw DocumentExtractionException("empty identity extraction")
        }

        val expiryDate = info.expiryDate
            ?.toInstant()
            ?.atZone(ZoneOffset.UTC)
            ?.toLocalDate()
            ?.toString()
        return when (type) {
            DocumentType.PASSPORT -> Passport(
                detectedType = info.type.name,
                number = info.number.ifBlank { null },
                firstName = info.firstName.ifBlank { null },
                lastName = info.lastName.ifBlank { null },
                dateOfBirth = info.dateOfBirth,
                issueDate = info.issueDate,
                expiryDate = expiryDate,
                countryCode = info.countryCode.ifBlank { null },
                sidesPresent = info.sidesPresent,
                valid = info.valid,
                invalidityReason = info.invalidityReason,
                confidence = info.confidence,
                fieldConfidences = info.fieldConfidences,
            )
            else -> NationalId(
                detectedType = info.type.name,
                number = info.number.ifBlank { null },
                firstName = info.firstName.ifBlank { null },
                lastName = info.lastName.ifBlank { null },
                dateOfBirth = info.dateOfBirth,
                issueDate = info.issueDate,
                expiryDate = expiryDate,
                countryCode = info.countryCode.ifBlank { null },
                sidesPresent = info.sidesPresent,
                valid = info.valid,
                invalidityReason = info.invalidityReason,
                confidence = info.confidence,
                fieldConfidences = info.fieldConfidences,
            )
        }
    }

    private fun isEmpty(info: KycInfo): Boolean = info.type == IdentificationType.UNKNOWN &&
        info.number.isBlank() &&
        info.firstName.isBlank() &&
        info.lastName.isBlank() &&
        info.invalidityReason == null
}
