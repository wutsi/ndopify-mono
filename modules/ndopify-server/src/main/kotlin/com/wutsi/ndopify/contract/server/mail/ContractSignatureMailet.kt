package com.wutsi.ndopify.contract.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.MandateConversationService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Asks a party of a contract to read and sign it. A mandate's contract joins the customer's conversation about that
 * mandate ([MandateConversationService]): its subject names the mandate, not the request. A lease keeps the platform
 * subject and carries no thread header, since it has no mandate to thread on.
 */
@Service
class ContractSignatureMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val conversationService: MandateConversationService,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: Votre contrat {{contract_number}} est prêt à être signé"

        private val TYPES = mapOf(
            ContractType.SEARCH to "Mandat de recherche",
            ContractType.LISTING to "Mandat de mise en location",
            ContractType.SALE_SEARCH to "Mandat de recherche (achat)",
            ContractType.SALE_LISTING to "Mandat de vente",
            ContractType.LEASE_AGREEMENT to "Contrat de bail",
        )
    }

    fun send(
        contract: ContractEntity,
        contractParty: ContractPartyEntity,
        agent: AgentEntity? = null,
        mandate: MandateEntity? = null,
    ) {
        val tenant = tenantService.findById(contract.tenantId!!)
        val conversation = mandate?.let { conversationService.of(it.id!!) }
        val agentContext = agent?.let {
            AgentMailContext.customer(it, TenantFormatter(tenant)) +
                mapOf("agent_whatsapp_reply_url" to AgentMailContext.replyUrl(it, "de votre contrat ${contract.number}"))
        } ?: emptyMap()
        mailService.send(
            Email(
                sender = agent?.let { AgentMailContext.from(it) },
                recipient = Receipient(email = contractParty.legalEmail, displayName = contractParty.legalName),
                subject = conversation?.subject
                    ?: SUBJECT.replace("{{tenant_name}}", tenant.name).replace("{{contract_number}}", contract.number),
                messageId = conversation?.messageId,
                inReplyTo = conversation?.inReplyTo,
                references = conversation?.references ?: emptyList(),
                body = mailBodyResolver.resolve(
                    path = "/mail/contract/signature_request.mjml",
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to contractParty.legalName,
                        "contract_type" to (TYPES[contract.type] ?: "Contrat"),
                        "contract_number" to contract.number,
                        // The party reads it in its local time
                        "deadline" to contract.signatureDeadline?.let { TenantFormatter(tenant).date(it) },
                        // Shows the contract and signs it: the e-mail never links the document itself
                        "sign_url" to tenant.eSignaturePortalUrl?.let { url -> "$url/sign/${contractParty.id}" },
                    ) + agentContext,
                ),
            )
        )
    }
}
