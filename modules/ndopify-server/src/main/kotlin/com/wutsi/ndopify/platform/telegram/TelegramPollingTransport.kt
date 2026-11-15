package com.wutsi.ndopify.platform.telegram

import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The local transport: Telegram long polling on a virtual thread.
 *
 * [start] clears the bot's webhook first, so `getUpdates` answers (a webhook and polling are exclusive on Telegram's
 * side), then polls until [stop]. A failed call is logged and retried 5 seconds later, never thrown: a Telegram outage
 * is reflected in the health indicator, not in a crash loop. Nothing here logs the client's URL, which carries the
 * token.
 *
 * @param enabled false when another transport runs, or polling is off (the `qa` profile): [start] does nothing then
 */
class TelegramPollingTransport(
    private val client: TelegramClient,
    private val handler: TelegramUpdateHandler,
    private val enabled: Boolean,
    private val timeoutSeconds: Int,
) : SmartLifecycle {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramPollingTransport::class.java)
        private const val RETRY_DELAY_MS = 5000L
    }

    private val running = AtomicBoolean(false)
    private var offset: Long? = null
    private var thread: Thread? = null

    /**
     * Fetches the pending updates once and hands each to the handler, then moves the offset past the last one so that
     * Telegram drops them.
     */
    fun pollOnce() {
        client.getUpdates(offset, timeoutSeconds).forEach { update ->
            handler.handle(update)
            offset = update.updateId + 1
        }
    }

    override fun start() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return
        }
        try {
            client.deleteWebhook()
        } catch (ex: Exception) {
            LOGGER.warn("Could not clear the Telegram webhook, polling anyway", ex)
        }
        thread = Thread.ofVirtual().name("telegram-poller").start { loop() }
    }

    override fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    override fun isRunning(): Boolean = running.get()

    private fun loop() {
        while (running.get()) {
            try {
                pollOnce()
            } catch (ex: InterruptedException) {
                return
            } catch (ex: Exception) {
                LOGGER.warn("Telegram polling failed, retrying in $RETRY_DELAY_MS ms", ex)
                try {
                    Thread.sleep(RETRY_DELAY_MS)
                } catch (ex: InterruptedException) {
                    return
                }
            }
        }
    }
}
