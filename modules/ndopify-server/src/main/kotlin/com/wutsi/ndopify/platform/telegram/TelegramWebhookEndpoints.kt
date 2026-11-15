package com.wutsi.ndopify.platform.telegram

import com.wutsi.ndopify.platform.telegram.model.TelegramUpdate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

/**
 * Telegram's webhook, in webhook mode only. It's public (no JWT, [com.wutsi.ndopify.security.server.servlet
 * .PublicEndpoints.GLOBAL]): the `X-Telegram-Bot-Api-Secret-Token` header Telegram echoes is its only protection, so a
 * missing or wrong one is a 403 and nothing is handled. The comparison is constant-time, as Telegram recommends.
 *
 * An update is answered 200 even when it's ignored: a non-2xx makes Telegram retry it.
 */
@RestController
@ConditionalOnProperty(name = ["ndopify.telegram.transport"], havingValue = "webhook")
class TelegramWebhookEndpoints(
    private val handler: TelegramUpdateHandler,
    @param:Value("\${ndopify.telegram.webhook.secret}") private val secret: String,
) {
    @PostMapping("/telegram/webhook")
    fun receive(
        @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) token: String?,
        @RequestBody update: TelegramUpdate,
    ): ResponseEntity<Void> {
        if (token == null || !MessageDigest.isEqual(token.toByteArray(), secret.toByteArray())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        handler.handle(update)
        return ResponseEntity.ok().build()
    }
}
