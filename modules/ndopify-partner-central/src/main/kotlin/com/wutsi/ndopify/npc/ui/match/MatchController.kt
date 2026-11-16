package com.wutsi.ndopify.npc.ui.match

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * Sending a matched listing to the client (propose), e-mailing its link again (resend) or setting it aside (dismiss),
 * from the mandate page's *Biens correspondants*, then back to it with the outcome (flash attribute [OUTCOME], and the
 * listing's id as [LISTING]), worded by `MandateController`.
 *
 * ndopify-server lets only the mandate's agent act on its matches. The match is read first: one of another mandate
 * than the URL's (a hand-edited id), or no longer in the action's statuses (sent or set aside meanwhile, ex: from
 * another tab), is [CHANGED] without any call, so that a double post never e-mails the client twice. So is
 * ndopify-server's 403, 404 or 409, but a resend refused by the throttle: that 409 carries `MATCH_RATE_LIMITED` in its
 * body, which the page words itself ([RATE_LIMITED]), not as a stale page. A non-numeric id goes back to the Espace
 * agent.
 */
@Controller
@RequestMapping("/mandates/{mandateId}/matches/{matchId}")
class MatchController(
    private val client: NdMatchClient,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchController::class.java)

        const val OUTCOME = "match"
        const val LISTING = "matchListing"
        const val PROPOSED = "proposed"
        const val DISMISSED = "dismissed"
        const val RESENT = "resent"
        const val RATE_LIMITED = "rate-limited"
        const val CHANGED = "changed"
        const val FAILED = "error"

        private val CHANGED_STATUSES = setOf(403, 404, 409)
    }

    @PostMapping("/propose")
    fun propose(@PathVariable mandateId: String, @PathVariable matchId: String, redirect: RedirectAttributes): String =
        act(mandateId, matchId, PROPOSED, redirect) { client.propose(it) }

    @PostMapping("/dismiss")
    fun dismiss(@PathVariable mandateId: String, @PathVariable matchId: String, redirect: RedirectAttributes): String =
        act(mandateId, matchId, DISMISSED, redirect) { client.dismiss(it) }

    // A fresh link to a sent match, answered (interested) or not: its status is left as is
    @PostMapping("/resend")
    fun resend(@PathVariable mandateId: String, @PathVariable matchId: String, redirect: RedirectAttributes): String =
        act(mandateId, matchId, RESENT, redirect, MatchRules.RESENDABLE) { client.resend(it) }

    private fun act(
        mandateId: String,
        matchId: String,
        outcome: String,
        redirect: RedirectAttributes,
        statuses: Set<MatchStatus> = MatchRules.ACTIONABLE,
        call: (Long) -> Unit,
    ): String {
        val mid = mandateId.toLongOrNull()
        val id = matchId.toLongOrNull()
        if (mid == null || id == null) {
            return "redirect:/"
        }

        val result =
            try {
                val match = client.get(id)
                redirect.addFlashAttribute(LISTING, match.listingId)
                if (match.mandateId != mid || match.status !in statuses) {
                    LOGGER.warn("Match $id not $outcome: ${match.status}, of mandate ${match.mandateId}")
                    CHANGED
                } else {
                    call(id)
                    outcome
                }
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Match $id not $outcome: ${ex.statusCode}")
                // A 409 says either that the page is stale or, for a resend, that one was just sent: the body tells
                val code = try {
                    ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
                } catch (ignored: Exception) {
                    null
                }
                when {
                    code == ErrorCode.MATCH_RATE_LIMITED -> RATE_LIMITED
                    ex.statusCode.value() in CHANGED_STATUSES -> CHANGED
                    else -> FAILED
                }
            } catch (ex: RestClientException) {
                LOGGER.error("Match $id not $outcome", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, result)
        // The outcome shows at the top of the matches, below the status: land there, not at the top of the page.
        return "redirect:/mandates/$mid#matches-title"
    }
}
