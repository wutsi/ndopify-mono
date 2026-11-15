package com.wutsi.ndopify.npc.ui.telegram

import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * The Telegram connection page (`GET /telegram`) and its code request (`POST /telegram/code`).
 *
 * The page explains the three steps and says whether the agent's chat is linked; the button asks ndopify-server for
 * a code, which it e-mails. Both go back through the page, with a flash attribute. The redirect target is fixed,
 * never a URL from the request.
 */
@Controller
@RequestMapping("/telegram")
class TelegramController(
    private val currentAgent: CurrentAgent,
    private val agentClient: NdAgentClient,
    @Value("\${ndopify.telegram.username}") private val botUsername: String,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramController::class.java)

        const val VIEW = "telegram"
        const val REDIRECT = "redirect:/telegram"
    }

    @GetMapping
    fun show(model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
        model.addAttribute("telegramConnected", agent.telegramConnected)
        model.addAttribute("telegramBotUrl", TelegramRules.botUrl(botUsername))
        return VIEW
    }

    @PostMapping("/code")
    fun requestCode(redirect: RedirectAttributes): String {
        currentAgent.get() ?: throw SignInRequiredException()

        try {
            agentClient.requestTelegramCode()
            redirect.addFlashAttribute(TelegramRules.SENT, true)
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode == HttpStatus.CONFLICT) {
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
}
