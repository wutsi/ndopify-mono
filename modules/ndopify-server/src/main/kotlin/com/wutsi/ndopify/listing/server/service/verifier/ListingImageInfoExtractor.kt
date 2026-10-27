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
            - valid: (true|false) "true" when the image is valid for an online property listing.
            - quality: (1..5). Quality of the image, where 1 is very poor and 5 is excellent.
            - reason: If the image is not valid, explain why - in english!

            # Validity rules:
            - The image must be relevant to the property listing (e.g. a picture of a room, a kitchen, a bathroom, a garden, etc.)
            - **Disallowed Content:** Generic maps, random stock photos of furniture/lifestyle models, photos of contracts/documents, unrelated landscapes, offensive or inappropriate content (e.g. nudity, violence, etc.).
            - **No Contact Info / Text Overlays:** Photos with phone numbers, WhatsApp handles, external agency links, or prices written across the image should be rejected. This prevents agents from bypassing platform communications and keeps listings clean.
            - **No Heavy Watermarks:** Large, opaque watermarks from competing real estate platforms or agents that cover the room/building details should be disallowed (platform-generated subtle watermarks added post-upload are fine).

            # Quality rules:
            - 1: very poor - The image is of very low quality, blurry, dark, or irrelevant to the property listing.
            - 2: poor - The image is of low quality, slightly blurry, dark, or somewhat irrelevant to the property listing.
            - 3: average - The image is of average quality, not blurry, not too dark, and relevant to the property listing.
            - 4: good - The image is of good quality, clear, well lit, and relevant to the property listing.
            - 5: excellent - The image is of excellent quality, clear, well lit, and highly relevant to the property listing.
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
    val quality: Int = 0,
    val reason: String? = null,
)
