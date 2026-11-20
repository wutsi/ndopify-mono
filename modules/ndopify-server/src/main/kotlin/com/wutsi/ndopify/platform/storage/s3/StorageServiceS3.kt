package com.wutsi.ndopify.platform.storage.s3

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StoredObject
import com.wutsi.ndopify.refdata.dto.StorageType
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.time.Clock
import java.util.Date

class StorageServiceS3(
    private val bucket: String,
    private val s3: AmazonS3,
    private val clock: Clock,
) : StorageService {
    override fun type(): StorageType {
        return StorageType.S3
    }

    override fun store(
        path: String,
        content: InputStream,
        contentType: String?,
    ) {
        val meta = ObjectMetadata()
        meta.contentType = contentType

        val request = PutObjectRequest(bucket, path, content, meta)
        s3.putObject(request)
    }

    override fun get(path: String, os: OutputStream) {
        val request = GetObjectRequest(bucket, path)
        val obj = s3.getObject(request)
        obj.use {
            obj.objectContent.copyTo(os)
        }
    }

    override fun generatePresignedUrl(path: String, expirationInSeconds: Int): URL {
        val request = GeneratePresignedUrlRequest(bucket, path)
            .withMethod(com.amazonaws.HttpMethod.GET)
            .withExpiration(Date(clock.millis() + expirationInSeconds * 1000))

        return s3.generatePresignedUrl(request)
    }

    override fun presignedUploadUrl(path: String, contentType: String, expirationInSeconds: Int): URL {
        val request = GeneratePresignedUrlRequest(bucket, path)
            .withMethod(com.amazonaws.HttpMethod.PUT)
            .withContentType(contentType)
            .withExpiration(Date(clock.millis() + expirationInSeconds * 1000L))
        return s3.generatePresignedUrl(request)
    }

    override fun metadata(path: String): StoredObject? =
        try {
            val meta = s3.getObjectMetadata(bucket, path)
            StoredObject(meta.contentType, meta.contentLength)
        } catch (ex: AmazonS3Exception) {
            if (ex.statusCode == 404) null else throw ex
        }

    override fun readPrefix(path: String, maxBytes: Int): ByteArray =
        s3.getObject(GetObjectRequest(bucket, path).withRange(0, maxBytes - 1L))
            .objectContent.use { it.readBytes() }

    override fun delete(path: String) {
        s3.deleteObject(bucket, path)
    }

    override fun publicUrl(path: String): URL {
        return s3.getUrl(bucket, path)
    }
}
