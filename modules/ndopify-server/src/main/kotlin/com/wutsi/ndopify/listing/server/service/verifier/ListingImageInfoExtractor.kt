package com.wutsi.ndopify.listing.server.service.verifier

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.ai.ChatClients
import com.wutsi.ndopify.platform.util.MimeUtils
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.io.File

@Service
class ListingImageInfoExtractor(
    @param:Qualifier(ChatClients.VISION) private val client: ChatClient,
    private val locationService: LocationService,
) {
    companion object {
        val PROMPT = """
            You are a real estate agent helping customers to rent or buy properties.
            Your goal is to analyze pictures provided to extract informations to describe the property.

            The listing describes: {{propertyType}} ({{listingType}}), lot area {{lotArea}} m2, in {{city}}.

            You must return the image information in JSON having the following fields:
            - title: Title of the picture in less than 100 characters - in french
            - description: Description of the picture in less than 200 characters - in french
            - valid: (true|false) "true" when the image is valid for an online property listing.
            - quality: (1..5). Quality of the image, where 1 is very poor and 5 is excellent.
            - kind: (PHOTO|FLOORPLAN|MAP). "PHOTO" for a view of the property itself (a room, the facade, the garden, the land, the street),
              "FLOORPLAN" for a plan or drawing of the property, "MAP" for a map or satellite view of the land.
            - reason: If the image is not valid, explain why - in english!

            # Validity rules:
            - The image must be relevant to the property listing described above (e.g. a picture of a room, a kitchen, a bathroom, a garden,
              the land itself, a floor plan of the property, a map of the land, etc.)
            - The image must show the property of this listing, not a similar one: a plan with a different layout, or a map of a different
              neighbourhood, is not valid.

            # Accepted content:
            - **Photos:** the property itself, its land or its immediate surroundings (street, gate, view from the property).
            - **Floor plans:** architectural plans, floor plans and site plans of the property, hand-drawn or software generated, in color or
              black and white, including when they carry room names, dimensions, a legend or a square-meter total. A plan of the property is
              valid whatever the property type.
            - **Parcel maps (land listings only):** maps, satellite views and sketches showing the land of this listing: a cadastral extract,
              a Google Maps / Google Earth screenshot, a surveyor plan, as long as the parcel is identifiable (its outline, its dimensions or
              its parcel number are visible) and the rest of the image is context around it.
            - For a land listing, a plan or a map of the parcel is as relevant as a photo of the land.
            - When the property type is not specified (-), accept a parcel map if the image itself shows a plot with its boundaries.

            # Disallowed content:
            - **Generic maps:** any map where the parcel is not identifiable - a city, region or country map, a map with a single pin or marker
              on a street, an itinerary or a search result screenshot.
            - **Parcel maps for another property type:** a map of the land on a listing that is not a land.
            - **Unrelated plans:** a floor plan, a site plan or a map of another property, or one whose layout contradicts the property
              described above.
            - Random stock photos of furniture/lifestyle models, photos of contracts/documents, unrelated landscapes, screenshots of a website
              or an application, technical schematics that are not plans of the property, offensive or inappropriate content
              (e.g. nudity, violence, etc.).
            - **No Contact Info / Text Overlays:** Photos with phone numbers, WhatsApp handles, external agency links, or prices written across
              the image should be rejected. This prevents agents from bypassing platform communications and keeps listings clean. Text that is
              part of a plan or a map (room names, dimensions, parcel numbers, scale, north arrow, coordinates, "© Google", "© OpenStreetMap") is
              not contact info and must not cause a rejection.
            - **No Heavy Watermarks:** Large, opaque watermarks from competing real estate platforms or agents that cover the room/building
              details should be disallowed (platform-generated subtle watermarks added post-upload are fine). The attribution and the legend of
              a map or of a plan are not watermarks.

            # Quality rules:
            - Judge a photo on its photography, and a plan or a map on its legibility at full resolution. Never penalise a plan for being
              black and white, hand-drawn or a scan, nor a map for being a screenshot, or for lacking light.
            - 1: very poor - Photo: very low quality, blurry, dark, or irrelevant to the property listing. Plan/Map: illegible, too small,
              too blurry or too dark to read anything.
            - 2: poor - Photo: low quality, slightly blurry or dark. Plan/Map: some information readable, but the layout or the boundaries
              are hard to follow.
            - 3: average - Photo: average quality, not blurry, not too dark. Plan/Map: the layout or the parcel is readable, but blurry or
              low resolution.
            - 4: good - Photo: good quality, clear, well lit. Plan/Map: clear - the layout, or the parcel outline and its dimensions, are
              readable.
            - 5: excellent - Photo: excellent quality, clear, well lit, and highly relevant. Plan/Map: sharp and complete - the whole layout
              or parcel is visible with its dimensions, labels and scale easy to read.

            # Title and description:
            - Describe only what is visible; never invent a room, a floor or a dimension.
            - Floor plan: title it by what it shows (e.g. "Plan du rez-de-chaussée", "Plan du terrain et de ses dimensions") and describe the
              rooms, areas or dimensions it contains.
            - Map: title it by what it shows (e.g. "Situation du terrain", "Vue satellite du terrain avec limites parcellaires") and describe
              what can be identified: the parcel, the adjoining street, the neighbourhood.
        """.trimIndent()
    }

    fun extract(file: File, listing: ListingEntity): ListingImageInfo? {
        val instructions = PROMPT
            .replace("{{propertyType}}", listing.propertyType?.name ?: "-")
            .replace("{{listingType}}", listing.listingType.name)
            .replace("{{lotArea}}", listing.area?.toString() ?: "-")
            .replace("{{city}}", city(listing) ?: "-")

        return client.prompt()
            .user { user ->
                val spec = user.text(instructions)
                val mediaType = MediaType.parseMediaType(MimeUtils.getMimeTypeFromExtension(file.extension))
                val resource = FileSystemResource(file)
                spec.media(mediaType, resource)
            }
            .call()
            .entity(ListingImageInfo::class.java)
    }

    private fun city(listing: ListingEntity): String? =
        locationService.search(SearchLocationRequest(ids = listOf(listing.cityId), limit = 1)).firstOrNull()?.name
}

data class ListingImageInfo(
    val title: String = "",
    val description: String = "",
    val valid: Boolean = false,
    val quality: Int = 0,
    val kind: String? = null,
    val reason: String? = null,
)
