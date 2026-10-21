package com.wutsi.ndopify.web.ui.layout

import com.wutsi.ndopify.refdata.dto.Tenant

/** Links to the tenant's Partner Central, where the site's calls to action go. */
object PartnerCentralUrls {
    /** `{partnerCentralUrl}{path}`, or null when the tenant has no `partnerCentralUrl`. */
    fun of(tenant: Tenant, path: String): String? =
        tenant.partnerCentralUrl
            ?.takeIf { url -> url.isNotBlank() }
            ?.let { url -> url.trimEnd('/') + path }
}
