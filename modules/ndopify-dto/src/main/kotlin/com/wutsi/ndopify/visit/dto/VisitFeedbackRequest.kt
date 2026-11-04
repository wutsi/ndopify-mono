package com.wutsi.ndopify.visit.dto

import com.wutsi.ndopify.match.dto.MatchRejectionReason
import jakarta.validation.constraints.Size

data class VisitFeedbackRequest(
    val interest: VisitInterest = VisitInterest.INTERESTED,

    // Why the property doesn't interest the customer, when it doesn't
    val reasonCode: MatchRejectionReason? = null,

    @get:Size(max = 1000) val comment: String? = null,
)
