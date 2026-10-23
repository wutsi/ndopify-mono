package com.wutsi.ndopify.esignature.ui.layout

import com.wutsi.ndopify.esignature.service.security.AccessTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.client.HttpClientErrorException

/**
 * A 403 or 404 from ndopify-server that a page doesn't handle itself (ex: a contract that isn't the signer's, or gone)
 * is answered with the same status, so that Spring Boot renders `templates/error/403.html` / `404.html`: uncaught, it
 * would be a 500. Pages that know better catch it before it gets here.
 *
 * A 401 means that ndopify-server rejected the token (bad signature, expired meanwhile): this app doesn't check the
 * signature (see [com.wutsi.ndopify.esignature.service.security.JwtDecoder]), so the cookie is cleared and the signer
 * goes back to the page, signed out: the sign link's intro (its e-mail step), or the verify page, which needs no token.
 */
@ControllerAdvice
class HttpClientErrorHandler(private val accessTokenService: AccessTokenService) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(HttpClientErrorHandler::class.java)
        private val SIGN_PATH = Regex("^/sign/[^/]+")
        private const val VERIFY_PATH = "/verify/"
    }

    @ExceptionHandler(HttpClientErrorException.Forbidden::class, HttpClientErrorException.NotFound::class)
    fun clientError(request: HttpServletRequest, response: HttpServletResponse, ex: HttpClientErrorException) {
        LOGGER.warn("${request.method} ${request.requestURI}: ${ex.statusCode} from ndopify-server")
        response.sendError(ex.statusCode.value())
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized::class)
    fun unauthorized(request: HttpServletRequest): String {
        LOGGER.warn("${request.method} ${request.requestURI}: 401 from ndopify-server, signing out")
        accessTokenService.remove()
        val uri = request.requestURI
        val target = SIGN_PATH.find(uri)?.value
            ?: uri.takeIf { it.startsWith(VERIFY_PATH) }
            ?: "/error" // Nothing else calls ndopify-server (SecurityConfiguration denies every other path)
        return "redirect:$target"
    }
}
