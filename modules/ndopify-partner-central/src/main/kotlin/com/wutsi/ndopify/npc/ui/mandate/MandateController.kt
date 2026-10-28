package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.listing.ListingViews
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.slf4j.LoggerFactory
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

/**
 * The page of one of the agent's mandates (`mandate.html`), opened from *Mes mandats*: its status as steps, the
 * customer's contact details, its criteria, and the actions of its status. Any status but the five of
 * [MandateRules.SHOWN], or a mandate that can't be loaded, is the locked view.
 *
 * The actions (send for approval, close, cancel) are post-redirect-get, back to the page with their outcome (flash
 * attribute [OUTCOME]). A mandate that changed since the page was loaded (approved, closed or cancelled meanwhile:
 * ndopify-server's 409, gone: 404, or no longer the agent's: 403) is [CHANGED], not an error: the page it was on was just stale. The close /
 * cancel form opens without JS through `GET /mandates/{id}?end=close|cancel`, and comes back with its errors.
 *
 * The edit page (`mandate/edit.html`) is only for a mandate the customer hasn't signed ([MandateRules.EDITABLE]),
 * checked again when it's saved. ndopify-server refuses it too once signed or closed (409, shown as [CHANGED]), and
 * cancels the contract of one out for signature: the customer can't sign the old version, the agent sends it again.
 *
 * A landlord's signed mandate also shows its listing ([ListingViews.forMandate]): "Créer l'annonce", posted to
 * `ListingController`, then where the listing stands.
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
    private val listingClient: NdListingClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateController::class.java)

        const val VIEW = "mandate"
        const val VIEW_EDIT = "mandate/edit"

        // GET /mandates/{id}?end=…: the close / cancel form, open without JS.
        const val END_CLOSE = "close"
        const val END_CANCEL = "cancel"

        const val OUTCOME = "outcome"
        const val SENT = "sent"
        const val RESENT = "resent"
        const val CLOSED = "closed"
        const val CANCELLED = "cancelled"
        const val SAVED = "saved"

        // Saved while out for signature: ndopify-server cancelled its contract, the mandate is ASSIGNED again
        const val SAVED_UNSENT = "saved-unsent"
        const val CHANGED = "changed"
        const val FAILED = "error"

        // Request-approval: the customer's e-mail is another member's (ndopify-server's
        // CONTRACT_PARTY_MEMBERSHIP_CONFLICT), it couldn't sign. Not a stale page: the agent must correct it.
        const val EMAIL_CONFLICT = "email-conflict"

        // 403: no longer the agent's (ndopify-server's MANDATE_ACCESS_DENIED), retrying won't help
        private val CHANGED_STATUSES = setOf(403, 404, 409)

        // The edit page's locked view: not found (or a status the pages don't show), or signed by the customer.
        const val LOCKED_UNAVAILABLE = "unavailable"
        const val LOCKED_APPROVED = "approved"

        private const val SAVE_FAILED = "Enregistrement impossible pour le moment. Réessayez dans quelques instants."
        private const val SAVE_CONFLICT =
            "Ce mandat a été modifié entre-temps. Vos réponses sont conservées : vérifiez-les, puis enregistrez à nouveau."

        // Not a reason the agent can choose: UNKNOWN is no answer, EXPIRED is the server's own.
        private val CANCELLATION_CODES = MandateRules.CANCELLATION_LABELS.keys
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    /** [end] is a String so that a hand-edited value is ignored instead of failing the page; same for [id]. */
    @GetMapping("/{id}")
    fun show(@PathVariable id: String, @RequestParam(required = false) end: String?, model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)

        val mandate = id.toLongOrNull()?.let { load(it) } ?: return locked(model)
        return render(mandate, end, MandateEndForm(), emptyMap(), model)
    }

    @PostMapping("/{id}/request-approval")
    fun requestApproval(@PathVariable id: Long, redirect: RedirectAttributes): String {
        currentAgent.get() ?: throw SignInRequiredException()

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
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val mandate = current(id, redirect) { it.status == MandateStatus.APPROVED } ?: return back(id)

        val amount = finalPrice(form.finalPrice)
        if (amount == null || amount < 1) {
            ProfileRules.header(agent, model)
            val errors = mapOf("finalPrice" to "Indiquez le loyer convenu, en ${formatter().currency()} par mois.")
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
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val mandate = current(id, redirect) { it.status in MandateRules.ACTIVE } ?: return back(id)

        val code =
            form.cancellationCode?.takeIf { it in CANCELLATION_CODES && !binding.hasFieldErrors("cancellationCode") }
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

    @GetMapping("/{id}/edit")
    fun edit(@PathVariable id: String, model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)

        val mandate = id.toLongOrNull()?.let { load(it) }
        return when {
            mandate == null -> lockedEdit(id, LOCKED_UNAVAILABLE, model)
            mandate.status !in MandateRules.EDITABLE -> lockedEdit(id, LOCKED_APPROVED, model)
            else -> edit(mandate, MandateEditForm.of(mandate), emptyMap(), null, model)
        }
    }

    /** Unknown enum values (hand-edited form) fail the binding: they read as no answer, [binding] keeps it from a 400. */
    @PostMapping("/{id}/edit")
    fun save(
        @PathVariable id: Long,
        @ModelAttribute form: MandateEditForm,
        binding: BindingResult,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        // Signed since the form was shown (ex: in another tab): ndopify-server would update it all the same.
        val mandate = current(id, redirect) { it.status in MandateRules.EDITABLE } ?: return back(id)

        val answers = form.copy(neighborhoodIds = form.neighborhoodIds.distinct())
        val owner = mandate.mandateType == MandateType.LISTING
        ProfileRules.header(agent, model)
        try {
            val errors = MandateEditRules.errors(answers, owner, formatter()).ifEmpty { neighborhoodErrors(mandate, answers) }
            if (errors.isNotEmpty()) return edit(mandate, answers, errors, null, model)

            mandateClient.update(id, MandateEditRules.request(answers, mandate))
        } catch (ex: HttpClientErrorException) {
            if (errorCode(ex) in setOf(ErrorCode.MANDATE_ALREADY_APPROVED, ErrorCode.MANDATE_ALREADY_CLOSED)) {
                LOGGER.warn("Mandate $id not saved: ${ex.statusCode}")
                redirect.addFlashAttribute(OUTCOME, CHANGED)
                return back(id)
            }
            if (errorCode(ex) != ErrorCode.MANDATE_VERSION_CONFLICT) {
                LOGGER.error("Mandate $id not saved: ${ex.message}", ex)
                return edit(mandate, answers, emptyMap(), SAVE_FAILED, model)
            }
            // Changed since the form was shown: saving again, now based on the version just loaded, is the agent's call
            LOGGER.warn("Mandate $id not saved: changed since version ${answers.version}")
            return edit(mandate, answers.copy(version = mandate.version), emptyMap(), SAVE_CONFLICT, model)
        } catch (ex: RestClientException) {
            LOGGER.error("Mandate $id not saved: ${ex.message}", ex)
            return edit(mandate, answers, emptyMap(), SAVE_FAILED, model)
        }
        redirect.addFlashAttribute(OUTCOME, if (mandate.status == MandateStatus.PENDING_APPROVAL) SAVED_UNSENT else SAVED)
        return back(id)
    }

    /** The city isn't editable: only the neighbourhoods are checked against it. */
    private fun neighborhoodErrors(mandate: Mandate, form: MandateEditForm): Map<String, String> =
        sector.errors(mandate.cityId, form.neighborhoodIds).filterKeys { it == "neighborhoodIds" }

    private fun lockedEdit(id: String, reason: String, model: Model): String {
        model.addAttribute("mandateId", id)
        model.addAttribute("locked", reason)
        return VIEW_EDIT
    }

    private fun edit(
        mandate: Mandate,
        answers: MandateEditForm,
        errors: Map<String, String>,
        error: String?,
        model: Model,
    ): String {
        // Names of the city and the chosen neighbourhoods (the select's initial options). Capped: a form shown again
        // with its errors may carry whatever ids were posted.
        val neighborhoodIds = answers.neighborhoodIds.take(FindPropertyRules.MAX_NEIGHBORHOODS)
        val names = lookup("location names of mandate ${mandate.id}") {
            sector.locations(listOf(mandate.cityId) + neighborhoodIds)
        } ?: emptyMap()
        val neighborhoods = neighborhoodIds.mapNotNull { names[it] }

        // The title, number and wording only: no need for the contract, customer and offer.
        model.addAttribute(
            "mandate",
            MandateViews.of(
                mandate,
                null,
                null,
                null,
                names,
                clock.millis(),
                formatter(),
            ),
        )
        model.addAttribute("owner", mandate.mandateType == MandateType.LISTING)
        model.addAttribute("mandateId", mandate.id.toString())
        model.addAttribute("city", names[mandate.cityId])
        model.addAttribute("cityId", mandate.cityId)
        model.addAttribute("neighborhoods", neighborhoods)
        model.addAttribute("answers", answers)
        model.addAttribute("errors", errors)
        model.addAttribute("error", error)
        model.addAttribute("currency", formatter().currency())
        model.addAttribute("summaries", MandateEditRules.summaries(answers, neighborhoods.map { it.name }, formatter()))
        model.addAttribute(
            "openGroups",
            MandateEditRules.GROUPS.filterValues { fields -> fields.any { it in errors } }.keys,
        )
        return VIEW_EDIT
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
        model.addAttribute("currency", formatter().currency())
        model.addAttribute("mandateListing", ListingViews.forMandate(mandate, listing(mandate), formatter()))
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
            SAVED -> "Mandat modifié."
            SAVED_UNSENT ->
                "Mandat modifié. L'ancienne version ne peut plus être signée : envoyez-le à nouveau pour que " +
                    "${view.words.the} signe la nouvelle."

            CHANGED -> "Ce mandat a changé entre-temps : vérifiez son état avant de recommencer."
            EMAIL_CONFLICT ->
                "L'adresse e-mail ${view.words.of} est déjà utilisée par un autre compte : " +
                    "corrigez-la avant de demander sa signature."
            FAILED -> "Action impossible pour le moment. Réessayez dans quelques instants."
            else -> return
        }
        model.addAttribute("outcomeText", text)
        model.addAttribute(
            "outcomeStyle",
            when (outcome) {
                CHANGED -> "warning"
                FAILED, EMAIL_CONFLICT -> "error"
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
                when {
                    errorCode(ex) == ErrorCode.CONTRACT_PARTY_MEMBERSHIP_CONFLICT -> EMAIL_CONFLICT
                    ex.statusCode.value() in CHANGED_STATUSES -> CHANGED
                    else -> FAILED
                }
            } catch (ex: RestClientException) {
                LOGGER.error("Mandate $id not $outcome", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, result)
        return back(id)
    }

    private fun back(id: Long): String = "redirect:/mandates/$id"

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }

    /**
     * "150 000" or "150.000" → 150000; anything but digits and separators ("150k") → null: closing is final, so a
     * typo is refused rather than read as another amount (the prototype's check).
     */
    private fun finalPrice(value: String?): Long? =
        value.orEmpty()
            .filterNot { it.isWhitespace() || it == '.' || it == '\u00a0' || it == '\u202f' }
            .takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }
            ?.let { FindPropertyRules.parseAmount(it) }

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
            formatter(),
        )

    private fun formatter(): TenantFormatter = TenantFormatter(tenantContext.getTenant())

    private fun contract(mandate: Mandate): Contract? {
        val id = mandate.contractId ?: return null
        return lookup("contract $id of mandate ${mandate.id}") { contractClient.get(id) }
    }

    /** A landlord's signed mandate only: its listing, null before it's created or when it can't be loaded. */
    private fun listing(mandate: Mandate): Listing? {
        if (mandate.mandateType != MandateType.LISTING || mandate.status != MandateStatus.APPROVED) return null
        val id = mandate.listingId ?: return null
        return lookup("listing $id of mandate ${mandate.id}") { listingClient.get(id) }
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
