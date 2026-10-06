package com.wutsi.ndopify.platform.storage.s3

import com.amazonaws.services.s3.AmazonS3
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.springframework.boot.health.contributor.Status
import kotlin.test.assertEquals

class S3HealthIndicatorTest {
    private val s3 = mock<AmazonS3>()
    private val health = S3HealthIndicator("foo", s3)

    @Test
    fun up() {
        doReturn("foo").whenever(s3).getBucketLocation(ArgumentMatchers.anyString())
        assertEquals(Status.UP, health.health().status)
    }

    @Test
    fun down() {
        doThrow(RuntimeException::class).whenever(s3).getBucketLocation(ArgumentMatchers.anyString())
        assertEquals(Status.DOWN, health.health().status)
    }
}
