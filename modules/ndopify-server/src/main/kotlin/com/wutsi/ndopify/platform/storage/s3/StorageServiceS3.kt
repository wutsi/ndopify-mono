package com.wutsi.ndopify.platform.storage.s3

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.wutsi.ndopify.platform.storage.StorageService
import java.io.InputStream
import java.io.OutputStream

class StorageServiceS3(
    private val bucket: String,
    private val s3: AmazonS3,
) : StorageService {
    override fun store(
        path: String,
        content: InputStream,
        contentType: String,
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
}
