package com.wutsi.ndopify.util.jpa

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.party.dto.IdentificationStatus
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.dto.StorageType
import jakarta.persistence.Converter

// The enums of the entities, stored by name (see EnumConverter). Applied to every attribute of their type, so an
// entity's enum field needs no annotation: add one here for each new enum stored in an entity.

// agent
@Converter(autoApply = true)
class AgentTypeConverter : EnumConverter<AgentType>(AgentType::class.java, AgentType.UNKNOWN)

@Converter(autoApply = true)
class ExperienceLevelConverter : EnumConverter<ExperienceLevel>(ExperienceLevel::class.java, ExperienceLevel.UNKNOWN)

// contract
@Converter(autoApply = true)
class ContractPartyRoleConverter :
    EnumConverter<ContractPartyRole>(ContractPartyRole::class.java, ContractPartyRole.UNKNOWN)

@Converter(autoApply = true)
class ContractStatusConverter : EnumConverter<ContractStatus>(ContractStatus::class.java, ContractStatus.UNKNOWN)

@Converter(autoApply = true)
class ContractTypeConverter : EnumConverter<ContractType>(ContractType::class.java, ContractType.UNKNOWN)

// listing. PropertyType, FurnitureType and ContactPeriodType are optional (null when not set)
@Converter(autoApply = true)
class ContactPeriodTypeConverter : EnumConverter<ContactPeriodType>(ContactPeriodType::class.java, null)

@Converter(autoApply = true)
class FurnitureTypeConverter : EnumConverter<FurnitureType>(FurnitureType::class.java, null)

@Converter(autoApply = true)
class ListingTypeConverter : EnumConverter<ListingType>(ListingType::class.java, ListingType.UNKNOWN)

@Converter(autoApply = true)
class MovingPeriodConverter : EnumConverter<MovingPeriod>(MovingPeriod::class.java, MovingPeriod.UNKNOWN)

@Converter(autoApply = true)
class PropertyCategoryConverter :
    EnumConverter<PropertyCategory>(PropertyCategory::class.java, PropertyCategory.UNKNOWN)

@Converter(autoApply = true)
class PropertyTypeConverter : EnumConverter<PropertyType>(PropertyType::class.java, null)

// mandate
@Converter(autoApply = true)
class AssignmentStatusConverter :
    EnumConverter<AssignmentStatus>(AssignmentStatus::class.java, AssignmentStatus.UNKNOWN)

@Converter(autoApply = true)
class MandateStatusConverter : EnumConverter<MandateStatus>(MandateStatus::class.java, MandateStatus.UNKNOWN)

@Converter(autoApply = true)
class MandateTypeConverter : EnumConverter<MandateType>(MandateType::class.java, MandateType.UNKNOWN)

// party
@Converter(autoApply = true)
class IdentificationStatusConverter :
    EnumConverter<IdentificationStatus>(IdentificationStatus::class.java, IdentificationStatus.UNKNOWN)

@Converter(autoApply = true)
class IdentificationTypeConverter :
    EnumConverter<IdentificationType>(IdentificationType::class.java, IdentificationType.UNKNOWN)

@Converter(autoApply = true)
class KycImageTypeConverter : EnumConverter<KycImageType>(KycImageType::class.java, KycImageType.UNKNOWN)

@Converter(autoApply = true)
class KycStatusConverter : EnumConverter<KycStatus>(KycStatus::class.java, KycStatus.UNKNOWN)

@Converter(autoApply = true)
class KycVerificationTypeConverter :
    EnumConverter<KycVerificationType>(KycVerificationType::class.java, KycVerificationType.UNKNOWN)

@Converter(autoApply = true)
class PaymentMethodStatusConverter :
    EnumConverter<PaymentMethodStatus>(PaymentMethodStatus::class.java, PaymentMethodStatus.UNKNOWN)

@Converter(autoApply = true)
class PaymentMethodTypeConverter :
    EnumConverter<PaymentMethodType>(PaymentMethodType::class.java, PaymentMethodType.UNKNOWN)

// refdata
@Converter(autoApply = true)
class AuthTypeConverter : EnumConverter<AuthType>(AuthType::class.java, AuthType.UNKNOWN)

@Converter(autoApply = true)
class LocationTypeConverter : EnumConverter<LocationType>(LocationType::class.java, LocationType.UNKNOWN)

@Converter(autoApply = true)
class PeriodicityTypeConverter : EnumConverter<PeriodicityType>(PeriodicityType::class.java, PeriodicityType.UNKNOWN)

@Converter(autoApply = true)
class StorageTypeConverter : EnumConverter<StorageType>(StorageType::class.java, StorageType.UNKNOWN)
