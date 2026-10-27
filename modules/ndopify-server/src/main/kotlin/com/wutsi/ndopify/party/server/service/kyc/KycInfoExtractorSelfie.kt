package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.server.service.KycInfoExtractor
import com.wutsi.ndopify.platform.ai.ChatClients
import com.wutsi.ndopify.platform.util.MimeUtils
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.io.File

@Service
class KycInfoExtractorSelfie(
    @Qualifier(ChatClients.VISION) private val client: ChatClient,
) : KycInfoExtractor {
    companion object {
        const val PROMPT = """
            You are an expert in selfie verification.
            You will be provided images of identity documents and a selfie of the person.
            Your task is to make sure that the selfie matches the identity document to extract the following information from the identity document:
            - Type: Type if identification document ({{ID_TYPES}})
            - valid: true if the selfie matches the identity document, false otherwise
            - Reason for invalidity: If the document is invalid, provide a reason for invalidity. If the document is valid, do not include this field.
            - confidence: A score between 0..1 indicating the confidence of your assessment. 1 means you are very confident, 0 means you are not confident at all.

            Return the information in the following JSON format:
            {
                "type": "PASSPORT",
                "valid": false,
                "invalidityReason": "Document is expired",
                "confidence": 0.5
            }
        """
    }

    override fun extract(images: List<File>): KycInfo {
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
            .entity(KycInfo::class.java)
            ?: KycInfo()
    }
}
