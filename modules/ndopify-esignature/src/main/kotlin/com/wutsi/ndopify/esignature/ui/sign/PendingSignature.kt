package com.wutsi.ndopify.esignature.ui.sign

import java.io.Serializable

/**
 * A sign-in waiting for its one-time code, kept in the HTTP session between `POST /sign/{id}/otp` and
 * `POST /sign/{id}/verify`. It only opens the code step of the link it was started from ([contractPartyId]).
 */
data class PendingSignature(
    val contractPartyId: String,
    val email: String,
    val expiresAt: Long,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "nes.pending-signature"
    }
}
