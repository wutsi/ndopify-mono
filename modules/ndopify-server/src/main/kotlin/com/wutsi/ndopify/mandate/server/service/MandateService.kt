package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.CreateContractParty
import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.dto.SetContractRequest
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.service.ContractHasher
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.customer.dto.CreateCustomerRequest
import com.wutsi.ndopify.customer.dto.UpdateCustomerRequest
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.service.RentalTerms
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.RequestMandateApprovalRequest
import com.wutsi.ndopify.mandate.dto.SearchMandateRequest
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.ExistingSearchInterestEvent
import com.wutsi.ndopify.mandate.server.event.MandateApprovedEvent
import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import com.wutsi.ndopify.mandate.server.event.MandateCreatedEvent
import com.wutsi.ndopify.party.dto.UpdatePartyRequest
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.util.Date
import java.util.UUID

@Service
class MandateService(
    private val dao: MandateRepository,
    private val assignmentDao: AssignmentRepository,
    private val clock: Clock,
    private val customerService: CustomerService,
    private val partyService: PartyService,
    private val agentService: AgentService,
    private val eventPublisher: ApplicationEventPublisher,
    private val contractService: ContractService,
    private val dispatcher: MandateDispatcher,
    private val contractGenerator: MandateContractGenerator,
    private val storageProvider: StorageServiceProvider,
    private val tx: TransactionTemplate,
    private val access: MandateAccessService,
    private val approvedNotifier: MandateApprovedNotifier,
    private val originListingResolver: OriginListingResolver,

    @param:Value("\${ndopify.mandate.contract.duration-months}") private val contractDurationMonths: Int,

    // The rate a sale contract gets when the request omits it, like the duration's default
    @param:Value("\${ndopify.mandate.contract.sale-commission-rate}") private val saleCommissionRate: Int,
) {
    companion object {
        // New SEARCH mandates a visitor's page may create for one customer in 24 h (attaching to an existing search
        // isn't counted: it creates nothing)
        const val MAX_NEW_SEARCHES_PER_DAY = 3L
        private const val DAY_MILLIS = 86_400_000L
    }

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
            request.offeredTo?.let { agentId ->
                // A subquery rather than a join: the mandates offered to the agent, whatever its other offers
                val sub = query.subquery(Long::class.java)
                val assignment = sub.from(AssignmentEntity::class.java)
                sub.select(assignment.get<MandateEntity>("mandate").get("id"))
                    .where(
                        cb.equal(assignment.get<Long>("agentId"), agentId),
                        cb.equal(assignment.get<Any>("status"), AssignmentStatus.PENDING),
                    )
                predicates.add(root.get<Long>("id").`in`(sub))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, Sort.by("id"))).content
    }

    fun findByIdOrNull(id: Long, forUpdate: Boolean = false): MandateEntity? {
        return if (forUpdate) {
            dao.findByIdForUpdate(id)
        } else {
            dao.findById(id).orElse(null)
        }
    }

    fun findById(id: Long, forUpdate: Boolean = false): MandateEntity {
        return findByIdOrNull(id, forUpdate)
            ?: throw NotFoundException(error = Error(code = ErrorCode.MANDATE_NOT_FOUND))
    }

    fun findAllByIds(ids: Collection<Long>): List<MandateEntity> = dao.findAllById(ids).toList()

    /** The signed contract's per-visit fee, or no fee when the mandate has no contract (or a legacy blank currency). */
    fun visitFee(mandate: MandateEntity): Money {
        val contract = mandate.contractId?.let { contractService.findByIdOrNull(it) } ?: return Money()
        return if (contract.visitFeeCurrency.isBlank()) {
            Money()
        } else {
            Money(contract.visitFeeAmount, contract.visitFeeCurrency)
        }
    }

    /**
     * Creates the mandate. The purchase answers are the buyer's brief: a rental keeps no financing, and only a land
     * purchase keeps documents (empty is a valid answer). The seller's ownership documents are the seller's brief in
     * the same way: a rental and any non-land sale have none, and they filter nothing. The implied price per m² is
     * computed on the page and never stored.
     */
    @Transactional
    fun create(request: CreateMandateRequest): MandateEntity {
        validateRentalTerms(request.securityDeposit, request.advanceRent)
        // A mandate seeded from a listing takes its criteria and budget from the listing, never from the request: the
        // public page can't be turned into an arbitrary search
        val origin = request.originListingId?.let { id ->
            originListingResolver.resolve(id)
                ?: throw NotFoundException(error = Error(code = ErrorCode.LISTING_NOT_FOUND))
        }
        val criteria = origin?.criteria ?: request
        val sale = criteria.listingType == ListingType.SALE
        val titleDocuments = criteria.titleDocuments
            .filter { sale && criteria.propertyType == PropertyType.LAND && it != TitleDocumentType.UNKNOWN }
            .distinct()
        val ownershipDocuments = criteria.ownershipDocuments
            .filter { sale && criteria.propertyType == PropertyType.LAND && it != TitleDocumentType.UNKNOWN }
            .distinct()
        val customer = customerService.findOrCreate(
            CreateCustomerRequest(
                firstName = request.firstName,
                lastName = request.lastName,
                email = request.email,
                whatsappNumber = request.whatsappNumber,
            )
        )

        val now = Date(clock.millis())
        if (origin != null) {
            // One search of a city and listing type per customer: the listing attaches to it instead of duplicating it
            dao.findFirstByCustomerIdAndMandateTypeAndCityIdAndListingTypeAndStatusNotInOrderByIdDesc(
                customerId = customer.id!!,
                mandateType = MandateType.SEARCH,
                cityId = criteria.cityId,
                listingType = criteria.listingType,
                statuses = listOf(MandateStatus.CLOSED, MandateStatus.CANCELLED),
            )?.let { existing ->
                // The listing the customer asked for is remembered as the search's origin: a search without an agent
                // yet can't have a match, and an approved one records it ACCEPTED (MatchInterestService, and
                // MatchInterestService.proposeOrigin on its approval)
                val attached = if (existing.originListingId == null) {
                    dao.save(existing.copy(originListingId = origin.id, modifiedAt = now))
                } else {
                    existing
                }
                eventPublisher.publishEvent(
                    ExistingSearchInterestEvent(
                        mandateId = attached.id!!,
                        listingId = origin.id,
                        tenantId = TenantContext.get()!!,
                    ),
                )
                return attached
            }
            // A visitor can't flood an agent with offers: their page creates at most 3 new searches per day
            val recent = dao.countByCustomerIdAndMandateTypeAndCreatedAtAfter(
                customerId = customer.id!!,
                mandateType = MandateType.SEARCH,
                after = Date(now.time - DAY_MILLIS),
            )
            if (recent >= MAX_NEW_SEARCHES_PER_DAY) {
                throw ConflictException(error = Error(code = ErrorCode.MANDATE_RATE_LIMITED))
            }
        }
        val mandate = dao.save(
            MandateEntity(
                customerId = customer.id ?: -1,
                status = MandateStatus.PENDING_ASSIGNMENT,
                originListingId = origin?.id?.takeIf { origin.published },
                propertyCategory = criteria.propertyCategory,
                mandateType = criteria.mandateType,
                listingType = criteria.listingType,
                financingType = criteria.financingType?.takeIf { sale },
                titleDocuments = titleDocuments,
                ownershipDocuments = ownershipDocuments,
                cityId = criteria.cityId,
                neighborhoodIds = criteria.neighbourhoodIds,
                propertyType = criteria.propertyType,
                furnitureType = criteria.furnitureType,
                rooms = criteria.rooms,
                parkings = criteria.parkings,
                area = criteria.area,
                waterMeter = criteria.waterMeter,
                dedicatedWaterMeter = criteria.dedicatedWaterMeter,
                waterTank = criteria.waterTank,
                waterWell = criteria.waterWell,
                electricityMeter = criteria.electricityMeter,
                dedicatedElectricityMeter = criteria.dedicatedElectricityMeter,
                generatorSet = criteria.generatorSet,
                solarPanel = criteria.solarPanel,
                roadPaved = criteria.roadPaved,
                trackAccessibleByCar = criteria.trackAccessibleByCar,
                trackInaccessibleByCar = criteria.trackInaccessibleByCar,
                securedFenced = criteria.securedFenced,
                securityGuard = criteria.securityGuard,
                videoSurveillance = criteria.videoSurveillance,
                price = criteria.price.amount,
                pricePeriodicityType = criteria.price.periodicity,
                currencyCode = criteria.price.currency,
                securityDeposit = request.securityDeposit,
                advanceRent = request.advanceRent,
                leaseTerm = request.leaseTerm,
                noticePeriod = request.noticePeriod,
                movingPeriod = request.movingPeriod,
                availableFrom = request.availableFrom,
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

    /**
     * Updates the mandate's search, until its customer signs it: 404 MANDATE_NOT_FOUND, 403 MANDATE_ACCESS_DENIED, 409
     * MANDATE_ALREADY_CLOSED (closed or cancelled), 409 MANDATE_ALREADY_APPROVED, then 409 MANDATE_VERSION_CONFLICT
     * when [UpdateMandateRequest.version] isn't the mandate's: the caller's copy is stale, saving it would overwrite the
     * change made since. A PENDING_APPROVAL mandate goes back to ASSIGNED and its contract is cancelled: the customer
     * mustn't sign the terms of before the change, and request-approval creates a new one. Like a signature, the
     * contract is locked before the mandate. The purchase answers are kept the same way as on create: no financing for
     * a rental, documents only for a land purchase; the seller's ownership documents are kept for a land sale only and
     * filter nothing. The implied price per m² is never stored.
     */
    @Transactional
    fun update(id: Long, request: UpdateMandateRequest): MandateEntity {
        validateRentalTerms(request.securityDeposit, request.advanceRent)
        val contractId = dao.findContractIdById(id)
        contractId?.let { contractService.findByIdForUpdate(it) }

        val mandate = findByIdForUpdate(id)
        access.ensureCanUpdate(mandate)
        ensureNotClosed(mandate)
        if (mandate.status == MandateStatus.APPROVED) {
            throw ConflictException(Error(ErrorCode.MANDATE_ALREADY_APPROVED))
        }
        if (mandate.contractId != contractId) {
            // Replaced meanwhile by request-approval's step 1: not the contract locked above
            throw ConflictException(Error(ErrorCode.MANDATE_VERSION_CONFLICT))
        }
        if (request.version != null && request.version != mandate.version) {
            throw ConflictException(Error(ErrorCode.MANDATE_VERSION_CONFLICT))
        }

        val status = if (mandate.status == MandateStatus.PENDING_APPROVAL) {
            contractId?.let { contractService.cancel(it) }
            MandateStatus.ASSIGNED
        } else {
            mandate.status
        }
        val sale = request.listingType == ListingType.SALE
        val titleDocuments = request.titleDocuments
            .filter { sale && request.propertyType == PropertyType.LAND && it != TitleDocumentType.UNKNOWN }
            .distinct()
        val ownershipDocuments = request.ownershipDocuments
            .filter { sale && request.propertyType == PropertyType.LAND && it != TitleDocumentType.UNKNOWN }
            .distinct()
        return dao.save(
            mandate.copy(
                status = status,
                propertyCategory = request.propertyCategory,
                listingType = request.listingType,
                financingType = request.financingType?.takeIf { sale },
                titleDocuments = titleDocuments,
                ownershipDocuments = ownershipDocuments,
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
                availableFrom = request.availableFrom,
                comments = request.comments,
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    /**
     * Corrects the details of its customer, which every one of that customer's mandate pages shows: 404
     * MANDATE_NOT_FOUND, 403 MANDATE_ACCESS_DENIED, 409 MANDATE_ALREADY_CLOSED (closed or cancelled), then 409
     * PARTY_EMAIL_ALREADY_EXISTS when another party already owns the e-mail. A PENDING_APPROVAL mandate whose e-mail
     * changes goes back to ASSIGNED and its contract is cancelled: the customer mustn't sign a contract sent to the
     * old address, and request-approval creates a new one. A name or phone change leaves the contract. Like a
     * signature, the contract is locked before the mandate.
     */
    @Transactional
    fun updateCustomer(id: Long, request: UpdateCustomerRequest) {
        val contractId = dao.findContractIdById(id)
        contractId?.let { contractService.findByIdForUpdate(it) }

        val mandate = findByIdForUpdate(id)
        access.ensureCanUpdate(mandate)
        ensureNotClosed(mandate)

        val customer = customerService.findById(mandate.customerId)
        val email = request.email.trim().lowercase()
        val emailChanged = email != customer.party.email.lowercase()
        if (emailChanged && mandate.status == MandateStatus.PENDING_APPROVAL) {
            if (mandate.contractId != contractId) {
                // Replaced meanwhile by request-approval's step 1: not the contract locked above
                throw ConflictException(Error(ErrorCode.MANDATE_VERSION_CONFLICT))
            }
            mandate.contractId?.let { contractService.cancel(it) }
            dao.save(mandate.copy(status = MandateStatus.ASSIGNED, modifiedAt = Date(clock.millis())))
        }

        partyService.update(
            customer.party,
            UpdatePartyRequest(
                firstName = request.firstName.trim(),
                lastName = request.lastName.trim(),
                email = email,
            ),
        )
        customerService.update(customer, request.whatsappNumber.trim())
    }

    /**
     * Sends an ASSIGNED mandate (or a PENDING_APPROVAL one, again) to its customer for approval: it becomes
     * PENDING_APPROVAL, with a contract to sign. [request] confirms the contract's terms (duration 1..36, visit fee,
     * and for a sale mandate its commission in % of the sale price, 1..20 — anything else is 400
     * `HTTP_INVALID_PARAMETER`); null keeps the contract's current ones, or the tenant default for a new one (the
     * commission's from `ndopify.mandate.contract.sale-commission-rate`). A rental stores no commission. In 3
     * transactions, so that no lock is held while the contract's content is uploaded (creating a contract locks the
     * tenant's contract sequence until commit):
     * 1. Under the contract's lock, then the mandate's (the order of a signature, so that they can't deadlock): its
     *    contract, or a new one (none yet, or expired), with the requested terms, linked to the mandate right away. A
     *    concurrent request waits for the lock, then reuses it: never 2 contracts for a mandate.
     * 2. Its content, generated from the mandate and an in-memory copy of the contract with the requested terms
     *    (read-only), then uploaded outside any transaction.
     * 3. Under the contract's lock, then the mandate's again: the terms are applied, the content is set, the mandate is
     *    PENDING_APPROVAL and the signature is requested — one transaction, so that a contract the customer can still
     *    sign never has terms that differ from its content. 409 MANDATE_VERSION_CONFLICT if the mandate changed since
     *    2: the content would be stale.
     * A failure after 1 leaves the mandate and its DRAFT contract as they were, which the next request reuses.
     * Not @Transactional: each step is its own transaction.
     */
    fun requestApproval(id: Long, request: RequestMandateApprovalRequest? = null): MandateEntity {
        request?.commissionRate?.let { commissionRate ->
            if (commissionRate !in 1..20) throw invalid("commissionRate")
        }
        request?.durationMonths?.let { durationMonths ->
            if (durationMonths !in 1..36) throw invalid("durationMonths")
        }
        val contractId = tx.execute { prepareApproval(id, request) }
        val (version, html) = tx.execute {
            val mandate = findById(id)
            mandate.version to contractGenerator.generate(
                mandate,
                contractService.findById(contractId).withTerms(request),
            )
        }
        val content = html.toByteArray(Charsets.UTF_8)
        val url = storeContent(id, contractId, content)
        val contentHash = ContractHasher.contentHash(content)
        return tx.execute { completeApproval(id, contractId, request, version, url, contentHash) }
    }

    /**
     * Approves the PENDING_APPROVAL mandate of a contract that all its parties have signed, and publishes a
     * [MandateApprovedEvent]. Does nothing for a contract that isn't a mandate's, or a mandate no longer waiting for
     * approval (ex: cancelled in the meantime).
     */
    @Transactional
    fun onContractSigned(contractId: String): MandateEntity? {
        val mandate = dao.findIdByContractId(contractId)?.let { id -> dao.findByIdForUpdate(id) }
        if (mandate == null || mandate.status != MandateStatus.PENDING_APPROVAL) {
            return null
        }

        val now = Date(clock.millis())
        val approved = dao.save(
            mandate.copy(
                status = MandateStatus.APPROVED,
                modifiedAt = now,
                approvedAt = now
            )
        )
        eventPublisher.publishEvent(MandateApprovedEvent(mandateId = approved.id!!, tenantId = TenantContext.get()!!))
        return approved
    }

    /**
     * Tells the agent that its mandate is approved: over Telegram when its chat is linked, by e-mail otherwise or as
     * the fallback of a failed send. Nothing is sent once the mandate has moved on (closed or cancelled in the
     * meantime), nor for a mandate without agent or contract. Returns whether a channel delivered it.
     */
    @Transactional
    fun sendApprovedNotification(id: Long): Boolean {
        val mandate = findById(id)
        val agentId = mandate.agentId
        val contractId = mandate.contractId
        if (mandate.status != MandateStatus.APPROVED || agentId == null || contractId == null) {
            return false
        }

        approvedNotifier.send(
            mandate = mandate,
            contract = contractService.findById(contractId),
            customer = customerService.findById(mandate.customerId),
            agent = agentService.findById(agentId),
        )
        return true
    }

    /**
     * Closes an APPROVED mandate: the deal is done, outside any match, at [CloseMandateRequest.finalPrice] (a positive
     * amount, else 400 HTTP_INVALID_PARAMETER). Its [MandateEntity.age] is the number of days it took, from its
     * approval to now. Locked, so it can't be cancelled meanwhile.
     */
    @Transactional
    fun close(id: Long, request: CloseMandateRequest): MandateEntity {
        val mandate = findByIdForUpdate(id)
        access.ensureCanUpdate(mandate)
        ensureNotClosed(mandate)
        if (mandate.status != MandateStatus.APPROVED) {
            throw ConflictException(Error(ErrorCode.MANDATE_NOT_APPROVED))
        }
        if (request.finalPrice.amount < 1) {
            throw BadRequestException(
                Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    parameter = Parameter(name = "finalPrice", type = ParameterType.PARAMETER_TYPE_PAYLOAD),
                ),
            )
        }

        val now = Date(clock.millis())
        val closed = dao.save(
            mandate.copy(
                status = MandateStatus.CLOSED,
                finalPrice = request.finalPrice.amount,
                dealMatchId = null,
                dealOutside = true,
                closedAt = now,
                age = age(mandate.approvedAt ?: mandate.createdAt, now),
                modifiedAt = now,
            )
        )
        eventPublisher.publishEvent(MandateClosedEvent(mandateId = id, tenantId = TenantContext.get()!!))
        return closed
    }

    /**
     * Closes both sides of a deal (or one) with the same price: only APPROVED sides, a side already CLOSED/CANCELLED is
     * left as is, 409 when no side is left. Locks in ascending id order; stores [matchId] on each closed side.
     */
    @Transactional
    fun closeDeal(
        searchMandateId: Long,
        counterpartMandateId: Long?,
        matchId: Long,
        finalPrice: Money,
    ): List<MandateEntity> {
        val ids = listOfNotNull(searchMandateId, counterpartMandateId).distinct().sorted()
        val entities = ids.map { id -> findByIdForUpdate(id) }
        val toClose = entities.filter { it.status != MandateStatus.CLOSED && it.status != MandateStatus.CANCELLED }
        if (toClose.isEmpty()) {
            throw ConflictException(Error(ErrorCode.MANDATE_ALREADY_CLOSED))
        }
        if (toClose.any { it.status != MandateStatus.APPROVED }) {
            throw ConflictException(Error(ErrorCode.MANDATE_NOT_APPROVED))
        }

        val now = Date(clock.millis())
        return toClose.map { mandate ->
            dao.save(
                mandate.copy(
                    status = MandateStatus.CLOSED,
                    finalPrice = finalPrice.amount,
                    closedAt = now,
                    age = age(mandate.approvedAt ?: mandate.createdAt, now),
                    dealMatchId = matchId,
                    dealOutside = false,
                    modifiedAt = now,
                )
            ).also { closed ->
                eventPublisher.publishEvent(
                    MandateClosedEvent(mandateId = closed.id!!, tenantId = TenantContext.get()!!)
                )
            }
        }
    }

    /**
     * Cancels a mandate that isn't closed yet, whatever its status. Its PENDING offer, if any, is expired so the agent
     * can no longer accept it (not re-dispatched: no AssignmentClosedEvent), and its contract, if any, is cancelled so
     * the customer can no longer sign it. Locked, so it can't be closed meanwhile. The contract is locked (and
     * cancelled) before the mandate, like a signature does: a failed check below rolls its cancellation back.
     */
    @Transactional
    fun cancel(id: Long, request: CancelMandateRequest): MandateEntity {
        val contractId = dao.findContractIdById(id)
        contractId?.let { contractService.cancel(it) }

        val mandate = findByIdForUpdate(id)
        access.ensureCanUpdate(mandate)
        ensureNotClosed(mandate)
        if (mandate.contractId != contractId) {
            // Linked meanwhile by request-approval's step 1: a DRAFT, which can't be signed, so locking it now is safe
            mandate.contractId?.let { contractService.cancel(it) }
        }

        val now = Date(clock.millis())
        assignmentDao.findByMandateAndStatusIn(mandate, listOf(AssignmentStatus.PENDING)).forEach { assignment ->
            assignmentDao.save(assignment.copy(status = AssignmentStatus.EXPIRED, modifiedAt = now))
        }

        val cancelled = dao.save(
            mandate.copy(
                status = MandateStatus.CANCELLED,
                cancellationCode = request.cancellationCode ?: MandateCancellationCode.OTHER,
                cancellationReason = request.cancellationReason?.trim()?.ifEmpty { null },
                closedAt = now,
                modifiedAt = now,
            )
        )
        eventPublisher.publishEvent(MandateClosedEvent(mandateId = id, tenantId = TenantContext.get()!!))
        return cancelled
    }

    // The caution and the advance: null (none), or a legacy 0 to 24 months
    private fun validateRentalTerms(securityDeposit: Int?, advanceRent: Int?) {
        if (!RentalTerms.isValid(securityDeposit)) throw invalid("securityDeposit")
        if (!RentalTerms.isValid(advanceRent)) throw invalid("advanceRent")
    }

    private fun invalid(name: String) = BadRequestException(
        Error(
            code = ErrorCode.HTTP_INVALID_PARAMETER,
            parameter = Parameter(name = name, type = ParameterType.PARAMETER_TYPE_PAYLOAD),
        ),
    )

    private fun findByIdForUpdate(id: Long): MandateEntity {
        return dao.findByIdForUpdate(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.MANDATE_NOT_FOUND))
    }

    private fun ensureNotClosed(mandate: MandateEntity) {
        if (mandate.status == MandateStatus.CLOSED || mandate.status == MandateStatus.CANCELLED) {
            throw ConflictException(Error(ErrorCode.MANDATE_ALREADY_CLOSED))
        }
    }

    // Whole days elapsed (a mandate closed the day it's approved is 0 day old)
    private fun age(from: Date, to: Date): Int =
        Duration.ofMillis(to.time - from.time).toDays().toInt().coerceAtLeast(0)

    // Signed by the customer only: the agent is a party, so that it can read the contract, but doesn't sign it
    private fun createContract(mandate: MandateEntity, request: RequestMandateApprovalRequest?): ContractEntity {
        val customer = customerService.findById(mandate.customerId)
        val agent = agentService.findById(mandate.agentId!!) // An ASSIGNED / PENDING_APPROVAL mandate has an agent
        val sale = mandate.listingType == ListingType.SALE // A sale has its own contract and states its commission
        return contractService.create(
            CreateContractRequest(
                type = when (mandate.mandateType) {
                    MandateType.SEARCH -> if (sale) ContractType.SALE_SEARCH else ContractType.SEARCH
                    MandateType.LISTING -> if (sale) ContractType.SALE_LISTING else ContractType.LISTING
                    else -> throw IllegalArgumentException("Unsupported mandate type: ${mandate.mandateType}")
                },
                durationMonths = request?.durationMonths ?: contractDurationMonths,
                visitFee = request?.visitFee,
                commissionRate = if (sale) request?.commissionRate ?: saleCommissionRate else null,
                parties = listOf(
                    CreateContractParty(
                        partyId = customer.party.id ?: -1,
                        legalName = customer.party.fullName(),
                        legalEmail = customer.party.email,
                        role = ContractPartyRole.MANDATOR,
                        signatureRequired = true,
                    ),
                    CreateContractParty(
                        partyId = agent.party.id ?: -1,
                        legalName = agent.party.fullName(),
                        legalEmail = agent.party.email,
                        role = ContractPartyRole.AGENT,
                        signatureRequired = false,
                    ),
                )
            )
        )
    }

    /** Returns the id of the mandate's contract to sign, created if it has none, or it expired or was cancelled. */
    private fun prepareApproval(id: Long, request: RequestMandateApprovalRequest?): String {
        // The contract before the mandate, the order of a signature: a concurrent request waits for the lock
        val contractId = dao.findContractIdById(id)
        contractId?.let { contractService.findByIdForUpdate(it) }

        val mandate = findByIdForUpdate(id)
        access.ensureCanUpdate(mandate)
        ensureCanBeApproved(mandate)
        if (mandate.contractId != contractId) {
            // Replaced meanwhile by another request: not the contract locked above
            throw ConflictException(Error(ErrorCode.MANDATE_VERSION_CONFLICT))
        }

        val current = contractId?.let { contractService.findById(it) }
        // A contract cancelled by an update can't be signed any more, like an expired one: a new one replaces it
        if (current != null && current.status != ContractStatus.EXPIRED && current.status != ContractStatus.CANCELLED) {
            return current.id // Its terms are applied in step 3, with the content they are stated in
        }
        val contract = createContract(mandate, request)
        dao.save(mandate.copy(contractId = contract.id, modifiedAt = Date(clock.millis())))
        return contract.id
    }

    /** The contract with the terms being sent, without persisting them: step 2 generates their content, step 3 saves. */
    private fun ContractEntity.withTerms(request: RequestMandateApprovalRequest?): ContractEntity =
        copy(
            durationMonths = request?.durationMonths ?: durationMonths,
            visitFeeAmount = request?.visitFee?.amount ?: visitFeeAmount,
            commissionRate = request?.commissionRate ?: commissionRate,
        )

    /** Returns the content's URL. A new path each time: a content being signed is never overwritten. */
    private fun storeContent(id: Long, contractId: String, content: ByteArray): String {
        val path = "mandates/$id/contracts/$contractId/${UUID.randomUUID()}.html"
        val storage = storageProvider.get()
        storage.store(path, ByteArrayInputStream(content), "text/html")
        return storage.publicUrl(path).toString()
    }

    /** [version] is the mandate's the content was generated from, [contentHash] the hash of the stored content. */
    private fun completeApproval(
        id: Long,
        contractId: String,
        request: RequestMandateApprovalRequest?,
        version: Long?,
        url: String,
        contentHash: String,
    ): MandateEntity {
        contractService.findByIdForUpdate(contractId) // Before the mandate, like a signature
        val mandate = findByIdForUpdate(id)
        ensureCanBeApproved(mandate)
        if (mandate.version != version) {
            // Updated meanwhile (or its contract replaced by a concurrent request): the content isn't the mandate's
            throw ConflictException(Error(ErrorCode.MANDATE_VERSION_CONFLICT))
        }

        // With the content, so that a contract still signable never states terms other than these (a signature since
        // step 1 wins: setTerms then answers 409 and nothing here is saved)
        contractService.setTerms(contractId, request?.durationMonths, request?.visitFee, request?.commissionRate)
        contractService.setContent(contractId, SetContractRequest(url = url), contentHash)
        val updated = dao.save(
            mandate.copy(
                status = MandateStatus.PENDING_APPROVAL,
                modifiedAt = Date(clock.millis()),
            )
        )
        contractService.requestSignature(contractId)
        return updated
    }

    private fun ensureCanBeApproved(mandate: MandateEntity) {
        if (mandate.status != MandateStatus.ASSIGNED && mandate.status != MandateStatus.PENDING_APPROVAL) {
            throw ConflictException(Error(ErrorCode.MANDATE_CANNOT_BE_APPROVED))
        }
    }
}
