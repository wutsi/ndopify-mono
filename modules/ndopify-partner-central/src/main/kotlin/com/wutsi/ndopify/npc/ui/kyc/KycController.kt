package com.wutsi.ndopify.npc.ui.kyc

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.service.security.CurrentParty
import com.wutsi.ndopify.npc.ui.kyc.KycRules.PHOTOS
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEPS
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEP_CONFIRMATION
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEP_FRONT
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEP_REVIEW
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.Party
import jakarta.servlet.http.HttpSession
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.multipart.MultipartFile

/**
 * The agent's identity verification, on a single page (`kyc.html`), in three views:
 * - wizard: four steps, [STEPS] (front → back → selfie → review). `GET /kyc?step=…` shows a step. Each upload
 *   step posts its photo to `POST /kyc/{step}`, which adds it straight to the agent's PENDING case on
 *   ndopify-server, so the case is the wizard's state and the session holds no photos. `POST /kyc/review`
 *   submits the case.
 * - confirmation: `?step=confirmation`, right after submitting (while [SubmittedKyc] is in the session).
 * - status: once the case is no longer PENDING (pending / verified / rejected), whatever the URL says. A rejected
 *   case can be restarted (`POST /kyc/restart`), which opens a new case.
 *
 * The URL is never trusted: a step is only shown once the case has every photo before it.
 */
@Controller
@RequestMapping("/kyc")
class KycController(
    private val kycClient: NdKycClient,
    private val currentParty: CurrentParty,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(KycController::class.java)
        private val TIKA = Tika()

        const val VIEW = "kyc"
        const val RETURN_TO_REVIEW = "review"
        const val ERROR_TOO_LARGE = "too-large"

        // No party for the signed-in user (gone, other tenant, or party-less like an admin): have them sign in again.
        const val SIGN_OUT = "redirect:/logout"
    }

    @GetMapping
    fun show(
        @RequestParam(name = "step", required = false) requestedStep: String?,
        @RequestParam(required = false) returnTo: String?,
        @RequestParam(name = "error", required = false) uploadError: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val party = currentParty.get() ?: return SIGN_OUT
        val case = kycClient.current(party.id)
        val status = KycRules.status(case)

        val submitted = session.getAttribute(SubmittedKyc.SESSION_ATTRIBUTE) as SubmittedKyc?
        if (requestedStep == STEP_CONFIRMATION && status != null && submitted?.caseId == case?.id) {
            header(party, model)
            model.addAttribute("view", "confirmation")
            return VIEW
        }
        if (status != null) {
            return status(party, status, case!!, model)
        }

        val photos = KycRules.photos(case)
        val requested = STEPS.indexOf(requestedStep).coerceAtLeast(0)
        val allowed = STEPS.indexOf(KycRules.firstIncompleteStep(photos.keys))
        val shown = STEPS[minOf(requested, allowed)]
        val error =
            if (uploadError == ERROR_TOO_LARGE && shown == requestedStep) "Cette photo dépasse la taille maximale de 5 Mo." else null
        return wizard(party, shown, photos, returnTo, error, model)
    }

    @PostMapping("/{step:front|back|selfie}")
    fun upload(
        @PathVariable step: String,
        @RequestParam(required = false) photo: MultipartFile?,
        @RequestParam(required = false) returnTo: String?,
        model: Model,
    ): String {
        val party = currentParty.get() ?: return SIGN_OUT
        val case = openCase(party.id) ?: return "redirect:/kyc"
        val photos = KycRules.photos(case)
        val firstIncomplete = KycRules.firstIncompleteStep(photos.keys)
        if (STEPS.indexOf(step) > STEPS.indexOf(firstIncomplete)) return redirect(firstIncomplete)

        val imageType = PHOTOS.getValue(step)
        if (photo == null || photo.isEmpty) {
            // "Suivant" on a step whose photo is already in the case just moves on.
            if (imageType !in photos) {
                return wizard(party, step, photos, returnTo, "Ajoutez une photo pour continuer.", model)
            }
        } else {
            val content = photo.bytes
            // The browser's content type is a claim; the bytes are the truth.
            val mimeType = TIKA.detect(content)
            KycRules.photo(photo.size, mimeType)
                ?.let { error -> return wizard(party, step, photos, returnTo, error, model) }
            try {
                kycClient.upload(case.id, imageType, content, mimeType)
            } catch (ex: RestClientException) {
                val code = failureCode(ex)
                if (code == ErrorCode.KYC_CASE_NOT_PENDING) return "redirect:/kyc"
                return wizard(party, step, photos, returnTo, toMessage(code), model)
            }
        }

        val done = photos.keys + imageType
        val next = if (returnTo == RETURN_TO_REVIEW) STEP_REVIEW else STEPS[STEPS.indexOf(step) + 1]
        return redirect(minOf(next, KycRules.firstIncompleteStep(done), compareBy { STEPS.indexOf(it) }))
    }

    @PostMapping("/$STEP_REVIEW")
    fun submit(session: HttpSession, model: Model): String {
        val party = currentParty.get() ?: return SIGN_OUT
        val case = kycClient.current(party.id)
        if (case?.status != KycStatus.PENDING) return "redirect:/kyc"

        val photos = KycRules.photos(case)
        val firstIncomplete = KycRules.firstIncompleteStep(photos.keys)
        if (firstIncomplete != STEP_REVIEW) return redirect(firstIncomplete)

        try {
            kycClient.submit(case.id)
        } catch (ex: RestClientException) {
            val code = failureCode(ex)
            if (code == ErrorCode.KYC_CASE_NOT_PENDING) return "redirect:/kyc"
            return wizard(party, STEP_REVIEW, photos, null, toMessage(code), model)
        }

        session.setAttribute(SubmittedKyc.SESSION_ATTRIBUTE, SubmittedKyc(caseId = case.id))
        return redirect(STEP_CONFIRMATION)
    }

    /** "Soumettre de nouveaux documents" on a rejected case: a new case, and the wizard starts over empty. */
    @PostMapping("/restart")
    fun restart(): String {
        val party = currentParty.get() ?: return SIGN_OUT
        if (kycClient.current(party.id)?.status == KycStatus.REJECTED) {
            kycClient.create(party.id)
        }
        return "redirect:/kyc"
    }

    /**
     * The PENDING case photos go to: the current one, or a new one when the agent has none yet (or it was
     * cancelled). Null once the current case has been submitted, since its photos can't change any more.
     */
    private fun openCase(partyId: Long): KycCase? {
        val case = kycClient.current(partyId)
        return when (case?.status) {
            KycStatus.PENDING -> case
            null, KycStatus.CANCELLED -> kycClient.get(kycClient.create(partyId))
            else -> null
        }
    }

    private fun toMessage(code: String?): String = when (code) {
        ErrorCode.KYC_IMAGE_INVALID_MIME_TYPE -> "Format non accepté. Utilisez une photo JPG, PNG ou WEBP."
        ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE ->
            "Il manque une ou plusieurs photos. Ajoutez-les pour pouvoir envoyer votre dossier."

        else -> "Envoi impossible pour le moment. Réessayez dans quelques instants."
    }

    private fun status(party: Party, status: String, case: KycCase, model: Model): String {
        header(party, model)
        model.addAttribute("view", "status")
        model.addAttribute("status", status)
        if (status == KycRules.STATUS_REJECTED) {
            model.addAttribute("rejectionReason", KycRules.rejectionReason(case.errorCode))
        }
        return VIEW
    }

    private fun wizard(
        party: Party,
        step: String,
        photos: Map<KycImageType, KycImage>,
        returnTo: String?,
        error: String?,
        model: Model,
    ): String {
        header(party, model)
        model.addAttribute("view", "wizard")
        model.addAttribute("step", step)
        model.addAttribute("stepNumber", STEPS.indexOf(step) + 1)
        model.addAttribute("stepCount", STEPS.size)
        model.addAttribute("error", error)
        // "Revenir à la revue" instead of "Suivant": only once every photo is in.
        model.addAttribute(
            "returning",
            returnTo == RETURN_TO_REVIEW && step != STEP_REVIEW && KycRules.firstIncompleteStep(photos.keys) == STEP_REVIEW,
        )

        // Photos already in the case: an upload step previews its own, the review shows all three.
        val shown = if (step == STEP_REVIEW) photos.values else listOfNotNull(photos[PHOTOS[step]])
        // A photo whose URL can't be had is left out (the page still works without its preview).
        model.addAttribute(
            "photoUrls",
            shown.mapNotNull { image -> imageUrl(image)?.let { url -> image.imageType.name to url } }.toMap(),
        )
        return VIEW
    }

    private fun header(party: Party, model: Model) {
        model.addAttribute("email", party.email)
        model.addAttribute("initials", KycRules.initials(party.firstName, party.lastName))
    }

    private fun redirect(step: String): String =
        if (step == STEP_FRONT) "redirect:/kyc" else "redirect:/kyc?step=$step"

    private fun imageUrl(image: KycImage): String? =
        try {
            kycClient.imageUrl(image.id)
        } catch (ex: RestClientException) {
            LOGGER.warn("No URL for KYC image ${image.id}: ${ex.message}")
            null
        }

    /** The server's error code, if it sent one; null for a 5xx or an unreachable server (logged with its stack). */
    private fun failureCode(ex: RestClientException): String? {
        val code = (ex as? HttpClientErrorException)?.let { errorCode(it) }
        if (code == ErrorCode.KYC_CASE_NOT_PENDING) {
            LOGGER.warn("KYC case no longer pending: ${ex.message}")
        } else {
            LOGGER.error("Server error: ${ex.message}", ex)
        }
        return code
    }

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }
}
