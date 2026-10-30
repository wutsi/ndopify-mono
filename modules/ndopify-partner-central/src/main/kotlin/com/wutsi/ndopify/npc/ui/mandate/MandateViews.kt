package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.util.Date

/** Builds the mandate page's [MandateView], as the prototype's `initMandate` (partner-central.js) renders it. */
object MandateViews {
    private val STEPS = listOf(
        MandateStatus.ASSIGNED,
        MandateStatus.PENDING_APPROVAL,
        MandateStatus.APPROVED,
        MandateStatus.CLOSED,
    )

    private val ENDED = setOf(MandateStatus.CLOSED, MandateStatus.CANCELLED)

    // Shown with the type ("Maison, 3 chambres") or folded.
    private const val TYPE = "Type de propriété"
    private const val ROOMS = "Chambres"
    private const val COMMENTS = "Autres précisions"
    private const val KEY_FACTS = 3

    private val CAMEROON_PHONE = Regex("""^\+237(\d)(\d{2})(\d{2})(\d{2})(\d{2})$""")

    /**
     * [contract], [customer] and [accepted] (the mandate's accepted offer) are null when the mandate has none or they
     * can't be loaded: the lines that need them are left out.
     */
    fun of(
        mandate: Mandate,
        contract: Contract?,
        customer: Customer?,
        accepted: AssignmentSummary?,
        locations: Map<Long, Location>,
        now: Long,
        formatter: TenantFormatter,
    ): MandateView {
        fun format(date: Date?): String? = date?.let { formatter.date(it) }

        val status = mandate.status
        val ended = status in ENDED
        val requestedAt = MandateRules.requestedAt(contract)
        val deadline = contract?.signatureDeadline
        val current = if (ended) STEPS.size - 1 else STEPS.indexOf(status).coerceAtLeast(0)
        val dates = listOf(accepted?.modifiedAt, requestedAt, mandate.approvedAt, mandate.closedAt)
        val steps = STEPS.mapIndexed { i, step ->
            StepView(
                status = step.name,
                label = if (step == MandateStatus.CLOSED && status == MandateStatus.CANCELLED) {
                    MandateRules.tag(MandateStatus.CANCELLED).label
                } else {
                    MandateRules.tag(step).label
                },
                date = if (i <= current) format(dates[i]) else null,
                // A mandate ended without a lease may have skipped steps (ex: cancelled before its approval).
                done = if (ended) i == current || dates[i] != null else i < current,
            )
        }
        val (key, more) = criteria(mandate, locations, formatter)

        return MandateView(
            id = mandate.id,
            status = status,
            title = MandateLabels.title(mandate),
            tag = MandateRules.tag(status),
            words = MandateRules.words(mandate.mandateType),
            owner = mandate.mandateType == MandateType.LISTING,
            acceptedAt = format(accepted?.modifiedAt),
            steps = steps,
            currentStep = current,
            stepSummary = "Étape ${current + 1} sur ${STEPS.size} · " + MandateRules.tag(status).label +
                (steps[current].date?.let { ", le $it" } ?: ""),
            requestedAt = format(requestedAt),
            signatureDeadline = format(deadline),
            deadlinePassed = deadline != null && deadline.time < now,
            approvedAt = format(mandate.approvedAt),
            validUntil = format(contract?.validUntil),
            closedAt = format(mandate.closedAt),
            finalPrice = mandate.finalPrice?.let { formatter.money(it, mandate.price.currency) + " / mois" },
            age = mandate.age?.let { if (it > 1) "$it jours" else "$it jour" },
            cancellation = if (status == MandateStatus.CANCELLED) MandateRules.cancellation(mandate.cancellationCode) else null,
            cancellationReason = mandate.cancellationReason?.trim()?.ifEmpty { null },
            contact = customer?.let { contact(mandate, it) },
            keyCriteria = key,
            moreCriteria = more,
            editable = status in MandateRules.EDITABLE,
            closable = status == MandateStatus.APPROVED,
            cancellable = status in MandateRules.ACTIVE,
            signatureTtlDays = MandateRules.signatureTtlDays(contract),
        )
    }

    /** Visible: sector, amount, date, the type with its rooms, then the comments. Folded: everything else. */
    private fun criteria(
        mandate: Mandate,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
    ): Pair<List<Pair<String, String>>, List<Pair<String, String>>> {
        val rows = MandateLabels.criteria(mandate, locations, formatter)
        val byTerm = rows.toMap()
        val rooms = byTerm[ROOMS]?.let { if (it == "1") "1 chambre" else "$it chambres" }
        val key = rows.take(KEY_FACTS) +
            listOf("Type de bien" to listOfNotNull(byTerm[TYPE], rooms).joinToString(", ")) +
            listOfNotNull(byTerm[COMMENTS]?.let { COMMENTS to it })
        val more = rows.drop(KEY_FACTS).filter { (term, _) -> term !in setOf(TYPE, ROOMS, COMMENTS) }
        return key to more
    }

    private fun contact(mandate: Mandate, customer: Customer): ContactView {
        val whatsapp = customer.whatsappNumber?.ifBlank { null }
        return ContactView(
            name = listOf(customer.party.firstName, customer.party.lastName).filter { it.isNotBlank() }.joinToString(" "),
            email = customer.party.email,
            whatsapp = whatsapp,
            whatsappDisplay = whatsapp?.let { displayPhone(it) },
            whatsappDigits = whatsapp?.filter { it.isDigit() },
            period = MandateLabels.CONTACT_PERIOD_LABELS[mandate.preferredContactPeriod],
        )
    }

    /** "+237699123456" → "+237 6 99 12 34 56" (Cameroon numbers; others are shown as they are). */
    private fun displayPhone(e164: String): String =
        CAMEROON_PHONE.matchEntire(e164)?.groupValues?.drop(1)?.joinToString(" ", prefix = "+237 ") ?: e164
}
