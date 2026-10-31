package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.CreateListingRequest
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.SearchListingRequest
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.mail.ListingPublishedMailet
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.Date

/** The changes are checked by [ListingAccessService] (the reads by `ListingEndpoints`): an admin, or the agent. */
@Service
class ListingService(
    private val dao: ListingRepository,
    private val mandateService: MandateService,
    private val agentService: AgentService,
    private val customerService: CustomerService,
    private val publishedMailet: ListingPublishedMailet,
    private val sectionService: ListingSectionService,
    private val locationService: LocationService,
    private val accessService: ListingAccessService,
    private val clock: Clock,
) {
    /** Newest first. */
    fun search(request: SearchListingRequest): List<ListingEntity> {
        val spec = Specification<ListingEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (request.mandateIds.isNotEmpty()) {
                predicates.add(root.get<MandateEntity>("mandate").get<Long>("id").`in`(request.mandateIds))
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

    /**
     * Creates the DRAFT listing of a mandate, from its property: 404 MANDATE_NOT_FOUND, 403 HTTP_ACCESS_DENIED but for
     * an admin or the mandate's agent, 409 MANDATE_NOT_ASSIGNED for a
     * mandate without agent (the listing's agent is the mandate's), 409 LISTING_ALREADY_EXISTS if it already has one.
     */
    @Transactional
    fun create(request: CreateListingRequest): ListingEntity {
        val mandate = mandateService.findById(request.mandateId)
        accessService.ensureCanCreate(mandate)
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
                // The mandate's neighbourhoods aren't checked: its first one of the city, if any
                neighborhoodId = mandate.neighborhoodIds.firstOrNull { id -> isNeighborhood(id, mandate.cityId) },
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
     * Saves one section of a DRAFT listing, which is then VERIFIED on its checklist: only the section's fields, as
     * sent (a null clears an optional field). 404 LISTING_NOT_FOUND, 403 HTTP_ACCESS_DENIED but for an admin or its
     * agent, 409 LISTING_VERSION_CONFLICT if [UpdateListingRequest.version] isn't
     * the listing's, 409 LISTING_NOT_DRAFT once sent; then 400 for no section, PHOTOS (uploaded, not saved), a
     * required field missing or a neighbourhood that isn't one of the listing's city. The city never changes: it's
     * the mandate's, the agent's area.
     */
    @Transactional
    fun update(id: Long, request: UpdateListingRequest): ListingEntity {
        val listing = findById(id)
        accessService.ensureCanUpdate(listing)
        if (request.version != listing.version) {
            throw ConflictException(Error(ErrorCode.LISTING_VERSION_CONFLICT))
        }
        if (listing.status != ListingStatus.DRAFT) {
            throw ConflictException(Error(ErrorCode.LISTING_NOT_DRAFT))
        }

        val updated = when (request.section) {
            ListingSection.LOCATION -> listing.copy(
                neighborhoodId = required(
                    "neighborhoodId",
                    request.neighborhoodId?.takeIf { id -> isNeighborhood(id, listing.cityId) }
                ),
                landmark = required("landmark", request.landmark?.trim()?.ifEmpty { null }),
                accessDirections = request.accessDirections?.trim()?.ifEmpty { null },
            )

            ListingSection.GPS -> listing.copy(
                latitude = required("latitude", request.latitude?.takeIf { it in -90.0..90.0 }),
                longitude = required("longitude", request.longitude?.takeIf { it in -180.0..180.0 }),
            )

            ListingSection.PROPERTY -> listing.copy(
                propertyCategory = required(
                    "propertyCategory",
                    request.propertyCategory?.takeIf { it != PropertyCategory.UNKNOWN }
                ),
                propertyType = required("propertyType", request.propertyType),
                furnitureType = request.furnitureType,
                rooms = request.rooms,
                bathrooms = request.bathrooms,
                livingRooms = request.livingRooms,
                kitchens = request.kitchens,
                parkings = request.parkings,
                area = required("area", request.area?.takeIf { it > 0 }),
                levels = request.levels,
                floorLevel = request.floorLevel,
            )

            ListingSection.AMENITIES -> {
                // The road access is one answer
                val roadAccess = listOf(request.roadPaved, request.trackAccessibleByCar, request.trackInaccessibleByCar)
                if (roadAccess.count { it == true } != 1) {
                    throw invalid("roadPaved")
                }
                listing.copy(
                    waterMeter = request.waterMeter == true,
                    dedicatedWaterMeter = request.dedicatedWaterMeter == true,
                    waterTank = request.waterTank == true,
                    waterWell = request.waterWell == true,
                    electricityMeter = request.electricityMeter == true,
                    dedicatedElectricityMeter = request.dedicatedElectricityMeter == true,
                    generatorSet = request.generatorSet == true,
                    solarPanel = request.solarPanel == true,
                    roadPaved = request.roadPaved == true,
                    trackAccessibleByCar = request.trackAccessibleByCar == true,
                    trackInaccessibleByCar = request.trackInaccessibleByCar == true,
                    securedFenced = request.securedFenced == true,
                    securityGuard = request.securityGuard == true,
                    videoSurveillance = request.videoSurveillance == true,
                )
            }

            ListingSection.TERMS -> listing.copy(
                price = required("price", request.price?.takeIf { it > 0 }),
                securityDeposit = required("securityDeposit", request.securityDeposit?.takeIf { it >= 0 }),
                advanceRent = request.advanceRent,
                leaseTerm = request.leaseTerm,
                noticePeriod = request.noticePeriod,
                availableFrom = required("availableFrom", request.availableFrom),
            )

            ListingSection.REMARKS -> listing.copy(
                agentRemarks = request.agentRemarks?.trim()?.ifEmpty { null },
            )

            ListingSection.PHOTOS, ListingSection.UNKNOWN -> throw invalid("section")
        }

        val now = Date(clock.millis())
        sectionService.verify(id, request.section, now)
        return dao.save(updated.copy(modifiedAt = now))
    }

    /**
     * Sends a DRAFT listing for verification, once its checklist is done: UNDER_VERIFICATION, the verification itself
     * being done asynchronously (`ListingJobs.verify`). 404 LISTING_NOT_FOUND, 403 HTTP_ACCESS_DENIED but for an admin or its agent, 409 LISTING_NOT_DRAFT once sent, then 409 LISTING_INCOMPLETE
     * while a required section isn't VERIFIED (PHOTOS: fewer than MIN_PHOTOS, or one REJECTED).
     */
    @Transactional
    fun verify(id: Long): ListingEntity {
        val listing = findById(id)
        accessService.ensureCanUpdate(listing)
        if (listing.status != ListingStatus.DRAFT) {
            throw ConflictException(Error(ErrorCode.LISTING_NOT_DRAFT))
        }
        if (sectionService.summaries(id).any { it.required && it.status != ListingSectionStatus.VERIFIED }) {
            throw ConflictException(Error(ErrorCode.LISTING_INCOMPLETE))
        }

        return dao.save(
            listing.copy(
                status = ListingStatus.UNDER_VERIFICATION,
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    /**
     * Sends a VERIFIED listing for publication: PUBLISHING, its content being generated asynchronously
     * (`ListingJobs.publish`). 404 LISTING_NOT_FOUND, 403 HTTP_ACCESS_DENIED but for an admin or its agent, then 409
     * LISTING_NOT_VERIFIED.
     */
    @Transactional
    fun publish(id: Long): ListingEntity {
        val listing = findById(id)
        accessService.ensureCanUpdate(listing)
        return publish(listing)
    }

    @Transactional
    fun publish(listing: ListingEntity): ListingEntity {
        if (listing.status != ListingStatus.VERIFIED) {
            throw ConflictException(Error(ErrorCode.LISTING_NOT_VERIFIED))
        }

        return dao.save(
            listing.copy(
                status = ListingStatus.PUBLISHING,
                retries = 0, // The publication's own attempts (ListingPublisherService.MAX_RETRIES)
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    /**
     * E-mails the agent of a PUBLISHED listing that it's published (not the customer, for now). Nothing is sent once the listing
     * has moved on (no longer PUBLISHED). Returns whether it was sent.
     */
    @Transactional
    fun sendPublishedEmails(id: Long): Boolean {
        val listing = findById(id)
        if (listing.status != ListingStatus.PUBLISHED) {
            return false
        }

        publishedMailet.send(
            listing = listing,
            customer = customerService.findById(listing.mandate.customerId),
            agent = listing.agent,
        )
        return true
    }

    // A neighbourhood of the city
    private fun isNeighborhood(id: Long, cityId: Long): Boolean {
        val location = locationService.findByIdOrNull(id)
        return location?.type == LocationType.NEIGHBORHOOD && location.parentId == cityId
    }

    private fun <T> required(name: String, value: T?): T = value ?: throw invalid(name)

    private fun invalid(name: String) = BadRequestException(
        Error(
            code = ErrorCode.HTTP_INVALID_PARAMETER,
            parameter = Parameter(name = name, type = ParameterType.PARAMETER_TYPE_PAYLOAD),
        )
    )

    // A mandate's count ("2", or "2+" for 2 or more) as a number: 2
    private fun count(value: String?): Int? = value?.trim()?.removeSuffix("+")?.toIntOrNull()
}
