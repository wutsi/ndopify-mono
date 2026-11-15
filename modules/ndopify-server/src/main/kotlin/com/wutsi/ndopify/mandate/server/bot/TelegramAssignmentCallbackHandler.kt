package com.wutsi.ndopify.mandate.server.bot

import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.WutsiException
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.service.AssignmentService
import com.wutsi.ndopify.platform.telegram.TelegramCallbackHandler
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.telegram.model.TelegramCallbackQuery
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.platform.tenant.jpa.TenantSessionScope
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Answers an offer from the chat: the tap on `Accepter` / `Refuser` accepts or declines the assignment through the
 * agent-scoped entry points of [AssignmentService], then edits the offer's message with the outcome.
 *
 * The bot has no JWT and no `X-Tenant-ID`: the chat id names #135's global link, whose row gives the tenant the action
 * runs in and the agent it runs for. A tap therefore only ever answers the offer of that agent, in that tenant, and a
 * wrong chat id, an unknown assignment or another agent's offer changes nothing and reveals nothing: the toast is the
 * same generic one, and the message isn't edited.
 *
 * No exception escapes [handle] (the webhook answers 200 whatever happens); a failing edit never undoes the
 * transition, and the toast is answered once per tap.
 */
@Service
class TelegramAssignmentCallbackHandler(
    private val client: TelegramClient,
    private val telegramService: AgentTelegramService,
    private val service: AssignmentService,
    private val tenantService: TenantService,
    private val tenantSession: TenantSessionScope,
) : TelegramCallbackHandler {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramAssignmentCallbackHandler::class.java)

        const val ACCEPTED = "Mandat accepté."
        const val DECLINED = "Mandat refusé."
        const val UNAVAILABLE = "Ce mandat n'est plus disponible."
        const val FAILED = "Action impossible."
        const val SEE_MANDATE = "Voir le mandat"
    }

    override fun handle(callback: TelegramCallbackQuery) {
        var toast = FAILED
        try {
            val call = TelegramOfferCallback.parse(callback.data) ?: return
            val chatId = callback.message?.chat?.id ?: return
            val link = telegramService.findByChatId(chatId) ?: return
            // The link is global; the request's session was opened before its tenant was known
            TenantContext.set(link.tenantId)
            tenantSession.reopen()

            when (call.action) {
                TelegramOfferCallback.Action.ACCEPT -> {
                    val accepted = service.acceptForAgent(link.agentId, call.assignmentId)
                    toast = ACCEPTED
                    edit(callback, toast, mandateButtons(link.tenantId, accepted))
                }

                TelegramOfferCallback.Action.DECLINE -> {
                    service.declineForAgent(link.agentId, call.assignmentId)
                    toast = DECLINED
                    edit(callback, toast)
                }
            }
        } catch (ex: ConflictException) {
            // Expired, already answered, mandate cancelled: the offer's buttons go, nothing changes
            toast = UNAVAILABLE
            edit(callback, toast)
        } catch (ex: WutsiException) {
            // 404 / 403: changing or editing anything would reveal that the assignment exists
            LOGGER.warn("Telegram callback refused: ${ex.error.code}")
        } catch (ex: Exception) {
            // The link lookup, the session, the transition: logged and contained, so the webhook answers 200
            LOGGER.error("Telegram callback failed", ex)
        } finally {
            answer(callback, toast)
            TenantContext.remove()
        }
    }

    // A failing toast is only logged: the webhook answers 200, and Telegram must not retry the update forever
    private fun answer(callback: TelegramCallbackQuery, toast: String) {
        try {
            client.answerCallbackQuery(callback.id, toast)
        } catch (ex: Exception) {
            LOGGER.warn("Unable to answer Telegram callback ${callback.id}", ex)
        }
    }

    // A deleted message must not undo the transition
    private fun edit(callback: TelegramCallbackQuery, outcome: String, buttons: List<TelegramButton> = emptyList()) {
        val message = callback.message ?: return
        try {
            client.editMessageText(
                message.chat.id,
                message.messageId,
                listOfNotNull(message.text, outcome).joinToString("\n\n"),
                buttons,
            )
        } catch (ex: Exception) {
            LOGGER.warn("Unable to edit Telegram message ${message.messageId}", ex)
        }
    }

    /** One `Voir le mandat` button to the tenant's mandate page, or none without a `partnerCentralUrl`. */
    private fun mandateButtons(tenantId: Long, accepted: AssignmentEntity): List<TelegramButton> =
        tenantService.findById(tenantId).partnerCentralUrl
            ?.takeIf { it.isNotBlank() }
            ?.removeSuffix("/")
            ?.let { url -> listOf(TelegramButton(SEE_MANDATE, url = "$url/mandates/${accepted.mandate.id}")) }
            ?: emptyList()
}
