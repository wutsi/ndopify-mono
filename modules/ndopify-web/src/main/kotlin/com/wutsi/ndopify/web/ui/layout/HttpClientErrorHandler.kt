package com.wutsi.ndopify.web.ui.layout

import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.client.HttpClientErrorException

/**
 * A 403 or 404 from ndopify-server (ex: a listing that isn't PUBLISHED anymore) is answered with the same status, so
 * that Spring Boot renders `templates/error/404.html`: uncaught, it would be a 500.
 *
 * A 401 means that ndopify-server rejected the token in the cookie (ex: secret rotation, clock skew): the token is sent
 * on every call while the cookie is present, so any page could get it. The cookie is cleared and the visitor goes back
 * to the same page, signed out. Without a cookie there is nothing to clear (and a redirect would loop): the 404 page.
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
    fun unauthorized(request: HttpServletRequest, response: HttpServletResponse): String? {
        LOGGER.warn("${request.method} ${request.requestURI}: 401 from ndopify-server")
        if (accessTokenService.get() == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return null
        }
        accessTokenService.remove()
        return "redirect:${request.requestURI}"
    }
}
