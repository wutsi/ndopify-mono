package com.wutsi.ndopify.web.core.service

import com.wutsi.ndopify.refdata.dto.Tenant

/** The web app a [TenantContext] serves: which of the tenant's URLs the request URL must start with. */
enum class TenantPortal(private val url: (Tenant) -> String?) {
    PARTNER_CENTRAL({ it.partnerCentralUrl }),
    E_SIGNATURE({ it.eSignaturePortalUrl }),
    PUBLIC_PORTAL({ it.publicPortalUrl }),
    ;

    fun url(tenant: Tenant): String? = url.invoke(tenant)
}
