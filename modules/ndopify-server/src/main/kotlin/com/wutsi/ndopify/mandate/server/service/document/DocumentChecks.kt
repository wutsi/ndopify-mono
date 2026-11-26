package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * The problem codes of an extracted document, in the order they're reported: a mismatch, a missing page, the required
 * fields a low confidence or a blank value makes unusable, then an unreadable required expiry.
 *
 * An answer the model marked invalid stops here: `DOCUMENT_UNREADABLE` alone, unless a side the type needs is missing
 * (a fact the model still reports): `DOCUMENT_PAGE_MISSING` alone then, since the agent must fix the page first. A missing per-field confidence counts as 0; only the required fields and the [DocumentRequirements.oneOf]
 * groups are checked, an optional field keeps whatever confidence it got. The expiry is the expiry rule's, never the
 * generic field rule. The `DOCUMENT_EXPIRED` warning is not added here.
 */
@Service
class DocumentChecks(
    @param:Value("\${ndopify.mandate.document.extract.min-confidence}") private val minConfidence: Double,
) {
    /** The codes of [data] for a document declared as [type]; empty when it's usable. */
    fun check(type: DocumentType, data: DocumentData): List<String> {
        val requirements = DocumentRequirements.of(type) ?: return emptyList()
        val sidesPresent = sides(data)

        // An answer the model couldn't read is alone, but a missing side is a fact it still reports: the agent must
        // fix the page before a new read can say anything
        if (!data.valid) {
            return if (sidesPresent.containsAll(requirements.sides)) {
                listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE)
            } else {
                listOf(DocumentProblemCodes.DOCUMENT_PAGE_MISSING)
            }
        }

        val codes = mutableListOf<String>()
        val detectedType = data.detectedType
        if (detectedType != null && !detectedType.trim().equals(type.name, ignoreCase = true)) {
            codes.add(DocumentProblemCodes.DOCUMENT_TYPE_MISMATCH)
        }
        if (!sidesPresent.containsAll(requirements.sides)) {
            codes.add(DocumentProblemCodes.DOCUMENT_PAGE_MISSING)
        }
        if (hasLowConfidenceField(requirements, data)) {
            codes.add(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE)
        }
        if (requirements.requiresExpiry && data.expiresAt() == null) {
            codes.add(DocumentProblemCodes.EXPIRY_DATE_UNREADABLE)
        }
        return codes
    }

    private fun sides(data: DocumentData): Set<String> =
        data.sidesPresent.orEmpty().map { side -> side.trim().uppercase() }.toSet()

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
