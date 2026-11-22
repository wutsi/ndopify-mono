package com.wutsi.ndopify.refdata.server.domain

import com.wutsi.ndopify.util.jpa.StringListConverter
import jakarta.persistence.Column
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
    val timeZone: String = "Africa/Douala", // IANA id; same default as T_TENANT.time_zone
    val iconUrl: String? = null,

    @Column(name = "icon_192_url")
    val icon192Url: String? = null,

    @Column(name = "icon_512_url")
    val icon512Url: String? = null,

    @Column(name = "maskable_icon_url")
    val maskableIconUrl: String? = null,

    @Column(name = "apple_touch_icon_url")
    val appleTouchIconUrl: String? = null,

    val logoUrl: String? = null,
    val active: Boolean = true,
    val createdAt: Date = Date(),

    val adminConsoleUrl: String? = null,
    val partnerCentralUrl: String? = null,
    val publicPortalUrl: String? = null,
    val eSignaturePortalUrl: String? = null,
)
