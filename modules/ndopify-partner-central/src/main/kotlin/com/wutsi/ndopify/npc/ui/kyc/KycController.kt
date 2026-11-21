package com.wutsi.ndopify.npc.ui.kyc

import com.wutsi.ndopify.common.dto.AttachUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.CurrentParty
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.kyc.KycRules.PHOTOS
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEPS
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEP_CONFIRMATION
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEP_FRONT
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEP_REVIEW
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import jakarta.servlet.http.HttpSession
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.multipart.MultipartFile

/**
 * The agent's identity verification, on a single page (`kyc.html`), in three views:
 * - wizard: four steps, [STEPS] (front → back → selfie → review). `GET /kyc?step=…` shows a step. Each upload
 *   step posts its photo to `POST /kyc/{step}`, which adds it straight to the agent's PENDING_VERIFICATION case on
 *   ndopify-server, so the case is the wizard's state and the session holds no photos. With JS, `kyc.js` first sends
 *   the photo straight to the storage (`POST /kyc/{step}/presign`, PUT, `POST /kyc/{step}/attach`), then posts the
 *   step without it. `POST /kyc/review` submits the case.
 * - confirmation: `?step=confirmation`, right after submitting (while [SubmittedKyc] is in the session).
 * - status: once the case is no longer PENDING_VERIFICATION (pending / verified / rejected), whatever the URL says. A rejected
 *   case can be restarted (`POST /kyc/restart`), which opens a new case.
 *
 * The URL is never trusted: a step is only shown once the case has every photo before it.
 */
@Controller
@RequestMapping("/kyc")
class KycController(
    private val kycClient: NdKycClient,
    private val currentParty: CurrentParty,
    private val currentAgent: CurrentAgent,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(KycController::class.java)
        private val TIKA = Tika()

        const val VIEW = "kyc"
        const val RETURN_TO_REVIEW = "review"
        const val ERROR_TOO_LARGE = "too-large"
    }

    @GetMapping
    fun show(
        @RequestParam(name = "step", required = false) requestedStep: String?,
        @RequestParam(required = false) returnTo: String?,
        @RequestParam(name = "error", required = false) uploadError: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val party = currentParty.get() ?: throw SignInRequiredException()
        val case = kycClient.current(party.id)
        val status = KycRules.status(case)

        val submitted = session.getAttribute(SubmittedKyc.SESSION_ATTRIBUTE) as SubmittedKyc?
        if (requestedStep == STEP_CONFIRMATION && status != null && submitted?.caseId == case?.id) {
            header(model)
            model.addAttribute("view", "confirmation")
            return VIEW
        }
        if (status != null) {
            return status(status, case!!, model)
        }

        val photos = KycRules.photos(case)
        val requested = STEPS.indexOf(requestedStep).coerceAtLeast(0)
        val allowed = STEPS.indexOf(KycRules.firstIncompleteStep(photos.keys))
        val shown = STEPS[minOf(requested, allowed)]
        val error =
            if (uploadError == ERROR_TOO_LARGE && shown == requestedStep) "Cette photo dépasse la taille maximale de 5 Mo." else null
        return wizard(shown, photos, returnTo, error, model)
    }

    @PostMapping("/{step:front|back|selfie}")
    fun upload(
        @PathVariable step: String,
        @RequestParam(required = false) photo: MultipartFile?,
        @RequestParam(required = false) returnTo: String?,
        model: Model,
    ): String {
        val party = currentParty.get() ?: throw SignInRequiredException()
        val case = openCase(party.id) ?: return "redirect:/kyc"
        val photos = KycRules.photos(case)
        val firstIncomplete = KycRules.firstIncompleteStep(photos.keys)
        if (!KycRules.stepAllowed(step, photos.keys)) return redirect(firstIncomplete)

        val imageType = PHOTOS.getValue(step)
        if (photo == null || photo.isEmpty) {
            // "Suivant" on a step whose photo is already in the case just moves on.
            if (imageType !in photos) {
                return wizard(step, photos, returnTo, "Ajoutez une photo pour continuer.", model)
            }
        } else {
            val content = photo.bytes
            // The browser's content type is a claim; the bytes are the truth.
            val mimeType = TIKA.detect(content)
            KycRules.photo(photo.size, mimeType)
                ?.let { error -> return wizard(step, photos, returnTo, error, model) }
            try {
                kycClient.upload(case.id, imageType, content, mimeType)
            } catch (ex: RestClientException) {
                val code = failureCode(ex)
                if (code == ErrorCode.KYC_CASE_NOT_PENDING) return "redirect:/kyc"
                return wizard(step, photos, returnTo, toMessage(code), model)
            }
        }

        val done = photos.keys + imageType
        val next = if (returnTo == RETURN_TO_REVIEW) STEP_REVIEW else STEPS[STEPS.indexOf(step) + 1]
        return redirect(minOf(next, KycRules.firstIncompleteStep(done), compareBy { STEPS.indexOf(it) }))
    }

    /**
     * A short-lived URL to upload the step's photo straight to the storage, for `kyc.js`. Like the form post, a case is
     * opened when the agent has none (or a cancelled one); 409 once it is submitted or when the step is ahead of the
     * first missing photo, as the form post would redirect; else the storage's answer (no url with local storage), or
     * ndopify-server's status when it refuses (a 401 signs the agent out), 502 when it fails.
     */
    @PostMapping("/{step:front|back|selfie}/presign", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun presign(
        @PathVariable step: String,
        @RequestBody request: PresignUploadRequest,
    ): ResponseEntity<PresignUploadResponse> =
        proxy("Presign", step) { case ->
            ResponseEntity.ok(
                kycClient.presign(case.id, NdKycClient.IDENTIFICATION_TYPE, PHOTOS.getValue(step), request.contentType.orEmpty()),
            )
        }

    /**
     * Attaches the step's photo uploaded straight to the storage, replacing the case's one, for `kyc.js`. Same case and 409s
     * as [presign], else 200, or ndopify-server's status when it refuses the file: `kyc.js` then posts it to [upload].
     */
    @PostMapping("/{step:front|back|selfie}/attach", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun attach(
        @PathVariable step: String,
        @RequestBody request: AttachUploadRequest,
    ): ResponseEntity<Void> =
        proxy("Attach", step) { case ->
            kycClient.attach(case.id, NdKycClient.IDENTIFICATION_TYPE, PHOTOS.getValue(step), request.path)
            ResponseEntity.ok().build()
        }

    /**
     * The presign/attach proxies' frame: the signed-in party's open case (opened as the form post does), 409 when it is
     * submitted or the step is ahead; ndopify-server's status when it refuses, a 401 rethrown (HttpClientErrorHandler
     * signs the agent out), 502 when it fails or can't be reached — the party and the case lookups included, so
     * `fetch` always gets a status, never an HTML error page.
     */
    private fun <T : Any> proxy(operation: String, step: String, call: (KycCase) -> ResponseEntity<T>): ResponseEntity<T> {
        var partyId: Long? = null
        return try {
            val party = currentParty.get() ?: throw SignInRequiredException()
            partyId = party.id
            val case = openCase(party.id) ?: return ResponseEntity.status(HttpStatus.CONFLICT).build()
            if (!KycRules.stepAllowed(step, KycRules.photos(case).keys)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build()
            }
            call(case)
        } catch (ex: HttpClientErrorException.Unauthorized) {
            throw ex
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("$operation of a KYC photo of Party#${partyId ?: "-"} refused: ${ex.statusCode}")
            ResponseEntity.status(ex.statusCode).build()
        } catch (ex: RestClientException) {
            LOGGER.error("$operation of a KYC photo of Party#${partyId ?: "-"} failed", ex)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
        }
    }

    @PostMapping("/$STEP_REVIEW")
    fun submit(session: HttpSession, model: Model): String {
        val party = currentParty.get() ?: throw SignInRequiredException()
        val case = kycClient.current(party.id)
        if (case?.status != KycStatus.PENDING_VERIFICATION) return "redirect:/kyc"

        val photos = KycRules.photos(case)
        val firstIncomplete = KycRules.firstIncompleteStep(photos.keys)
        if (firstIncomplete != STEP_REVIEW) return redirect(firstIncomplete)

        try {
            kycClient.submit(case.id)
        } catch (ex: RestClientException) {
            val code = failureCode(ex)
            if (code == ErrorCode.KYC_CASE_NOT_PENDING) return "redirect:/kyc"
            return wizard(STEP_REVIEW, photos, null, toMessage(code), model)
        }

        session.setAttribute(SubmittedKyc.SESSION_ATTRIBUTE, SubmittedKyc(caseId = case.id))
        return redirect(STEP_CONFIRMATION)
    }

    /** "Soumettre de nouveaux documents" on a rejected case: a new case, and the wizard starts over empty. */
    @PostMapping("/restart")
    fun restart(): String {
        val party = currentParty.get() ?: throw SignInRequiredException()
        if (kycClient.current(party.id)?.status == KycStatus.REJECTED) {
            kycClient.create(party.id)
        }
        return "redirect:/kyc"
    }

    /**
     * The PENDING_VERIFICATION case photos go to: the current one, or a new one when the agent has none yet (or it was
     * cancelled). Null once the current case has been submitted, since its photos can't change any more.
     */
    private fun openCase(partyId: Long): KycCase? {
        val case = kycClient.current(partyId)
        return when (case?.status) {
            KycStatus.PENDING_VERIFICATION -> case
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

    private fun status(status: String, case: KycCase, model: Model): String {
        header(model)
        model.addAttribute("view", "status")
        model.addAttribute("status", status)
        if (status == KycRules.STATUS_REJECTED) {
            model.addAttribute("rejectionReason", KycRules.rejectionReason(case.errorCode))
        }
        return VIEW
    }

    private fun wizard(
        step: String,
        photos: Map<KycImageType, KycImage>,
        returnTo: String?,
        error: String?,
        model: Model,
    ): String {
        header(model)
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

    /** The account menu: the signed-in agent is read again, for its id, name and e-mail (KYC only knows the party). */
    private fun header(model: Model) {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
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
