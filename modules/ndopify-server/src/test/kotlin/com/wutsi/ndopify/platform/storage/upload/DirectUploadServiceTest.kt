package com.wutsi.ndopify.platform.storage.upload

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.StoredObject
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectUploadServiceTest {
    private val storage = mock<StorageService>()
    private val service = DirectUploadService(StorageServiceProvider(StorageType.S3, storage, storage), 600)

    private fun image(format: String): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), format, out)
        return out.toByteArray()
    }

    private fun png() = image("png")

    private fun jpeg() = image("jpeg")

    @BeforeEach
    fun setUp() {
        doReturn(null).whenever(storage).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - s3`() {
        doReturn(URL("https://s3.example.com/put")).whenever(storage)
            .presignedUploadUrl(any(), eq("image/jpeg"), eq(600))

        val response = service.presign("listings/101/images", "image/jpeg")

        assertEquals("https://s3.example.com/put", response.url)
        assertTrue(response.path!!.matches(Regex("listings/101/images/[0-9a-f-]{36}\\.jpg")), response.path)
    }

    @Test
    fun `presign - local storage returns no url`() {
        val response = service.presign("listings/101/images", "image/png")

        assertNull(response.url)
        assertNull(response.path)
    }

    @Test
    fun `presign - content type with parameters`() {
        service.presign("listings/101/images", "IMAGE/PNG; charset=x")

        verify(storage).presignedUploadUrl(any(), eq("image/png"), eq(600))
    }

    @Test
    fun `presign - not an image`() {
        assertFailsWith<BadRequestException> { service.presign("listings/101/images", "application/pdf") }
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `verify - returns the detected type`() {
        val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
        doReturn(png()).whenever(storage).readPrefix(path, 4096)

        assertEquals("image/png", service.verify(path, "listings/101/images"))
    }

    @Test
    fun `verify - path outside the prefix`() {
        val path = "listings/102/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"

        assertFailsWith<BadRequestException> { service.verify(path, "listings/101/images") }
        verify(storage, never()).metadata(any())
        verify(storage, never()).delete(any())
    }

    @Test
    fun `verify - traversal`() {
        val path = "listings/101/images/../../kyc/1/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"

        assertFailsWith<BadRequestException> { service.verify(path, "listings/101/images") }
        verify(storage, never()).metadata(any())
        verify(storage, never()).delete(any())
    }

    @Test
    fun `verify - object missing`() {
        val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        doReturn(null).whenever(storage).metadata(path)

        assertFailsWith<BadRequestException> { service.verify(path, "listings/101/images") }
        verify(storage, never()).delete(any())
    }

    @Test
    fun `verify - too large`() {
        val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        doReturn(StoredObject("image/png", DirectUploadService.MAX_BYTES + 1)).whenever(storage).metadata(path)

        assertFailsWith<BadRequestException> { service.verify(path, "listings/101/images") }
        verify(storage).delete(path)
    }

    @Test
    fun `verify - bytes are not an image`() {
        val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        doReturn(StoredObject("text/plain", 13)).whenever(storage).metadata(path)
        doReturn("not an image".toByteArray()).whenever(storage).readPrefix(path, 4096)

        assertFailsWith<BadRequestException> { service.verify(path, "listings/101/images") }
        verify(storage).delete(path)
    }

    @Test
    fun `verify - extension does not match the bytes`() {
        val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        doReturn(StoredObject("image/jpeg", 3)).whenever(storage).metadata(path)
        doReturn(jpeg()).whenever(storage).readPrefix(path, 4096)

        assertFailsWith<BadRequestException> { service.verify(path, "listings/101/images") }
        verify(storage).delete(path)
    }
}
