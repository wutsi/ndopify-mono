package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * The life of a mandate as the agent sees it, worded as the prototype's `partner-central.js` (`STATUS_TAGS`,
 * `partyWords`, `nextStep`, `CANCELLATION_LABELS`): shared by the Espace agent's *Mes mandats* and the mandate pages.
 */
object MandateRules {
    private const val DAY = 24L * 60 * 60 * 1000

    /** Listed in *Mes mandats*. */
    val ACTIVE: Set<MandateStatus> = setOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL, MandateStatus.APPROVED)

    /** The criteria can still change: the customer hasn't signed. */
    val EDITABLE: Set<MandateStatus> = setOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL)

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

    /** How the pages name the agent's customer: a landlord for a listing, else a client. */
    data class PartyWords(
        val the: String,
        val theUpper: String,
        val of: String,
        val search: String,
        val searchUpper: String,
        /** "envoyé au client". */
        val to: String,
    )

    private val OWNER = PartyWords("le bailleur", "Le bailleur", "du bailleur", "son bien", "Son bien", "au bailleur")
    private val CLIENT = PartyWords("le client", "Le client", "du client", "sa recherche", "Sa recherche", "au client")

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

    /** An unknown status reads as the neutral "Fermé" tag: the page never shows one anyway (see [SHOWN]). */
    fun tag(status: MandateStatus): Tag = TAGS[status] ?: TAGS.getValue(MandateStatus.CLOSED)

    fun words(type: MandateType): PartyWords = if (type == MandateType.LISTING) OWNER else CLIENT

    fun cancellation(code: MandateCancellationCode?): String =
        CANCELLATION_LABELS[code] ?: CANCELLATION_LABELS.getValue(MandateCancellationCode.OTHER)

    /** "22 octobre 2026", in the tenant's time zone. */
    fun formatDate(date: Date, zone: ZoneId): String = DATE_FORMAT.format(date.toInstant().atZone(zone))

    /**
     * When the mandate was last sent to the customer: no DTO says so, but each sending resets the contract's
     * signature deadline to then + [ttlDays] (`ndopify.contract.signature.ttl-days`, which must match ndopify-server's).
     */
    fun requestedAt(contract: Contract?, ttlDays: Int): Date? =
        contract?.signatureDeadline?.let { Date(it.time - ttlDays * DAY) }

    /**
     * Edited since it was sent: the customer would sign an outdated version. Sending saves the mandate before
     * setting the deadline, so a mandate that wasn't edited since is never after [requestedAt].
     */
    fun editedSince(mandate: Mandate, requestedAt: Date?): Boolean =
        requestedAt != null && mandate.modifiedAt.after(requestedAt)

    /** The card's one-line next step; null once the mandate is no longer in progress. */
    fun nextStep(mandate: Mandate, contract: ContractSummary?, now: Long, zone: ZoneId): String? {
        val words = words(mandate.mandateType)
        return when (mandate.status) {
            MandateStatus.ASSIGNED -> "Contactez ${words.the}, puis envoyez-lui le mandat à approuver."
            MandateStatus.PENDING_APPROVAL -> {
                val deadline = contract?.signatureDeadline
                when {
                    deadline == null -> "${words.theUpper} doit signer le mandat."
                    deadline.time < now -> "Délai de signature dépassé : renvoyez le mandat."
                    else -> "${words.theUpper} a jusqu'au ${formatDate(deadline, zone)} pour signer le mandat."
                }
            }
            MandateStatus.APPROVED ->
                contract?.validUntil
                    ?.let { "Mandat signé, valable jusqu'au ${formatDate(it, zone)}." }
                    ?: "Mandat signé."
            else -> null
        }
    }
}
