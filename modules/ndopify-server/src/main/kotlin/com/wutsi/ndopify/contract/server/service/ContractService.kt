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
import org.apache.commons.validator.routines.InetAddressValidator
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
    private val signerService: ContractSignerService,
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
            if (request.contractPartyIds.isNotEmpty()) {
                val sub = query!!.subquery(String::class.java)
                val party = sub.from(ContractPartyEntity::class.java)
                // With parties too (always for a non-admin, forced to its own party), on the same row: the
                // contract party must be one of those parties', not just any party of the same contract
                val conditions = mutableListOf(party.get<String>("id").`in`(request.contractPartyIds))
                if (request.partyIds.isNotEmpty()) {
                    conditions.add(party.get<Long>("partyId").`in`(request.partyIds))
                }
                sub.select(party.get<ContractEntity>("contract").get("id"))
                    .where(*conditions.toTypedArray())
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

    // The signed party of a contract with that hash: an unknown contract, hash or unsigned party are all the same 404
    fun findSignature(id: String, hash: String): Pair<ContractEntity, ContractPartyEntity> {
        val contract = findByIdOrNull(id)
        val party = contract?.let { partyDao.findByContractAndSignatureHash(it, hash) }
        if (contract == null || party == null || !party.signed || party.signedAt == null) {
            throw NotFoundException(error = Error(code = ErrorCode.CONTRACT_SIGNATURE_NOT_FOUND))
        }
        return Pair(contract, party)
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
        if (request.parties.none { party -> party.signatureRequired }) {
            throw invalidParameter("parties.signatureRequired", "false") // Nobody could make it SIGNED
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
                    signatureRequired = party.signatureRequired,
                )
            )
        }
        return contract
    }

    /**
     * Sets the URL of the contract's document, while it can still be signed: DRAFT or PENDING_SIGNATURE. Locked like a
     * signature, so that the last party can't sign it while its document changes. [contentHash] is the caller's, of
     * the bytes it stored (ContractHasher.contentHash): null when unknown (ex: POST /v1/contracts/{id}/content), which
     * clears the previous content's.
     */
    @Transactional
    fun setContent(id: String, request: SetContractRequest, contentHash: String? = null): ContractEntity {
        val contract = findByIdForUpdate(id)
        ensureNotClosed(contract)
        return dao.save(contract.copy(contentUrl = request.url.trim(), contentHash = contentHash))
    }

    /**
     * Sends a DRAFT or PENDING_SIGNATURE contract to its parties for signature: it becomes PENDING_SIGNATURE, its
     * parties have `ttl-days` from now to sign it (each request resets the deadline), and are e-mailed after commit, by
     * a listener of [ContractSignatureRequestedEvent]. Locked like a signature, so that it can't expire while its
     * deadline is reset.
     */
    @Transactional
    fun requestSignature(id: String): ContractEntity {
        val contract = findByIdForUpdate(id)

        ensureNotClosed(contract)
        signerService.grantAccess(partyDao.findByContract(contract)) // Before the save: a 409 leaves the contract as it was
        val now = Date(clock.millis()) // Read here, after the caller's own changes (ex: the mandate's modifiedAt)
        val pending = dao.save(
            contract.copy(
                status = ContractStatus.PENDING_SIGNATURE,
                signatureRequestedAt = now,
                signatureDeadline = Date(now.time + Duration.ofDays(signatureTtlDays).toMillis()),
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
     * E-mails each party that must sign the contract and hasn't yet. Returns how many e-mails were sent. One party's
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
            .filter { party -> party.signatureRequired && !party.signed }
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
     * Only a party whose signature is required can sign, and only the contract's content: [SignContractRequest.contentHash]
     * must be its `contentHash`, so a contract without one can't be signed. The contract is SIGNED once all those parties have signed, and publishes a [ContractSignedEvent]: it's valid from
     * this last signature, for its `durationMonths` (added in the tenant's time zone, so a month ends on the same day).
     */
    @Transactional
    fun sign(id: String, request: SignContractRequest): ContractEntity {
        validate(request)
        val contract = findByIdForUpdate(id)
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
        if (!party.signatureRequired) {
            throw ConflictException(error = Error(code = ErrorCode.CONTRACT_PARTY_SIGNATURE_NOT_REQUIRED))
        }
        if (party.signed) {
            throw ConflictException(error = Error(code = ErrorCode.CONTRACT_PARTY_ALREADY_SIGNED))
        }
        // Only the content the signer was shown, and only a content whose hash is known: it's in the signature's hash
        val contentHash = contract.contentHash
        if (contentHash == null || !contentHash.equals(request.contentHash.trim(), ignoreCase = true)) {
            throw ConflictException(error = Error(code = ErrorCode.CONTRACT_CONTENT_MISMATCH))
        }

        val signed = party.copy(
            signed = true,
            signatureSalt = ContractHasher.newSalt(),
            ipAddress = request.ipAddress,
            userAgent = request.userAgent,
            // To the second, as stored (DATETIME): the signature's hash must be recomputable from the stored row
            signedAt = Date(now.time / 1000 * 1000),
        )
        partyDao.save(signed.copy(signatureHash = ContractHasher.signatureHash(contract, signed)))

        val allSigned = parties
            .filter { other -> other.signatureRequired }
            .all { other -> other.id == party.id || other.signed }
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
     * Sets the signed PDF of a SIGNED contract: its [url] and [hash] (SHA-256 of its bytes). Locked like a signature.
     * The first one wins: a contract that already has one, or is no longer SIGNED, is returned unchanged.
     */
    @Transactional
    fun setSignedContent(id: String, url: String, hash: String): ContractEntity {
        val contract = findByIdForUpdate(id)
        if (contract.status != ContractStatus.SIGNED || contract.signedContentUrl != null) {
            return contract
        }
        return dao.save(contract.copy(signedContentUrl = url, signedContentHash = hash))
    }

    /**
     * Cancels a contract that is still in force or can still be signed (DRAFT, PENDING_SIGNATURE, SIGNED): it can no
     * longer be signed, nor expired by `ContractJobs`. An EXPIRED or CANCELLED one is returned unchanged. Locked like a
     * signature, so that its last party can't sign it meanwhile. Called by `MandateService.cancel`, under the mandate's
     * lock.
     */
    @Transactional
    fun cancel(id: String): ContractEntity {
        val contract = findByIdForUpdate(id)
        return when (contract.status) {
            ContractStatus.DRAFT, ContractStatus.PENDING_SIGNATURE, ContractStatus.SIGNED ->
                dao.save(contract.copy(status = ContractStatus.CANCELLED))
            else -> contract
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

    /** The SIGNED contracts without signed PDF, signed between [from] (included) and [to], oldest signature first. */
    fun findIdsWithoutSignedContent(from: Date, to: Date): List<String> =
        dao.findIdsWithoutSignedContent(
            signed = ContractStatus.SIGNED,
            from = from,
            to = to,
            page = PageRequest.of(0, BATCH_SIZE),
        )

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

    /**
     * Every write of a contract locks it first (content, request for signature, signature, expiry, cancellation), so that they're
     * serialized: each one checks the status the previous one left, rather than failing on its version. Public for
     * the mandate's writes that also change its contract: they lock the contract before the mandate, in the order of
     * a signature (`sign`, then `MandateService.onContractSigned`), so that they can't deadlock with it.
     */
    fun findByIdForUpdate(id: String): ContractEntity {
        return dao.findByIdForUpdate(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.CONTRACT_NOT_FOUND))
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

    // The signature's evidence: a real address (no DNS lookup). Its length is checked by SignContractRequest's @Size
    private fun validate(request: SignContractRequest) {
        val ipAddress = request.ipAddress
        if (ipAddress != null && !InetAddressValidator.getInstance().isValid(ipAddress)) {
            throw invalidParameter("ipAddress", ipAddress)
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
