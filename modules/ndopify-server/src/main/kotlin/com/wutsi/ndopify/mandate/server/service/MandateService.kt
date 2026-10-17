package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.CreateContractParty
import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.customer.dto.CreateCustomerRequest
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.SearchMandateRequest
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.MandateCreatedEvent
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.apache.commons.lang3.time.DateUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class MandateService(
    private val dao: MandateRepository,
    private val clock: Clock,
    private val customerService: CustomerService,
    private val eventPublisher: ApplicationEventPublisher,
    private val contractService: ContractService,
    private val dispatcher: MandateDispatcher,

    @param:Value("\${ndopify.mandate.contract.duration-days}") private val contractDurationDays: Int,
) {
    fun search(request: SearchMandateRequest): List<MandateEntity> {
        val spec = Specification<MandateEntity> { root, query, cb ->
            query.distinct(true)

            val predicates = mutableListOf<Predicate>()

            if (request.ids.isNotEmpty()) {
                predicates.add(root.get<Long>("id").`in`(request.ids))
            }
            if (request.agentIds.isNotEmpty()) {
                predicates.add(root.get<Long>("agentId").`in`(request.agentIds))
            }
            if (request.customerIds.isNotEmpty()) {
                predicates.add(root.get<Long>("customerId").`in`(request.customerIds))
            }
            if (request.cityIds.isNotEmpty()) {
                predicates.add(root.get<Long>("cityId").`in`(request.cityIds))
            }
            if (request.neighborhoodIds.isNotEmpty()) {
                predicates.add(root.join<MandateEntity, Long>("neighborhoodIds").`in`(request.neighborhoodIds))
            }
            request.mandateType?.let { mandateType ->
                predicates.add(cb.equal(root.get<Any>("mandateType"), mandateType))
            }
            request.listingType?.let { listingType ->
                predicates.add(cb.equal(root.get<Any>("listingType"), listingType))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<Any>("status").`in`(request.statuses))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, Sort.by("id"))).content
    }

    fun findByIdOrNull(id: Long): MandateEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findById(id: Long): MandateEntity {
        return findByIdOrNull(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.MANDATE_NOT_FOUND))
    }

    @Transactional
    fun create(request: CreateMandateRequest): MandateEntity {
        val customer = customerService.findOrCreate(
            CreateCustomerRequest(
                firstName = request.firstName,
                lastName = request.lastName,
                email = request.email,
                whatsappNumber = request.whatsappNumber,
            )
        )

        val now = Date(clock.millis())
        val mandate = dao.save(
            MandateEntity(
                customerId = customer.id ?: -1,
                status = MandateStatus.PENDING_ASSIGNMENT,
                propertyCategory = request.propertyCategory,
                mandateType = request.mandateType,
                listingType = request.listingType,
                cityId = request.cityId,
                neighborhoodIds = request.neighbourhoodIds,
                propertyType = request.propertyType,
                furnitureType = request.furnitureType,
                rooms = request.rooms,
                parkings = request.parkings,
                area = request.area,
                waterMeter = request.waterMeter,
                dedicatedWaterMeter = request.dedicatedWaterMeter,
                waterTank = request.waterTank,
                waterWell = request.waterWell,
                electricityMeter = request.electricityMeter,
                dedicatedElectricityMeter = request.dedicatedElectricityMeter,
                generatorSet = request.generatorSet,
                solarPanel = request.solarPanel,
                roadPaved = request.roadPaved,
                trackAccessibleByCar = request.trackAccessibleByCar,
                trackInaccessibleByCar = request.trackInaccessibleByCar,
                securedFenced = request.securedFenced,
                securityGuard = request.securityGuard,
                videoSurveillance = request.videoSurveillance,
                price = request.price.amount,
                pricePeriodicityType = request.price.periodicity,
                currencyCode = request.price.currency,
                securityDeposit = request.securityDeposit,
                advanceRent = request.advanceRent,
                leaseTerm = request.leaseTerm,
                noticePeriod = request.noticePeriod,
                movingPeriod = request.movingPeriod,
                preferredContactPeriod = request.preferredContactPeriod,
                comments = request.comments,
                createdAt = now,
                modifiedAt = now,
            )
        )

        // Offered to an agent after commit, by a listener of this event
        eventPublisher.publishEvent(MandateCreatedEvent(mandateId = mandate.id!!, tenantId = TenantContext.get()!!))
        return mandate
    }

    /**
     * Dispatches the mandate now. Returns the PENDING assignment, or null if nothing was offered. Not @Transactional:
     * [MandateDispatcher.dispatch] is, on another bean, so its proxy applies.
     */
    fun dispatch(id: Long): AssignmentEntity? {
        findById(id) // 404 MANDATE_NOT_FOUND, rather than a silent null
        return dispatcher.dispatch(id)
    }

    @Transactional
    fun update(id: Long, request: UpdateMandateRequest): MandateEntity {
        val mandate = findById(id)
        return dao.save(
            mandate.copy(
                propertyCategory = request.propertyCategory,
                listingType = request.listingType,
                cityId = request.cityId,
                neighborhoodIds = request.neighbourhoodIds,
                propertyType = request.propertyType,
                furnitureType = request.furnitureType,
                rooms = request.rooms,
                parkings = request.parkings,
                area = request.area,
                waterMeter = request.waterMeter,
                dedicatedWaterMeter = request.dedicatedWaterMeter,
                waterTank = request.waterTank,
                waterWell = request.waterWell,
                electricityMeter = request.electricityMeter,
                dedicatedElectricityMeter = request.dedicatedElectricityMeter,
                generatorSet = request.generatorSet,
                solarPanel = request.solarPanel,
                roadPaved = request.roadPaved,
                trackAccessibleByCar = request.trackAccessibleByCar,
                trackInaccessibleByCar = request.trackInaccessibleByCar,
                securedFenced = request.securedFenced,
                securityGuard = request.securityGuard,
                videoSurveillance = request.videoSurveillance,
                price = request.price.amount,
                pricePeriodicityType = request.price.periodicity,
                currencyCode = request.price.currency,
                securityDeposit = request.securityDeposit,
                advanceRent = request.advanceRent,
                leaseTerm = request.leaseTerm,
                noticePeriod = request.noticePeriod,
                movingPeriod = request.movingPeriod,
                comments = request.comments,
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    @Transactional
    public fun requestApproval(id: Long): MandateEntity {
        // Mandate
        val mandate = findById(id)
        if (mandate.status != MandateStatus.ASSIGNED) {
            throw ConflictException(Error(ErrorCode.MANDATE_NOT_ASSIGNED))
        }

        // Create contract
        val contract = createContract(mandate)
        val updated = dao.save(
            mandate.copy(
                status = MandateStatus.PENDING_APPROVAL,
                contractId = contract.id,
                modifiedAt = Date(clock.millis()),
            )
        )

        // Request signature for approval
        contractService.requestSignature(contract.id)
        return updated
    }

    /**
     * Approves the PENDING_APPROVAL mandate of a contract that all its parties have signed. Does nothing for a contract
     * that isn't a mandate's, or a mandate no longer waiting for approval (ex: cancelled in the meantime).
     */
    @Transactional
    fun onContractSigned(contractId: String): MandateEntity? {
        val mandate = dao.findByContractId(contractId)
        if (mandate == null || mandate.status != MandateStatus.PENDING_APPROVAL) {
            return null
        }

        val now = Date(clock.millis())
        return dao.save(
            mandate.copy(
                status = MandateStatus.APPROVED,
                modifiedAt = now,
                approvedAt = now
            )
        )
    }

    private fun createContract(mandate: MandateEntity): ContractEntity {
        val now = Date(clock.millis())
        val customer = customerService.findById(mandate.customerId)
        return contractService.create(
            CreateContractRequest(
                type = when (mandate.mandateType) {
                    MandateType.SEARCH -> ContractType.SEARCH
                    MandateType.LISTING -> ContractType.LISTING
                    else -> throw IllegalArgumentException("Unsupported mandate type: ${mandate.mandateType}")
                },
                validFrom = now,
                validTo = DateUtils.addDays(now, contractDurationDays),
                parties = listOf(
                    CreateContractParty(
                        partyId = customer.party.id ?: -1,
                        legalName = customer.party.fullName(),
                        legalEmail = customer.party.email,
                        role = ContractPartyRole.MANDATOR,
                    )
                )
            )
        )
    }
}
