package com.wutsi.ndopify.npc.ui.login

import java.io.Serializable

/**
 * A sign-in waiting for its one-time code, kept in the HTTP session between `POST /login` and `POST /login/verify`.
 * Its presence is what allows the code step of `login.html` to be shown.
 */
data class PendingLogin(
    val email: String,
    val expiresAt: Long,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.pending-login"
    }
}
