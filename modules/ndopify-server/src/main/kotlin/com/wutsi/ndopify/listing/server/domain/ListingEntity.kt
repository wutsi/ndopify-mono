package com.wutsi.ndopify.listing.server.domain

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.util.jpa.StringListConverter
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.TenantId
import java.time.LocalDate
import java.util.Date

@Entity
@Table(name = "T_LISTING")
data class ListingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    // Optimistic locking: a write based on a stale read fails (409) instead of silently overwriting the change
    @Version
    val version: Long? = null,

    @OneToOne
    @JoinColumn(name = "mandate_id")
    val mandate: MandateEntity = MandateEntity(),

    @ManyToOne
    @JoinColumn(name = "agent_id")
    val agent: AgentEntity = AgentEntity(),

    val propertyCategory: PropertyCategory = PropertyCategory.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,
    val status: ListingStatus = ListingStatus.UNKNOWN,

    val cityId: Long = -1,
    val neighborhoodId: Long? = null,
    val landmark: String? = null,
    val accessDirections: String? = null,

    val longitude: Double? = null,
    val latitude: Double? = null,

    val propertyType: PropertyType? = null,
    val furnitureType: FurnitureType? = null,
    val rooms: Int? = null,
    val bathrooms: Int? = null,
    val livingRooms: Int? = null,
    val kitchens: Int? = null,
    val parkings: Int? = null,
    val area: Int? = null,
    val levels: Int? = null,
    val floorLevel: Int? = null,

    val waterMeter: Boolean = false,
    val dedicatedWaterMeter: Boolean = false,
    val waterTank: Boolean = false,
    val waterWell: Boolean = false,

    val electricityMeter: Boolean = false,
    val dedicatedElectricityMeter: Boolean = false,
    val generatorSet: Boolean = false,
    val solarPanel: Boolean = false,

    val roadPaved: Boolean = false,
    val trackAccessibleByCar: Boolean = false,
    val trackInaccessibleByCar: Boolean = false,

    val securedFenced: Boolean = false,
    val securityGuard: Boolean = false,
    val videoSurveillance: Boolean = false,

    val price: Long = -1,
    val currencyCode: String = "",
    val pricePeriodicityType: PeriodicityType = PeriodicityType.UNKNOWN,
    val securityDeposit: Int? = null,
    val advanceRent: Int? = null,
    val leaseTerm: Int? = null,
    val noticePeriod: Int? = null,

    val availableFrom: LocalDate? = null,

    val agentRemarks: String? = null,

    val title: String? = null,
    val summary: String? = null,
    val description: String? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
    val publishedAt: Date? = null,
    val closedAt: Date? = null,

    @Convert(StringListConverter::class)
    val errors: List<String> = emptyList()
)
