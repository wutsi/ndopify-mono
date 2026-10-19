package com.wutsi.ndopify.contract.server.service

import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.server.service.MembershipService
import com.wutsi.ndopify.security.server.service.UserService
import org.springframework.stereotype.Service

/**
 * Gives the parties asked to sign a contract what they need to sign in to the e-signature portal: a membership of the
 * tenant, as their party, with `e-signature` access. Created here, when a signature is requested, rather than when an
 * agent or a customer is: only the people actually asked to sign get an account. Runs in the caller's transaction.
 */
@Service
class ContractSignerService(
    private val userService: UserService,
    private val membershipService: MembershipService,
    private val partyService: PartyService,
) {
    fun grantAccess(parties: List<ContractPartyEntity>) {
        parties
            .filter { party -> party.signatureRequired && !party.signed }
            .forEach { party -> grantAccess(party) }
    }

    private fun grantAccess(contractParty: ContractPartyEntity) {
        // An agent already has one; anyone else gets a user from the address the signature e-mail goes to
        val membership = membershipService.findByPartyIdOrNull(contractParty.partyId)
            ?: membershipService.findOrCreate(
                user = userService.findByEmailOrCreate(contractParty.legalEmail),
                party = partyService.findById(contractParty.partyId),
            )

        // The address is already a member's, as another party: that member could never sign as this one
        if (membership.party?.id != contractParty.partyId) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.CONTRACT_PARTY_MEMBERSHIP_CONFLICT,
                    message = "${contractParty.legalEmail} is a member as another party",
                )
            )
        }
        membershipService.grantAccess(membership, ApplicationCode.E_SIGNATURE)
    }
}
