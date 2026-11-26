package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * The problem codes of an extracted document, in the order they're reported: a mismatch, a missing page, an unreadable
 * required expiry, then the required fields a low confidence or a blank value makes unusable.
 *
 * An answer the model marked invalid stops here: `DOCUMENT_UNREADABLE` alone, since its detection and fields aren't
 * trusted. A missing per-field confidence counts as 0; only the required fields and the [DocumentRequirements.oneOf]
 * groups are checked, an optional field keeps whatever confidence it got. The expiry is the expiry rule's, never the
 * generic field rule. The `DOCUMENT_EXPIRED` warning is not added here.
 */
@Service
class DocumentChecks(
    @param:Value("\${ndopify.mandate.document.extract.min-confidence}") private val minConfidence: Double,
) {
    /** The codes of [data] for a document declared as [type]; empty when it's usable. */
    fun check(type: DocumentType, data: DocumentData): List<String> {
        if (!data.valid) {
            return listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE)
        }
        val requirements = DocumentRequirements.of(type) ?: return emptyList()
        val codes = mutableListOf<String>()

        if (data.detectedType != null && !data.detectedType.equals(type.name, ignoreCase = true)) {
            codes.add(DocumentProblemCodes.DOCUMENT_TYPE_MISMATCH)
        }

        val sides = data.sidesPresent.orEmpty().map { side -> side.uppercase() }.toSet()
        if (!sides.containsAll(requirements.sides)) {
            codes.add(DocumentProblemCodes.DOCUMENT_PAGE_MISSING)
        }

        if (requirements.requiresExpiry && data.expiresAt() == null) {
            codes.add(DocumentProblemCodes.EXPIRY_DATE_UNREADABLE)
        }

        if (hasLowConfidenceField(requirements, data)) {
            codes.add(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE)
        }
        return codes
    }

    private fun hasLowConfidenceField(requirements: DocumentRequirements.Requirements, data: DocumentData): Boolean {
        val fields = data.toFields()
        val confidences = data.fieldConfidences.orEmpty()
        // An identity's expiry is read as a date: it's checked by the expiry rule, never as a generic field
        if (requirements.required.any { key -> key != "expiryDate" && !isConfident(fields[key], confidences[key]) }) {
            return true
        }
        return requirements.oneOf.any { group ->
            group.none { key -> isConfident(fields[key], confidences[key]) }
        }
    }

    private fun isConfident(value: String?, confidence: Double?): Boolean =
        !value.isNullOrBlank() && (confidence ?: 0.0) >= minConfidence
}
