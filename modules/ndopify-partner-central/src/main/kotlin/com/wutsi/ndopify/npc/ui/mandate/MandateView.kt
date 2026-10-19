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
    val acceptedAt: String?,
    /** Assigné > En approbation > Actif > Fermé (or Annulé). */
    val steps: List<StepView>,
    val currentStep: Int,
    /** "Étape 2 sur 4 · En approbation, le 15 octobre 2026": the phones' line under the steps. */
    val stepSummary: String,
    val requestedAt: String?,
    val signatureDeadline: String?,
    val deadlinePassed: Boolean,
    /** Edited since sent to the customer: sending it again becomes the step's main action. */
    val editedSince: Boolean,
    val approvedAt: String?,
    val validUntil: String?,
    val closedAt: String?,
    val finalPrice: String?,
    val age: String?,
    val cancellation: String?,
    val cancellationReason: String?,
    /** Null when the customer can't be loaded. */
    val contact: ContactView?,
    /** Sector, amount, moving date, type (with its rooms) and comments: always visible. */
    val keyCriteria: List<Pair<String, String>>,
    /** Under "Voir tous les critères". */
    val moreCriteria: List<Pair<String, String>>,
    val editable: Boolean,
    val closable: Boolean,
    val cancellable: Boolean,
    val signatureTtlDays: Int?, // Null without a request for signature
)

data class StepView(
    val status: String,
    val label: String,
    val date: String?,
    val done: Boolean,
)

data class ContactView(
    val name: String,
    val email: String,
    /** E.164, for `tel:`. */
    val whatsapp: String?,
    /** "+237 6 99 12 34 56". */
    val whatsappDisplay: String?,
    /** "237699123456", for `https://wa.me/`. */
    val whatsappDigits: String?,
    /** "le matin": when the customer wants to be called. */
    val period: String?,
)
