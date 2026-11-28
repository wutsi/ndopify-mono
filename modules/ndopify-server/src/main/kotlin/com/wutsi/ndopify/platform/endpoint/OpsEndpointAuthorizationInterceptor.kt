package com.wutsi.ndopify.platform.endpoint

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.server.service.CallerService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * An [OpsEndpoint] is an admin's: an `admin-console` token with the `admin` role ([CallerService.isAdmin]). A
 * non-admin gets 403 `HTTP_ACCESS_DENIED` before the endpoint runs; a request without a token is a 401 from the
 * security filter, so [CallerService.getPrincipal] never throws here.
 */
class OpsEndpointAuthorizationInterceptor(private val callerService: CallerService) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler is HandlerMethod && AnnotatedElementUtils.hasAnnotation(handler.method, OpsEndpoint::class.java)) {
            if (!callerService.isAdmin(callerService.getPrincipal())) {
                throw ForbiddenException(Error(code = ErrorCode.HTTP_ACCESS_DENIED))
            }
        }
        return true
    }
}
