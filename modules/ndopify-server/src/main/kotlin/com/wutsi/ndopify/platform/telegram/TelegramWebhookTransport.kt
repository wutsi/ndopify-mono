package com.wutsi.ndopify.platform.telegram

import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The TEST/PROD transport: registers the bot's webhook with [client] at startup, so that Telegram POSTs the updates to
 * [url] (`${SERVER_URL}/telegram/webhook`) with [secret] in the `X-Telegram-Bot-Api-Secret-Token` header.
 *
 * A blank URL or secret fails startup: the webhook would never receive anything, and nobody would notice. A
 * `setWebhook` that fails (a Telegram outage) is only logged: the endpoint still answers once Telegram retries, and
 * the health indicator reports the outage.
 *
 * [isRunning] only answers `true` once [start] has run: Spring starts a [SmartLifecycle] only while it reports itself
 * stopped (`DefaultLifecycleProcessor`), so one that derived its state from the configuration alone would never be
 * started, and would never register the webhook.
 *
 * @param transport `polling` outside TEST/PROD: [start] does nothing then
 */
class TelegramWebhookTransport(
    private val client: TelegramClient,
    private val transport: String,
    private val url: String,
    private val secret: String,
) : SmartLifecycle {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramWebhookTransport::class.java)
    }

    private val running = AtomicBoolean(false)

    override fun start() {
        if (transport != "webhook") {
            return
        }
        check(url.isNotBlank()) { "ndopify.telegram.webhook.url is required in webhook mode" }
        check(secret.isNotBlank()) { "ndopify.telegram.webhook.secret is required in webhook mode" }
        try {
            client.setWebhook(url, secret)
        } catch (ex: Exception) {
            LOGGER.error("Could not register the Telegram webhook, Telegram won't deliver updates until it does", ex)
        }
        running.set(true)
    }

    override fun stop() {
        running.set(false)
    }

    override fun isRunning(): Boolean = running.get()
}
