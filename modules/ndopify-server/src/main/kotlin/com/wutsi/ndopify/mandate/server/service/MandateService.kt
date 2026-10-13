package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.customer.dto.CreateCustomerRequest
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.SearchMandateRequest
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
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

        return dao.findAll(spec, Sort.by("id"))
            .drop(request.offset)
            .take(request.limit)
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
        return dao.save(
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
    }

    @Transactional
    fun update(id: Long, request: UpdateMandateRequest): MandateEntity {
        val mandate = findById(id)
        return dao.save(
            mandate.copy(
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
                comments = request.comments,
                modifiedAt = Date(clock.millis()),
            )
        )
    }
}
