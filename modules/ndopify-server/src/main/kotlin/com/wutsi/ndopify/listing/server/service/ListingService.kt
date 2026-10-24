package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.CreateListingRequest
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.SearchListingRequest
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.Date

/** No access check: the listings are public, and any caller with a token may create or update one. */
@Service
class ListingService(
    private val dao: ListingRepository,
    private val mandateService: MandateService,
    private val agentService: AgentService,
    private val clock: Clock,
) {
    /** Newest first. */
    fun search(request: SearchListingRequest): List<ListingEntity> {
        val spec = Specification<ListingEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            request.mandateId?.let { mandateId ->
                predicates.add(cb.equal(root.get<MandateEntity>("mandate").get<Long>("id"), mandateId))
            }
            request.agentId?.let { agentId ->
                predicates.add(cb.equal(root.get<Any>("agent").get<Long>("id"), agentId))
            }
            request.propertyCategory?.let { propertyCategory ->
                predicates.add(cb.equal(root.get<Any>("propertyCategory"), propertyCategory))
            }
            request.listingType?.let { listingType ->
                predicates.add(cb.equal(root.get<Any>("listingType"), listingType))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<Any>("status").`in`(request.statuses))
            }
            if (request.propertyTypes.isNotEmpty()) {
                predicates.add(root.get<Any>("propertyType").`in`(request.propertyTypes))
            }
            request.cityId?.let { cityId ->
                predicates.add(cb.equal(root.get<Long>("cityId"), cityId))
            }
            if (request.neighborhoodIds.isNotEmpty()) {
                predicates.add(root.get<Long>("neighborhoodId").`in`(request.neighborhoodIds))
            }
            request.furnitureType?.let { furnitureType ->
                predicates.add(cb.equal(root.get<Any>("furnitureType"), furnitureType))
            }
            request.minPrice?.let { minPrice ->
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice))
            }
            request.maxPrice?.let { maxPrice ->
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice))
            }
            request.availableFrom?.let { date ->
                // Available by that date: from then or earlier, or without a date
                val availableFrom = root.get<LocalDate>("availableFrom")
                predicates.add(cb.or(cb.isNull(availableFrom), cb.lessThanOrEqualTo(availableFrom, date)))
            }
            request.rooms?.trim()?.ifEmpty { null }?.let { rooms ->
                // "2": exactly 2 rooms, "2+": 2 or more. Anything else is ignored
                val count = count(rooms)
                if (count != null) {
                    predicates.add(
                        if (rooms.endsWith("+")) {
                            cb.greaterThanOrEqualTo(root.get("rooms"), count)
                        } else {
                            cb.equal(root.get<Int>("rooms"), count)
                        }
                    )
                }
            }

            cb.and(*predicates.toTypedArray())
        }

        val sort = Sort.by(Sort.Direction.DESC, "id")
        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, sort)).content
    }

    fun findById(id: Long): ListingEntity {
        return dao.findById(id).orElse(null)
            ?: throw NotFoundException(error = Error(code = ErrorCode.LISTING_NOT_FOUND))
    }

    fun findIdByMandateId(mandateId: Long): Long? = dao.findIdByMandateId(mandateId)

    /**
     * Creates the DRAFT listing of a mandate, from its property: 404 MANDATE_NOT_FOUND, 409 MANDATE_NOT_ASSIGNED for a
     * mandate without agent (the listing's agent is the mandate's), 409 LISTING_ALREADY_EXISTS if it already has one.
     */
    @Transactional
    fun create(request: CreateListingRequest): ListingEntity {
        val mandate = mandateService.findById(request.mandateId)
        val agentId = mandate.agentId
            ?: throw ConflictException(Error(ErrorCode.MANDATE_NOT_ASSIGNED))
        if (dao.existsByMandate(mandate)) {
            throw ConflictException(Error(ErrorCode.LISTING_ALREADY_EXISTS))
        }

        val now = Date(clock.millis())
        return dao.save(
            ListingEntity(
                mandate = mandate,
                agent = agentService.findById(agentId),
                status = ListingStatus.DRAFT,
                propertyCategory = mandate.propertyCategory,
                listingType = mandate.listingType,
                cityId = mandate.cityId,
                neighborhoodId = mandate.neighborhoodIds.firstOrNull() ?: -1,
                propertyType = mandate.propertyType,
                furnitureType = mandate.furnitureType,
                rooms = count(mandate.rooms),
                parkings = count(mandate.parkings),
                area = mandate.area,
                waterMeter = mandate.waterMeter,
                dedicatedWaterMeter = mandate.dedicatedWaterMeter,
                waterTank = mandate.waterTank,
                waterWell = mandate.waterWell,
                electricityMeter = mandate.electricityMeter,
                dedicatedElectricityMeter = mandate.dedicatedElectricityMeter,
                generatorSet = mandate.generatorSet,
                solarPanel = mandate.solarPanel,
                roadPaved = mandate.roadPaved,
                trackAccessibleByCar = mandate.trackAccessibleByCar,
                trackInaccessibleByCar = mandate.trackInaccessibleByCar,
                securedFenced = mandate.securedFenced,
                securityGuard = mandate.securityGuard,
                videoSurveillance = mandate.videoSurveillance,
                price = mandate.price,
                currencyCode = mandate.currencyCode,
                pricePeriodicityType = mandate.pricePeriodicityType,
                securityDeposit = mandate.securityDeposit,
                advanceRent = mandate.advanceRent,
                leaseTerm = mandate.leaseTerm,
                noticePeriod = mandate.noticePeriod,
                availableFrom = mandate.availableFrom,
                createdAt = now,
                modifiedAt = now,
            )
        )
    }

    /**
     * Updates the listing's property and terms, whatever its status. With a [UpdateListingRequest.version], it must be
     * the listing's (409 LISTING_VERSION_CONFLICT otherwise).
     */
    @Transactional
    fun update(id: Long, request: UpdateListingRequest): ListingEntity {
        val listing = findById(id)
        if (request.version != null && request.version != listing.version) {
            throw ConflictException(Error(ErrorCode.LISTING_VERSION_CONFLICT))
        }
        return dao.save(
            listing.copy(
                propertyCategory = request.propertyCategory,
                propertyType = request.propertyType,
                cityId = request.cityId,
                neighborhoodId = request.neighborhoodId,
                landmark = request.landmark,
                accessDirections = request.accessDirections,
                longitude = request.longitude,
                latitude = request.latitude,
                furnitureType = request.furnitureType,
                rooms = request.rooms,
                bathrooms = request.bathrooms,
                livingRooms = request.livingRooms,
                kitchens = request.kitchens,
                parkings = request.parkings,
                area = request.area,
                levels = request.levels,
                floorLevel = request.floorLevel,
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
                price = request.price,
                currencyCode = request.currencyCode,
                pricePeriodicityType = request.pricePeriodicityType,
                securityDeposit = request.securityDeposit,
                advanceRent = request.advanceRent,
                leaseTerm = request.leaseTerm,
                noticePeriod = request.noticePeriod,
                availableFrom = request.availableFrom,
                agentRemarks = request.agentRemarks,
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    // A mandate's count ("2", or "2+" for 2 or more) as a number: 2
    private fun count(value: String?): Int? = value?.trim()?.removeSuffix("+")?.toIntOrNull()
}
