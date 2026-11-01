package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.match.dto.MatchRejectionReason

/** The "Pas pour moi" popup: the main reason (radio `reason`) and an optional `comment`. */
data class RejectForm(
    val reason: MatchRejectionReason? = null,
    val comment: String? = null,
)
