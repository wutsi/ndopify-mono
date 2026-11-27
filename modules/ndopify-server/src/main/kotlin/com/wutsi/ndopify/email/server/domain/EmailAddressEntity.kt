package com.wutsi.ndopify.email.server.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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

    @Enumerated(EnumType.STRING)
    val reason: VerificationReason = VerificationReason.NONE,

    @Enumerated(EnumType.STRING)
    val hasValidSyntax: Verdict = Verdict.HIGH,

    @Enumerated(EnumType.STRING)
    val hasValidDnsRecords: Verdict = Verdict.HIGH,

    @Enumerated(EnumType.STRING)
    val mailboxExists: Verdict = Verdict.HIGH,

    @Enumerated(EnumType.STRING)
    val isRoleAddress: Verdict = Verdict.HIGH,

    @Enumerated(EnumType.STRING)
    val isDisposable: Verdict = Verdict.HIGH,

    @Enumerated(EnumType.STRING)
    val isRandomInput: Verdict = Verdict.HIGH,

    val checkedAt: Date = Date(),

    val expiresAt: Date = Date(),
)
