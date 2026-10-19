package com.wutsi.ndopify.esignature.ui.sign

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.esignature.client.ContractContentClient
import com.wutsi.ndopify.esignature.client.ndopify.NdAuthClient
import com.wutsi.ndopify.esignature.client.ndopify.NdContractClient
import com.wutsi.ndopify.esignature.client.ndopify.NdOtpClient
import com.wutsi.ndopify.esignature.service.security.AccessTokenService
import com.wutsi.ndopify.esignature.service.security.JwtAuthentication
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.STEP_CODE
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.STEP_DONE
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.STEP_SIGN
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock
import java.time.Duration

/**
 * The link of the "contrat à signer" e-mail, `/sign/{contractPartyId}`, on a single page (`sign.html`):
 * 1. Intro, signed out: the signer types the e-mail the link was sent to; `POST /otp` has ndopify-server e-mail a code
 *    and records a [PendingSignature] in the HTTP session (`resend=true` sends a new one to the pending address).
 * 2. Code: `GET ?step=code`, only while a [PendingSignature] of this link is in the session; `POST /verify` exchanges
 *    the code for an `e-signature` access token, kept in the cookie.
 * 3. Signed in: the contract (or why it can't be signed, see [SignViews]); `POST /` signs it, then `?step=done`, but
 *    only the version the page showed: the form posts back the shown `contentUrl`, compared to the contract's.
 *
 * Nothing about the contract is shown before sign-in. The contract party is the URL's, the contract the one ndopify-server
 * finds for it among the signer's own: never a form field.
 */
@Controller
@RequestMapping("/sign/{contractPartyId}")
class SignController(
    private val otpClient: NdOtpClient,
    private val authClient: NdAuthClient,
    private val contractClient: NdContractClient,
    private val contentClient: ContractContentClient,
    private val accessTokenService: AccessTokenService,
    private val views: SignViews,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SignController::class.java)

        private val OTP_TTL: Duration = Duration.ofMinutes(5)

        const val VIEW = "sign"

        const val EMAIL_UNKNOWN =
            "Aucun contrat n'a été envoyé à cette adresse. Utilisez l'adresse e-mail à laquelle vous avez reçu ce lien."
        const val OTP_FAILED = "Le code n'a pas pu être envoyé. Vérifiez votre connexion, puis réessayez."
        const val CODE_EXPIRED = "Ce code a expiré. Demandez-en un nouveau ci-dessous."
        const val CODE_ALREADY_USED = "Ce code a déjà été utilisé. Demandez-en un nouveau ci-dessous."
        const val CODE_WRONG = "Code incorrect. Vérifiez les 6 chiffres reçus par e-mail."
        const val AUTH_FAILED = "Connexion impossible pour le moment. Réessayez dans quelques instants."
        const val CONSENT_REQUIRED = "Cochez la case pour signer le contrat."
        const val SIGN_FAILED = "Votre signature n'a pas pu être enregistrée. Vérifiez votre connexion, puis réessayez."
        const val CONTENT_CHANGED = "Le contrat a été modifié depuis son affichage : relisez-le avant de signer."

        // The contract can no longer be signed by this party, or already is: the done step says which
        private val SIGN_SETTLED = setOf(403, 404, 409)
    }

    @GetMapping
    fun show(
        @PathVariable contractPartyId: String,
        @RequestParam(name = "step", required = false) step: String?,
        session: HttpSession,
        model: Model,
    ): String {
        if (!signedIn()) {
            val pending = pending(session, contractPartyId)
            return if (step == STEP_CODE && pending != null) {
                codeStep(contractPartyId, pending, model)
            } else {
                introStep(contractPartyId, null, model)
            }
        }

        val view = views.build(findContract(contractPartyId), contractPartyId, step, contentClient::fetch)
        return render(contractPartyId, view, model)
    }

    @PostMapping("/otp")
    fun otp(
        @PathVariable contractPartyId: String,
        @ModelAttribute form: OtpForm,
        session: HttpSession,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        // A resend reuses the pending sign-in's address: the code step doesn't carry the e-mail in the page
        val email = if (form.resend) {
            pending(session, contractPartyId)?.email ?: return "redirect:/sign/$contractPartyId"
        } else {
            form.email.trim().lowercase()
        }
        try {
            otpClient.create(CreateOtpRequest(email = email, ttl = OTP_TTL.seconds.toInt()))
        } catch (ex: HttpClientErrorException) {
            warn(ex)

            // A rejected request replaces any earlier one: don't leave a code step open for another address
            session.removeAttribute(PendingSignature.SESSION_ATTRIBUTE)
            model.addAttribute("error", if (errorCode(ex) == ErrorCode.USER_NOT_FOUND) EMAIL_UNKNOWN else OTP_FAILED)
            return introStep(contractPartyId, email, model)
        }

        session.setAttribute(
            PendingSignature.SESSION_ATTRIBUTE,
            PendingSignature(
                contractPartyId = contractPartyId,
                email = email,
                expiresAt = clock.millis() + OTP_TTL.toMillis(),
            ),
        )
        redirect.addFlashAttribute("resent", form.resend)
        return "redirect:/sign/$contractPartyId?step=$STEP_CODE"
    }

    @PostMapping("/verify")
    fun verify(
        @PathVariable contractPartyId: String,
        @ModelAttribute form: VerifyForm,
        session: HttpSession,
        model: Model,
    ): String {
        // The e-mail comes from the session, never the form: it can't be swapped at verify time
        val pending = pending(session, contractPartyId) ?: return "redirect:/sign/$contractPartyId"
        val code = form.code.filter { it.isDigit() }

        val accessToken = try {
            authClient.authenticate(
                AuthenticateRequest(
                    email = pending.email,
                    applicationCode = ApplicationCode.E_SIGNATURE,
                    authType = AuthType.OTP,
                    secret = code,
                ),
            ).accessToken
        } catch (ex: HttpClientErrorException) {
            warn(ex)
            model.addAttribute("error", toCodeMessage(ex))
            model.addAttribute("code", code)
            return codeStep(contractPartyId, pending, model)
        }

        session.removeAttribute(PendingSignature.SESSION_ATTRIBUTE)
        accessTokenService.set(accessToken)
        return "redirect:/sign/$contractPartyId?step=$STEP_SIGN"
    }

    @PostMapping
    fun sign(
        @PathVariable contractPartyId: String,
        @ModelAttribute form: SignForm,
        request: HttpServletRequest,
        model: Model,
    ): String {
        if (!signedIn()) return "redirect:/sign/$contractPartyId"
        val contract = findContract(contractPartyId) ?: return "redirect:/sign/$contractPartyId"

        // Freshly built: signs only what the signer can be shown now, and shows the new version if it changed
        val view = views.build(contract, contractPartyId, STEP_SIGN, contentClient::fetch)
        if (view.step != STEP_SIGN) {
            return "redirect:/sign/$contractPartyId?step=$STEP_DONE" // No longer signable: the page says why
        }
        // Only compared, never used to find anything: the contract is the one ndopify-server found for the link
        if (!view.canSign || form.contentUrl == null || form.contentUrl != contract.contentUrl) {
            model.addAttribute("error", CONTENT_CHANGED)
            return render(contractPartyId, view, model)
        }
        if (form.consent != "yes") {
            model.addAttribute("error", CONSENT_REQUIRED)
            return render(contractPartyId, view, model)
        }

        try {
            contractClient.sign(
                contract.id,
                SignContractRequest(
                    partyId = contractPartyId,
                    ipAddress = request.getHeader("X-Forwarded-For")?.substringBefore(',')?.trim()?.ifEmpty { null }
                        ?: request.remoteAddr,
                    userAgent = request.getHeader(HttpHeaders.USER_AGENT),
                ),
            )
        } catch (ex: HttpClientErrorException) {
            warn(ex)
            if (ex.statusCode.value() !in SIGN_SETTLED) {
                model.addAttribute("error", SIGN_FAILED)
                return render(contractPartyId, view, model)
            }
            // Signed already (a double tap), or no longer signable: the page says which
        }
        return "redirect:/sign/$contractPartyId?step=$STEP_DONE"
    }

    /** "Utiliser une autre adresse": signs out, back to the intro of the same link. */
    @PostMapping("/logout")
    fun logout(@PathVariable contractPartyId: String, session: HttpSession): String {
        accessTokenService.remove()
        session.invalidate()
        return "redirect:/sign/$contractPartyId"
    }

    private fun signedIn(): Boolean = SecurityContextHolder.getContext().authentication is JwtAuthentication

    // Restricted by ndopify-server to the caller's party: another party's contract party finds nothing
    private fun findContract(contractPartyId: String): Contract? =
        contractClient.search(SearchContractRequest(contractPartyIds = listOf(contractPartyId), limit = 1))
            .firstOrNull()
            ?.let { summary -> contractClient.get(summary.id) }

    // Only the sign-in started from this link opens its code step
    private fun pending(session: HttpSession, contractPartyId: String): PendingSignature? =
        (session.getAttribute(PendingSignature.SESSION_ATTRIBUTE) as PendingSignature?)
            ?.takeIf { pending -> pending.contractPartyId == contractPartyId }

    private fun introStep(contractPartyId: String, email: String?, model: Model): String {
        model.addAttribute("email", email)
        return render(contractPartyId, views.intro(), model)
    }

    private fun codeStep(contractPartyId: String, pending: PendingSignature, model: Model): String =
        render(contractPartyId, views.code(pending), model)

    private fun render(contractPartyId: String, view: SignView, model: Model): String {
        model.addAttribute("contractPartyId", contractPartyId)
        model.addAttribute("view", view)
        return VIEW
    }

    private fun toCodeMessage(ex: HttpClientErrorException): String =
        when (errorCode(ex)) {
            ErrorCode.AUTH_CREDENTIALS_EXPIRED -> CODE_EXPIRED
            ErrorCode.AUTH_CREDENTIALS_ALREADY_USED -> CODE_ALREADY_USED
            ErrorCode.AUTH_INVALID_CREDENTIALS -> CODE_WRONG
            // A member of the tenant without e-signature access: not one of the signers
            ErrorCode.AUTH_ACCESS_DENIED -> EMAIL_UNKNOWN
            else -> AUTH_FAILED
        }

    // Status and error code only: the body's message can name the e-mail ("User not found: <email>")
    private fun warn(ex: HttpClientErrorException) {
        LOGGER.warn("Server error: ${ex.statusCode} ${errorCode(ex)}")
    }

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }
}
