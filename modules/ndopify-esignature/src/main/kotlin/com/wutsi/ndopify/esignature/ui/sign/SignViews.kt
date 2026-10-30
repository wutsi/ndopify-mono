package com.wutsi.ndopify.esignature.ui.sign

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractParty
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.esignature.client.ContractContent
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.BLOCKED_CANCELLED
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.BLOCKED_EXPIRED
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.BLOCKED_NOT_REQUIRED
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.BLOCKED_NOT_YOURS
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.BLOCKED_SIGNED
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.BLOCKED_UNAVAILABLE
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.STEP_BLOCKED
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.STEP_CODE
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.STEP_DONE
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.STEP_INTRO
import com.wutsi.ndopify.esignature.ui.sign.SignView.Companion.STEP_SIGN
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

/** Builds the [SignView] of a contract party's link: which step or blocked state, and what it shows. */
@Service
class SignViews(
    private val clock: Clock,
    private val tenantContext: TenantContext,
) {
    companion object {
        // As ContractSignatureMailet's, so that the page names the contract as the e-mail did
        private val TYPES = mapOf(
            ContractType.SEARCH to "Mandat de recherche",
            ContractType.LISTING to "Mandat de mise en location",
            ContractType.LEASE_AGREEMENT to "Contrat de bail",
        )

        fun maskEmail(email: String): String {
            val at = email.indexOf('@')
            if (at <= 0) return email
            val name = email.substring(0, at)
            return name.take(if (name.length <= 2) 1 else 2) + "•••" + email.substring(at)
        }
    }

    /** Signed out: nothing about the contract is shown before sign-in. */
    fun intro(): SignView = SignView(step = STEP_INTRO)

    fun code(pending: PendingSignature): SignView =
        SignView(
            step = STEP_CODE,
            maskedEmail = maskEmail(pending.email),
            codeExpiresAt = formatter().time(Date(pending.expiresAt)),
        )

    /**
     * Signed in. [contract] is the one the link's contract party belongs to, as found by a search restricted to the
     * signer's party: null when the link is someone else's. The content is only fetched for the `sign` step.
     */
    fun build(
        contract: Contract?,
        contractPartyId: String,
        requestedStep: String?,
        fetchContent: (String) -> ContractContent?,
    ): SignView {
        // Not found by a search restricted to the caller's party: the link is someone else's
        contract ?: return SignView(step = STEP_BLOCKED, blocked = BLOCKED_NOT_YOURS)
        val party = contract.parties.find { party -> party.id == contractPartyId }
            ?: return blocked(BLOCKED_UNAVAILABLE, contract)

        val now = Date(clock.millis())
        val reason = when {
            party.signed -> if (requestedStep == STEP_DONE) return done(contract, party) else BLOCKED_SIGNED
            !party.signatureRequired -> BLOCKED_NOT_REQUIRED
            contract.status == ContractStatus.CANCELLED -> BLOCKED_CANCELLED
            // EXPIRED is only set by the daily job: a deadline already past is expired too
            contract.status == ContractStatus.EXPIRED || contract.signatureDeadline?.before(now) == true -> BLOCKED_EXPIRED
            contract.status == ContractStatus.PENDING_SIGNATURE -> null
            else -> BLOCKED_UNAVAILABLE
        }
        if (reason != null) {
            return blocked(reason, contract)
        }

        val content = contract.contentUrl?.let(fetchContent)
        return sign(contract, party, content, now)
    }

    private fun blocked(reason: String, contract: Contract): SignView {
        val formatter = formatter()
        return SignView(
            step = STEP_BLOCKED,
            blocked = reason,
            contractType = type(contract),
            contractNumber = contract.number,
            senderName = sender(contract),
            deadline = contract.signatureDeadline?.let(formatter::date),
        )
    }

    private fun done(contract: Contract, party: ContractParty): SignView {
        val formatter = formatter()
        return SignView(
            step = STEP_DONE,
            contractType = type(contract),
            contractNumber = contract.number,
            signerName = party.legalName,
            signerFirstName = firstName(party),
            signedAt = party.signedAt?.let { date -> "${formatter.date(date)} à ${formatter.time(date)}" },
        )
    }

    private fun sign(contract: Contract, party: ContractParty, content: ContractContent?, now: Date): SignView {
        val formatter = formatter()
        return SignView(
            step = STEP_SIGN,
            contractType = type(contract),
            contractNumber = contract.number,
            senderName = sender(contract),
            deadline = contract.signatureDeadline?.let(formatter::date),
            signerName = party.legalName,
            signerFirstName = firstName(party),
            signDate = formatter.date(now),
            contentHtml = content?.html,
            contentUrl = contract.contentUrl?.takeIf { content != null },
            contentHash = content?.hash,
            // Never signable unseen
            canSign = content != null,
        )
    }

    private fun type(contract: Contract): String = TYPES[contract.type] ?: "Contrat"

    // The agent who sent the contract to sign
    private fun sender(contract: Contract): String? =
        contract.parties.find { party -> party.role == ContractPartyRole.AGENT }?.legalName

    private fun firstName(party: ContractParty): String = party.legalName.trim().substringBefore(' ')

    private fun formatter(): TenantFormatter = TenantFormatter(tenantContext.getTenant())
}
