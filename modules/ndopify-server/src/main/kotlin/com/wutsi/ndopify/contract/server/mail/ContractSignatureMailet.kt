package com.wutsi.ndopify.contract.server.mail

import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Asks a party of a contract to read and sign it. */
@Service
class ContractSignatureMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: Votre contrat {{contract_number}} est prêt à être signé"
        private val DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        private val TYPES = mapOf(
            ContractType.SEARCH to "Mandat de recherche",
            ContractType.LISTING to "Mandat de mise en location",
            ContractType.LEASE_AGREEMENT to "Contrat de bail",
        )
    }

    fun send(contract: ContractEntity, party: ContractPartyEntity) {
        val tenant = tenantService.findById(contract.tenantId!!)
        mailService.send(
            Email(
                recipient = Receipient(email = party.legalEmail, displayName = party.legalName),
                subject = SUBJECT
                    .replace("{{tenant_name}}", tenant.name)
                    .replace("{{contract_number}}", contract.number),
                body = mailBodyResolver.resolve(
                    path = "/mail/contract/signature_request.mjml",
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to party.legalName,
                        "contract_type" to (TYPES[contract.type] ?: "Contrat"),
                        "contract_number" to contract.number,
                        // The party reads it in its local time
                        "deadline" to contract.signatureDeadline?.toInstant()?.atZone(ZoneId.of(tenant.timeZone))
                            ?.format(DEADLINE_FORMAT),
                        // Shows the contract and signs it: the e-mail never links the document itself
                        "sign_url" to tenant.partnerCentralUrl?.let { url ->
                            "$url/contracts/${contract.id}/sign?party-id=${party.id}"
                        },
                    )
                )
            )
        )
    }
}
