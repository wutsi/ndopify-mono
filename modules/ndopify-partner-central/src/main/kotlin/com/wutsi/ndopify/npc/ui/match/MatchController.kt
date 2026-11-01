package com.wutsi.ndopify.npc.ui.match

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
 * Sending a matched listing to the client (propose) or setting it aside (dismiss), from the mandate page's *Biens
 * correspondants*, then back to it with the outcome (flash attribute [OUTCOME], and the listing's id as [LISTING]),
 * worded by `MandateController`.
 *
 * ndopify-server lets only the mandate's agent act on its matches. The match is read first: one of another mandate
 * than the URL's (a hand-edited id), or no longer ACTIVE (sent or set aside meanwhile, ex: from another tab), is
 * [CHANGED] without any call, so that a double post never e-mails the client twice. So is ndopify-server's 403, 404
 * or 409: the page was just stale. A non-numeric id goes back to the Espace agent.
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

    private fun act(
        mandateId: String,
        matchId: String,
        outcome: String,
        redirect: RedirectAttributes,
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
                if (match.mandateId != mid || match.status != MatchStatus.ACTIVE) {
                    LOGGER.warn("Match $id not $outcome: ${match.status}, of mandate ${match.mandateId}")
                    CHANGED
                } else {
                    call(id)
                    outcome
                }
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Match $id not $outcome: ${ex.statusCode}")
                if (ex.statusCode.value() in CHANGED_STATUSES) CHANGED else FAILED
            } catch (ex: RestClientException) {
                LOGGER.error("Match $id not $outcome", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, result)
        // The outcome shows at the top of the matches, below the status: land there, not at the top of the page.
        return "redirect:/mandates/$mid#matches-title"
    }
}
