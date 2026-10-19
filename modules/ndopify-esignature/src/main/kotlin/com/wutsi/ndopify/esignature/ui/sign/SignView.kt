package com.wutsi.ndopify.esignature.ui.sign

/**
 * What `sign.html` shows (model attribute `view`), dates already in the tenant's format. Only the `sign` step carries
 * the contract's content; `blocked` / `done` never do, and `not-yours` carries nothing of the contract.
 */
data class SignView(
    val step: String,
    val blocked: String? = null,
    val contractType: String? = null,
    val contractNumber: String? = null,
    val senderName: String? = null,
    val deadline: String? = null,
    val signerName: String? = null,
    val signerFirstName: String? = null,
    val signDate: String? = null,
    val signedAt: String? = null,
    val contentHtml: String? = null,
    val canSign: Boolean = false,
    val maskedEmail: String? = null,
    val codeExpiresAt: String? = null,
) {
    companion object {
        const val STEP_INTRO = "intro"
        const val STEP_CODE = "code"
        const val STEP_SIGN = "sign"
        const val STEP_DONE = "done"
        const val STEP_BLOCKED = "blocked"

        const val BLOCKED_EXPIRED = "expired"
        const val BLOCKED_CANCELLED = "cancelled"
        const val BLOCKED_SIGNED = "signed"
        const val BLOCKED_NOT_REQUIRED = "not-required"
        const val BLOCKED_UNAVAILABLE = "unavailable"
        const val BLOCKED_NOT_YOURS = "not-yours"
    }
}
