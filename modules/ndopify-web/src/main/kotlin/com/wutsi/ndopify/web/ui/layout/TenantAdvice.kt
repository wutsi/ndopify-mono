package com.wutsi.ndopify.web.ui.layout

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.service.TenantContext
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * Puts the current tenant in the model of every page as `tenant`, for the logo and icon.
 * Throws an [IllegalStateException] when no tenant matches the request URL (see [TenantContext.getTenant]).
 */
@ControllerAdvice
class TenantAdvice(private val tenantContext: TenantContext) {
    @ModelAttribute("tenant")
    fun tenant(): Tenant = tenantContext.getTenant()
}
