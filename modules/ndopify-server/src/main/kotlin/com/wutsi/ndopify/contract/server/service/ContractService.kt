package com.wutsi.ndopify.contract.server.service

import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.contract.dto.SetContractRequest
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.contract.server.dao.ContractPartyRepository
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import com.wutsi.ndopify.contract.server.event.ContractExpiredEvent
import com.wutsi.ndopify.contract.server.event.ContractSignatureRequestedEvent
import com.wutsi.ndopify.contract.server.event.ContractSignedEvent
import com.wutsi.ndopify.contract.server.mail.ContractSignatureMailet
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.Date

@Service
class ContractService(
    private val dao: ContractRepository,
    private val partyDao: ContractPartyRepository,
    private val partyService: PartyService,
    private val tenantService: TenantService,
    private val numberGenerator: ContractNumberGenerator,
    private val signatureMailet: ContractSignatureMailet,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
    private val tx: TransactionTemplate,
    @param:Value("\${ndopify.contract.signature.ttl-days}") private val signatureTtlDays: Long,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ContractService::class.java)
        const val BATCH_SIZE = 100
    }

    // Newest first. By party: the contracts the party is one of the parties of
    fun search(request: SearchContractRequest): List<ContractEntity> {
        val spec = Specification<ContractEntity> { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            if (request.ids.isNotEmpty()) {
                predicates.add(root.get<String>("id").`in`(request.ids))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<Any>("status").`in`(request.statuses))
            }
            if (request.partyIds.isNotEmpty()) {
                // A subquery rather than a join: a party appearing twice in a contract mustn't return it twice
                val sub = query!!.subquery(String::class.java)
                val party = sub.from(ContractPartyEntity::class.java)
                sub.select(party.get<ContractEntity>("contract").get("id"))
                    .where(party.get<Long>("partyId").`in`(request.partyIds))
                predicates.add(root.get<String>("id").`in`(sub))
            }

            cb.and(*predicates.toTypedArray())
        }

        val sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, sort)).content
    }

    fun findByIdOrNull(id: String): ContractEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findById(id: String): ContractEntity {
        return findByIdOrNull(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.CONTRACT_NOT_FOUND))
    }

    // Read from the table rather than ContractEntity.contractParties, which may be stale right after a signature
    fun findParties(contract: ContractEntity): List<ContractPartyEntity> {
        return partyDao.findByContract(contract)
    }

    @Transactional
    fun create(request: CreateContractRequest): ContractEntity {
        if (request.type == ContractType.UNKNOWN) {
            throw invalidParameter("type", request.type.name)
        }
        if (request.durationMonths < 1) {
            throw invalidParameter("durationMonths", request.durationMonths.toString())
        }
        request.parties.forEach { party ->
            if (party.role == ContractPartyRole.UNKNOWN) {
                throw invalidParameter("parties.role", party.role.name)
            }
            partyService.findById(party.partyId) // 404 PARTY_NOT_FOUND, also for a party of another tenant
        }

        // Last, once the request is valid: the sequence stays locked until the commit
        val tenantId = TenantContext.get()!!
        val now = Date(clock.millis())
        val number = numberGenerator.generate(
            tenantId = tenantId,
            type = request.type,
            now = now.toInstant(),
            timeZone = ZoneId.of(tenantService.findById(tenantId).timeZone),
        )

        val contract = dao.save(
            ContractEntity(
                number = number,
                type = request.type,
                status = ContractStatus.DRAFT,
                durationMonths = request.durationMonths,
                createdAt = now,
            )
        )
        request.parties.forEach { party ->
            partyDao.save(
                ContractPartyEntity(
                    contract = contract,
                    role = party.role,
                    partyId = party.partyId,
                    legalName = party.legalName,
                    legalEmail = party.legalEmail.lowercase(),
                )
            )
        }
        return contract
    }

    /**
     * Sets the URL of the contract's document, while it can still be signed: DRAFT or PENDING_SIGNATURE. Locked like a
     * signature, so that the last party can't sign it while its document changes.
     */
    @Transactional
    fun setContent(id: String, request: SetContractRequest): ContractEntity {
        val contract = dao.findByIdForUpdate(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.CONTRACT_NOT_FOUND))
        return setContent(contract, request)
    }

    @Transactional
    fun setContent(contract: ContractEntity, request: SetContractRequest): ContractEntity {
        ensureNotClosed(contract)
        return dao.save(contract.copy(contentUrl = request.url.trim()))
    }

    /**
     * Sends a DRAFT or PENDING_SIGNATURE contract to its parties for signature: it becomes PENDING_SIGNATURE, its
     * parties have `ttl-days` from now to sign it (each request resets the deadline), and are e-mailed after commit, by
     * a listener of [ContractSignatureRequestedEvent].
     */
    @Transactional
    fun requestSignature(id: String): ContractEntity {
        val contract = findById(id)

        ensureNotClosed(contract)
        val pending = dao.save(
            contract.copy(
                status = ContractStatus.PENDING_SIGNATURE,
                signatureDeadline = Date(clock.millis() + Duration.ofDays(signatureTtlDays).toMillis()),
            )
        )
        eventPublisher.publishEvent(
            ContractSignatureRequestedEvent(
                contractId = pending.id,
                tenantId = TenantContext.get()!!
            )
        )
        return pending
    }

    /**
     * E-mails each party that hasn't signed yet the contract to sign. Returns how many e-mails were sent. One party's
     * failure is logged, and doesn't stop the e-mails of the others. Called by `ContractEventListener` with
     * `TenantContext` set: the transaction gives the mailet a session to load the parties.
     */
    @Transactional
    fun sendSignatureRequestEmails(id: String): Int {
        val contract = findById(id)
        if (contract.status != ContractStatus.PENDING_SIGNATURE) {
            return 0 // Signed or cancelled in the meantime: nothing to sign anymore
        }

        return partyDao.findByContract(contract)
            .filter { party -> !party.signed }
            .count { party ->
                try {
                    signatureMailet.send(contract, party)
                    true
                } catch (ex: Exception) {
                    LOGGER.warn("Contract#$id - Unable to send the signature request to party#${party.id}", ex)
                    false
                }
            }
    }

    /**
     * Records the signature of one of the contract's parties ([SignContractRequest.partyId] is a contract party id).
     * The contract is SIGNED once all its parties have signed, and publishes a [ContractSignedEvent]: it's valid from
     * this last signature, for its `durationMonths` (added in the tenant's time zone, so a month ends on the same day).
     */
    @Transactional
    fun sign(id: String, request: SignContractRequest): ContractEntity {
        val contract = dao.findByIdForUpdate(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.CONTRACT_NOT_FOUND))
        if (contract.status != ContractStatus.PENDING_SIGNATURE) {
            throw ConflictException(error = Error(code = ErrorCode.CONTRACT_NOT_PENDING))
        }

        val now = Date(clock.millis())
        if (isSignatureLate(contract, now)) {
            throw ConflictException(error = Error(code = ErrorCode.CONTRACT_EXPIRED))
        }

        val parties = partyDao.findByContract(contract)
        val party = parties.find { party -> party.id == request.partyId }
            ?: throw NotFoundException(error = Error(code = ErrorCode.CONTRACT_PARTY_NOT_FOUND))
        if (party.signed) {
            throw ConflictException(error = Error(code = ErrorCode.CONTRACT_PARTY_ALREADY_SIGNED))
        }

        partyDao.save(
            party.copy(
                signed = true,
                ipAddress = request.ipAddress,
                userAgent = request.userAgent,
                signedAt = now,
            )
        )

        val allSigned = parties.all { other -> other.id == party.id || other.signed }
        return if (allSigned) {
            val zone = ZoneId.of(tenantService.findById(TenantContext.get()!!).timeZone)
            val validUntil = now.toInstant().atZone(zone).plusMonths(contract.durationMonths.toLong()).toInstant()
            val signed = dao.save(
                contract.copy(
                    status = ContractStatus.SIGNED,
                    signedAt = now,
                    validFrom = now,
                    validUntil = Date.from(validUntil),
                )
            )
            eventPublisher.publishEvent(ContractSignedEvent(contractId = signed.id, tenantId = TenantContext.get()!!))
            signed
        } else {
            contract
        }
    }

    /**
     * Expires the contracts not signed by all their parties before their deadline, and the signed ones past their end
     * date. Each one in its own transaction, so one failing contract doesn't roll back the others. Returns how many
     * expired.
     */
    fun expireDue(): Int {
        val ids = dao.findIdsToExpire(
            pending = ContractStatus.PENDING_SIGNATURE,
            signed = ContractStatus.SIGNED,
            now = Date(clock.millis()),
            page = PageRequest.of(0, BATCH_SIZE),
        )
        return ids.count { id ->
            try {
                tx.execute { expire(id) } == true
            } catch (ex: Exception) {
                LOGGER.warn("Contract#$id - Unable to expire", ex)
                false
            }
        }
    }

    /**
     * Expires a contract, locked like a signature so that it can't expire while its last party signs it. Checked
     * again once locked: false if it was signed (or its signature requested again) in the meantime. Publishes a
     * [ContractExpiredEvent].
     */
    private fun expire(id: String): Boolean {
        val contract = dao.findByIdForUpdate(id) ?: return false
        val now = Date(clock.millis())
        val due = isSignatureLate(contract, now) ||
            (contract.status == ContractStatus.SIGNED && contract.validUntil?.before(now) == true)
        if (!due) {
            return false
        }

        dao.save(contract.copy(status = ContractStatus.EXPIRED))
        eventPublisher.publishEvent(ContractExpiredEvent(contractId = id, tenantId = TenantContext.get()!!))
        return true
    }

    // Only a DRAFT or PENDING_SIGNATURE contract can still change
    private fun ensureNotClosed(contract: ContractEntity) {
        when (contract.status) {
            ContractStatus.EXPIRED -> throw ConflictException(Error(code = ErrorCode.CONTRACT_EXPIRED))
            ContractStatus.CANCELLED -> throw ConflictException(Error(code = ErrorCode.CONTRACT_CANCELLED))
            ContractStatus.SIGNED -> throw ConflictException(Error(code = ErrorCode.CONTRACT_ALREADY_SIGNED))
            ContractStatus.DRAFT, ContractStatus.PENDING_SIGNATURE -> {}
            else -> throw ConflictException(Error(code = ErrorCode.CONTRACT_NOT_PENDING))
        }
    }

    private fun isSignatureLate(contract: ContractEntity, now: Date): Boolean =
        contract.status == ContractStatus.PENDING_SIGNATURE && contract.signatureDeadline?.before(now) == true

    private fun invalidParameter(name: String, value: String): BadRequestException {
        return BadRequestException(
            error = Error(
                code = ErrorCode.HTTP_INVALID_PARAMETER,
                parameter = Parameter(
                    name = name,
                    type = ParameterType.PARAMETER_TYPE_PAYLOAD,
                    value = value,
                ),
            )
        )
    }
}
