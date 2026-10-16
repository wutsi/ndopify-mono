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
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock
import java.time.Duration

/**
 * Email + one-time-code sign-in, on a single page (`login.html`) with two steps:
 * 1. E-mail: `GET /login` asks for it, `POST /login` has ndopify-server e-mail a code and records a [PendingLogin] in
 *    the HTTP session. `POST /login` with `resend=true` sends a new code to the pending address.
 * 2. Code: `GET /login?step=code` asks for it, `POST /login/verify` exchanges it for an access token.
 *
 * The URL is never trusted: the code step is only shown while a [PendingLogin] is in the session, otherwise the
 * e-mail step is. The e-mail lives in the session, never in the URL or a form field, so it doesn't end up in logs or
 * history and can't be swapped at verify time.
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

        const val VIEW = "login"
        const val STEP_EMAIL = "email"
        const val STEP_CODE = "code"

        fun maskEmail(email: String): String {
            val at = email.indexOf('@')
            if (at <= 0) return email
            val name = email.substring(0, at)
            return name.take(if (name.length <= 2) 1 else 2) + "•••" + email.substring(at)
        }
    }

    @GetMapping
    fun show(@RequestParam(name = "step", required = false) requestedStep: String?, session: HttpSession, model: Model): String {
        val pending = pending(session)
        return if (requestedStep == STEP_CODE && pending != null) {
            codeStep(pending, model)
        } else {
            emailStep(pending?.email, model)
        }
    }

    /** Kept for links to the former two-page flow. */
    @GetMapping("/connection")
    fun connection(): String = "redirect:/login?step=$STEP_CODE"

    @PostMapping
    fun submit(
        @ModelAttribute form: LoginForm,
        session: HttpSession,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        // A resend reuses the pending sign-in's address: the code step doesn't carry the e-mail in the page.
        val email = if (form.resend) {
            pending(session)?.email ?: return "redirect:/login"
        } else {
            form.email.trim().lowercase()
        }
        try {
            otpClient.create(CreateOtpRequest(email = email, ttl = OTP_TTL.seconds.toInt()))
        } catch (ex: HttpClientErrorException) {
            LOGGER.error("Server error: ${ex.statusCode} ${ex.responseBodyAsString}", ex)

            // A rejected request replaces any earlier one: don't leave a code step open for another address.
            session.removeAttribute(PendingLogin.SESSION_ATTRIBUTE)
            model.addAttribute("error", "Cette adresse email n'est pas valide!")
            return emailStep(email, model)
        }

        session.setAttribute(
            PendingLogin.SESSION_ATTRIBUTE,
            PendingLogin(email = email, expiresAt = clock.millis() + OTP_TTL.toMillis()),
        )
        redirect.addFlashAttribute("resent", form.resend)
        return "redirect:/login?step=$STEP_CODE"
    }

    @PostMapping("/verify")
    fun verify(@ModelAttribute form: VerifyForm, session: HttpSession, model: Model): String {
        val pending = pending(session) ?: return "redirect:/login"

        val accessToken = try {
            authClient.authenticate(
                AuthenticateRequest(
                    email = pending.email,
                    applicationCode = ApplicationCode.PARTNER_CENTRAL,
                    authType = AuthType.OTP,
                    secret = form.code.filter { it.isDigit() },
                ),
            ).accessToken
        } catch (ex: HttpClientErrorException) {
            LOGGER.error("Server error: ${ex.statusCode} ${ex.responseBodyAsString}", ex)

            model.addAttribute("error", toMessage(ex))
            return codeStep(pending, model)
        }

        session.removeAttribute(PendingLogin.SESSION_ATTRIBUTE)
        accessTokenService.set(accessToken)
        // The Espace agent, which sends an agent who isn't verified yet to the KYC page.
        return "redirect:/"
    }

    private fun pending(session: HttpSession): PendingLogin? =
        session.getAttribute(PendingLogin.SESSION_ATTRIBUTE) as PendingLogin?

    private fun emailStep(email: String?, model: Model): String {
        model.addAttribute("step", STEP_EMAIL)
        model.addAttribute("email", email)
        return VIEW
    }

    private fun codeStep(pending: PendingLogin, model: Model): String {
        model.addAttribute("step", STEP_CODE)
        model.addAttribute("maskedEmail", maskEmail(pending.email))
        model.addAttribute("expiresAt", pending.expiresAt)
        return VIEW
    }

    private fun toMessage(ex: HttpClientErrorException): String {
        val code = try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }
        return when (code) {
            ErrorCode.AUTH_CREDENTIALS_EXPIRED -> "Ce code a expiré. Demandez un nouveau code ci-dessous."
            ErrorCode.AUTH_CREDENTIALS_ALREADY_USED -> "Ce code a déjà été utilisé. Demandez un nouveau code ci-dessous."
            ErrorCode.AUTH_INVALID_CREDENTIALS -> "Code incorrect. Vérifiez les 6 chiffres reçus par email."
            ErrorCode.AUTH_ACCESS_DENIED ->
                "Aucun compte partenaire n'utilise cette adresse. Vérifiez-la, ou rejoignez Ndopify."

            else -> "Connexion impossible pour le moment. Réessayez dans quelques instants."
        }
    }
}
