package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock
import java.time.ZoneId

/**
 * The page of one of the agent's mandates (`mandate.html`), opened from *Mes mandats*: its status as steps, the
 * customer's contact details, its criteria, and the actions of its status. Any status but the five of
 * [MandateRules.SHOWN], or a mandate that can't be loaded, is the locked view.
 *
 * The actions (send for approval, close, cancel) are post-redirect-get, back to the page with their outcome (flash
 * attribute [OUTCOME]). A mandate that changed since the page was loaded (approved, closed or cancelled meanwhile:
 * ndopify-server's 409, or gone: 404) is [CHANGED], not an error: the page it was on was just stale. The close /
 * cancel form opens without JS through `GET /mandates/{id}?end=close|cancel`, and comes back with its errors.
 *
 * Out of scope for now (and not checked by ndopify-server either): that the mandate is the signed-in agent's.
 */
@Controller
@RequestMapping("/mandates")
class MandateController(
    private val currentAgent: CurrentAgent,
    private val mandateClient: NdMandateClient,
    private val contractClient: NdContractClient,
    private val customerClient: NdCustomerClient,
    private val assignmentClient: NdAssignmentClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
    @get:Value("\${ndopify.contract.signature.ttl-days}") private val ttlDays: Int,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateController::class.java)

        const val VIEW = "mandate"
        const val SIGN_OUT = "redirect:/logout"

        // GET /mandates/{id}?end=…: the close / cancel form, open without JS.
        const val END_CLOSE = "close"
        const val END_CANCEL = "cancel"

        const val OUTCOME = "outcome"
        const val SENT = "sent"
        const val RESENT = "resent"
        const val CLOSED = "closed"
        const val CANCELLED = "cancelled"
        const val SAVED = "saved"
        const val CHANGED = "changed"
        const val FAILED = "error"

        private val CHANGED_STATUSES = setOf(404, 409)

        // Not a reason the agent can choose: UNKNOWN is no answer, EXPIRED is the server's own.
        private val CANCELLATION_CODES = MandateRules.CANCELLATION_LABELS.keys
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    /** [end] is a String so that a hand-edited value is ignored instead of failing the page; same for [id]. */
    @GetMapping("/{id}")
    fun show(@PathVariable id: String, @RequestParam(required = false) end: String?, model: Model): String {
        val agent = currentAgent.get() ?: return SIGN_OUT
        ProfileRules.header(agent, model)

        val mandate = id.toLongOrNull()?.let { load(it) } ?: return locked(model)
        return render(mandate, end, MandateEndForm(), emptyMap(), model)
    }

    @PostMapping("/{id}/request-approval")
    fun requestApproval(@PathVariable id: Long, redirect: RedirectAttributes): String {
        currentAgent.get() ?: return SIGN_OUT

        // Sending again resets the deadline: worded "renvoyé". Without the mandate, it's still worth sending.
        val again = lookup("mandate $id") { mandateClient.get(id) }?.status == MandateStatus.PENDING_APPROVAL
        return act(id, if (again) RESENT else SENT, redirect) { mandateClient.requestApproval(id) }
    }

    @PostMapping("/{id}/close")
    fun close(
        @PathVariable id: Long,
        @ModelAttribute form: MandateEndForm,
        binding: BindingResult,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: return SIGN_OUT
        val mandate = current(id, redirect) { it.status == MandateStatus.APPROVED } ?: return back(id)

        val amount = FindPropertyRules.parseAmount(form.finalPrice)
        if (amount == null || amount < 1) {
            ProfileRules.header(agent, model)
            val errors = mapOf("finalPrice" to "Indiquez le loyer convenu, en FCFA par mois.")
            return render(mandate, END_CLOSE, form, errors, model)
        }
        val price = Money(amount, mandate.price.currency, PeriodicityType.MONTHLY)
        return act(id, CLOSED, redirect) { mandateClient.close(id, CloseMandateRequest(price)) }
    }

    /** An unknown code (hand-edited form) fails the binding: it reads as no code, [binding] keeps it from a 400. */
    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
        @ModelAttribute form: MandateEndForm,
        binding: BindingResult,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: return SIGN_OUT
        val mandate = current(id, redirect) { it.status in MandateRules.ACTIVE } ?: return back(id)

        val code = form.cancellationCode?.takeIf { it in CANCELLATION_CODES && !binding.hasFieldErrors("cancellationCode") }
        val reason = form.cancellationReason?.trim()?.ifEmpty { null }
        val errors = listOfNotNull(
            if (code == null) "cancellationCode" to "Choisissez la raison de l'annulation." else null,
            FindPropertyRules.comments(reason)?.let { "cancellationReason" to it },
        ).toMap()
        if (errors.isNotEmpty()) {
            ProfileRules.header(agent, model)
            return render(mandate, END_CANCEL, form.copy(cancellationCode = code), errors, model)
        }
        return act(id, CANCELLED, redirect) { mandateClient.cancel(id, CancelMandateRequest(code, reason)) }
    }

    private fun render(
        mandate: Mandate,
        end: String?,
        form: MandateEndForm,
        errors: Map<String, String>,
        model: Model,
    ): String {
        val view = view(mandate)
        model.addAttribute("mandate", view)
        model.addAttribute(
            "end",
            end?.takeIf { (it == END_CLOSE && view.closable) || (it == END_CANCEL && view.cancellable) },
        )
        model.addAttribute("form", form)
        model.addAttribute("errors", errors)
        (model.getAttribute(OUTCOME) as? String)?.let { outcome -> outcome(outcome, view, model) }
        return VIEW
    }

    /** The flash outcome of the last action, worded after the mandate as it is now. */
    private fun outcome(outcome: String, view: MandateView, model: Model) {
        val to = view.contact?.let { "à ${it.email}" } ?: view.words.to
        val text = when (outcome) {
            SENT -> "Mandat envoyé $to pour signature."
            RESENT -> "Mandat renvoyé $to pour signature."
            CLOSED -> "Mandat fermé : félicitations pour ce bail !"
            CANCELLED -> "Mandat annulé. Il ne figure plus dans vos mandats en cours."
            SAVED ->
                if (view.status == MandateStatus.PENDING_APPROVAL) {
                    "Mandat modifié. Renvoyez-le pour que ${view.words.the} signe la nouvelle version."
                } else {
                    "Mandat modifié."
                }
            CHANGED -> "Ce mandat a changé entre-temps : vérifiez son état avant de recommencer."
            FAILED -> "Action impossible pour le moment. Réessayez dans quelques instants."
            else -> return
        }
        model.addAttribute("outcomeText", text)
        model.addAttribute(
            "outcomeStyle",
            when (outcome) {
                CHANGED -> "warning"
                FAILED -> "error"
                else -> "success"
            },
        )
    }

    /**
     * The mandate, if the action still applies to it; else null, with the outcome to show: [CHANGED] when it moved
     * on or is gone, [FAILED] when it can't be loaded.
     */
    private fun current(id: Long, redirect: RedirectAttributes, applies: (Mandate) -> Boolean): Mandate? {
        val outcome =
            try {
                val mandate = mandateClient.get(id)
                if (applies(mandate)) return mandate
                CHANGED
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("No mandate $id: ${ex.statusCode}")
                if (ex.statusCode.value() in CHANGED_STATUSES) CHANGED else FAILED
            } catch (ex: RestClientException) {
                LOGGER.error("No mandate $id", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, outcome)
        return null
    }

    private fun act(id: Long, outcome: String, redirect: RedirectAttributes, call: () -> Unit): String {
        val result =
            try {
                call()
                outcome
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Mandate $id not $outcome: ${ex.statusCode}")
                if (ex.statusCode.value() in CHANGED_STATUSES) CHANGED else FAILED
            } catch (ex: RestClientException) {
                LOGGER.error("Mandate $id not $outcome", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, result)
        return back(id)
    }

    private fun back(id: Long): String = "redirect:/mandates/$id"

    private fun locked(model: Model): String {
        model.addAttribute("locked", true)
        return VIEW
    }

    /** The mandate, null when it can't be had or the page doesn't show its status. */
    private fun load(id: Long): Mandate? =
        try {
            mandateClient.get(id).takeIf { it.status in MandateRules.SHOWN }
        } catch (ex: RestClientException) {
            LOGGER.warn("No mandate $id: ${ex.message}")
            null
        }

    /** Each secondary lookup that fails leaves its lines out: only the mandate itself is required. */
    private fun view(mandate: Mandate): MandateView =
        MandateViews.of(
            mandate,
            contract(mandate),
            customer(mandate),
            accepted(mandate),
            locations(mandate),
            clock.millis(),
            ZoneId.of(tenantContext.getTenant().timeZone),
            ttlDays,
        )

    private fun contract(mandate: Mandate): Contract? {
        val id = mandate.contractId ?: return null
        return lookup("contract $id of mandate ${mandate.id}") { contractClient.get(id) }
    }

    private fun customer(mandate: Mandate): Customer? =
        lookup("customer ${mandate.customerId} of mandate ${mandate.id}") { customerClient.get(mandate.customerId) }

    private fun accepted(mandate: Mandate): AssignmentSummary? =
        lookup("accepted offer of mandate ${mandate.id}") { assignmentClient.accepted(mandate.id) }

    private fun locations(mandate: Mandate): Map<Long, Location> =
        lookup("location names of mandate ${mandate.id}") {
            sector.locations(listOf(mandate.cityId) + mandate.neighborhoodIds)
        } ?: emptyMap()

    private fun <T> lookup(what: String, call: () -> T?): T? =
        try {
            call()
        } catch (ex: RestClientException) {
            LOGGER.warn("No $what: ${ex.message}")
            null
        }
}
