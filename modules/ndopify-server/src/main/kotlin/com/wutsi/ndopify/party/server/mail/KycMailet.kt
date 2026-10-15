package com.wutsi.ndopify.party.server.mail

import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

@Service
class KycMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT_REJECTED = "{{tenant_name}}: Mise à jour concernant votre vérification KYC"
        const val SUBJECT_VERIFIED = "{{tenant_name}}: Votre vérification KYC a réussi"
    }

    fun send(case: KycCaseEntity) {
        val party = case.party

        // The tenant of the request wins; fall back to the user's home tenant.
        val tenantId = TenantContext.get()
            ?: party.tenantId
            ?: throw BadRequestException(
                Error(
                    code = ErrorCode.HTTP_MISSING_PARAMETER,
                    parameter = Parameter(name = HttpHeader.TENANT_ID, type = ParameterType.PARAMETER_TYPE_HEADER),
                )
            )
        val tenant = tenantService.findById(tenantId)

        val fullName = party.fullName()
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = when (case.status) {
                    KycStatus.VERIFIED -> SUBJECT_VERIFIED.replace("{{tenant_name}}", tenant.name)
                    else -> SUBJECT_REJECTED.replace("{{tenant_name}}", tenant.name)
                },
                body = mailBodyResolver.resolve(
                    path = when (case.status) {
                        KycStatus.VERIFIED -> "/mail/party/kyc_verified.mjml"
                        else -> "/mail/party/kyc_rejected.mjml"
                    },
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "kyc_url" to "${tenant.partnerCentralUrl}/kyc",
                        "portal_url" to tenant.partnerCentralUrl,
                    )
                )
            )
        )
    }
}
