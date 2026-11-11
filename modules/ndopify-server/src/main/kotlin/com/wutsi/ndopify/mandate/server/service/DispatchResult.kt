package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity

/** Why [MandateDispatcher.dispatch] made no offer. */
enum class DispatchReason {
    MANDATE_NOT_FOUND,
    NOT_PENDING,
    ALREADY_PENDING,
    OVERDUE_MANUAL,
    NIGHT,
    NO_CANDIDATE,
}

/** Why [MandateDispatcher] passed an agent over. The candidate filters report the first rule they hit. */
enum class CandidateRuleOut {
    SELF,
    DECLINED_OR_EXPIRED,
    AT_CAPACITY,
    MAX_PENDING,
}

/** A candidate that passed the filters, with its total score and the value of each factor, for logs only. */
data class CandidateScore(
    val agent: AgentEntity,
    val total: Double,
    val neighborhood: Double,
    val fairness: Double,
    val rating: Double,
    val responsiveness: Double,
    val capacity: Double,
    val jitter: Double,
)

/** What the candidate search and the scoring saw, for the summary and for tests. */
data class DispatchDiagnostics(
    val candidateCount: Int,
    val excluded: Map<CandidateRuleOut, Int>,
    val eligible: List<CandidateScore>,
)

/** The outcome of [MandateDispatcher.dispatchResult]. */
sealed interface DispatchResult {
    data class Offered(val assignment: AssignmentEntity, val diagnostics: DispatchDiagnostics) : DispatchResult

    data class Skipped(
        val reason: DispatchReason,
        val detail: String = "",
        val diagnostics: DispatchDiagnostics? = null,
    ) : DispatchResult
}

/** The PENDING offer, or null when nothing was offered. */
val DispatchResult.assignmentOrNull: AssignmentEntity?
    get() = (this as? DispatchResult.Offered)?.assignment
