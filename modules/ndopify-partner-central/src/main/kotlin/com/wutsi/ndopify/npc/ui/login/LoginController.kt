package com.wutsi.ndopify.npc.ui.login

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAuthClient
import com.wutsi.ndopify.npc.client.ndopify.NdOtpClient
import com.wutsi.ndopify.npc.service.security.AccessTokenService
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock
import java.time.Duration

/**
 * Email + one-time-code sign-in:
 * 1. `GET /login` asks for the email, `POST /login` has ndopify-server email a code (also used to resend it).
 * 2. `GET /login/connection` asks for the code, `POST /login/verify` exchanges it for an access token.
 *
 * The email travels between steps as a flash attribute, never in the URL, so it doesn't end up in logs or history.
 */
@Controller
@RequestMapping("/login")
class LoginController(
    private val otpClient: NdOtpClient,
    private val authClient: NdAuthClient,
    private val accessTokenService: AccessTokenService,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(LoginController::class.java)

        val OTP_TTL: Duration = Duration.ofMinutes(5)

        fun maskEmail(email: String): String {
            val at = email.indexOf('@')
            if (at <= 0) return email
            val name = email.substring(0, at)
            return name.take(if (name.length <= 2) 1 else 2) + "•••" + email.substring(at)
        }
    }

    @GetMapping
    fun show(): String = "login/index"

    @PostMapping
    fun submit(@ModelAttribute form: LoginForm, model: Model, redirect: RedirectAttributes): String {
        val email = form.email.trim().lowercase()
        try {
            otpClient.create(CreateOtpRequest(email = email, ttl = OTP_TTL.seconds.toInt()))
        } catch (ex: HttpClientErrorException) {
            LOGGER.error("Server error: ${ex.statusCode} ${ex.responseBodyAsString}", ex)

            model.addAttribute("email", email)
            model.addAttribute("error", "Cette adresse email n'est pas valide!")
            return "login/index"
        }

        redirect.addFlashAttribute("email", email)
        redirect.addFlashAttribute("expiresAt", clock.millis() + OTP_TTL.toMillis())
        redirect.addFlashAttribute("resent", form.resend)
        return "redirect:/login/connection"
    }

    @GetMapping("/connection")
    fun connection(model: Model): String {
        (model.getAttribute("email") as String?)?.let { email -> model.addAttribute("maskedEmail", maskEmail(email)) }
        return "login/connection"
    }

    @PostMapping("/verify")
    fun verify(
        @ModelAttribute form: VerifyForm,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): String {
        val email = form.email.trim().lowercase()
        if (email.isEmpty()) return "login/connection" // no email: the page shows the "start again" alert

        val accessToken = try {
            authClient.authenticate(
                AuthenticateRequest(
                    email = email,
                    applicationCode = ApplicationCode.PARTNER_CENTRAL,
                    authType = AuthType.OTP,
                    secret = form.code.filter { it.isDigit() },
                ),
            ).accessToken
        } catch (ex: HttpClientErrorException) {
            LOGGER.error("Server error: ${ex.statusCode} ${ex.responseBodyAsString}", ex)

            model.addAttribute("email", email)
            model.addAttribute("maskedEmail", maskEmail(email))
            model.addAttribute("error", toMessage(ex))
            return "login/connection"
        }

        accessTokenService.set(accessToken)
        return "redirect:/kyc"
    }

    private fun toMessage(ex: HttpClientErrorException): String {
        val code = try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }
        return when (code) {
            ErrorCode.AUTH_CREDENTIALS_EXPIRED -> "Ce code a expiré. Demandez un nouveau code ci-dessous."
            ErrorCode.AUTH_INVALID_CREDENTIALS -> "Code incorrect. Vérifiez les 6 chiffres reçus par email."
            ErrorCode.AUTH_ACCESS_DENIED ->
                "Aucun compte partenaire n'utilise cette adresse. Vérifiez-la, ou rejoignez Ndopify."

            else -> "Connexion impossible pour le moment. Réessayez dans quelques instants."
        }
    }
}
