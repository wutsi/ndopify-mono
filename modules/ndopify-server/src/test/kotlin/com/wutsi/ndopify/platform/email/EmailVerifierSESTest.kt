package com.wutsi.ndopify.platform.email

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.EmailAddressInsightsConfidenceVerdict
import software.amazon.awssdk.services.sesv2.model.EmailAddressInsightsMailboxEvaluations
import software.amazon.awssdk.services.sesv2.model.EmailAddressInsightsVerdict
import software.amazon.awssdk.services.sesv2.model.GetEmailAddressInsightsRequest
import software.amazon.awssdk.services.sesv2.model.GetEmailAddressInsightsResponse
import software.amazon.awssdk.services.sesv2.model.MailboxValidation
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailVerifierSESTest {
    private lateinit var client: SesV2Client
    private lateinit var verifier: EmailVerifierSES

    @BeforeEach
    fun setUp() {
        client = mock()
        verifier = EmailVerifierSES(client)
    }

    @Test
    fun `maps a HIGH address to a valid verdict`() {
        respond()

        val verdict = verifier.verify("ray@gmail.com")

        assertEquals(1.0, verdict.score)
        assertEquals(VerificationReason.NONE, verdict.reason)
    }

    @Test
    fun `an overall invalid verdict with only a disposable LOW keeps the deliverability score`() {
        respond(
            isValid = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
            disposable = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
        )

        val result = verifier.verify("ray@mailinator.com")

        assertEquals(1.0, result.score)
        assertEquals(VerificationReason.DISPOSABLE, result.reason)
        assertEquals(Verdict.LOW, result.isDisposable)
    }

    @Test
    fun `a missing mailbox is undeliverable`() {
        respond(mailbox = verdict(EmailAddressInsightsConfidenceVerdict.LOW))

        val result = verifier.verify("nobody@example.com")

        assertEquals(0.0, result.score)
        assertEquals(VerificationReason.MAILBOX_UNREACHABLE, result.reason)
    }

    @Test
    fun `an invalid syntax is undeliverable`() {
        respond(syntax = verdict(EmailAddressInsightsConfidenceVerdict.LOW))

        assertEquals(0.0, verifier.verify("ray@").score)
        assertEquals(VerificationReason.SYNTAX_INVALID, verifier.verify("ray@").reason)
    }

    @Test
    fun `an invalid domain is undeliverable`() {
        respond(dns = verdict(EmailAddressInsightsConfidenceVerdict.LOW))

        assertEquals(0.0, verifier.verify("ray@gmail.com").score)
        assertEquals(VerificationReason.DOMAIN_INVALID, verifier.verify("ray@gmail.com").reason)
    }

    @Test
    fun `an overall invalid verdict with only a role LOW keeps the deliverability score`() {
        respond(
            isValid = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
            role = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
        )

        assertEquals(1.0, verifier.verify("contact@example.com").score)
        assertEquals(VerificationReason.ROLE_ADDRESS, verifier.verify("contact@example.com").reason)
    }

    @Test
    fun `an overall invalid verdict with only a random-input LOW keeps the deliverability score`() {
        respond(
            isValid = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
            random = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
        )

        assertEquals(1.0, verifier.verify("a8f7d6s5@example.com").score)
        assertEquals(VerificationReason.RANDOM_INPUT, verifier.verify("a8f7d6s5@example.com").reason)
    }

    @Test
    fun `a MEDIUM deliverability scores half`() {
        respond(mailbox = verdict(EmailAddressInsightsConfidenceVerdict.MEDIUM))

        assertEquals(0.5, verifier.verify("ray@gmail.com").score)
        assertEquals(VerificationReason.NONE, verifier.verify("ray@gmail.com").reason)
    }

    @Test
    fun `the reason follows the precedence`() {
        respond(
            syntax = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
            dns = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
            mailbox = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
            role = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
            disposable = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
            random = verdict(EmailAddressInsightsConfidenceVerdict.LOW),
        )

        assertEquals(VerificationReason.SYNTAX_INVALID, verifier.verify("ray@gmail.com").reason)
    }

    @Test
    fun `a missing evaluation counts as HIGH`() {
        val response = GetEmailAddressInsightsResponse.builder()
            .mailboxValidation(MailboxValidation.builder().build())
            .build()
        doReturn(response).whenever(client).getEmailAddressInsights(any<GetEmailAddressInsightsRequest>())

        val result = verifier.verify("ray@gmail.com")

        assertEquals(1.0, result.score)
        assertEquals(VerificationReason.NONE, result.reason)
        assertEquals(Verdict.HIGH, result.hasValidSyntax)
    }

    @Test
    fun `one call is made with the address`() {
        respond()

        verifier.verify("ray@gmail.com")

        val captor = argumentCaptor<GetEmailAddressInsightsRequest>()
        verify(client).getEmailAddressInsights(captor.capture())
        assertEquals("ray@gmail.com", captor.firstValue.emailAddress())
    }

    private fun respond(
        isValid: EmailAddressInsightsVerdict = verdict(),
        syntax: EmailAddressInsightsVerdict = verdict(),
        dns: EmailAddressInsightsVerdict = verdict(),
        mailbox: EmailAddressInsightsVerdict = verdict(),
        role: EmailAddressInsightsVerdict = verdict(),
        disposable: EmailAddressInsightsVerdict = verdict(),
        random: EmailAddressInsightsVerdict = verdict(),
    ) {
        val evaluations = EmailAddressInsightsMailboxEvaluations.builder()
            .hasValidSyntax(syntax)
            .hasValidDnsRecords(dns)
            .mailboxExists(mailbox)
            .isRoleAddress(role)
            .isDisposable(disposable)
            .isRandomInput(random)
            .build()
        val response = GetEmailAddressInsightsResponse.builder()
            .mailboxValidation(MailboxValidation.builder().isValid(isValid).evaluations(evaluations).build())
            .build()
        doReturn(response).whenever(client).getEmailAddressInsights(any<GetEmailAddressInsightsRequest>())
    }

    private fun verdict(value: EmailAddressInsightsConfidenceVerdict = EmailAddressInsightsConfidenceVerdict.HIGH) =
        EmailAddressInsightsVerdict.builder().confidenceVerdict(value).build()
}
