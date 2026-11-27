package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import java.time.Duration
import java.util.Date

/**
 * The life of a mandate as the agent sees it, worded as the prototype's `partner-central.js` (`STATUS_TAGS`,
 * `partyWords`, `CANCELLATION_LABELS`): shared by the Espace agent's *Mes mandats* and the mandate pages.
 */
object MandateRules {
    /** Listed in *Mes mandats*. */
    val ACTIVE: Set<MandateStatus> = setOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL, MandateStatus.APPROVED)

    /** The criteria can still change: the customer hasn't signed. */
    val EDITABLE: Set<MandateStatus> = setOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL)

    /** A landlord's signed mandate: the only one with a listing. */
    fun listed(mandate: Mandate): Boolean =
        mandate.mandateType == MandateType.LISTING && mandate.status == MandateStatus.APPROVED

    /** The mandate page shows these; any other status is the locked view. */
    val SHOWN: Set<MandateStatus> = ACTIVE + MandateStatus.CLOSED + MandateStatus.CANCELLED

    data class Tag(val label: String, val style: String)

    private val TAGS = mapOf(
        MandateStatus.ASSIGNED to Tag("Assigné", "info"),
        MandateStatus.PENDING_APPROVAL to Tag("En approbation", "warning"),
        MandateStatus.APPROVED to Tag("Actif", "success"),
        MandateStatus.CLOSED to Tag("Fermé", "neutral"),
        MandateStatus.CANCELLED to Tag("Annulé", "neutral"),
    )

    // MandateCancellationCode, as the cancel form words them.
    val CANCELLATION_LABELS: Map<MandateCancellationCode, String> = linkedMapOf(
        MandateCancellationCode.FOUND_INDEPENDENTLY to "trouvé sans votre aide",
        MandateCancellationCode.FOUND_VIA_OTHER_AGENT to "trouvé par un autre agent",
        MandateCancellationCode.CHANGE_OF_PLANS to "changement de projet",
        MandateCancellationCode.UNREALISTIC_CRITERIA to "critères irréalistes",
        MandateCancellationCode.UNSATISFACTORY_SERVICE to "insatisfait du service",
        MandateCancellationCode.OTHER to "autre raison",
    )

    /** How the pages name the agent's customer: a landlord for a listing, else a client; a sale's seller or buyer. */
    data class PartyWords(
        val the: String,
        val theUpper: String,
        val of: String,
        val search: String,
        val searchUpper: String,
        /** "envoyé au client". */
        val to: String,
        /** "Client": the label before the customer's name on the contact card. */
        val role: String,
    )

    private val OWNER = PartyWords("le bailleur", "Le bailleur", "du bailleur", "son bien", "Son bien", "au bailleur", "Bailleur")
    private val CLIENT = PartyWords("le client", "Le client", "du client", "sa recherche", "Sa recherche", "au client", "Client")

    // A sale's parties, as the prototype's `partyWords`: the buyer's elided "l'acheteur". The search words stay
    // neutral: the seller has a bien, the buyer a recherche.
    private val SELLER = PartyWords("le vendeur", "Le vendeur", "du vendeur", "son bien", "Son bien", "au vendeur", "Vendeur")
    private val BUYER = PartyWords("l'acheteur", "L'acheteur", "de l'acheteur", "sa recherche", "Sa recherche", "à l'acheteur", "Acheteur")

    /** An unknown status reads as the neutral "Fermé" tag: the page never shows one anyway (see [SHOWN]). */
    fun tag(status: MandateStatus): Tag = TAGS[status] ?: TAGS.getValue(MandateStatus.CLOSED)

    /** Who the customer is in words: a sale's seller or buyer, else today's landlord or client. */
    fun words(type: MandateType, listingType: ListingType? = null): PartyWords = when {
        !FindPropertyRules.isSale(listingType) -> if (type == MandateType.LISTING) OWNER else CLIENT
        type == MandateType.LISTING -> SELLER
        else -> BUYER
    }

    fun cancellation(code: MandateCancellationCode?): String =
        CANCELLATION_LABELS[code] ?: CANCELLATION_LABELS.getValue(MandateCancellationCode.OTHER)

    /** When the mandate was last sent to the customer: the contract's last request for signature. */
    fun requestedAt(contract: Contract?): Date? = contract?.signatureRequestedAt

    /** How many days the customer was given to sign: from the contract's last request to its deadline. */
    fun signatureTtlDays(contract: Contract?): Int? {
        val requestedAt = contract?.signatureRequestedAt ?: return null
        val deadline = contract.signatureDeadline ?: return null
        return Duration.ofMillis(deadline.time - requestedAt.time).plusHours(12).toDays().toInt() // Rounded
    }
}
