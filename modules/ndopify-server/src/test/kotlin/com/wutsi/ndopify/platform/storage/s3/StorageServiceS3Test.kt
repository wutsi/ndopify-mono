package com.wutsi.ndopify.platform.storage.s3

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class StorageServiceS3Test {
    private val s3 = mock<AmazonS3>()
    private val storage = StorageServiceS3("test", s3)

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
}
