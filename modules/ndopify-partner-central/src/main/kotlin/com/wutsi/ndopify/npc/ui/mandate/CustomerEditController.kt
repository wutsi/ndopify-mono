package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock

/**
 * The customer edit page (`mandate/customer-edit.html`), reached from the mandate page's contact block: the agent
 * corrects the first name, last name, e-mail and WhatsApp number its customer mistyped in the public wizard. The
 * correction goes to the customer's party and customer row through its mandate (`POST /v1/mandates/{id}/customer`),
 * so it shows on all of that customer's mandate pages.
 *
 * - `GET /mandates/{id}/customer/edit` pre-fills the form; it is locked ("unavailable") when the mandate can't be read
 *   or isn't the agent's, or the customer can't be read, and ("closed") once the mandate is CLOSED or CANCELLED.
 * - `POST /mandates/{id}/customer/edit` validates the fields ([CustomerEditRules]), saves, then redirects to the
 *   mandate page with the outcome: `customer-saved`, or `customer-saved-unsent` when the e-mail of a PENDING_APPROVAL
 *   mandate changed (ndopify-server cancelled its contract and put it back to ASSIGNED). An e-mail another party owns is
 *   the e-mail field's error; a 403/404/409 is the mandate page's `changed`; a 5xx or a network failure keeps the form
 *   with a generic error.
 *
 * The status is read again on POST: the page may have gone stale while the agent typed. Everything is checked again by
 * ndopify-server, which owns the rules (access, the closed status, the e-mail's uniqueness).
 */
@Controller
@RequestMapping("/mandates/{id}/customer/edit")
class CustomerEditController(
    private val currentAgent: CurrentAgent,
    private val mandateClient: NdMandateClient,
    private val customerClient: NdCustomerClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CustomerEditController::class.java)

        const val VIEW = "mandate/customer-edit"

        const val LOCKED_UNAVAILABLE = "unavailable"
        const val LOCKED_CLOSED = "closed"

        private val CHANGED_STATUSES = setOf(403, 404, 409)

        private const val SAVE_FAILED = "Enregistrement impossible pour le moment. Réessayez dans quelques instants."
        private const val EMAIL_TAKEN = "Cette adresse email est déjà utilisée par un autre contact."
    }

    @GetMapping
    fun show(@PathVariable id: String, model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)

        val mandateId = id.toLongOrNull() ?: return locked(id, LOCKED_UNAVAILABLE, model)
        val mandate = load(mandateId) ?: return locked(id, LOCKED_UNAVAILABLE, model)
        if (mandate.status !in MandateRules.CUSTOMER_EDITABLE) {
            return locked(id, LOCKED_CLOSED, model)
        }
        val customer = customer(mandate) ?: return locked(id, LOCKED_UNAVAILABLE, model)
        return form(mandate, CustomerEditForm.of(customer), emptyMap(), null, model)
    }

    @PostMapping
    fun save(
        @PathVariable id: Long,
        @ModelAttribute form: CustomerEditForm,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)

        val mandate = load(id)
        if (mandate == null || mandate.status !in MandateRules.CUSTOMER_EDITABLE) {
            return changed(id, redirect)
        }
        // Needed for the outcome of a PENDING_APPROVAL save; unreadable, nothing is saved and the answers are kept.
        val customer = customer(mandate) ?: return form(mandate, form, emptyMap(), SAVE_FAILED, model)

        val errors = CustomerEditRules.errors(form)
        if (errors.isNotEmpty()) {
            return form(mandate, form, errors, null, model)
        }

        try {
            customerClient.update(id, CustomerEditRules.request(form))
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("Customer of mandate $id not saved: ${ex.statusCode}")
            return when {
                errorCode(ex) == ErrorCode.PARTY_EMAIL_ALREADY_EXISTS ->
                    form(mandate, form, mapOf("email" to EMAIL_TAKEN), null, model)
                ex.statusCode.value() in CHANGED_STATUSES -> changed(id, redirect)
                else -> form(mandate, form, emptyMap(), SAVE_FAILED, model)
            }
        } catch (ex: RestClientException) {
            LOGGER.error("Customer of mandate $id not saved", ex)
            return form(mandate, form, emptyMap(), SAVE_FAILED, model)
        }

        val outcome = if (mandate.status == MandateStatus.PENDING_APPROVAL && emailChanged(form, customer)) {
            MandateController.CUSTOMER_SAVED_UNSENT
        } else {
            MandateController.CUSTOMER_SAVED
        }
        redirect.addFlashAttribute(MandateController.OUTCOME, outcome)
        return "redirect:/mandates/$id"
    }

    private fun emailChanged(form: CustomerEditForm, customer: Customer): Boolean =
        !form.email.trim().equals(customer.party.email.trim(), ignoreCase = true)

    private fun form(
        mandate: Mandate,
        answers: CustomerEditForm,
        errors: Map<String, String>,
        error: String?,
        model: Model,
    ): String {
        model.addAttribute(
            "mandate",
            MandateViews.of(
                mandate,
                contract = null,
                customer = null,
                accepted = null,
                locations = emptyMap(),
                now = clock.millis(),
                formatter = TenantFormatter(tenantContext.getTenant()),
            ),
        )
        model.addAttribute("mandateId", mandate.id)
        model.addAttribute("answers", answers)
        model.addAttribute("errors", errors)
        model.addAttribute("error", error)
        return VIEW
    }

    private fun locked(id: String, reason: String, model: Model): String {
        model.addAttribute("mandateId", id)
        model.addAttribute("locked", reason)
        return VIEW
    }

    private fun changed(id: Long, redirect: RedirectAttributes): String {
        redirect.addFlashAttribute(MandateController.OUTCOME, MandateController.CHANGED)
        return "redirect:/mandates/$id"
    }

    /** The mandate, null when it can't be read or the page doesn't show its status. */
    private fun load(id: Long): Mandate? =
        try {
            mandateClient.get(id).takeIf { it.status in MandateRules.SHOWN }
        } catch (ex: RestClientException) {
            LOGGER.warn("No mandate $id: ${ex.message}")
            null
        }

    private fun customer(mandate: Mandate): Customer? =
        try {
            customerClient.get(mandate.customerId)
        } catch (ex: RestClientException) {
            LOGGER.warn("No customer ${mandate.customerId} of mandate ${mandate.id}: ${ex.message}")
            null
        }

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }
}
