package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import jakarta.servlet.http.HttpSession
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
 * The visit page (`templates/visit.html`): one of the agent's visits, its customer and listing, and the action of each
 * status. The listing and the customer are secondary reads: a failure leaves their lines out, never fails the page.
 * Only the visit itself is required; a 403 (another agent's visit), a 404 or an unreadable one is the locked page.
 *
 * Every action is a form posting here, then back to the page with the outcome ([OUTCOME] flash attribute): the page
 * works without JavaScript. The visit is read first and its status checked before the call, so a double post (or a page
 * left open) is [CHANGED] without a second call or e-mail; so are ndopify-server's 403, 404 and 409. The agent never
 * receives the code; after `VISIT_CODE_LOCKED`, the session remembers it for this agent ([SESSION_CODE_LOCKED]) so the
 * check-in form stays hidden.
 */
@Controller
@RequestMapping("/visits")
class VisitController(
    private val currentAgent: CurrentAgent,
    private val visitClient: NdVisitClient,
    private val listingClient: NdListingClient,
    private val customerClient: NdCustomerClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VisitController::class.java)

        const val VIEW = "visit"

        const val LOCKED_NOT_YOURS = "not-yours"
        const val LOCKED_UNAVAILABLE = "unavailable"

        const val OUTCOME = "visitOutcome"
        const val FLASH_TEXT = "flashText"
        const val FLASH_STYLE = "flashStyle"
        const val CHECK_IN_LOCKED = "checkInLocked"
        const val SESSION_CODE_LOCKED = "npc.visit.code-locked."

        const val CONFIRMED = "confirmed"
        const val REFUSED = "refused"
        const val CANCELLED = "cancelled"
        const val CHECKED_IN = "checked-in"
        const val NO_SHOW = "no-show"
        const val COMPLETED = "completed"
        const val INVALID_SLOT = "invalid-slot"
        const val CODE_MISSING = "code-missing"
        const val CODE_INVALID = "code-invalid"
        const val CODE_LOCKED = "code-locked"
        const val COMMENT_MISSING = "comment-missing"
        const val COMMENT_TOO_LONG = "comment-too-long"
        const val REASON_MISSING = "reason-missing"
        const val TOO_EARLY = "too-early"
        const val WHATSAPP_MISSING = "whatsapp-missing"
        const val CHANGED = "changed"
        const val FAILED = "error"

        // 403: no longer the agent's, 404: gone, 409: its status moved on. The page was just stale.
        private val CHANGED_STATUSES = setOf(403, 404, 409)
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    @GetMapping("/{id}")
    fun show(@PathVariable id: String, model: Model, session: HttpSession): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)

        val visitId = id.toLongOrNull() ?: return locked(LOCKED_UNAVAILABLE, model)
        val visit =
            try {
                visitClient.get(visitId)
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Visit $visitId not shown: ${ex.statusCode}")
                return locked(if (ex.statusCode.value() == 403) LOCKED_NOT_YOURS else LOCKED_UNAVAILABLE, model)
            } catch (ex: RestClientException) {
                LOGGER.error("Visit $visitId not shown: ${ex.message}", ex)
                return locked(LOCKED_UNAVAILABLE, model)
            }
        if (visit.agentId != agent.id) {
            LOGGER.warn("Visit $visitId not shown: it belongs to agent ${visit.agentId}")
            return locked(LOCKED_NOT_YOURS, model)
        }

        val listing = lookup("listing ${visit.listingId} of visit $visitId") { listingClient.get(visit.listingId) }
        val customer = lookup("customer ${visit.customerId} of visit $visitId") { customerClient.get(visit.customerId) }
        val locations = lookup("location names of visit $visitId") {
            sector.locations(listOfNotNull(listing?.cityId, listing?.neighborhoodId))
        } ?: emptyMap()
        val tenant = tenantContext.getTenant()
        val formatter = TenantFormatter(tenant)

        model.addAttribute(
            "page",
            VisitViews.of(visit, customer, listing, locations, clock.millis(), tenant, formatter),
        )
        model.addAttribute(CHECK_IN_LOCKED, session.getAttribute(SESSION_CODE_LOCKED + visitId) == true)
        (model.getAttribute(OUTCOME) as? String)?.let { flash(it, visit, tenant, formatter, model) }
        model.addAttribute("currency", formatter.currency())
        return VIEW
    }

    @PostMapping("/{id}/schedule")
    fun schedule(@PathVariable id: String, @ModelAttribute form: VisitScheduleForm, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val visitId = id.toLongOrNull() ?: return "redirect:/"
        // The home page's inline confirmation comes back to the queue, not to the visit page.
        val returnUrl = if (form.from == "home") "redirect:/#visits-title" else null
        val visit = current(agent, visitId, redirect) { it.status == VisitStatus.REQUESTED }
            ?: return returnUrl ?: back(visitId)

        val request = form.toRequest(tenantContext.getTenant(), visit.type)
            ?: return flashed(visitId, INVALID_SLOT, redirect, returnUrl)
        return perform(
            visitId,
            redirect,
            CONFIRMED,
            returnUrl = returnUrl,
            map = { ex ->
                when (errorCode(ex)) {
                    "VISIT_INVALID_SLOT" -> INVALID_SLOT
                    "VISIT_WHATSAPP_MISSING" -> WHATSAPP_MISSING
                    else -> changed(ex)
                }
            },
        ) { visitClient.schedule(visitId, request) }
    }

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: String, @ModelAttribute form: VisitCancelForm, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val visitId = id.toLongOrNull() ?: return "redirect:/"
        val visit = current(agent, visitId, redirect) { VisitRules.canCancel(it) } ?: return back(visitId)

        val outcome = if (visit.status == VisitStatus.REQUESTED) REFUSED else CANCELLED
        return perform(visitId, redirect, outcome) { visitClient.cancel(visitId, form.toRequest()) }
    }

    @PostMapping("/{id}/check-in")
    fun checkIn(
        @PathVariable id: String,
        @ModelAttribute form: VisitCheckInForm,
        redirect: RedirectAttributes,
        session: HttpSession,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val visitId = id.toLongOrNull() ?: return "redirect:/"
        val now = clock.millis()
        current(agent, visitId, redirect) { VisitRules.canCheckIn(it, now) } ?: return back(visitId)

        if (session.getAttribute(SESSION_CODE_LOCKED + visitId) == true) {
            return flashed(visitId, CODE_LOCKED, redirect)
        }
        val request = form.toRequest() ?: return flashed(visitId, CODE_MISSING, redirect)
        return perform(
            visitId,
            redirect,
            CHECKED_IN,
            map = { ex ->
                when (errorCode(ex)) {
                    "VISIT_CODE_INVALID" -> CODE_INVALID
                    "VISIT_CODE_LOCKED" -> {
                        session.setAttribute(SESSION_CODE_LOCKED + visitId, true)
                        CODE_LOCKED
                    }

                    "VISIT_OUTSIDE_WINDOW", "VISIT_NOT_CONFIRMED" -> CHANGED
                    else -> changed(ex)
                }
            },
        ) { visitClient.checkIn(visitId, request) }
    }

    @PostMapping("/{id}/no-show")
    fun noShow(@PathVariable id: String, @ModelAttribute form: VisitNoShowForm, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val visitId = id.toLongOrNull() ?: return "redirect:/"
        val now = clock.millis()
        current(agent, visitId, redirect) { VisitRules.canNoShow(it, now) } ?: return back(visitId)

        val request = form.toRequest() ?: return flashed(visitId, REASON_MISSING, redirect)
        return perform(visitId, redirect, NO_SHOW) { visitClient.noShow(visitId, request) }
    }

    @PostMapping("/{id}/check-out")
    fun checkOut(@PathVariable id: String, @ModelAttribute form: VisitCheckOutForm, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val visitId = id.toLongOrNull() ?: return "redirect:/"
        val now = clock.millis()
        val visit = current(agent, visitId, redirect) { it.status == VisitStatus.CHECKED_IN } ?: return back(visitId)

        val comment = form.comment?.trim().orEmpty()
        if (comment.isEmpty()) return flashed(visitId, COMMENT_MISSING, redirect)
        if (comment.length > 2000) return flashed(visitId, COMMENT_TOO_LONG, redirect)
        if (!VisitRules.checkOutReady(visit, now)) return flashed(visitId, TOO_EARLY, redirect)

        val request = form.toRequest() ?: return flashed(visitId, FAILED, redirect)
        return perform(
            visitId,
            redirect,
            COMPLETED,
            map = { ex ->
                when (errorCode(ex)) {
                    "VISIT_OUTSIDE_WINDOW" -> TOO_EARLY
                    "VISIT_NOT_CHECKED_IN" -> CHANGED
                    else -> changed(ex)
                }
            },
        ) { visitClient.checkOut(visitId, request) }
    }

    /** The outcome's wording after the visit as it is now. */
    private fun flash(outcome: String, visit: Visit, tenant: Tenant, formatter: TenantFormatter, model: Model) {
        val at = visit.scheduledAt?.let { VisitSlots.dateTimeLabel(it, tenant, formatter) }.orEmpty()
        val text = when (outcome) {
            CONFIRMED ->
                "Visite confirmée pour le $at : le client a reçu par e-mail la date, l'adresse et son code." +
                    if (visit.type == VisitType.VIDEO) {
                        " À l'heure prévue, appelez le client en vidéo sur WhatsApp depuis le bien."
                    } else {
                        ""
                    }

            REFUSED -> "Demande refusée : le client est prévenu par e-mail."
            CANCELLED -> "Visite annulée : le client est prévenu par e-mail."
            CHECKED_IN -> "Visite démarrée à ${visit.checkedInAt?.let { formatter.time(it) }.orEmpty()}. " +
                "Terminez-la ici une fois la visite finie."

            NO_SHOW -> "Absence du client enregistrée : il est prévenu par e-mail."
            COMPLETED -> "Visite terminée. Le client reçoit un e-mail pour dire si le bien l'intéresse."
            INVALID_SLOT -> "Ce créneau n'est plus disponible. Choisissez-en un autre."
            CODE_MISSING -> "Saisissez les 4 chiffres du code du client."
            CODE_INVALID -> "Code incorrect. Vérifiez le code du client."
            CODE_LOCKED -> "Trop d'essais : contactez le support ou déclarez le client absent."
            COMMENT_MISSING -> "Écrivez votre compte rendu de la visite."
            COMMENT_TOO_LONG -> "Votre compte rendu dépasse 2 000 caractères."
            REASON_MISSING -> "Choisissez ce qui s'est passé."
            TOO_EARLY -> "Vous pourrez terminer la visite 10 minutes après son démarrage."
            WHATSAPP_MISSING -> "Le client n'a pas de numéro WhatsApp : choisissez une visite sur place."
            CHANGED -> "Cette visite a changé entre-temps : vérifiez son état avant de recommencer."
            FAILED -> "Action impossible pour le moment. Réessayez dans quelques instants."
            else -> return
        }
        model.addAttribute(FLASH_TEXT, text)
        model.addAttribute(FLASH_STYLE, style(outcome))
        if (outcome == CODE_LOCKED) {
            model.addAttribute(CHECK_IN_LOCKED, true)
        }
    }

    private fun style(outcome: String): String =
        when (outcome) {
            INVALID_SLOT, CODE_INVALID, CODE_LOCKED, TOO_EARLY, WHATSAPP_MISSING, CHANGED -> "warning"
            CODE_MISSING, COMMENT_MISSING, COMMENT_TOO_LONG, REASON_MISSING, FAILED -> "error"
            else -> "success"
        }

    /**
     * The visit, if [applies] to it and it is the agent's; else null, with the outcome already flashed: [CHANGED] when
     * it moved on, is gone or is another agent's, [FAILED] when it can't be loaded. No action call follows.
     */
    private fun current(agent: Agent, id: Long, redirect: RedirectAttributes, applies: (Visit) -> Boolean): Visit? {
        val outcome =
            try {
                val visit = visitClient.get(id)
                if (visit.agentId == agent.id && applies(visit)) return visit
                CHANGED
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Visit $id not shown: ${ex.statusCode}")
                changed(ex)
            } catch (ex: RestClientException) {
                LOGGER.error("Visit $id not shown", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, outcome)
        return null
    }

    /** Runs the action and flashes its outcome; ndopify-server's 403, 404 and 409 are [CHANGED], a network or 5xx [FAILED]. */
    private fun perform(
        id: Long,
        redirect: RedirectAttributes,
        outcome: String,
        returnUrl: String? = null,
        map: (HttpClientErrorException) -> String = { changed(it) },
        call: () -> Unit,
    ): String {
        val result =
            try {
                call()
                outcome
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Visit $id not $outcome: ${ex.statusCode}")
                map(ex)
            } catch (ex: RestClientException) {
                LOGGER.error("Visit $id not $outcome", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, result)
        return returnUrl ?: back(id)
    }

    private fun changed(ex: HttpClientErrorException): String =
        if (ex.statusCode.value() in CHANGED_STATUSES) CHANGED else FAILED

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }

    private fun flashed(
        id: Long,
        outcome: String,
        redirect: RedirectAttributes,
        returnUrl: String? = null,
    ): String {
        redirect.addFlashAttribute(OUTCOME, outcome)
        return returnUrl ?: back(id)
    }

    private fun back(id: Long): String = "redirect:/visits/$id"

    private fun locked(reason: String, model: Model): String {
        model.addAttribute("locked", reason)
        return VIEW
    }

    /** Each secondary lookup that fails leaves its line out: only the visit itself is required. */
    private fun <T> lookup(what: String, call: () -> T): T? =
        try {
            call()
        } catch (ex: RestClientException) {
            LOGGER.warn("No $what: ${ex.message}")
            null
        }
}
