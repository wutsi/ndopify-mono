package com.wutsi.ndopify.contract.server.service

import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.contract.server.dao.ContractPartyRepository
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
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
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
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
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ContractService::class.java)
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
        if (!request.validTo.after(request.validFrom)) {
            throw invalidParameter("validTo", request.validTo.toString())
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
                contentUrl = request.contentUrl,
                validFrom = request.validFrom,
                validUntil = request.validTo,
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
     * Sends a DRAFT contract to its parties for signature: it becomes PENDING_SIGNATURE, and its parties are e-mailed
     * after commit, by a listener of [ContractSignatureRequestedEvent].
     */
    @Transactional
    fun requestSignature(id: String): ContractEntity {
        var contract = findById(id)

        if (contract.status == ContractStatus.EXPIRED || Date(clock.millis()).after(contract.validUntil)) {
            throw ConflictException(Error(code = ErrorCode.CONTRACT_EXPIRED))
        }

        if (contract.status == ContractStatus.CANCELLED) {
            throw ConflictException(Error(code = ErrorCode.CONTRACT_CANCELLED))
        }

        if (contract.status == ContractStatus.SIGNED) {
            throw ConflictException(Error(code = ErrorCode.CONTRACT_ALREADY_SIGNED))
        }

        if (contract.status == ContractStatus.DRAFT) {
            contract = dao.save(contract.copy(status = ContractStatus.PENDING_SIGNATURE))
        }
        eventPublisher.publishEvent(
            ContractSignatureRequestedEvent(
                contractId = contract.id,
                tenantId = TenantContext.get()!!
            )
        )
        return contract
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
     * The contract is SIGNED once all its parties have signed, and publishes a [ContractSignedEvent].
     */
    @Transactional
    fun sign(id: String, request: SignContractRequest): ContractEntity {
        val contract = dao.findByIdForUpdate(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.CONTRACT_NOT_FOUND))
        if (contract.status != ContractStatus.PENDING_SIGNATURE) {
            throw ConflictException(error = Error(code = ErrorCode.CONTRACT_NOT_PENDING))
        }

        val now = Date(clock.millis())
        if (now.after(contract.validUntil)) {
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
            val signed = dao.save(contract.copy(status = ContractStatus.SIGNED, signedAt = now))
            eventPublisher.publishEvent(ContractSignedEvent(contractId = signed.id, tenantId = TenantContext.get()!!))
            signed
        } else {
            contract
        }
    }

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
