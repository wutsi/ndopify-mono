package com.wutsi.ndopify.platform.storage

import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StorageServiceProviderTest {
    private val local = mock(StorageService::class.java)
    private val s3 = mock(StorageService::class.java)

    @Test
    fun `get returns local when type is LOCAL`() {
        val provider = StorageServiceProvider(StorageType.LOCAL, local, s3)

        assertEquals(local, provider.get(StorageType.LOCAL))
    }

    @Test
    fun `get returns s3 when type is S3`() {
        val provider = StorageServiceProvider(StorageType.LOCAL, local, s3)

        assertEquals(s3, provider.get(StorageType.S3))
    }

    @Test
    fun `get throws when type is UNKNOWN`() {
        val provider = StorageServiceProvider(StorageType.LOCAL, local, s3)

        assertFailsWith<IllegalArgumentException> {
            provider.get(StorageType.UNKNOWN)
        }
    }

    @Test
    fun `get without argument returns service for default type`() {
        val provider = StorageServiceProvider(StorageType.S3, local, s3)

        assertEquals(s3, provider.get())
    }
}
