package com.wutsi.ndopify.platform.email

/**
 * The verdict on an address: [score] is 1.0 for a HIGH deliverability, 0.5 for a MEDIUM, 0.0 for a LOW, and `null`
 * when SES could not answer; [reason] is the provider-neutral reason, computed with its precedence.
 *
 * The six raw verdicts are carried so that the cache keeps them: a policy change then costs no new call.
 */
data class EmailVerification(
    val score: Double?,
    val reason: VerificationReason,
    val hasValidSyntax: Verdict = Verdict.HIGH,
    val hasValidDnsRecords: Verdict = Verdict.HIGH,
    val mailboxExists: Verdict = Verdict.HIGH,
    val isRoleAddress: Verdict = Verdict.HIGH,
    val isDisposable: Verdict = Verdict.HIGH,
    val isRandomInput: Verdict = Verdict.HIGH,
)
