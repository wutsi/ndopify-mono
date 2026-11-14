package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
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
                SpecView("Loyer", "150\u00a0000\u00a0FCFA / mois"),
                SpecView("Caution", "3 mois (450\u00a0000\u00a0FCFA)"),
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
