package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.OwnershipVerification
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationReason
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus
import com.wutsi.ndopify.npc.ui.document.DocumentFieldLabels

/**
 * Words `GET /v1/mandates/{id}/ownership` (#297) as the approved prototype does, into an [OwnershipSectionView]. The
 * page only words the verdict; it never re-derives it from the documents. A null verdict or an `UNKNOWN` status is
 * the section's own "unavailable", never an empty FAILED block: `UNKNOWN` is the DTO's default and is never returned.
 *
 * The copy is the prototype's, one label per reason code with a generic line for a code this page doesn't know yet;
 * an `*_UNUSABLE` reason's `details` are the document's problem codes, worded by [DocumentFieldLabels] as the
 * document page words them (#331), in the server's order.
 */
object MandateOwnershipViews {
    private const val MISSING_ID =
        "Il manque la pièce d'identité du propriétaire : une carte nationale d'identité (recto et verso) ou un " +
            "passeport, à son nom."
    private const val MISSING_DOCUMENT =
        "Il manque le document de propriété : un titre foncier ou un certificat de propriété, à son nom."
    private const val MISSING_OTHER =
        "Une pièce manque pour vérifier le propriétaire : vérifiez les documents du mandat."
    private const val WAITING =
        "La lecture des pièces est en cours : la vérification se fera d'elle-même dès qu'elles seront analysées."
    private const val ADD_DOCUMENTS = "Ajouter ces pièces dans Documents"
    private const val SEE_DOCUMENTS = "Voir les documents"

    private const val ID_CHECK = "La pièce d'identité"
    private const val DOCUMENT_CHECK = "Le document de propriété"

    private val TAGS = mapOf(
        OwnershipVerificationStatus.PENDING to ("En cours" to "info"),
        OwnershipVerificationStatus.VERIFIED to ("Vérifié" to "success"),
        OwnershipVerificationStatus.FAILED to ("Non vérifié" to "warning"),
    )

    private data class Wording(val text: String, val action: String)

    private val REASON_WORDING = mapOf(
        "OWNERSHIP_ID_EXPIRED" to Wording(
            "La pièce est expirée : elle ne peut plus servir à vérifier le propriétaire.",
            "Reprenez la photo d'une pièce en cours de validité, puis ajoutez-la dans Documents.",
        ),
        "OWNERSHIP_ID_UNUSABLE" to Wording(
            "La pièce n'a pas pu être vérifiée.",
            "Corrigez la pièce dans Documents : reprenez sa photo, ou changez son type.",
        ),
        "OWNERSHIP_ID_NAME_MISMATCH" to Wording(
            "La pièce ne porte pas le nom du propriétaire : elle ne peut pas l'identifier.",
            "Ajoutez une pièce d'identité au nom du propriétaire.",
        ),
        "OWNERSHIP_DOCUMENT_UNUSABLE" to Wording(
            "Le document n'a pas pu être vérifié.",
            "Corrigez le document dans Documents : reprenez sa photo, ou changez son type.",
        ),
        "OWNERSHIP_DOCUMENT_NAME_MISMATCH" to Wording(
            "Le document ne désigne pas le propriétaire du bien.",
            "Ajoutez un titre foncier ou un certificat de propriété au nom du propriétaire.",
        ),
    )

    private val REASON_OTHER = Wording(
        "La vérification du propriétaire a échoué sur ce document.",
        "Vérifiez la pièce dans Documents.",
    )

    /** [verification] null when the read failed; [listingId] the mandate's listing, null when it has none. */
    fun section(
        mandate: Mandate,
        verification: OwnershipVerification?,
        listingId: Long?,
        labels: DocumentFieldLabels,
    ): OwnershipSectionView {
        val intro = "Ndopify vérifie que ${MandateRules.words(mandate.mandateType, mandate.listingType).the} est bien " +
            "le propriétaire du bien, à partir des pièces de la section Documents."
        val status = verification?.status ?: OwnershipVerificationStatus.UNKNOWN
        if (verification == null || status == OwnershipVerificationStatus.UNKNOWN) {
            return OwnershipSectionView(
                status = OwnershipVerificationStatus.UNKNOWN,
                unavailable = true,
                tagLabel = null,
                tagStyle = null,
                intro = intro,
                missing = emptyList(),
                waiting = null,
                documentsLabel = null,
                reasons = emptyList(),
                listingId = null,
            )
        }
        val tag = TAGS[status]
        val pending = status == OwnershipVerificationStatus.PENDING
        val waiting = pending && verification.reasons.isEmpty()
        return OwnershipSectionView(
            status = status,
            unavailable = false,
            tagLabel = tag?.first,
            tagStyle = tag?.second,
            intro = intro,
            missing = if (pending && !waiting) verification.reasons.map(::missing) else emptyList(),
            waiting = if (waiting) WAITING else null,
            documentsLabel = when {
                !pending -> null
                waiting -> SEE_DOCUMENTS
                else -> ADD_DOCUMENTS
            },
            reasons = if (status == OwnershipVerificationStatus.FAILED) {
                verification.reasons.map { reason(reason = it, labels = labels) }
            } else {
                emptyList()
            },
            listingId = listingId.takeIf { status == OwnershipVerificationStatus.VERIFIED },
        )
    }

    /** A PENDING reason: the piece the verdict found missing, and what counts for it. */
    private fun missing(reason: OwnershipVerificationReason): String = when (reason.code) {
        "OWNERSHIP_ID_MISSING" -> MISSING_ID
        "OWNERSHIP_DOCUMENT_MISSING" -> MISSING_DOCUMENT
        else -> MISSING_OTHER
    }

    private fun reason(reason: OwnershipVerificationReason, labels: DocumentFieldLabels): OwnershipReasonView {
        val wording = REASON_WORDING[reason.code] ?: REASON_OTHER
        return OwnershipReasonView(
            check = check(reason.code),
            text = wording.text,
            action = wording.action,
            problems = reason.details.map(labels::instruction),
        )
    }

    /** The kind of check a reason belongs to; null for a code this page doesn't know (no empty heading). */
    private fun check(code: String): String? = when {
        code.startsWith("OWNERSHIP_ID_") -> ID_CHECK
        code.startsWith("OWNERSHIP_DOCUMENT_") -> DOCUMENT_CHECK
        else -> null
    }
}
