package com.wutsi.ndopify.web.ui.listing

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.web.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingPagesTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val tenant = TENANT.copy(publicPortalUrl = "https://www.ndopify.cm/")
    private val douala = Location(id = 2370201, name = "Douala")
    private val bonapriso = Location(id = 5, parentId = 2370201, name = "Bonapriso")

    private val listing = Listing(
        id = 123,
        status = ListingStatus.PUBLISHED,
        listingType = ListingType.RENTAL,
        cityId = 2370201,
        neighborhoodId = 5,
        landmark = "la boulangerie Zepol",
        latitude = 4.0247,
        longitude = 9.6936,
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.FULLY_FURNISHED,
        rooms = 2,
        bathrooms = 2,
        livingRooms = 1,
        kitchens = 1,
        parkings = 1,
        area = 95,
        levels = 3,
        floorLevel = 2,
        dedicatedWaterMeter = true,
        waterWell = true,
        waterTank = true,
        dedicatedElectricityMeter = true,
        generatorSet = true,
        roadPaved = true,
        trackAccessibleByCar = true,
        securedFenced = true,
        securityGuard = true,
        videoSurveillance = true,
        price = 175000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        securityDeposit = 2,
        advanceRent = 3,
        leaseTerm = 12,
        noticePeriod = 1,
        availableFrom = LocalDate.of(2026, 12, 1),
        title = "Appartement meublé 2 chambres à Bonapriso",
        summary = "175K FCFA / mois. Logement audité par Ndopify.",
        description = "Au 2e étage d'un petit immeuble calme.\n\nLe quartier est résidentiel.",
        url = "/douala/bonapriso/listing/123/appartement",
        verifiedAt = Date.from(LocalDate.of(2026, 10, 12).atStartOfDay(ZoneId.of("Africa/Douala")).toInstant()),
    )

    private fun page(
        listing: Listing = this.listing,
        images: List<ListingImage> = emptyList(),
        locations: List<Location> = listOf(douala, bonapriso),
    ) = ListingPages.of(listing, images, locations, tenant, FORMATTER, today)

    @Test
    fun `full listing`() {
        val page = page()

        assertEquals(123L, page.id)
        assertEquals("Appartement meublé 2 chambres à Bonapriso", page.title)
        assertEquals("175K FCFA / mois. Logement audité par Ndopify.", page.metaDescription)
        assertEquals("https://www.ndopify.cm/douala/bonapriso/listing/123/appartement", page.canonicalUrl)
        assertEquals("À louer", page.typeBadge)
        assertEquals("Bonapriso, Douala", page.place)
        assertEquals("près de la boulangerie Zepol", page.landmark)
        assertEquals("12 oct. 2026", page.verifiedAt)
        assertEquals("175K FCFA / mois", page.price)
        assertEquals(
            listOf(
                Fact("bed", "2", "chambres"),
                Fact("bath", "2", "salles de bains"),
                Fact("area", "95", "m²"),
                Fact("sofa", null, "Meublé"),
                Fact("car", "1", "parking"),
            ),
            page.keyFacts,
        )
        assertEquals(
            listOf(
                "Type de bien" to "Appartement",
                "Ameublement" to "Meublé",
                "Chambres" to "2",
                "Salles de bains" to "2",
                "Salons" to "1",
                "Cuisines" to "1",
                "Surface" to "95 m²",
                "Étage" to "2e sur 3",
                "Parkings" to "1",
            ),
            page.specs,
        )
        assertEquals(
            listOf(
                "Loyer" to "175K FCFA / mois",
                "Caution" to "2 mois (350K FCFA)",
                "Avance sur loyer" to "3 mois (525K FCFA)",
                "Durée du bail" to "12 mois",
                "Préavis" to "1 mois",
                "Disponible" to "À partir du 01 déc. 2026",
            ),
            page.terms,
        )
        assertEquals(listOf("Caution : 2 mois", "Avance : 3 mois", "Disponible le 01 déc. 2026"), page.asideTerms)
        assertEquals(listOf("Au 2e étage d'un petit immeuble calme.", "Le quartier est résidentiel."), page.description)
        assertEquals("Apartment", page.schemaType)
        assertEquals("Douala", page.city)
        assertEquals("CM", page.countryCode)
    }

    @Test
    fun amenities() {
        assertEquals(
            listOf(
                AmenityGroup("Eau", listOf("Compteur d'eau dédié", "Réservoir d'eau", "Forage")),
                AmenityGroup("Électricité", listOf("Compteur électrique dédié", "Groupe électrogène")),
                AmenityGroup("Accès", listOf("Route bitumée", "Accessible en voiture")),
                AmenityGroup("Sécurité", listOf("Clôturé et sécurisé", "Gardien", "Vidéosurveillance")),
            ),
            page().amenities,
        )
    }

    @Test
    fun `dedicated meter replaces the plain one`() {
        val page = page(
            Listing(
                waterMeter = true,
                dedicatedWaterMeter = true,
                electricityMeter = true,
                solarPanel = true,
                trackInaccessibleByCar = true,
            ),
        )

        assertEquals(
            listOf(
                AmenityGroup("Eau", listOf("Compteur d'eau dédié")),
                AmenityGroup("Électricité", listOf("Compteur électrique", "Panneaux solaires")),
                AmenityGroup("Accès", listOf("Piste accessible à pied seulement")),
            ),
            page.amenities,
        )
    }

    @Test
    fun `hero photo first`() {
        val images = listOf(
            ListingImage(id = 1, url = "https://cdn/1.jpg", title = "Salon"),
            ListingImage(id = 2, url = "https://cdn/2.jpg", description = "Chambre principale"),
            ListingImage(id = 3, url = "https://cdn/3.jpg"),
        )

        val page = page(listing.copy(heroImageId = 3), images)

        assertEquals(listOf("https://cdn/3.jpg", "https://cdn/1.jpg", "https://cdn/2.jpg"), page.photos.map { it.src })
        assertEquals(listOf(listing.title, "Salon", "Chambre principale"), page.photos.map { it.alt })
    }

    @Test
    fun `first photo without hero`() {
        val images = listOf(ListingImage(id = 1, url = "https://cdn/1.jpg"), ListingImage(id = 2, url = "https://cdn/2.jpg"))

        assertEquals("https://cdn/1.jpg", page(listing.copy(heroImageId = 99), images).photos.first().src)
    }

    @Test
    fun `no photo`() {
        assertTrue(page().photos.isEmpty())
    }

    @Test
    fun `published without generated content`() {
        val page = page(listing.copy(title = null, summary = null, description = null))

        assertEquals("Appartement à louer à Bonapriso", page.title)
        assertEquals(page.title, page.metaDescription)
        assertTrue(page.description.isEmpty())
    }

    @Test
    fun `fallback title of a sale without type nor neighborhood`() {
        val page = page(
            listing.copy(title = null, listingType = ListingType.SALE, propertyType = null, neighborhoodId = null),
            locations = listOf(douala),
        )

        assertEquals("Logement à vendre à Douala", page.title)
        assertEquals("À vendre", page.typeBadge)
    }

    @Test
    fun `sparse listing`() {
        val page = page(
            listing.copy(
                neighborhoodId = null,
                landmark = null,
                rooms = null,
                bathrooms = null,
                furnitureType = FurnitureType.UNFURNISHED,
                parkings = null,
                livingRooms = null,
                kitchens = null,
                area = null,
                floorLevel = null,
                securityDeposit = null,
                advanceRent = null,
                leaseTerm = null,
                noticePeriod = null,
                availableFrom = today.minusDays(1),
                verifiedAt = null,
                url = null,
            ),
            locations = listOf(douala),
        )

        assertEquals("Douala", page.place)
        assertNull(page.landmark)
        assertNull(page.verifiedAt)
        assertNull(page.canonicalUrl)
        assertEquals(listOf(Fact("sofa", null, "Non meublé")), page.keyFacts)
        assertEquals(listOf("Type de bien" to "Appartement", "Ameublement" to "Non meublé"), page.specs)
        assertEquals(listOf("Loyer" to "175K FCFA / mois", "Disponible" to "Immédiatement"), page.terms)
        assertEquals(listOf("Disponible immédiatement"), page.asideTerms)
    }

    @Test
    fun `available without a date`() {
        assertEquals("Disponible" to "Immédiatement", page(listing.copy(availableFrom = null)).terms.last())
    }

    @Test
    fun `plural and floors`() {
        val page = page(listing.copy(rooms = 1, bathrooms = 1, parkings = 2, floorLevel = 1, levels = null))

        assertEquals(listOf("chambre", "salle de bains", "m²", "Meublé", "parkings"), page.keyFacts.map { it.label })
        assertEquals("1er", page.specs.toMap()["Étage"])
        assertEquals("Rez-de-chaussée", page(listing.copy(floorLevel = 0)).specs.toMap()["Étage"])
    }

    @Test
    fun `price periodicity`() {
        assertEquals("175K FCFA / an", page(listing.copy(pricePeriodicityType = PeriodicityType.YEARLY)).price)
        assertEquals("175K FCFA / semaine", page(listing.copy(pricePeriodicityType = PeriodicityType.WEEKLY)).price)
        assertEquals("175K FCFA / jour", page(listing.copy(pricePeriodicityType = PeriodicityType.DAILY)).price)
        val sale = page(listing.copy(price = 25_000_000, pricePeriodicityType = PeriodicityType.NONE))
        assertEquals("25M FCFA", sale.price)
        // No amount next to the months of a price that isn't monthly
        assertEquals("Caution" to "2 mois", sale.terms[1])
    }

    @Test
    fun `schema type`() {
        assertEquals("House", page(listing.copy(propertyType = PropertyType.VILLA)).schemaType)
        assertEquals("Room", page(listing.copy(propertyType = PropertyType.ROOM)).schemaType)
        assertEquals("Apartment", page(listing.copy(propertyType = PropertyType.STUDIO)).schemaType)
        assertEquals("Accommodation", page(listing.copy(propertyType = PropertyType.OFFICE)).schemaType)
    }

    @Test
    fun `description paragraphs`() {
        assertEquals(listOf("A", "B", "C"), page(listing.copy(description = "A\n\n B\nC\n\n")).description)
    }

    @Test
    fun `visit days and times`() {
        val page = page()

        assertEquals(8, page.visitDays.size)
        assertEquals(VisitDay("2026-10-31", "sam.", "31", "oct."), page.visitDays.first())
        assertEquals(VisitDay("2026-11-07", "sam.", "7", "nov."), page.visitDays.last())
        assertEquals(23, page.visitTimes.size)
        assertEquals("10:00" to "10h00", page.visitTimes.first())
        assertEquals("21:00" to "21h00", page.visitTimes.last())
    }

    @Test
    fun `never the agent's remarks`() {
        val page = page(listing.copy(agentRemarks = "Clé chez le gardien"))

        assertFalse(page.toString().contains("Clé chez le gardien"))
    }
}
