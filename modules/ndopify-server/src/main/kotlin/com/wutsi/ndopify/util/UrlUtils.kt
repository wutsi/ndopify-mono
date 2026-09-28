package com.wutsi.ndopify.util

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object UrlUtils {
    fun download(url: URL): File {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val contentType = connection.contentType
                val extension = MimeUtils.getExtensionFromMimeType(contentType)
                val file = File.createTempFile(UUID.randomUUID().toString(), extension)
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return file
            } else {
                throw IOException("status: " + connection.responseCode)
            }
        } finally {
            connection.disconnect()
        }
    }
}
