package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
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

/**
 * Sending a mandate's contract to the customer (`mandate/contract.html`), on its own page rather than a form inline
 * on the mandate page: the agent confirms the terms the customer accepts by signing, the mandate's duration, a sale's
 * commission in % of the sale price, and the visit fee the visitors pay the agent for each visit (offline, Ndopify
 * collecting nothing). Reached from the mandate page's *Envoyer au client* (ASSIGNED) or *Renvoyer* (PENDING_APPROVAL).
 *
 * `GET /mandates/{id}/contract` pre-fills the form from the mandate's current contract (a cancelled one included),
 * else 6 months, no fee and the default commission; a mandate that isn't ASSIGNED / PENDING_APPROVAL redirects to its
 * page. A rental has no commission field, whatever was sent. `POST` validates
 * the answers ([MandateContractRules]), then sends them with the approval request: the mandate page's `sent` /
 * `resent` outcome, `changed` for a 403 / 404 / 409 (the mandate moved on), `email-conflict` when the customer's
 * address is another account's, `error` for a 5xx.
 *
 * The form posts without any script: the page has no page script, and the rules mirror ndopify-server's.
 */
@Controller
@RequestMapping("/mandates/{id}/contract")
class MandateContractController(
    private val currentAgent: CurrentAgent,
    private val mandateClient: NdMandateClient,
    private val contractClient: NdContractClient,
    private val customerClient: NdCustomerClient,
    private val tenantContext: TenantContext,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateContractController::class.java)

        const val VIEW = "mandate/contract"

        // The statuses whose mandate can be sent (again)
        private val SENDABLE = setOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL)

        private val CHANGED_STATUSES = setOf(403, 404, 409)
    }

    @GetMapping
    fun show(@PathVariable id: String, model: Model, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()

        val mandate = id.toLongOrNull()?.let { load(it) } ?: return "redirect:/"
        if (mandate.status !in SENDABLE) {
            return "redirect:/mandates/${mandate.id}"
        }
        val contract = contract(mandate)
        if (contract == null && mandate.contractId != null) {
            // Never send the default terms for a contract whose own couldn't be read: that would reset them
            LOGGER.warn("Contract ${mandate.contractId} of mandate ${mandate.id} not readable")
            redirect.addFlashAttribute(MandateController.OUTCOME, MandateController.FAILED)
            return back(mandate.id)
        }
        return render(agent, mandate, contract, MandateContractRules.prefill(contract), emptyMap(), model)
    }

    @PostMapping
    fun send(
        @PathVariable id: Long,
        @ModelAttribute form: MandateContractForm,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()

        val mandate = load(id)
        val sale = mandate?.listingType == ListingType.SALE
        // A contract set through the API may be outside the UI's range: keeping it is valid, choosing another isn't
        val currentDuration = form.durationMonths.trim().toIntOrNull()
            ?.takeIf { it !in MandateContractRules.MIN_MONTHS..MandateContractRules.MAX_MONTHS }
            ?.let { mandate?.let { contract(it)?.durationMonths } }
        val errors = MandateContractRules.errors(form, currentDuration, sale)
        if (errors.isNotEmpty()) {
            if (mandate == null || mandate.status !in SENDABLE) {
                redirect.addFlashAttribute(MandateController.OUTCOME, MandateController.CHANGED)
                return back(id)
            }
            return render(agent, mandate, contract(mandate), form, errors, model)
        }

        val again = mandate?.status == MandateStatus.PENDING_APPROVAL
        val currency = tenantContext.getTenant().currencyCode
        return act(id, if (again) MandateController.RESENT else MandateController.SENT, redirect) {
            mandateClient.requestApproval(id, MandateContractRules.request(form, currency, sale))
        }
    }

    private fun render(
        agent: Agent,
        mandate: Mandate,
        contract: Contract?,
        form: MandateContractForm,
        errors: Map<String, String>,
        model: Model,
    ): String {
        ProfileRules.header(agent, model)
        model.addAttribute("mandate", mandate)
        model.addAttribute("form", form)
        model.addAttribute("errors", errors)
        model.addAttribute("resend", mandate.status == MandateStatus.PENDING_APPROVAL)
        model.addAttribute("sale", mandate.listingType == ListingType.SALE)
        model.addAttribute("email", email(mandate, contract))
        model.addAttribute("signatureTtlDays", MandateRules.signatureTtlDays(contract))
        model.addAttribute("months", MandateContractRules.months(contract))
        model.addAttribute("currency", TenantFormatter(tenantContext.getTenant()).currency())
        return VIEW
    }

    /** The customer's address for the hint: the contract's mandator, else the customer's; null when unknown. */
    private fun email(mandate: Mandate, contract: Contract?): String? =
        contract?.parties
            ?.firstOrNull { party -> party.role == ContractPartyRole.MANDATOR }
            ?.legalEmail
            ?.takeIf { it.isNotBlank() }
            ?: lookup("customer ${mandate.customerId} of mandate ${mandate.id}") {
                customerClient.get(mandate.customerId)
            }?.party?.email?.takeIf { it.isNotBlank() }

    private fun contract(mandate: Mandate): Contract? {
        val id = mandate.contractId ?: return null
        return lookup("contract $id of mandate ${mandate.id}") { contractClient.get(id) }
    }

    /** The mandate, null when it can't be had. */
    private fun load(id: Long): Mandate? =
        lookup("mandate $id") { mandateClient.get(id) }

    private fun act(id: Long, outcome: String, redirect: RedirectAttributes, call: () -> Unit): String {
        val result =
            try {
                call()
                outcome
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Mandate $id not $outcome: ${ex.statusCode}")
                when {
                    errorCode(ex) == ErrorCode.CONTRACT_PARTY_MEMBERSHIP_CONFLICT -> MandateController.EMAIL_CONFLICT
                    ex.statusCode.value() in CHANGED_STATUSES -> MandateController.CHANGED
                    else -> MandateController.FAILED
                }
            } catch (ex: RestClientException) {
                LOGGER.error("Mandate $id not $outcome", ex)
                MandateController.FAILED
            }
        redirect.addFlashAttribute(MandateController.OUTCOME, result)
        return back(id)
    }

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }

    private fun back(id: Long): String = "redirect:/mandates/$id"

    private fun <T> lookup(what: String, call: () -> T?): T? =
        try {
            call()
        } catch (ex: RestClientException) {
            LOGGER.warn("No $what: ${ex.message}")
            null
        }
}
