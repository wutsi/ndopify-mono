package com.wutsi.ndopify.listing.server.service.publisher

import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.ai.ChatClients
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class ListingContentGenerator(
    @param:Qualifier(ChatClients.TEXT) private val client: ChatClient,
    private val locationService: LocationService,
    private val imageDao: ListingImageRepository,
) {
    companion object {
        const val PROMPT = """
            You are a real estate agent helping customers to rent or buy properties.
            You analyze all the information provided to provide an accurate and detailed content of the property.

            You must return the property content in JSON that looks like:

            ```json
            {
                "title": "Title of the property in less than 100 characters - in french",
                "summary": "Short SEO friendly summary in less than 160 characters - in french",
                "description": "A more comprehensive description in less than 500 characters - in french",
                "heroImageId": "ID of the image that should be used as the hero image for the property listing"
            }
            ```

            # Instructions:

            - Instructions for crafting the title:
                - The title should include ONLY the following information:
                    - Type of property
                    - Listing type,
                    - Highlight if the property is fully furnished (if applicable)
                    - Number of bedrooms (to include if the property is not a land)
                    - For a building sold as a whole, its number of units and its number of levels instead of the bedrooms
                    - For land properties, include the Lot area
                    - City
                    - Neighbourhood in round brackets
                - Examples:
                    - Maison à louer, 3 chambres, Douala (Bonapriso)
                    - Appartement meublé à louer, 3 chambres, Douala (Bonapriso)
                    - Terrain à vendre, 1200m2, Douala (Bonapriso)

            - Instructions for crafting the description:
                - Start with Compelling Opening: Your first sentence or two should immediately grab attention and reinforce your unique selling proposition from your title.
                - For fully furnished properties, expand on key Features and amenities: Now's the time to elaborate on the highlights
                  mentioned in your title and introduce other enticing features. Be specific and descriptive.
                - Highlight the experience: Think about what makes your property special. Is it the peace and quiet, the convenience
                  to attractions, the luxurious amenities (for fully furnished properties), or the thoughtful touches you provide?
                - For readability, break the description in multiple paragraphs.

            - Instructions for crafting the summary:
                - Expand on the Title and Hook
                - Focus on the Benefits for the Sharer's Audience: Why should someone click on this link? What kind of experience awaits the customer?
                - Use Action-Oriented Language: Encourage clicks and create a sense of desire.

            - Images quality is rated from 1 to 5, where 1 is very poor and 5 is excellent. The hero image should be the one with the highest quality score, that represent the property the best.
            - For a building sold as a whole, title and describe it by its units and its levels instead of its bedrooms.

            # Goal

            Create the detailed description of a property listing based on the provided information.

            # Property Details

            - Type of listing: {{listingType}}
            - Type of property: {{propertyType}}
            - Bedrooms: {{bedrooms}}
            - Bathrooms: {{bathrooms}}
            - Units: {{units}}
            - Levels: {{levels}}
            - Living Rooms: {{livingRooms}}
            - Kitchens: {{kitchens}}
            - Lot area (m2): {{lotArea}}
            - Furnished: {{furnished}}
            - Water Meter: {{waterMeter}}
            - Water Tank: {{waterTank}}
            - Water Well: {{waterWell}}
            - Dedicated Water Meter: {{dedicatedWaterMeter}}
            - Electricity Meter: {{electricityMeter}}
            - Dedicated Electricity Meter: {{dedicatedElectricityMeter}}
            - Generator Set: {{generatorSet}}
            - Solar Panel: {{solarPanel}}
            - Road Paved: {{roadPaved}}
            - Security Guard: {{securityGuard}}
            - Video Surveillance: {{videoSurveillance}}
            - Secured Fenced: {{securedFenced}}
            - Parkings: {{parkings}}
            - City: {{city}}
            - Neighborhood: {{neighborhood}}
            - Agent remarks: {{agentRemarks}}

            # Images
            {{images}}

        """
    }

    fun generate(listing: ListingEntity): ListingContentInfo? {
        val locations = locationService.search(
            SearchLocationRequest(
                ids = listOfNotNull(listing.cityId, listing.neighborhoodId),
                limit = 2,
            )
        )
        val neighborhood = locations.find { location -> location.id == listing.neighborhoodId }
        val city = locations.find { location -> location.id == listing.cityId }

        val images = imageDao
            .findByListingIdAndStatusInOrderById(listing.id ?: -1, listOf(ImageStatus.VERIFIED))
            .mapIndexed { index, image ->
                "## Image #${index + 1}\n\n" +
                    "- ID: ${image.id}\n" +
                    "- Title: ${image.title ?: "No title available"}\n" +
                    "- Quality: ${image.quality}\n" +
                    "- Description: ${image.description ?: "No description available"}\n"
            }

        // A building sold as a whole has no bedrooms, bathrooms, parkings nor utilities: its counts word it instead
        val building = listing.listingType == ListingType.SALE && listing.propertyType == PropertyType.BUILDING
        val instructions = PROMPT
            .replace("{{listingType}}", listing.listingType.name)
            .replace("{{propertyType}}", listing.propertyType?.name ?: "-")
            .replace("{{bedrooms}}", if (building) "-" else listing.rooms?.toString() ?: "-")
            .replace("{{bathrooms}}", if (building) "-" else listing.bathrooms?.toString() ?: "-")
            .replace("{{units}}", listing.units?.toString() ?: "-")
            .replace("{{levels}}", listing.levels?.toString() ?: "-")
            .replace("{{livingRooms}}", listing.livingRooms?.toString() ?: "-")
            .replace("{{kitchens}}", listing.kitchens?.toString() ?: "-")
            .replace("{{lotArea}}", listing.area?.toString() ?: "-")
            .replace("{{parkings}}", if (building) "-" else listing.parkings?.toString() ?: "-")
            .replace("{{furnished}}", listing.furnitureType?.name ?: "-")
            .replace("{{waterMeter}}", amenity(building, listing.waterMeter))
            .replace("{{waterTank}}", amenity(building, listing.waterTank))
            .replace("{{waterWell}}", amenity(building, listing.waterWell))
            .replace("{{dedicatedWaterMeter}}", amenity(building, listing.dedicatedWaterMeter))
            .replace("{{electricityMeter}}", amenity(building, listing.electricityMeter))
            .replace("{{dedicatedElectricityMeter}}", amenity(building, listing.dedicatedElectricityMeter))
            .replace("{{generatorSet}}", amenity(building, listing.generatorSet))
            .replace("{{solarPanel}}", amenity(building, listing.solarPanel))
            .replace("{{roadPaved}}", amenity(building, listing.roadPaved))
            .replace("{{securedFenced}}", amenity(building, listing.securedFenced))
            .replace("{{securityGuard}}", amenity(building, listing.securityGuard))
            .replace("{{videoSurveillance}}", amenity(building, listing.videoSurveillance))
            .replace("{{city}}", city?.name ?: "-")
            .replace("{{neighborhood}}", neighborhood?.name ?: "-")
            .replace("{{agentRemarks}}", listing.agentRemarks ?: "-")
            .replace("{{images}}", images.joinToString(separator = "\n\n"))

        return client.prompt()
            .user { user -> user.text(instructions) }
            .call()
            .entity(ListingContentInfo::class.java)
    }

    // "-" for a building sold as a whole, which has none of the utilities
    private fun amenity(building: Boolean, value: Boolean): String =
        if (building) "-" else yesNo(value)

    private fun yesNo(value: Boolean): String {
        return if (value) "Yes" else "No"
    }
}

data class ListingContentInfo(
    val title: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val heroImageId: Long? = null,
)
