package com.wutsi.ndopify.mandate.server.domain

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyType
import com.wutsi.koki.listing.dto.RoadAccessType
import com.wutsi.ndopify.listing.dto.ElectricityAccessType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.WaterAccessType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.platform.util.jpa.EnumListConverter
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId

@Entity
@Table(name = "T_MANDATE")
data class MandateEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    val customerId: Long? = null,
    val agentId: Long? = null,

    val status: MandateStatus = MandateStatus.UNKNOWN,
    val mandateType: MandateType = MandateType.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,

    val cityId: Long = -1,
    val neighbourhoodIds: List<Long> = emptyList(),

    @Convert(EnumListConverter::class)
    val propertyTypes: List<PropertyType> = emptyList(),

    val furnitureType: FurnitureType? = null,
    val rooms: String? = null,
    val parkings: String? = null,
    val minArea: Int? = null,
    val maxArea: Int? = null,

    val waterAccessType: WaterAccessType? = null,
    val waterTank: Boolean = false,
    val waterWell: Boolean = false,

    val electricityAccessType: ElectricityAccessType? = null,
    val generatorSet: Boolean = false,
    val solarPanel: Boolean = false,

    val roadAccessType: RoadAccessType? = null,

    val securedFenced: Boolean = false,
    val securityGuard: Boolean = false,
    val videoSurveillance: Boolean = false,

    val price: Long = -1,
    val currencyCode: String = "",
    val securityDeposit: Int? = null,
    val advanceRent: Int? = null,
    val leaseTerm: Int? = null,
    val noticePeriod: Int? = null,

    val comments: String? = null,
)
