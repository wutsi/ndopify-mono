package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.InternalErrorException
import com.wutsi.ndopify.platform.image.ImagePreviewService
import com.wutsi.ndopify.platform.image.UnprocessableImageException
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.StoredObject
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.StorageType
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The derivation of a page's display copy: stored next to the original at a deterministic path, generated once at
 * write or lazily on the first read under a per-path lock, deleted with the page.
 */
class MandateDocumentPreviewServiceTest {
    private val storageProvider = mock<StorageServiceProvider>()
    private val imagePreview = mock<ImagePreviewService>()
    private val tenantService = mock<TenantService>()
    private val storage = mock<StorageService>()

    private val service = MandateDocumentPreviewService(storageProvider, imagePreview, tenantService)

    private val path = "mandate/301/documents/page-1.jpg"
    private val previewPath = "$path.preview-v1.jpg"
    private val original = "original".toByteArray()
    private val preview = "preview".toByteArray()

    @BeforeEach
    fun setUp() {
        TenantContext.set(1)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(storage).whenever(storageProvider).get(StorageType.LOCAL)
        doReturn(TenantEntity(id = 1, name = "Ndopify")).whenever(tenantService).findById(1)
        doAnswer { invocation ->
            invocation.getArgument<OutputStream>(1).write(original)
            null
        }.whenever(storage).get(eq(path), any())
        doReturn(original).whenever(imagePreview).preview(any(), anyOrNull(), any())
        doAnswer { invocation ->
            invocation.getArgument<OutputStream>(1).write(preview)
            null
        }.whenever(storage).get(eq(previewPath), any())
    }

    @AfterEach
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `generate - reads the stored original and stores the watermarked preview next to it`() {
        doReturn(preview).whenever(imagePreview).preview(eq(original), anyOrNull(), any())

        val result = service.generate(301, storage, path, "image/jpeg")

        assertContentEquals(preview, result)
        verify(imagePreview).preview(any(), eq("image/jpeg"), eq("Ndopify - Mandat n° 301"))
        verify(storage).store(eq(previewPath), any(), eq("image/jpeg"))
    }

    @Test
    fun `generate - does not read the stored original when its bytes are given`() {
        service.generate(301, storage, path, "image/jpeg", original = original)

        verify(storage, never()).get(any(), any())
    }

    @Test
    fun `read - the stored preview is returned without regenerating`() {
        doReturn(StoredObject("image/jpeg", preview.size.toLong())).whenever(storage).metadata(previewPath)

        val result = service.read(301, path, StorageType.S3, "image/jpeg")

        assertContentEquals(preview, result)
        verify(imagePreview, never()).preview(any(), anyOrNull(), any())
    }

    @Test
    fun `read - the first read generates and stores, the next one reads it`() {
        val stored = AtomicInteger(0)
        doAnswer { if (stored.get() > 0) StoredObject("image/jpeg", preview.size.toLong()) else null }
            .whenever(storage).metadata(previewPath)
        doAnswer {
            stored.incrementAndGet()
            null
        }.whenever(storage).store(eq(previewPath), any(), eq("image/jpeg"))
        doReturn(preview).whenever(imagePreview).preview(any(), anyOrNull(), any())

        assertContentEquals(preview, service.read(301, path, StorageType.S3, "image/jpeg"))
        assertContentEquals(preview, service.read(301, path, StorageType.S3, "image/jpeg"))

        verify(imagePreview).preview(any(), eq("image/jpeg"), eq("Ndopify - Mandat n° 301"))
        verify(storage).store(eq(previewPath), any(), eq("image/jpeg"))
    }

    @Test
    fun `read - a legacy page with no storage type uses the default storage`() {
        service.read(301, path, StorageType.UNKNOWN, null)

        verify(storageProvider).get()
        verify(storageProvider, never()).get(StorageType.UNKNOWN)
    }

    @Test
    fun `read - two concurrent reads generate the preview once`() {
        val stored = AtomicBoolean(false)
        doAnswer { if (stored.get()) StoredObject("image/jpeg", preview.size.toLong()) else null }
            .whenever(storage).metadata(previewPath)
        doAnswer {
            stored.set(true)
            null
        }.whenever(storage).store(eq(previewPath), any(), eq("image/jpeg"))
        val generations = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            generations.incrementAndGet()
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            preview
        }.whenever(imagePreview).preview(any(), anyOrNull(), any())

        val results = mutableListOf<ByteArray>()
        val errors = mutableListOf<Throwable>()
        val first = Thread {
            TenantContext.set(1)
            try {
                results.add(service.read(301, path, StorageType.S3, "image/jpeg"))
            } catch (t: Throwable) {
                errors.add(t)
            } finally {
                TenantContext.remove()
            }
        }
        first.start()
        assertTrue(entered.await(10, TimeUnit.SECONDS), "the first read never generated: $errors")
        val second = Thread {
            TenantContext.set(1)
            try {
                results.add(service.read(301, path, StorageType.S3, "image/jpeg"))
            } catch (t: Throwable) {
                errors.add(t)
            } finally {
                TenantContext.remove()
            }
        }
        second.start()
        release.countDown()
        first.join()
        second.join()

        assertEquals(2, results.size)
        assertEquals(1, generations.get())
    }

    @Test
    fun `read - a page that cannot be previewed fails closed`() {
        doThrow(UnprocessableImageException("boom")).whenever(imagePreview).preview(any(), anyOrNull(), any())

        val exception = assertFailsWith<InternalErrorException> {
            service.read(301, path, StorageType.S3, "image/jpeg")
        }

        assertEquals(ErrorCode.HTTP_INTERNAL, exception.error.code)
        assertEquals(null, exception.cause)
    }

    @Test
    fun `deleteQuietly - deletes the preview and swallows the failure`() {
        service.deleteQuietly(301, path, StorageType.S3)
        verify(storage).delete(previewPath)

        doThrow(RuntimeException("boom")).whenever(storage).delete(previewPath)
        service.deleteQuietly(301, path, StorageType.S3)
    }

    @Test
    fun `deleteQuietly - a legacy page's preview is in the default storage`() {
        service.deleteQuietly(301, path, StorageType.UNKNOWN)

        verify(storage).delete(previewPath)
    }

    @Test
    fun `generate - an unprocessable image propagates`() {
        doThrow(UnprocessableImageException("boom")).whenever(imagePreview).preview(any(), anyOrNull(), any())

        assertFailsWith<UnprocessableImageException> {
            service.generate(301, storage, path, "image/jpeg")
        }
        verify(storage, never()).store(any(), any(), anyOrNull())
    }
}
