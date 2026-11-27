package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.OwnershipVerification
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationReason
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.document.DocumentDataCodec
import com.wutsi.ndopify.mandate.server.service.document.IdentityData
import com.wutsi.ndopify.mandate.server.service.document.TitleData
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.platform.util.KycUtils
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Whether a LISTING mandate's owner is verified (#297), derived on every read from the mandate's documents: an
 * identity document (`NATIONAL_ID` / `PASSPORT`) not expired in the tenant's time zone and naming the customer, and a
 * proof of ownership (`LAND_TITLE` / `CERTIFICATE_OF_OWNERSHIP`) whose owners name the same customer. Both checks are
 * required; an unusable, expired or wrongly-named document next to a valid one of the same kind is ignored.
 *
 * The documents are the record: nothing is stored, no state is written. A document only counts when `EXTRACTED` — it
 * then already passed `DocumentChecks`, so its type, sides and field confidences are never re-checked; only the expiry
 * and the name link are added. Its `problemCodes` are ignored as a verdict (the `DOCUMENT_EXPIRED` warning alone never
 * decides, the date does); an `*_UNUSABLE` reason carries them as details.
 *
 * Every document type but the four above is ignored: a `UTILITY_BILL`, a `LEASE_CONTRACT`, a `TAX_RECEIPT`, a
 * `SURVEY_PLAN`, a `POWER_OF_ATTORNEY` and the rest are not ownership evidence in v1. The verdict is type-agnostic
 * here; the endpoint that only answers for a LISTING mandate is `MandateEndpoints`.
 *
 * Never logs a name, a document number or any extracted value: the mandate's id, the status and the reason codes only.
 */
@Service
class OwnershipVerdictService(
    private val documentDao: MandateDocumentRepository,
    private val codec: DocumentDataCodec,
    private val customerService: CustomerService,
    private val tenantService: TenantService,
    private val logger: KVLogger,
    private val clock: Clock,
    @param:Value("\${ndopify.mandate.ownership.name-threshold}") private val nameThreshold: Double,
) {
    companion object {
        /** The types that prove who the customer is. */
        private val IDENTITY_TYPES = setOf(DocumentType.NATIONAL_ID, DocumentType.PASSPORT)

        /** The types that prove the customer owns the property; a lease or a utility bill never does in v1. */
        private val OWNERSHIP_TYPES = setOf(DocumentType.LAND_TITLE, DocumentType.CERTIFICATE_OF_OWNERSHIP)
    }

    /** How one check ended; only [Pass] and [InFlight] contribute no reason. */
    private sealed interface Outcome {
        object Pass : Outcome
        object Missing : Outcome
        object InFlight : Outcome
        object Expired : Outcome
        object Mismatch : Outcome
        data class Unusable(val details: List<String>) : Outcome
    }

    /**
     * The verdict of [mandate]: the tenant's today (`clock`, in the tenant's time zone), the customer's name and the
     * mandate's documents, newest first, as the repository returns them. `TenantContext` must hold the tenant, as for
     * every tenant-scoped read. Logs the verdict once, without any extracted value.
     */
    fun verify(mandate: MandateEntity): OwnershipVerification {
        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant in context")
        val tenant = tenantService.findById(tenantId)
        val today = LocalDate.now(clock.withZone(ZoneId.of(tenant.timeZone)))
        val customerName = customerService.findById(mandate.customerId).party.fullName()
        val verification = evaluate(documentDao.findByMandateIdOrderByIdDesc(mandate.id!!), customerName, today)

        logger.add("mandate_id", mandate.id)
        logger.add("ownership_status", verification.status.name)
        logger.add("ownership_reason_codes", verification.reasons.map { reason -> reason.code })
        logger.log()
        return verification
    }

    /**
     * The verdict over [documents] for [today]: `VERIFIED` when both checks pass, `FAILED` when either is decisive
     * (expired, unusable or naming another person), `PENDING` otherwise. Reasons come one per failed check, identity
     * first; an in-flight check contributes none, a missing one its `*_MISSING`, an unusable one the union of its
     * documents' problem codes (deduplicated, in document order).
     */
    internal fun evaluate(
        documents: List<MandateDocumentEntity>,
        customerName: String,
        today: LocalDate,
    ): OwnershipVerification {
        val identity = identityOutcome(documents.filter { it.type in IDENTITY_TYPES }, customerName, today)
        val ownership = ownershipOutcome(documents.filter { it.type in OWNERSHIP_TYPES }, customerName)

        val status = when {
            identity is Outcome.Pass && ownership is Outcome.Pass -> OwnershipVerificationStatus.VERIFIED
            identity.isDecisive() || ownership.isDecisive() -> OwnershipVerificationStatus.FAILED
            else -> OwnershipVerificationStatus.PENDING
        }
        return OwnershipVerification(
            status = status,
            reasons = listOfNotNull(reason(identity, identityCheck = true), reason(ownership, identityCheck = false)),
        )
    }

    /**
     * The identity check: `Pass` on an `EXTRACTED` candidate whose expiry is today or later in the tenant's zone and
     * whose name matches; then, in this order, `InFlight` when a candidate is still `UPLOADED` / `EXTRACTING` (a valid
     * one may still arrive), `Unusable` when an extracted one can't be decoded or has no readable expiry, `Expired`,
     * `Mismatch`, and `Unusable` when only `FAILED` / `UNKNOWN` rows remain.
     */
    private fun identityOutcome(
        candidates: List<MandateDocumentEntity>,
        customerName: String,
        today: LocalDate,
    ): Outcome {
        if (candidates.isEmpty()) {
            return Outcome.Missing
        }
        val extracted = candidates.filter { it.status == DocumentStatus.EXTRACTED }
        val data = extracted.associateWith { document -> identityData(document) }
        val valid = data.values.firstOrNull { identity ->
            val expiry = identity?.expiresAt()
            expiry != null && !expiry.isBefore(today) && matches(identity.fullName(), customerName)
        }
        if (valid != null) {
            return Outcome.Pass
        }
        if (candidates.any { it.isInFlight() }) {
            return Outcome.InFlight
        }
        if (extracted.any { document -> data[document] == null || data[document]?.expiresAt() == null }) {
            return Outcome.Unusable(details(candidates))
        }
        if (extracted.any { document -> data[document]?.expiresAt()?.isBefore(today) == true }) {
            return Outcome.Expired
        }
        if (extracted.isNotEmpty()) {
            return Outcome.Mismatch
        }
        return Outcome.Unusable(details(candidates))
    }

    /** The ownership check: `Pass` on an `EXTRACTED` candidate whose owners name the customer; then `InFlight`, `Unusable`, `Mismatch`, `Unusable`. */
    private fun ownershipOutcome(candidates: List<MandateDocumentEntity>, customerName: String): Outcome {
        if (candidates.isEmpty()) {
            return Outcome.Missing
        }
        val extracted = candidates.filter { it.status == DocumentStatus.EXTRACTED }
        val data = extracted.associateWith { document -> titleData(document) }
        val valid = data.values.firstOrNull { title ->
            title?.owners.orEmpty().any { owner -> matches(owner, customerName) }
        }
        if (valid != null) {
            return Outcome.Pass
        }
        if (candidates.any { it.isInFlight() }) {
            return Outcome.InFlight
        }
        if (extracted.any { document -> data[document] == null }) {
            return Outcome.Unusable(details(candidates))
        }
        if (extracted.isNotEmpty()) {
            return Outcome.Mismatch
        }
        return Outcome.Unusable(details(candidates))
    }

    /** The reason of a failed check; null for `Pass` and `InFlight`. */
    private fun reason(outcome: Outcome, identityCheck: Boolean): OwnershipVerificationReason? = when (outcome) {
        Outcome.Pass, Outcome.InFlight -> null
        Outcome.Missing -> OwnershipVerificationReason(
            if (identityCheck) OwnershipReasonCodes.ID_MISSING else OwnershipReasonCodes.DOCUMENT_MISSING,
        )
        Outcome.Expired -> OwnershipVerificationReason(OwnershipReasonCodes.ID_EXPIRED)
        Outcome.Mismatch -> OwnershipVerificationReason(
            if (identityCheck) OwnershipReasonCodes.ID_NAME_MISMATCH else OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH,
        )
        is Outcome.Unusable -> OwnershipVerificationReason(
            if (identityCheck) OwnershipReasonCodes.ID_UNUSABLE else OwnershipReasonCodes.DOCUMENT_UNUSABLE,
            outcome.details,
        )
    }

    private fun Outcome.isDecisive(): Boolean =
        this is Outcome.Expired || this is Outcome.Mismatch || this is Outcome.Unusable

    /**
     * A name matches [customerName] when [KycUtils.verifyName] scores at least [nameThreshold]; a blank name or a blank
     * customer name matches nothing (the customer with no name is never verified).
     */
    private fun matches(name: String?, customerName: String): Boolean =
        !name.isNullOrBlank() &&
            !customerName.isBlank() &&
            KycUtils.verifyName(name, customerName) * 100 >= nameThreshold

    /** The union of the candidates' problem codes, deduplicated, in document order (the repository's newest first). */
    private fun details(candidates: List<MandateDocumentEntity>): List<String> =
        candidates.flatMap { document -> document.problemCodes }.distinct()

    private fun identityData(document: MandateDocumentEntity): IdentityData? =
        codec.read(document.data, document.type, document.dataVersion) as? IdentityData

    private fun titleData(document: MandateDocumentEntity): TitleData? =
        codec.read(document.data, document.type, document.dataVersion) as? TitleData

    /** The name printed on the identity document; blank names never match. */
    private fun IdentityData.fullName(): String = listOfNotNull(firstName, lastName).joinToString(" ")

    private fun MandateDocumentEntity.isInFlight(): Boolean =
        status == DocumentStatus.UPLOADED || status == DocumentStatus.EXTRACTING
}
