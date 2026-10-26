package com.wutsi.ndopify.listing.server.service.verifier

import com.wutsi.ndopify.platform.util.MimeUtils
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.io.File

@Service
class ListingImageInfoExtractor(
    private val client: ChatClient,
) {
    companion object {
        val PROMPT = """
            You are a real estate agent helping customers to rent or buy properties.
            Your goal is to analyze pictures provided to extract informations to describe the property.

            You must return the image information in JSON having the following fields:
            - title: Title of the picture in less than 100 characters - in french
            - description: Description of the picture in less than 200 characters - in french
            - valid: (true|false) "true" when the image is valid for an online property listing
            - reason: If the image is not valid, explain why.
        """.trimIndent()
    }

    fun extract(file: File): ListingImageInfo {
        val instructions = PROMPT

        return client.prompt()
            .user { user ->
                val spec = user.text(instructions)
                val mediaType = MediaType.parseMediaType(MimeUtils.getMimeTypeFromExtension(file.extension))
                val resource = FileSystemResource(file)
                spec.media(mediaType, resource)
            }
            .call()
            .entity(ListingImageInfo::class.java)
            ?: ListingImageInfo()
    }
}

data class ListingImageInfo(
    val title: String = "",
    val description: String = "",
    val valid: Boolean = false,
    val reason: String? = null,
)
