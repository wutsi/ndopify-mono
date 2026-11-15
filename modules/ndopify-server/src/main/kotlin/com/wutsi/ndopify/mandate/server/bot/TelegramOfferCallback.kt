package com.wutsi.ndopify.mandate.server.bot

/**
 * The `callback_data` of an offer's buttons: `<accept|decline>:<assignment_id>`, far under Telegram's 64-byte limit.
 *
 * Anything else (another bot message, a button of an older version) is [parse]d as null, so that the callback handler
 * answers it generically instead of acting on it.
 */
internal object TelegramOfferCallback {
    enum class Action { ACCEPT, DECLINE }

    data class Call(val action: Action, val assignmentId: Long)

    fun data(action: Action, assignmentId: Long): String = "${action.name.lowercase()}:$assignmentId"

    fun parse(data: String?): Call? {
        val parts = data?.split(":") ?: return null
        if (parts.size != 2) {
            return null
        }
        val action = when (parts[0]) {
            "accept" -> Action.ACCEPT
            "decline" -> Action.DECLINE
            else -> return null
        }
        val assignmentId = parts[1].toLongOrNull() ?: return null
        return Call(action, assignmentId)
    }
}
