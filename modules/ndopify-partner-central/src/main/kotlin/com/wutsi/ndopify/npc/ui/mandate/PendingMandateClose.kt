package com.wutsi.ndopify.npc.ui.mandate

import java.io.Serializable

/**
 * The answers of a mandate-closing wizard in progress, kept in the HTTP session until the confirm step submits them.
 * It holds ids and numbers only, never rendered labels: the confirm step re-reads the candidates and the mandate, so a
 * stale session cannot close something the page no longer lists.
 *
 * `matchId` is the "Client" / "Bien" the deal is closed with; null means either no answer yet or the [outside] answer
 * ("hors de vos annonces / recherches"). The counterpart step is skipped when there are no candidates.
 */
data class PendingMandateClose(
    val matchId: Long? = null,
    val outside: Boolean = false,
    val priceAmount: Long? = null,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.pending-mandate-close"

        const val STEP_COUNTERPART = "counterpart"
        const val STEP_PRICE = "price"
        const val STEP_CONFIRM = "confirm"

        /** The wizard's steps in order, when the counterpart step applies. */
        val STEPS = listOf(STEP_COUNTERPART, STEP_PRICE, STEP_CONFIRM)

        /** The same steps without the counterpart one, when there are no candidates. */
        val STEPS_WITHOUT_COUNTERPART = listOf(STEP_PRICE, STEP_CONFIRM)
    }

    fun steps(counterpartApplicable: Boolean): List<String> =
        if (counterpartApplicable) STEPS else STEPS_WITHOUT_COUNTERPART

    /** Whether [step] has its answer. The counterpart step, when skipped, counts as complete. */
    fun isComplete(step: String, counterpartApplicable: Boolean): Boolean = when (step) {
        STEP_COUNTERPART -> !counterpartApplicable || matchId != null || outside
        STEP_PRICE -> priceAmount != null
        else -> false
    }

    /** The first step that still needs answers, or [STEP_CONFIRM] when they're all done. */
    fun firstIncompleteStep(counterpartApplicable: Boolean): String =
        steps(counterpartApplicable).dropLast(1).firstOrNull { !isComplete(it, counterpartApplicable) }
            ?: STEP_CONFIRM
}
