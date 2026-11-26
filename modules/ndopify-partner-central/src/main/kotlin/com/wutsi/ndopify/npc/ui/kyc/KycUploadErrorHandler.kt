package com.wutsi.ndopify.npc.ui.kyc

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * A photo over `spring.servlet.multipart.max-file-size` (or photos over `max-request-size`) is rejected before
 * [KycController], the profile photo page, a listing's photos page or a mandate's documents run (no handler is known
 * yet, so this advice has to be global): send the agent back to the step, or to that page, with the size error — a
 * document's add form, or its page with the replaced page's error.
 */
@ControllerAdvice
class KycUploadErrorHandler {
    companion object {
        private const val PROFILE_PHOTO_UPLOAD = "/profile/photo"
        private val KYC_UPLOAD = Regex("^/kyc/(${KycRules.PHOTOS.keys.joinToString("|")})$")
        private val LISTING_PHOTOS_UPLOAD = Regex("^/listings/(\\d+)/photos$")
        private val MANDATE_DOCUMENTS_UPLOAD = Regex("^/mandates/(\\d+)/documents$")
        private val MANDATE_DOCUMENT_PAGE_UPLOAD = Regex("^/mandates/(\\d+)/documents/(\\d+)/pages/(\\d+)$")
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(request: HttpServletRequest, ex: MaxUploadSizeExceededException): String {
        if (request.requestURI == PROFILE_PHOTO_UPLOAD) return "redirect:$PROFILE_PHOTO_UPLOAD?error=too-large"
        LISTING_PHOTOS_UPLOAD.find(request.requestURI)?.let { match ->
            return "redirect:/listings/${match.groupValues[1]}/photos?error=too-large"
        }
        MANDATE_DOCUMENTS_UPLOAD.find(request.requestURI)?.let { match ->
            return "redirect:/mandates/${match.groupValues[1]}/documents/new?error=too-large"
        }
        MANDATE_DOCUMENT_PAGE_UPLOAD.find(request.requestURI)?.let { match ->
            return "redirect:/mandates/${match.groupValues[1]}/documents/${match.groupValues[2]}" +
                "?replaceError=size&page=${match.groupValues[3]}"
        }
        val step = KYC_UPLOAD.find(request.requestURI)?.groupValues?.get(1) ?: throw ex
        return "redirect:/kyc?step=$step&error=too-large"
    }
}
