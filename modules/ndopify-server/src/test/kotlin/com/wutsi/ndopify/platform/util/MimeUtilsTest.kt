package com.wutsi.ndopify.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MimeUtilsTest {
    @Test
    fun `getExtensionFromMimeType should return jpg for image jpeg`() {
        val result = MimeUtils.getExtensionFromMimeType("image/jpeg")
        assertEquals("jpg", result)
    }

    @Test
    fun `getExtensionFromMimeType should return png for image png`() {
        val result = MimeUtils.getExtensionFromMimeType("image/png")
        assertEquals("png", result)
    }

    @Test
    fun `getExtensionFromMimeType should return gif for image gif`() {
        val result = MimeUtils.getExtensionFromMimeType("image/gif")
        assertEquals("gif", result)
    }

    @Test
    fun `getExtensionFromMimeType should return webp for image webp`() {
        val result = MimeUtils.getExtensionFromMimeType("image/webp")
        assertEquals("webp", result)
    }

    @Test
    fun `getExtensionFromMimeType should return pdf for application pdf`() {
        val result = MimeUtils.getExtensionFromMimeType("application/pdf")
        assertEquals("pdf", result)
    }

    @Test
    fun `getExtensionFromMimeType should return html for text html`() {
        val result = MimeUtils.getExtensionFromMimeType("text/html")
        assertEquals("html", result)
    }

    @Test
    fun `getExtensionFromMimeType should return txt for text plain`() {
        val result = MimeUtils.getExtensionFromMimeType("text/plain")
        assertEquals("txt", result)
    }

    @Test
    fun `getExtensionFromMimeType should return json for application json`() {
        val result = MimeUtils.getExtensionFromMimeType("application/json")
        assertEquals("json", result)
    }

    @Test
    fun `getExtensionFromMimeType should return bin for unknown mime type`() {
        val result = MimeUtils.getExtensionFromMimeType("application/unknown")
        assertEquals("bin", result)
    }

    @Test
    fun `getExtensionFromMimeType should return bin for null content type`() {
        val result = MimeUtils.getExtensionFromMimeType(null)
        assertEquals("bin", result)
    }

    @Test
    fun `getExtensionFromMimeType should ignore parameters like charset`() {
        val result = MimeUtils.getExtensionFromMimeType("image/jpeg; charset=UTF-8")
        assertEquals("jpg", result)
    }

    @Test
    fun `getExtensionFromMimeType should handle mime type with multiple parameters`() {
        val result = MimeUtils.getExtensionFromMimeType("text/html; charset=UTF-8; boundary=something")
        assertEquals("html", result)
    }

    @Test
    fun `getExtensionFromMimeType should be case insensitive`() {
        val result1 = MimeUtils.getExtensionFromMimeType("IMAGE/JPEG")
        assertEquals("jpg", result1)

        val result2 = MimeUtils.getExtensionFromMimeType("Image/jpeg")
        assertEquals("jpg", result2)
    }

    @Test
    fun `getExtensionFromMimeType should handle mime type with whitespace`() {
        val result = MimeUtils.getExtensionFromMimeType("  image/png  ")
        assertEquals("png", result)
    }

    @Test
    fun `getExtensionFromMimeType should return bin for empty string`() {
        val result = MimeUtils.getExtensionFromMimeType("")
        assertEquals("bin", result)
    }

    @Test
    fun `getExtensionFromMimeType should return bin for blank string`() {
        val result = MimeUtils.getExtensionFromMimeType("   ")
        assertEquals("bin", result)
    }

    // Tests for getMimeTypeFromExtension

    @Test
    fun `getMimeTypeFromExtension should return image jpeg for jpg`() {
        val result = MimeUtils.getMimeTypeFromExtension("jpg")
        assertEquals("image/jpeg", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return image jpeg for jpeg`() {
        val result = MimeUtils.getMimeTypeFromExtension("jpeg")
        assertEquals("image/jpeg", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return image png for png`() {
        val result = MimeUtils.getMimeTypeFromExtension("png")
        assertEquals("image/png", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return image gif for gif`() {
        val result = MimeUtils.getMimeTypeFromExtension("gif")
        assertEquals("image/gif", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return image webp for webp`() {
        val result = MimeUtils.getMimeTypeFromExtension("webp")
        assertEquals("image/webp", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return application pdf for pdf`() {
        val result = MimeUtils.getMimeTypeFromExtension("pdf")
        assertEquals("application/pdf", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return text html for html`() {
        val result = MimeUtils.getMimeTypeFromExtension("html")
        assertEquals("text/html", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return text html for htm`() {
        val result = MimeUtils.getMimeTypeFromExtension("htm")
        assertEquals("text/html", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return text plain for txt`() {
        val result = MimeUtils.getMimeTypeFromExtension("txt")
        assertEquals("text/plain", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return application json for json`() {
        val result = MimeUtils.getMimeTypeFromExtension("json")
        assertEquals("application/json", result)
    }

    @Test
    fun `getMimeTypeFromExtension should handle extension with leading dot`() {
        val result = MimeUtils.getMimeTypeFromExtension(".jpg")
        assertEquals("image/jpeg", result)
    }

    @Test
    fun `getMimeTypeFromExtension should handle extension with multiple dots`() {
        val result = MimeUtils.getMimeTypeFromExtension("..png")
        assertEquals("image/png", result)
    }

    @Test
    fun `getMimeTypeFromExtension should be case insensitive`() {
        val result1 = MimeUtils.getMimeTypeFromExtension("JPG")
        assertEquals("image/jpeg", result1)

        val result2 = MimeUtils.getMimeTypeFromExtension("PNG")
        assertEquals("image/png", result2)

        val result3 = MimeUtils.getMimeTypeFromExtension("Html")
        assertEquals("text/html", result3)
    }

    @Test
    fun `getMimeTypeFromExtension should handle extension with whitespace`() {
        val result = MimeUtils.getMimeTypeFromExtension("  png  ")
        assertEquals("image/png", result)
    }

    @Test
    fun `getMimeTypeFromExtension should handle extension with dot and whitespace`() {
        val result = MimeUtils.getMimeTypeFromExtension("  .jpg  ")
        assertEquals("image/jpeg", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return octet-stream for unknown extension`() {
        val result = MimeUtils.getMimeTypeFromExtension("xyz")
        assertEquals("application/octet-stream", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return octet-stream for null extension`() {
        val result = MimeUtils.getMimeTypeFromExtension(null)
        assertEquals("application/octet-stream", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return octet-stream for empty string`() {
        val result = MimeUtils.getMimeTypeFromExtension("")
        assertEquals("application/octet-stream", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return octet-stream for blank string`() {
        val result = MimeUtils.getMimeTypeFromExtension("   ")
        assertEquals("application/octet-stream", result)
    }

    @Test
    fun `getMimeTypeFromExtension should return octet-stream for dot only`() {
        val result = MimeUtils.getMimeTypeFromExtension(".")
        assertEquals("application/octet-stream", result)
    }
}
