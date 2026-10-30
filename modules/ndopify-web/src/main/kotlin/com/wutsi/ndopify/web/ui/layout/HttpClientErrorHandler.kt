package com.wutsi.ndopify.web.ui.layout

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.client.HttpClientErrorException

/**
 * A 403 or 404 from ndopify-server (ex: a listing that isn't PUBLISHED anymore) is answered with the same status, so
 * that Spring Boot renders `templates/error/404.html`: uncaught, it would be a 500. No sign-in on this site, so no 401
 * handling, unlike Partner Central's.
 */
@ControllerAdvice
class HttpClientErrorHandler {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(HttpClientErrorHandler::class.java)
    }

    @ExceptionHandler(HttpClientErrorException.Forbidden::class, HttpClientErrorException.NotFound::class)
    fun clientError(request: HttpServletRequest, response: HttpServletResponse, ex: HttpClientErrorException) {
        LOGGER.warn("${request.method} ${request.requestURI}: ${ex.statusCode} from ndopify-server")
        response.sendError(ex.statusCode.value())
    }
}
