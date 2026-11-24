package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.MandateStatus

/**
 * A mandate as its page shows it (`templates/mandate.html`): dates already worded in the tenant's time zone, null
 * when unknown (ex: the contract couldn't be loaded), so the page leaves the line out.
 */
data class MandateView(
    val id: Long,
    val status: MandateStatus,
    val title: String,
    val tag: MandateRules.Tag,
    val words: MandateRules.PartyWords,
    /** A landlord's listing, not a search. */
    val owner: Boolean,
    /** A sale mandate (a listing's sale, or a buyer's search): its closing screens say price, never rent. */
    val sale: Boolean,
    /** "Proposez des biens à l'acheteur et organisez les visites.": what the agent does next, after the contract. */
    val nextStepHint: String,
    val acceptedAt: String?,
    /** Assigné > En approbation > Actif > Fermé (or Annulé). */
    val steps: List<StepView>,
    val currentStep: Int,
    /** "Étape 2 sur 4 · En approbation, le 15 octobre 2026": the phones' line under the steps. */
    val stepSummary: String,
    val requestedAt: String?,
    val signatureDeadline: String?,
    val deadlinePassed: Boolean,
    val approvedAt: String?,
    val validUntil: String?,
    val closedAt: String?,
    val finalPrice: String?,
    val age: String?,
    val cancellation: String?,
    val cancellationReason: String?,
    /** The deal line of a CLOSED mandate: the listing, the counterpart mandate and the client, or the "outside" note. */
    val listingId: Long?,
    val listingLabel: String?,
    val counterpartId: Long?,
    val counterpartLabel: String?,
    val clientLabel: String?,
    val outside: String?,
    /** Null when the customer can't be loaded. */
    val contact: ContactView?,
    /**
     * Sector, amount, moving date, type (with its rooms), comments, and a purchase's required documents (shown with the
     * documents note): always visible.
     */
    val keyCriteria: List<Pair<String, String>>,
    /** Under "Voir tous les critères". */
    val moreCriteria: List<Pair<String, String>>,
    /** A purchase requiring at least one title document, whatever its type: the page shows the documents-filter note. */
    val titleDocumentsRequired: Boolean,
    val editable: Boolean,
    /** "Modifier" on the contact block: the customer's details can still be corrected. */
    val customerEditable: Boolean,
    val closable: Boolean,
    val cancellable: Boolean,
    val signatureTtlDays: Int?, // Null without a request for signature
    val hasContract: Boolean,
    val contractDuration: String?, // "6 mois"; null without a contract
    val visitFee: String?, // "5K FCFA"; null when there's none

    /** A sale contract's commission: "7 % du prix de vente"; null without a contract or for a rental. */
    val commission: String?,
)

data class StepView(
    val status: String,
    val label: String,
    val date: String?,
    val done: Boolean,
    /** The last step of a cancelled mandate ("Annulé"): its mark is red, not green. */
    val cancelled: Boolean = false,
)

data class ContactView(
    val name: String,
    val email: String,
    /** The stored number ("+237699123456"), for the `tel:` link. */
    val whatsapp: String?,
    /** "+237 6 99 12 34 56". */
    val whatsappDisplay: String?,
    /** "237699123456", for `https://wa.me/`. */
    val whatsappDigits: String?,
    /** "le matin": when the customer wants to be called. */
    val period: String?,
)
