package com.wutsi.ndopify.npc.ui.join

import java.io.Serializable

/**
 * A submitted application, kept in the HTTP session after `POST /rejoindre/revue` so the confirmation (which has no
 * URL of its own in the prototype) survives a reload. Its presence is what allows the confirmation to be shown.
 */
data class JoinedAgent(
    val email: String,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.joined-agent"
    }
}
