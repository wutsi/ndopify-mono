package com.wutsi.ndopify.esignature.ui.layout

import com.wutsi.ndopify.esignature.service.TenantContext
import com.wutsi.ndopify.refdata.dto.Tenant
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * Puts the current tenant in the model of every page as `tenant`, for the header's logo, incl. the error pages rendered
 * by Spring Boot's error controller. Null when the tenant can't be resolved (unknown URL, ndopify-server down): the
 * pages then fall back to Ndopify's logo, rather than failing, since an error page must always render.
 */
@ControllerAdvice
class TenantAdvice(private val tenantContext: TenantContext) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TenantAdvice::class.java)
    }

    @ModelAttribute("tenant")
    fun tenant(): Tenant? =
        try {
            tenantContext.getTenant()
        } catch (ex: Exception) {
            LOGGER.warn("Unable to resolve the tenant: ${ex.message}")
            null
        }
}
