package com.wutsi.ndopify.npc.ui.kyc

import java.io.Serializable

/** Set once the agent has submitted [caseId]: the confirmation (`/kyc?step=confirmation`) is shown while it's there. */
data class SubmittedKyc(val caseId: String) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.submitted-kyc"
    }
}
