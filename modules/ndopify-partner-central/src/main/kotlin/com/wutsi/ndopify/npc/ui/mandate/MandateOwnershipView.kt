package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus

/** The two display names of the city comparison; a blank one reads "non précisée" (set by the view builder). */
data class OwnershipPlacesView(
    val mandateCity: String,
    val documentLocality: String,
)

/** The UNRESOLVED comparison's neutral hint: the two places, the sentence and the Documents link's label. */
data class OwnershipLocationView(
    val places: OwnershipPlacesView,
    val text: String,
    val action: String,
)

/**
 * The *Vérification du propriétaire* section of a LISTING mandate's page (#298): the verdict #297 decided, worded.
 * The verdict carries no date and no document — only a status and reason codes — so this view has neither: it names
 * the check's kind, never a document's type, title, id, page, path or URL. [listingId] is the mandate's listing when
 * one exists, for the *Voir l'annonce* link of a VERIFIED verdict.
 *
 * [unavailable] when the verdict couldn't be read (or is `UNKNOWN`): the section says so and shows nothing else.
 */
data class OwnershipSectionView(
    val status: OwnershipVerificationStatus,
    val unavailable: Boolean,
    val tagLabel: String?,
    val tagStyle: String?,
    val intro: String,
    val missing: List<String>,
    val waiting: String?,
    val documentsLabel: String?,
    val reasons: List<OwnershipReasonView>,
    val listingId: Long?,
    val location: OwnershipLocationView?,
)

/**
 * One failed check of a FAILED verdict. [check] is the kind of piece the verdict looked at (*La pièce d'identité* /
 * *Le document de propriété*), null for a code the page doesn't know; [text] and [action] are its French wording, and
 * [problems] the document's own problem instructions (#331) of an `*_UNUSABLE` reason, in the server's order.
 */
data class OwnershipReasonView(
    val check: String?,
    val text: String,
    val action: String,
    val problems: List<String>,
    val places: OwnershipPlacesView?,
)
