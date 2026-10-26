package com.wutsi.ndopify.npc.ui.kyc

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * A photo over `spring.servlet.multipart.max-file-size` (or photos over `max-request-size`) is rejected before
 * [KycController], the profile photo page or a listing's photos page runs (no handler is known yet, so this advice
 * has to be global): send the agent back to the step, or to that page, with the size error.
 */
@ControllerAdvice
class KycUploadErrorHandler {
    companion object {
        private const val PROFILE_PHOTO_UPLOAD = "/profile/photo"
        private val KYC_UPLOAD = Regex("^/kyc/(${KycRules.PHOTOS.keys.joinToString("|")})$")
        private val LISTING_PHOTOS_UPLOAD = Regex("^/listings/(\\d+)/photos$")
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(request: HttpServletRequest, ex: MaxUploadSizeExceededException): String {
        if (request.requestURI == PROFILE_PHOTO_UPLOAD) return "redirect:$PROFILE_PHOTO_UPLOAD?error=too-large"
        LISTING_PHOTOS_UPLOAD.find(request.requestURI)?.let { match ->
            return "redirect:/listings/${match.groupValues[1]}/photos?error=too-large"
        }
        val step = KYC_UPLOAD.find(request.requestURI)?.groupValues?.get(1) ?: throw ex
        return "redirect:/kyc?step=$step&error=too-large"
    }
}
