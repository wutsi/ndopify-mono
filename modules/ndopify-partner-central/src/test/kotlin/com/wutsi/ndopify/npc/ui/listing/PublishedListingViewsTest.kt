package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ImageVariant
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublishedListingViewsTest {
    private val listing = ListingViewsTest.COMPLETE.copy(
        description = "Un appartement lumineux.\n\nLibre en novembre.",
        agentRemarks = "Libre depuis le 1er novembre.",
        accessDirections = "Immeuble bleu, 3e porte.",
        heroImageId = 2,
        availableFrom = LocalDate.of(2026, 11, 1),
    )
    private val formatter = TenantFormatter(TENANT)
    private val locations = ProfileFixtures.LOCATIONS.associateBy { it.id }

    @Test
    fun `owner - the whole sheet`() {
        val view = PublishedListingViews.of(listing, ListingViewsTest.IMAGES, locations, formatter, owner = true)

        assertEquals("Un appartement lumineux.\n\nLibre en novembre.", view.description)
        assertEquals(listOf(FactView("bed", "2 chambres"), FactView("area", "85 m²")), view.facts)
        assertEquals(
            listOf(SpecView("Type de bien", "Appartement"), SpecView("Chambres", "2"), SpecView("Surface", "85 m²")),
            view.property,
        )
        assertEquals(PlaceView("Bonapriso", "Douala", "Derrière l'école publique"), view.place)
        assertEquals(
            listOf(
                AmenityGroupView("Accès à l'eau", listOf("Compteur d'eau")),
                AmenityGroupView("Accès à l'électricité", listOf("Groupe électrogène")),
                AmenityGroupView("Accès routier", listOf("Route bitumée")),
            ),
            view.amenities,
        )
        assertEquals(
            listOf(
                SpecView("Loyer", "150K\u00a0FCFA / mois"),
                SpecView("Caution", "3 mois (450K\u00a0FCFA)"),
                SpecView("Disponible", "À partir du 01 nov. 2026"),
            ),
            view.terms,
        )
        assertEquals("Libre depuis le 1er novembre.", view.remarks)
        assertNull(view.placeNote)
        assertEquals(
            PrivateLocationView("4.03350, 9.69420", "https://maps.google.com/?q=4.0335,9.6942", "Immeuble bleu, 3e porte."),
            view.privateLocation,
        )
    }

    @Test
    fun `a sale's terms are its price and its availability`() {
        val sale = listing.copy(
            listingType = ListingType.SALE,
            price = 45_000_000,
            pricePeriodicityType = PeriodicityType.NONE,
            securityDeposit = 3,
            advanceRent = 2,
            leaseTerm = 12,
            noticePeriod = 1,
        )

        val view = PublishedListingViews.of(sale, ListingViewsTest.IMAGES, locations, formatter, owner = true)

        assertEquals(
            listOf(
                SpecView("Prix", "45M\u00a0FCFA"),
                SpecView("Disponible", "À partir du 01 nov. 2026"),
            ),
            view.terms,
        )
    }

    @Test
    fun `a sale's sheet never shows furniture`() {
        val furnished = listing.copy(furnitureType = FurnitureType.FULLY_FURNISHED)

        val rental = PublishedListingViews.of(furnished, ListingViewsTest.IMAGES, locations, formatter, owner = true)
        assertTrue(rental.facts.any { it.icon == "furnished" })
        assertTrue(rental.property.any { it.label == "Ameublement" })

        val sale = PublishedListingViews.of(
            furnished.copy(listingType = ListingType.SALE),
            ListingViewsTest.IMAGES,
            locations,
            formatter,
            owner = true,
        )
        assertTrue(sale.facts.none { it.icon == "furnished" })
        assertTrue(sale.property.none { it.label == "Ameublement" })
    }

    @Test
    fun `co-broker - no private location and the shared note`() {
        val view = PublishedListingViews.of(listing, ListingViewsTest.IMAGES, locations, formatter, owner = false)

        assertNull(view.privateLocation)
        assertEquals(
            "L'adresse exacte et l'itinéraire sont communiqués par l'agent avant la visite.",
            view.placeNote,
        )
        assertEquals(listing.description, view.description)
        assertEquals(listing.agentRemarks, view.remarks)
    }

    @Test
    fun `photos - hero first, rejected and blank left out`() {
        val images = ListingViewsTest.IMAGES + ListingImage(id = 4, url = "https://cdn/4.jpg", status = ImageStatus.VERIFIED) +
            ListingImage(id = 5, url = "", status = ImageStatus.VERIFIED)
        val view = PublishedListingViews.of(listing.copy(heroImageId = 4), images, locations, formatter, owner = true)

        assertEquals(listOf("https://cdn/4.jpg", "https://cdn/1.jpg", "https://cdn/2.jpg"), view.photos.map { it.url })
        assertEquals("Photo 1", view.photos.first().alt)
    }

    @Test
    fun `photos - the gallery is the 960 variant, the viewer the 2048 one`() {
        val image = ListingImage(
            id = 1,
            url = "https://cdn/1.jpg",
            status = ImageStatus.VERIFIED,
            variants = listOf(
                ImageVariant(240, 160, "https://ik/1-240.jpg"),
                ImageVariant(960, 640, "https://ik/1-960.jpg"),
                ImageVariant(2048, 1365, "https://ik/1-2048.jpg"),
            ),
        )

        val photo = PublishedListingViews.of(listing, listOf(image), locations, formatter, owner = true).photos.single()

        assertEquals("https://ik/1-960.jpg", photo.url)
        assertEquals("https://ik/1-2048.jpg", photo.fullUrl)
        assertEquals("https://ik/1-240.jpg 240w, https://ik/1-960.jpg 960w, https://ik/1-2048.jpg 2048w", photo.srcSet)
        assertEquals(960, photo.width)
        assertEquals(640, photo.height)
    }

    @Test
    fun `no gps or access - no private block`() {
        val view = PublishedListingViews.of(
            listing.copy(latitude = null, longitude = null, accessDirections = null),
            ListingViewsTest.IMAGES,
            locations,
            formatter,
            owner = true,
        )

        assertNull(view.privateLocation)
    }

    @Test
    fun `images unavailable - the sheet still renders`() {
        val view = PublishedListingViews.of(listing, null, locations, formatter, owner = true)

        assertTrue(view.photos.isEmpty())
        assertTrue(view.facts.isNotEmpty())
    }
}
