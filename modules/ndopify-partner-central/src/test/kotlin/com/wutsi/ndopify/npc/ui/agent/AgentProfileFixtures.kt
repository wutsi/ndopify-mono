package com.wutsi.ndopify.npc.ui.agent

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.Instant
import java.util.Date

/** The viewed agent (Clarisse, another agent) and its published listings. */
object AgentProfileFixtures {
    const val AGENT_ID = 30L

    val AGENT = Agent(
        id = AGENT_ID,
        party = Party(
            id = 300L,
            firstName = "Clarisse",
            lastName = "Mbarga",
            email = "clarisse@gmail.com",
            kycStatus = KycStatus.VERIFIED,
            photoUrl = "https://cdn.ndopify.com/party/300/photo/a.jpg",
        ),
        agentType = AgentType.INDEPENDANT,
        experienceLevel = ExperienceLevel.SENIOR,
        biography = "Démarcheuse à Douala depuis 3 ans.",
        whatsappNumber = "+237699123456",
        cityId = ProfileFixtures.DOUALA,
        neighborhoodIds = listOf(ProfileFixtures.BONAPRISO, ProfileFixtures.AKWA),
        joinedAt = Date.from(Instant.parse("2023-03-14T10:00:00Z")),
    )

    /** No photo, biography, WhatsApp, city, neighbourhoods or join date; no known name. */
    val SPARSE_AGENT = Agent(id = 99L)

    val LISTING_APARTMENT = ListingSummary(
        id = 5001L,
        agentId = AGENT_ID,
        status = ListingStatus.PUBLISHED,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.FULLY_FURNISHED,
        cityId = ProfileFixtures.DOUALA,
        neighborhoodId = ProfileFixtures.BONAPRISO,
        rooms = 2,
        area = 80,
        price = 250_000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        heroImageId = 9001L,
    )
    val LISTING_STUDIO = ListingSummary(
        id = 5002L,
        agentId = AGENT_ID,
        status = ListingStatus.PUBLISHED,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        propertyType = PropertyType.STUDIO,
        furnitureType = FurnitureType.UNFURNISHED,
        cityId = ProfileFixtures.DOUALA,
        neighborhoodId = ProfileFixtures.AKWA,
        rooms = 1,
        area = 35,
        price = 120_000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    val LISTING_HOUSE = ListingSummary(
        id = 5003L,
        agentId = AGENT_ID,
        status = ListingStatus.PUBLISHED,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        propertyType = PropertyType.HOUSE,
        cityId = ProfileFixtures.DOUALA,
        neighborhoodId = ProfileFixtures.AKWA,
        rooms = 3,
        area = 120,
        price = 400_000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    val SPARSE_LISTING = ListingSummary(id = 5009L)

    val LISTINGS = listOf(LISTING_APARTMENT, LISTING_STUDIO, LISTING_HOUSE)

    val LOCATIONS: Map<Long, Location> = ProfileFixtures.LOCATIONS.associateBy { it.id }
}
