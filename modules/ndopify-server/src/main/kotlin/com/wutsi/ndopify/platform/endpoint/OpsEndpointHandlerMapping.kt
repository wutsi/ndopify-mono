package com.wutsi.ndopify.platform.endpoint

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.Method

/** Doesn't map the [OpsEndpoint] methods when the operational endpoints are disabled. */
class OpsEndpointHandlerMapping(
    private val opsEnabled: Boolean,
) : RequestMappingHandlerMapping() {
    public override fun getMappingForMethod(method: Method, handlerType: Class<*>): RequestMappingInfo? {
        if (!opsEnabled && AnnotatedElementUtils.hasAnnotation(method, OpsEndpoint::class.java)) {
            return null
        }
        return super.getMappingForMethod(method, handlerType)
    }
}
