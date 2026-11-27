package com.wutsi.ndopify.email.server.service

import com.wutsi.ndopify.email.server.dao.EmailAddressRepository
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Clock
import java.util.Date

/**
 * The e-mail verification policy, above the [EmailVerifier] port: it normalizes the address, refuses a locally
 * malformed one before any paid call, serves the cached verdict inside its TTL, applies the cost guard, verifies and
 * caches the definitive answer, and refuses a deliverability LOW.
 *
 * The order of [ensureDeliverable] is: provider off (nothing at all), local syntax, cache, budget, verify, cache,
 * refuse. It fails open on every provider failure (accepted, nothing cached), and never logs the address — only its
 * domain.
 */
@Service
class EmailVerificationService(
    private val verifier: EmailVerifier,
    private val dao: EmailAddressRepository,
    private val throttle: EmailVerificationThrottle,
    private val clock: Clock,
    @param:Value("\${ndopify.email.verification.provider}") private val provider: String,
    @param:Value("\${ndopify.email.verification.ttl.valid-days}") private val validTtlDays: Long,
    @param:Value("\${ndopify.email.verification.ttl.invalid-days}") private val invalidTtlDays: Long,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(EmailVerificationService::class.java)
        private const val PROVIDER_SES = "ses"
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }

    /**
     * Accepts the address, or throws `400 EMAIL_ADDRESS_INVALID` with the `email` payload parameter when SES rates it
     * undeliverable. With `provider=none` it returns before touching the cache, the budget or the verifier.
     */
    fun ensureDeliverable(email: String, clientIp: String?) {
        if (provider != PROVIDER_SES) return

        val normalized = email.trim().lowercase()
        if (!EmailSyntax.isValid(normalized)) throw invalid()

        val hash = sha256(normalized)
        val now = Date(clock.millis())
        val cached = dao.findByEmailHash(hash)
        if (cached != null && cached.expiresAt.after(now)) {
            if (cached.score == 0.0) throw invalid()
            return
        }

        // Past the budget the submission is accepted without a paid call, and nothing is cached (Review Focus 4)
        if (!throttle.tryAcquire(clientIp)) return

        val verification = try {
            verifier.verify(normalized)
        } catch (ex: Exception) {
            // Fail open: only the domain is logged, never the address nor the provider's message
            LOGGER.warn("E-mail verification failed: {} ({})", ex.javaClass.simpleName, domain(normalized))
            null
        }
        val score = verification?.score ?: return

        // The upsert's own transaction commits the verdict even when the mandate rolls back on the refusal below
        val ttl = if (score >= 0.5) validTtlDays else invalidTtlDays
        dao.upsert(
            emailHash = hash,
            score = score,
            reason = verification.reason.name,
            hasValidSyntax = verification.hasValidSyntax.name,
            hasValidDnsRecords = verification.hasValidDnsRecords.name,
            mailboxExists = verification.mailboxExists.name,
            isRoleAddress = verification.isRoleAddress.name,
            isDisposable = verification.isDisposable.name,
            isRandomInput = verification.isRandomInput.name,
            checkedAt = now,
            expiresAt = Date(now.time + ttl * DAY_MILLIS),
        )

        if (score == 0.0) throw invalid()
    }

    private fun invalid() = BadRequestException(
        Error(
            code = ErrorCode.EMAIL_ADDRESS_INVALID,
            parameter = Parameter(name = "email", type = ParameterType.PARAMETER_TYPE_PAYLOAD),
        ),
    )

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return BigInteger(1, digest).toString(16).padStart(64, '0')
    }

    private fun domain(email: String): String = email.substringAfterLast('@')
}
