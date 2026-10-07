package com.wutsi.ndopify.refdata.server.domain

import com.wutsi.ndopify.util.jpa.StringListConverter
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "T_TENANT")
data class TenantEntity(
    @Id
    val id: Long? = null,

    val name: String = "",
    val domainName: String = "",
    val countryCode: String = "",
    val currencyCode: String = "",

    @Convert(converter = StringListConverter::class)
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
    val createdAt: Date = Date(),

    val adminConsoleUrl: String? = null,
    val partnerCentralUrl: String? = null,
    val publicPortalUrl: String? = null
)
