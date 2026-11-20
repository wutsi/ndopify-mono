package com.wutsi.ndopify.platform.storage.s3

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.storage.StoredObject
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.Clock
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageServiceS3Test {
    private val s3 = mock<AmazonS3>()
    private val clock = mock<Clock>()
    private val storage = StorageServiceS3("test", s3, clock)

    private val now = 1111111L

    @BeforeEach
    fun setUp() {
        whenever(clock.millis()).doReturn(now)
    }

    @Test
    fun type() {
        assertEquals(StorageType.S3, storage.type())
    }

    @Test
    fun store() {
        val content = ByteArrayInputStream("hello".toByteArray())
        storage.store("document/test.txt", content, "text/plain")

        val request: ArgumentCaptor<PutObjectRequest> = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        verify(s3).putObject(request.capture())
        assertEquals(request.value.bucketName, "test")
        assertEquals(request.value.metadata.contentType, "text/plain")
    }

    @Test
    fun get() {
        val os = ByteArrayOutputStream()

        val obj: S3Object = mock()
        val content: S3ObjectInputStream = mock()
        doReturn(-1).whenever(content).read(ArgumentMatchers.any())
        doReturn(content).whenever(obj).objectContent
        doReturn(obj).whenever(s3).getObject(ArgumentMatchers.any())

        storage.get("100/document/203920392/toto.txt", os)

        val request: ArgumentCaptor<GetObjectRequest> = ArgumentCaptor.forClass(GetObjectRequest::class.java)
        verify(s3).getObject(request.capture())
        assertEquals(request.value.bucketName, "test")
        assertEquals(request.value.key, "100/document/203920392/toto.txt")
    }

    @Test
    fun `public url`() {
        val expected = URL("https://test.s3.amazonaws.com/party/1/photo/a.jpg")
        doReturn(expected).whenever(s3).getUrl("test", "party/1/photo/a.jpg")

        assertEquals(expected, storage.publicUrl("party/1/photo/a.jpg"))
    }

    @Test
    fun `presigned url`() {
        val expected =
            URL("https://s3.amazonaws.com/test/document/test.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%")
        doReturn(expected).whenever(s3).generatePresignedUrl(any())

        val url = storage.generatePresignedUrl("document/test.txt", 5)

        assertEquals(expected, url)

        val request = ArgumentCaptor.forClass(GeneratePresignedUrlRequest::class.java)
        verify(s3).generatePresignedUrl(request.capture())
        assertEquals("test", request.value.bucketName)
        assertEquals("document/test.txt", request.value.key)
        assertEquals(now + 5 * 1000L, request.value.expiration.time)
    }

    @Test
    fun `presigned upload url`() {
        val expected = URL("https://s3.amazonaws.com/test/listings/1/images/a.jpg?X-Amz-Signature=x")
        doReturn(expected).whenever(s3).generatePresignedUrl(any())

        val url = storage.presignedUploadUrl("listings/1/images/a.jpg", "image/jpeg", 600)

        assertEquals(expected, url)
        val request = ArgumentCaptor.forClass(GeneratePresignedUrlRequest::class.java)
        verify(s3).generatePresignedUrl(request.capture())
        assertEquals("listings/1/images/a.jpg", request.value.key)
        assertEquals(HttpMethod.PUT, request.value.method)
        assertEquals("image/jpeg", request.value.contentType)
        assertEquals(now + 600 * 1000L, request.value.expiration.time)
    }

    @Test
    fun metadata() {
        val meta = ObjectMetadata().apply {
            contentType = "image/png"
            contentLength = 123
        }
        doReturn(meta).whenever(s3).getObjectMetadata("test", "listings/1/images/a.png")

        assertEquals(StoredObject("image/png", 123), storage.metadata("listings/1/images/a.png"))
    }

    @Test
    fun `metadata - missing object`() {
        doThrow(AmazonS3Exception("not found").apply { statusCode = 404 })
            .whenever(s3).getObjectMetadata("test", "listings/1/images/a.png")

        assertNull(storage.metadata("listings/1/images/a.png"))
    }

    @Test
    fun `read prefix`() {
        val obj: S3Object = mock()
        doReturn(S3ObjectInputStream(ByteArrayInputStream("hello".toByteArray()), null)).whenever(obj).objectContent
        doReturn(obj).whenever(s3).getObject(any<GetObjectRequest>())

        assertEquals("hello", String(storage.readPrefix("listings/1/images/a.jpg", 16)))

        val request = ArgumentCaptor.forClass(GetObjectRequest::class.java)
        verify(s3).getObject(request.capture())
        assertContentEquals(longArrayOf(0L, 15L), request.value.range)
    }

    @Test
    fun delete() {
        storage.delete("listings/1/images/a.jpg")

        verify(s3).deleteObject("test", "listings/1/images/a.jpg")
    }

    @Test
    fun `pathOf is the inverse of publicUrl`() {
        doReturn(URL("https://test.s3.amazonaws.com/")).whenever(s3).getUrl("test", "")

        assertEquals("mandates/1/c1/a b.html", storage.pathOf("https://test.s3.amazonaws.com/mandates/1/c1/a%20b.html"))
    }

    @Test
    fun `pathOf a URL of another bucket`() {
        doReturn(URL("https://test.s3.amazonaws.com/")).whenever(s3).getUrl("test", "")

        assertNull(storage.pathOf("https://other.s3.amazonaws.com/mandates/1/c1/a.html"))
    }
}
