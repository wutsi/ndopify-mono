package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.service.OtpService.Companion.SUBJECT
import org.springframework.stereotype.Service

@Service
class OtpMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val membershipService: MembershipService,
) {
    fun send(code: String, authFactor: AuthFactorEntity) {
        val user = authFactor.user
        val tenant = resolveTenant()

        val expiresAt = requireNotNull(authFactor.expiresAt) { "An OTP must have an expiry" }
        val ttlMinutes = (expiresAt.time - authFactor.modifiedAt.time) / 60_000 // rounded down: never overstates

        val party = membershipService.findByUserOrNull(user)?.party
        val fullName = party?.fullName()?.takeIf { it.isNotBlank() }
        mailService.send(
            Email(
                recipient = Receipient(email = user.email, displayName = fullName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = "/mail/auth/otp.mjml",
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to (fullName ?: ""),
                        "otp_code" to code,
                        "ttl" to ttlMinutes,
                    )
                )
            )
        )
    }

    /**
     * The tenant of the request. Public so that `OtpService` can fail the request (400/404) before the email is
     * handed off to another thread.
     */
    fun resolveTenant(): TenantEntity {
        return tenantService.findById(membershipService.currentTenantId())
    }
}
