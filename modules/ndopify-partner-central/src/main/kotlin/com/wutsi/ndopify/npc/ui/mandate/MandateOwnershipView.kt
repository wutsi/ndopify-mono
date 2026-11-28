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
 * The verdict carries no date and no document — a status, reason codes and the city comparison (#376) — so this view
 * has neither a date nor a document: it names the check's kind, never a document's type, title, id, page, path or
 * URL. [listingId] is the mandate's listing when one exists, for the *Voir l'annonce* link of a VERIFIED verdict.
 * [location] is the `UNRESOLVED` comparison's neutral hint (null otherwise): the comparison's two display names, the
 * sentence and the Documents link's label, never a document id, type, title, page, path or storage URL.
 *
 * [unavailable] when the verdict couldn't be read (or is `UNKNOWN`): the section says so and shows nothing else.
 * [overridden] is a `VERIFIED` verdict an admin's override decided (#377): its own pill and copy, never worded as a
 * document check; [overriddenText] is that copy, null otherwise. The intro drops its documents clause when it is set.
 */
data class OwnershipSectionView(
    val status: OwnershipVerificationStatus,
    val unavailable: Boolean,
    val tagLabel: String?,
    val tagStyle: String?,
    val overridden: Boolean,
    val overriddenText: String?,
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
 * [places] is the city comparison of an `OWNERSHIP_PROPERTY_MISMATCH` reason — the comparison's two display names
 * only, never a document id, type, title, page, path or storage URL.
 */
data class OwnershipReasonView(
    val check: String?,
    val text: String,
    val action: String,
    val problems: List<String>,
    val places: OwnershipPlacesView?,
)
