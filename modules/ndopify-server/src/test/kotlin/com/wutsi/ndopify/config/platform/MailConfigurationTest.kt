package com.wutsi.ndopify.config.platform

import com.nhaarman.mockitokotlin2.mock
import org.springframework.mail.javamail.JavaMailSender
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.sesv2.SesV2Client
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MailConfigurationTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

    @Test
    fun `the SES client uses the configured static credentials`() {
        val client = configuration("AKIATEST", "secret").sesV2Client()

        assertEquals("AKIATEST", client.credentials().resolveCredentials().accessKeyId())
    }

    @Test
    fun `without both values the SES client keeps the SDK default chain`() {
        assertFalse(configuration("", "").sesV2Client().credentials() is StaticCredentialsProvider)
    }

    @Test
    fun `a half-configured pair keeps the SDK default chain`() {
        assertFalse(configuration("AKIATEST", "").sesV2Client().credentials() is StaticCredentialsProvider)
        assertFalse(configuration("", "secret").sesV2Client().credentials() is StaticCredentialsProvider)
    }

    private fun configuration(accessKey: String, secretKey: String) =
        MailConfiguration(mock<JavaMailSender>(), "us-east-1", accessKey, secretKey, 2, 1, 15, clock)

    @Suppress("UNCHECKED_CAST")
    private fun SesV2Client.credentials() =
        serviceClientConfiguration().credentialsProvider() as AwsCredentialsProvider
}
