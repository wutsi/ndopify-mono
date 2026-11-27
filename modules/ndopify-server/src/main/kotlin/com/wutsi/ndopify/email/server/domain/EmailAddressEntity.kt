package com.wutsi.ndopify.email.server.domain

import com.wutsi.ndopify.platform.mail.verification.Verdict
import com.wutsi.ndopify.platform.mail.verification.VerificationReason
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.Date

/**
 * The verdict SES Email Validation last gave an address: one row per distinct address, global (no tenant).
 *
 * Only a definitive answer is stored, so [score] is never null here. [reason] and the six verdicts are kept so that a
 * policy change costs no new call.
 */
@Entity
@Table(name = "T_EMAIL_ADDRESS")
data class EmailAddressEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** The SHA-256 of the normalized address, in lowercase hex: the table holds no plaintext address. */
    val emailHash: String = "",

    val score: Double? = null,

    val reason: VerificationReason = VerificationReason.NONE,

    val hasValidSyntax: Verdict = Verdict.HIGH,

    val hasValidDnsRecords: Verdict = Verdict.HIGH,

    val mailboxExists: Verdict = Verdict.HIGH,

    val isRoleAddress: Verdict = Verdict.HIGH,

    val isDisposable: Verdict = Verdict.HIGH,

    val isRandomInput: Verdict = Verdict.HIGH,

    val checkedAt: Date = Date(),

    val expiresAt: Date = Date(),
)
