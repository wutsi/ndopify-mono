package com.wutsi.ndopify.npc.ui.telegram

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock

/**
 * The Telegram connection page (`GET /telegram`) and its code request (`POST /telegram/code`).
 *
 * The page explains the three steps and says whether the agent's chat is linked; its first step asks ndopify-server
 * for a code, which it e-mails. Both go back through the page, with a flash attribute. The redirect target is fixed,
 * never a URL from the request.
 *
 * The code's pending state is kept twice: [TelegramRules.SENT] is the flash that confirms the send once, and
 * [TelegramRules.SENT_UNTIL] the session's expiry, which outlives it for the code's TTL so that a refresh still
 * knows a code is waiting.
 */
@Controller
@RequestMapping("/telegram")
class TelegramController(
    private val currentAgent: CurrentAgent,
    private val agentClient: NdAgentClient,
    @Value("\${ndopify.telegram.username}") private val botUsername: String,
    private val clock: Clock,
    @Value("\${ndopify.telegram.code-ttl-minutes:15}") private val codeTtlMinutes: Long,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramController::class.java)

        const val VIEW = "telegram"
        const val REDIRECT = "redirect:/telegram"
    }

    @GetMapping
    fun show(model: Model, session: HttpSession): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
        model.addAttribute("telegramConnected", agent.telegramConnected)
        model.addAttribute("telegramBotUrl", TelegramRules.botUrl(botUsername))
        model.addAttribute("telegramCodePending", pending(model, session))
        return VIEW
    }

    @PostMapping("/code")
    fun requestCode(redirect: RedirectAttributes, session: HttpSession): String {
        currentAgent.get() ?: throw SignInRequiredException()

        try {
            agentClient.requestTelegramCode()
            redirect.addFlashAttribute(TelegramRules.SENT, true)
            session.setAttribute(TelegramRules.SENT_UNTIL, clock.millis() + codeTtlMinutes * 60_000)
        } catch (ex: HttpClientErrorException) {
            if (errorCode(ex) == ErrorCode.AGENT_TELEGRAM_ALREADY_CONNECTED) {
                redirect.addFlashAttribute(TelegramRules.ERROR, TelegramRules.ALREADY_CONNECTED)
            } else {
                LOGGER.error("Server error: ${ex.message}", ex)
                redirect.addFlashAttribute(TelegramRules.ERROR, TelegramRules.SEND_FAILED)
            }
        } catch (ex: RestClientException) {
            LOGGER.error("Server error: ${ex.message}", ex)
            redirect.addFlashAttribute(TelegramRules.ERROR, TelegramRules.SEND_FAILED)
        }

        return REDIRECT
    }

    /**
     * True while a code is pending: the flash covers the redirect right after the request, the session's expiry
     * the refreshes until the code itself expires. An expired entry is cleared, so step 2 closes again.
     */
    private fun pending(model: Model, session: HttpSession): Boolean {
        if (model.getAttribute(TelegramRules.SENT) != null) {
            return true
        }

        val until = session.getAttribute(TelegramRules.SENT_UNTIL) as? Long ?: return false
        if (until > clock.millis()) {
            return true
        }
        session.removeAttribute(TelegramRules.SENT_UNTIL)
        return false
    }

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }
}
