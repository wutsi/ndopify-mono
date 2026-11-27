package com.wutsi.ndopify.platform.email

import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.EmailAddressInsightsConfidenceVerdict
import software.amazon.awssdk.services.sesv2.model.EmailAddressInsightsVerdict
import software.amazon.awssdk.services.sesv2.model.GetEmailAddressInsightsRequest

/**
 * Verifies an address with SES Email Validation, one `GetEmailAddressInsights` call per address.
 *
 * The score follows the deliverability only: the lowest of `HasValidSyntax`, `HasValidDnsRecords` and `MailboxExists`
 * (HIGH 1.0, MEDIUM 0.5, LOW 0.0). `IsRoleAddress`, `IsDisposable` and `IsRandomInput` never change it; they only name
 * the reason. A missing evaluation counts as HIGH, so an incomplete answer never refuses. It catches nothing: a failed
 * call is the policy's problem.
 */
class EmailVerifierSES(private val client: SesV2Client) : EmailVerifier {
    override fun verify(email: String): EmailVerification {
        val validation = client
            .getEmailAddressInsights(GetEmailAddressInsightsRequest.builder().emailAddress(email).build())
            .mailboxValidation()
        val evaluations = validation?.evaluations()

        val syntax = verdict(evaluations?.hasValidSyntax())
        val dns = verdict(evaluations?.hasValidDnsRecords())
        val mailbox = verdict(evaluations?.mailboxExists())
        val role = verdict(evaluations?.isRoleAddress())
        val disposable = verdict(evaluations?.isDisposable())
        val random = verdict(evaluations?.isRandomInput())

        val score = minOf(score(syntax), score(dns), score(mailbox))
        val reason = when {
            syntax == Verdict.LOW -> VerificationReason.SYNTAX_INVALID
            dns == Verdict.LOW -> VerificationReason.DOMAIN_INVALID
            mailbox == Verdict.LOW -> VerificationReason.MAILBOX_UNREACHABLE
            disposable == Verdict.LOW -> VerificationReason.DISPOSABLE
            role == Verdict.LOW -> VerificationReason.ROLE_ADDRESS
            random == Verdict.LOW -> VerificationReason.RANDOM_INPUT
            else -> VerificationReason.NONE
        }
        return EmailVerification(
            score = score,
            reason = reason,
            hasValidSyntax = syntax,
            hasValidDnsRecords = dns,
            mailboxExists = mailbox,
            isRoleAddress = role,
            isDisposable = disposable,
            isRandomInput = random,
        )
    }

    private fun verdict(evaluation: EmailAddressInsightsVerdict?): Verdict =
        when (evaluation?.confidenceVerdict()) {
            EmailAddressInsightsConfidenceVerdict.MEDIUM -> Verdict.MEDIUM
            EmailAddressInsightsConfidenceVerdict.LOW -> Verdict.LOW
            else -> Verdict.HIGH // null, or a verdict a newer SDK added: never refuse on it
        }

    private fun score(verdict: Verdict): Double =
        when (verdict) {
            Verdict.HIGH -> 1.0
            Verdict.MEDIUM -> 0.5
            Verdict.LOW -> 0.0
        }
}
