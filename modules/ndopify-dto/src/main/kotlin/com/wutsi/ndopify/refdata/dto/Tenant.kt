package com.wutsi.ndopify.refdata.dto

import java.util.Date

data class Tenant(
    val id: Long = -1,
    val name: String = "",
    val domainName: String = "",
    val countryCode: String = "",
    val currencyCode: String = "",
    val locales: List<String> = emptyList(),
    val currencySymbol: String = "",
    val numberFormat: String = "",
    val monetaryFormat: String = "",
    val dateTimeFormat: String = "",
    val dateFormat: String = "",
    val timeFormat: String = "",
    val iconUrl: String? = null,
    val logoUrl: String? = null,
    val active: Boolean = true,
    val adminConsoleUrl: String? = null,
    val partnerCentralUrl: String? = null,
    val publicPortalUrl: String? = null,
    val createdAt: Date = Date(),
)
