package com.wutsi.ndopify.npc.ui.layout

import com.wutsi.ndopify.npc.service.security.AccessTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.client.HttpClientErrorException

/**
 * A 403 or 404 from ndopify-server that a page doesn't handle itself (ex: a contract that isn't the agent's, or gone)
 * is answered with the same status, so that Spring Boot renders `templates/error/403.html` / `404.html`: uncaught, it
 * would be a 500. Pages that know better (ex: [com.wutsi.ndopify.npc.ui.mandate.MandateController]'s locked view)
 * catch it before it gets here.
 *
 * A 401 means that ndopify-server rejected the token (bad signature, expired meanwhile): this app doesn't check the
 * signature (see [com.wutsi.ndopify.npc.service.security.JwtDecoder]), so the cookie is cleared and the agent signs in
 * again.
 */
@ControllerAdvice
class HttpClientErrorHandler(private val accessTokenService: AccessTokenService) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(HttpClientErrorHandler::class.java)
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
        return "redirect:/login"
    }
}
