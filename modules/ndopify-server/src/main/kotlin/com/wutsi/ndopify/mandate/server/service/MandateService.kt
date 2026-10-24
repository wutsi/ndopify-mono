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
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.SearchMandateRequest
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.MandateApprovedEvent
import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import com.wutsi.ndopify.mandate.server.event.MandateCreatedEvent
import com.wutsi.ndopify.mandate.server.mail.MandateApprovedMailet
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.tenant.TenantContext
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
    private val agentService: AgentService,
    private val eventPublisher: ApplicationEventPublisher,
    private val contractService: ContractService,
    private val dispatcher: MandateDispatcher,
    private val contractGenerator: MandateContractGenerator,
    private val storageProvider: StorageServiceProvider,
    private val tx: TransactionTemplate,
    private val access: MandateAccessService,
    private val approvedMailet: MandateApprovedMailet,

    @param:Value("\${ndopify.mandate.contract.duration-months}") private val contractDurationMonths: Int,
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
     * Updates the mandate's search. With a [UpdateMandateRequest.version], it must be the mandate's (409
     * MANDATE_VERSION_CONFLICT otherwise): the caller's copy is stale, saving it would overwrite the change made since.
     */
    @Transactional
    fun update(id: Long, request: UpdateMandateRequest): MandateEntity {
        val mandate = findById(id)
        access.ensureCanUpdate(mandate)
        if (request.version != null && request.version != mandate.version) {
            throw ConflictException(Error(ErrorCode.MANDATE_VERSION_CONFLICT))
        }
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
                availableFrom = request.availableFrom,
                comments = request.comments,
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    /**
     * Sends an ASSIGNED mandate (or a PENDING_APPROVAL one, again) to its customer for approval: it becomes
     * PENDING_APPROVAL, with a contract to sign. In 3 transactions, so that no lock is held while the contract's content
     * is uploaded (creating a contract locks the tenant's contract sequence until commit):
     * 1. Under the mandate's lock, its contract: the current one, or a new one (none yet, or expired), linked to the
     *    mandate right away. A concurrent request waits for the lock, then reuses it: never 2 contracts for a mandate.
     * 2. Its content, generated from the mandate (read-only), then uploaded outside any transaction.
     * 3. Under the contract's lock, then the mandate's again (the order of a signature, so that they can't deadlock):
     *    the content is set, the mandate is PENDING_APPROVAL and the signature is requested. 409
     *    MANDATE_VERSION_CONFLICT if the mandate changed since 2: the content would be stale.
     * A failure after 1 leaves the mandate as it was, with its DRAFT contract, which the next request reuses.
     * Not @Transactional: each step is its own transaction.
     */
    fun requestApproval(id: Long): MandateEntity {
        val contractId = tx.execute { prepareApproval(id) }
        val (version, html) = tx.execute {
            val mandate = findById(id)
            mandate.version to contractGenerator.generate(mandate, contractService.findById(contractId))
        }
        val content = html.toByteArray(Charsets.UTF_8)
        val url = storeContent(id, contractId, content)
        val contentHash = ContractHasher.contentHash(content)
        return tx.execute { completeApproval(id, contractId, version, url, contentHash) }
    }

    /**
     * Approves the PENDING_APPROVAL mandate of a contract that all its parties have signed, and publishes a
     * [MandateApprovedEvent]. Does nothing for a contract that isn't a mandate's, or a mandate no longer waiting for
     * approval (ex: cancelled in the meantime).
     */
    @Transactional
    fun onContractSigned(contractId: String): MandateEntity? {
        var mandate = dao.findByContractId(contractId)
        if (mandate == null || mandate.status != MandateStatus.PENDING_APPROVAL) {
            return null
        }

        mandate = dao.findByIdForUpdate(mandate.id!!)!!
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
     * E-mails the agent that its mandate is approved. Nothing is sent once the mandate has moved on (closed or
     * cancelled in the meantime), nor for a mandate without agent or contract. Returns whether it was sent.
     */
    @Transactional
    fun sendApprovedEmail(id: Long): Boolean {
        val mandate = findById(id)
        val agentId = mandate.agentId
        val contractId = mandate.contractId
        if (mandate.status != MandateStatus.APPROVED || agentId == null || contractId == null) {
            return false
        }

        approvedMailet.send(
            mandate = mandate,
            contract = contractService.findById(contractId),
            customer = customerService.findById(mandate.customerId),
            agent = agentService.findById(agentId),
        )
        return true
    }

    /**
     * Closes an APPROVED mandate: the deal is done, at [CloseMandateRequest.finalPrice]. Its [MandateEntity.age] is the
     * number of days it took, from its approval to now. Locked, so it can't be cancelled meanwhile.
     */
    @Transactional
    fun close(id: Long, request: CloseMandateRequest): MandateEntity {
        val mandate = findByIdForUpdate(id)
        access.ensureCanUpdate(mandate)
        ensureNotClosed(mandate)
        if (mandate.status != MandateStatus.APPROVED) {
            throw ConflictException(Error(ErrorCode.MANDATE_NOT_APPROVED))
        }

        val now = Date(clock.millis())
        val closed = dao.save(
            mandate.copy(
                status = MandateStatus.CLOSED,
                finalPrice = request.finalPrice.amount,
                closedAt = now,
                age = age(mandate.approvedAt ?: mandate.createdAt, now),
                modifiedAt = now,
            )
        )
        eventPublisher.publishEvent(MandateClosedEvent(mandateId = id, tenantId = TenantContext.get()!!))
        return closed
    }

    /**
     * Cancels a mandate that isn't closed yet, whatever its status. Its PENDING offer, if any, is expired so the agent
     * can no longer accept it (not re-dispatched: no AssignmentClosedEvent), and its contract, if any, is cancelled so
     * the customer can no longer sign it. Locked, so it can't be closed meanwhile. The contract is locked (and
     * cancelled) before the mandate, like a signature does: a failed check below rolls its cancellation back.
     */
    @Transactional
    fun cancel(id: Long, request: CancelMandateRequest): MandateEntity {
        val contractId = findById(id).contractId
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
    private fun createContract(mandate: MandateEntity): ContractEntity {
        val customer = customerService.findById(mandate.customerId)
        val agent = agentService.findById(mandate.agentId!!) // An ASSIGNED / PENDING_APPROVAL mandate has an agent
        return contractService.create(
            CreateContractRequest(
                type = when (mandate.mandateType) {
                    MandateType.SEARCH -> ContractType.SEARCH
                    MandateType.LISTING -> ContractType.LISTING
                    else -> throw IllegalArgumentException("Unsupported mandate type: ${mandate.mandateType}")
                },
                durationMonths = contractDurationMonths,
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

    /** Returns the id of the mandate's contract to sign, created if it has none or it expired. */
    private fun prepareApproval(id: Long): String {
        val mandate = findByIdForUpdate(id)
        access.ensureCanUpdate(mandate)
        ensureCanBeApproved(mandate)

        val current = mandate.contractId?.let { contractId -> contractService.findById(contractId) }
        if (current != null && current.status != ContractStatus.EXPIRED) {
            return current.id
        }
        val contract = createContract(mandate)
        dao.save(mandate.copy(contractId = contract.id, modifiedAt = Date(clock.millis())))
        return contract.id
    }

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
