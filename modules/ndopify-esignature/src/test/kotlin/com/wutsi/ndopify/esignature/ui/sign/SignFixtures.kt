package com.wutsi.ndopify.esignature.ui.sign

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractParty
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.esignature.client.ContractContent
import java.time.Instant
import java.util.Date

/** A search mandate waiting for its customer's signature; the agent is a party that doesn't sign. */
object SignFixtures {
    // 13:32 UTC: 14:32 in Douala
    val NOW: Instant = Instant.parse("2026-10-18T13:32:00Z")

    const val CONTRACT_PARTY_ID = "cp-customer"
    const val CONTENT_URL = "https://storage.ndopify.cm/contracts/MNDT-2026-000042.html"
    const val CONTENT_HTML = "<h3>Mandat de recherche</h3><p>Le Mandant confie au Mandataire…</p>"
    const val CONTENT_HASH = "4f1c0a7e3b2d9c8e5a6f1b0d2c3e4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f"
    val CONTENT = ContractContent(html = CONTENT_HTML, hash = CONTENT_HASH)

    val CUSTOMER = ContractParty(
        id = CONTRACT_PARTY_ID,
        role = ContractPartyRole.MANDATOR,
        partyId = 100,
        legalName = "Ray Sponsible",
        legalEmail = "ray.sponsible@gmail.com",
    )

    val AGENT = ContractParty(
        id = "cp-agent",
        role = ContractPartyRole.AGENT,
        partyId = 200,
        legalName = "Marie Ngo Biyong",
        legalEmail = "marie@gmail.com",
        signatureRequired = false,
    )

    val CONTRACT = Contract(
        id = "contract-1",
        number = "MNDT-2026-000042",
        type = ContractType.SEARCH,
        status = ContractStatus.PENDING_SIGNATURE,
        contentUrl = CONTENT_URL,
        parties = listOf(CUSTOMER, AGENT),
        signatureDeadline = Date.from(Instant.parse("2026-10-25T13:32:00Z")),
    )

    fun signed(contract: Contract = CONTRACT): Contract =
        contract.copy(
            parties = contract.parties.map { party ->
                if (party.id == CONTRACT_PARTY_ID) {
                    party.copy(signed = true, signedAt = Date.from(NOW))
                } else {
                    party
                }
            },
        )
}
