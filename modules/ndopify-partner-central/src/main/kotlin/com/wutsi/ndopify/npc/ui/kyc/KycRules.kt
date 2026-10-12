package com.wutsi.ndopify.npc.ui.kyc

import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import java.util.Locale

/** Rules of the identity verification page (`kyc.html`), kept free of HTTP so they can be tested on their own. */
object KycRules {
    const val STEP_FRONT = "recto"
    const val STEP_BACK = "verso"
    const val STEP_SELFIE = "selfie"
    const val STEP_REVIEW = "revue"
    const val STEP_CONFIRMATION = "confirmation"

    val STEPS = listOf(STEP_FRONT, STEP_BACK, STEP_SELFIE, STEP_REVIEW)

    /** The photo each upload step adds to the case, in the order of the steps. */
    val PHOTOS: Map<String, KycImageType> = linkedMapOf(
        STEP_FRONT to KycImageType.FRONT,
        STEP_BACK to KycImageType.BACK,
        STEP_SELFIE to KycImageType.SELFIE,
    )

    const val STATUS_PENDING = "attente"
    const val STATUS_VERIFIED = "verifie"
    const val STATUS_REJECTED = "rejete"

    const val MAX_PHOTO_BYTES: Long = 5L * 1024 * 1024
    val ACCEPTED_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    /**
     * The status panel shown instead of the wizard, or null while the agent still has documents to send: no case
     * yet, a PENDING one, or a cancelled one (the next upload opens a new case). Any status this page doesn't
     * know about reads as "en attente", since there is nothing for the agent to do.
     */
    fun status(case: KycCase?): String? = when (case?.status) {
        null, KycStatus.PENDING, KycStatus.CANCELLED -> null
        KycStatus.VERIFIED -> STATUS_VERIFIED
        KycStatus.REJECTED -> STATUS_REJECTED
        else -> STATUS_PENDING
    }

    /** The photos already in the case, by type. Only a PENDING case's photos count: the others are frozen. */
    fun photos(case: KycCase?): Map<KycImageType, KycImage> =
        if (case?.status == KycStatus.PENDING) case.images.associateBy { image -> image.imageType } else emptyMap()

    /** The first upload step whose photo is missing, or the review once they're all in. */
    fun firstIncompleteStep(photos: Set<KycImageType>): String =
        PHOTOS.entries.firstOrNull { (_, type) -> type !in photos }?.key ?: STEP_REVIEW

    /** Checks a photo from its real (sniffed) type and size; null when it's acceptable. */
    fun photo(size: Long, mimeType: String?): String? = when {
        mimeType !in ACCEPTED_TYPES -> "Format non accepté. Utilisez une photo JPG, PNG ou WEBP."
        size > MAX_PHOTO_BYTES -> {
            val mb = String.format(Locale.FRANCE, "%.1f", size / (1024.0 * 1024.0))
            "Cette photo pèse $mb Mo. La taille maximale est de 5 Mo."
        }
        else -> null
    }

    /** Ends "Motif : …" on the rejected status, from the case's error code (set by the ID / selfie verification). */
    fun rejectionReason(errorCode: String?): String = when (errorCode) {
        KycErrorCode.IDENTIFICATION_REJECTED -> "votre pièce d'identité n'a pas pu être validée."
        KycErrorCode.SELFIE_REJECTED -> "votre selfie ne correspond pas à la photo de votre pièce d'identité."
        else -> "vos documents n'ont pas pu être vérifiés."
    }

    /** Initials for the header's account button; "NP" (Ndopify Partner) when the name is unknown. */
    fun initials(firstName: String?, lastName: String?): String =
        listOfNotNull(firstName, lastName)
            .mapNotNull { name -> name.trim().firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "NP" }
}
