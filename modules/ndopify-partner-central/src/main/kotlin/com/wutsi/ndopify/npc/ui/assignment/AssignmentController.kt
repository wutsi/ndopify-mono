package com.wutsi.ndopify.npc.ui.assignment

import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * Accepting or declining an offer of the Espace agent, then back to it with the outcome (flash attribute
 * [OUTCOME]). The assignment id comes from the URL: ndopify-server only lets the offer's agent (from the token) act
 * on it. An offer that's no longer open (expired, already answered: 409; someone else's: 403; gone: 404) is
 * [CLOSED], not an error: the page it was on was just stale.
 */
@Controller
@RequestMapping("/assignments")
class AssignmentController(
    private val client: NdAssignmentClient,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AssignmentController::class.java)

        const val OUTCOME = "assignment"
        const val ACCEPTED = "accepted"
        const val DECLINED = "declined"
        const val CLOSED = "closed"
        const val FAILED = "error"

        private val CLOSED_STATUSES = setOf(403, 404, 409)
    }

    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: Long, redirect: RedirectAttributes): String =
        answer(id, ACCEPTED, redirect) { client.accept(id) }

    @PostMapping("/{id}/decline")
    fun decline(@PathVariable id: Long, redirect: RedirectAttributes): String =
        answer(id, DECLINED, redirect) { client.decline(id) }

    private fun answer(id: Long, outcome: String, redirect: RedirectAttributes, call: () -> Unit): String {
        val result =
            try {
                call()
                outcome
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Offer $id not $outcome: ${ex.statusCode}")
                if (ex.statusCode.value() in CLOSED_STATUSES) CLOSED else FAILED
            } catch (ex: RestClientException) {
                LOGGER.error("Offer $id not $outcome", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, result)
        return "redirect:/"
    }
}
