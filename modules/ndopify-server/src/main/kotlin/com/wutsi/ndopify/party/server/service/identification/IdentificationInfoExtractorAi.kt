package com.wutsi.ndopify.party.server.service.identification

import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.server.service.IdentificationInfoExtractor
import com.wutsi.ndopify.util.MimeUtils
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.io.File

@Service
class IdentificationInfoExtractorAi(private val chatClientBuilder: ChatClient.Builder) : IdentificationInfoExtractor {
    companion object {
        const val PROMPT = """
            You are an expert in identity verification.
            You will be provided images of identity documents and a selfie of the person.
            Your task is to extract the following information from the identity document:
            - Type: Type if identification document ({{ID_TYPES}})
            - Number: Identification number of the document
            - Fist name: First name of the holder of the document
            - Last name: Last name of the holder of the document
            - Country Code: 2 letter ISO code of the country that issued the document
            - Expiry date: Expiry date of the document in YYYY-MM-DD format
            - valid: true if the document is valid, false otherwise
                - Do not consider expired document as invalid, but provide the expiry date.
                - Consider the document valid if it looks forged or tampered, even if it is not expired.
            - Reason for invalidity: If the document is invalid, provide a reason for invalidity. If the document is valid, do not include this field.

            Return the information in the following JSON format:
            {
                "type": "PASSPORT",
                "number": "123456789",
                "firstName": "John",
                "lastName": "Doe",
                "countryCode": "US",
                "expiryDate": "2030-01-01",
                "valid": false,
                "invalidityReason": "Document is expired"
            }
        """
    }

    private val client = chatClientBuilder.build()

    override fun extract(images: List<File>): IdentificationInfo {
        val instructions = PROMPT
            .replace("{{ID_TYPES}}", IdentificationType.entries.joinToString(", ") { it.name })

        return client.prompt()
            .user { user ->
                val spec = user.text(instructions)
                images.forEach { file ->
                    val mediaType = MediaType.parseMediaType(MimeUtils.getMimeTypeFromExtension(file.extension))
                    val resource = FileSystemResource(file)
                    spec.media(mediaType, resource)
                }
                spec
            }
            .call()
            .entity(IdentificationInfo::class.java)
            ?: IdentificationInfo()
    }
}
