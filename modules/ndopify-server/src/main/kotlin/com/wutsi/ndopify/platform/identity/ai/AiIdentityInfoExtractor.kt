package com.wutsi.ndopify.platform.identity.ai

import com.wutsi.ndopify.platform.identity.model.IdentityInfo
import com.wutsi.ndopify.util.MimeUtils
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@Service
class AiIdentityInfoExtractor(private val chatClientBuilder: ChatClient.Builder) {
    companion object {
        const val PROMPT = """
            You are an expert in identity verification.
            You will be provided images of identity documents and a selfie of the person.
            Your task is to extract the following information from the identity document:
            - Type: Type if identification document (PASSPORT, NATIONAL_ID, DRIVER_LICENSE, RESIDENT_CARD, UNKNOWN)
            - Number: Identification number of the document
            - Fist name: First name of the holder of the document
            - Last name: Last name of the holder of the document
            - Country Code: 2 letter ISO code of the country that issued the document
            - Expiry date: Expiry date of the document in YYYY-MM-DD format
            - Status: VALID, EXPIRED, INVALID, UNKNOWN
            - Reason for invalidity (if the status is INVALID)

            Return the information in the following JSON format:
            {
                "type": "PASSPORT",
                "number": "123456789",
                "firstName": "John",
                "lastName": "Doe",
                "countryCode": "US",
                "expiryDate": "2030-01-01",
                "status": "VALID",
                "invalidityReason": ""
            }
        """
    }

    private val client = chatClientBuilder.build()

    fun extract(images: List<File>): IdentityInfo {
        return client.prompt()
            .user { user ->
                val spec = user.text(PROMPT)
                images.forEach { file ->
                    val mediaType = MediaType.parseMediaType(MimeUtils.getMimeTypeFromExtension(file.extension))
                    val resource = FileSystemResource(file)
                    spec.media(mediaType, resource)
                }
                spec
            }
            .call()
            .entity(IdentityInfo::class.java)
            ?: IdentityInfo()
    }

    private fun download(url: String): File {
        val uri = URL(url)
        val connection = uri.openConnection() as HttpURLConnection
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
