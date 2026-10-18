package com.wutsi.ndopify.npc.service

import com.wutsi.ndopify.refdata.dto.Tenant

/** A tenant formatted as ndopify-server's local seed (`V1_2_1__refdata-tenant.sql`). */
object TenantFixtures {
    val TENANT = Tenant(
        id = 1,
        countryCode = "CM",
        currencyCode = "XAF",
        currencySymbol = "FCFA",
        locales = listOf("fr-CM", "en-CM"),
        numberFormat = "#,###,##0.00",
        monetaryFormat = "#,###,##0 FCFA",
        dateFormat = "dd MMM yyyy",
        timeFormat = "HH:mm",
        timeZone = "Africa/Douala",
    )

    val FORMATTER = TenantFormatter(TENANT)
}
