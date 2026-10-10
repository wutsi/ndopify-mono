package com.wutsi.ndopify.refdata.server.mapper

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import org.springframework.stereotype.Service

@Service
class TenantMapper {
    fun toTenant(entity: TenantEntity): Tenant {
        return Tenant(
            id = entity.id ?: -1L,
            name = entity.name,
            domainName = entity.domainName,
            countryCode = entity.countryCode,
            locales = entity.locales,
            active = entity.active,
            currencyCode = entity.currencyCode,
            createdAt = entity.createdAt,
            dateFormat = entity.dateFormat,
            timeFormat = entity.timeFormat,
            currencySymbol = entity.currencySymbol,
            dateTimeFormat = entity.dateTimeFormat,
            monetaryFormat = entity.monetaryFormat,
            numberFormat = entity.numberFormat,
            logoUrl = entity.logoUrl,
            iconUrl = entity.iconUrl,
            adminConsoleUrl = entity.adminConsoleUrl,
            partnerCentralUrl = entity.partnerCentralUrl,
            publicPortalUrl = entity.publicPortalUrl,
        )
    }
}
