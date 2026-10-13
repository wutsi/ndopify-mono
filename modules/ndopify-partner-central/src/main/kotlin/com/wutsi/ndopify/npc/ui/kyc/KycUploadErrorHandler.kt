package com.wutsi.ndopify.npc.ui.kyc

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * A photo over `spring.servlet.multipart.max-file-size` is rejected before [KycController] runs (no handler is
 * known yet, so this advice has to be global): send the agent back to the step with the size error.
 */
@ControllerAdvice
class KycUploadErrorHandler {
    companion object {
        private val KYC_UPLOAD = Regex("^/kyc/(${KycRules.PHOTOS.keys.joinToString("|")})$")
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(request: HttpServletRequest, ex: MaxUploadSizeExceededException): String {
        val step = KYC_UPLOAD.find(request.requestURI)?.groupValues?.get(1) ?: throw ex
        return "redirect:/kyc?step=$step&error=too-large"
    }
}
