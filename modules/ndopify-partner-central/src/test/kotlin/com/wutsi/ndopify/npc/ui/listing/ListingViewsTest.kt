package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingSectionSummary
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingViewsTest {
    companion object {
        val CREATED: Date = Date.from(Instant.parse("2026-10-20T10:00:00Z"))
        val SENT: Date = Date.from(Instant.parse("2026-10-22T10:00:00Z"))

        /** A DRAFT whose location is checked, nothing else yet. */
        val LISTING = Listing(
            id = 501,
            version = 2,
            mandateId = 203,
            agentId = ProfileFixtures.AGENT.id,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            status = ListingStatus.DRAFT,
            cityId = DOUALA,
            neighborhoodId = BONAPRISO,
            landmark = "Derrière l'école publique",
            propertyType = PropertyType.APARTMENT,
            rooms = 2,
            area = 85,
            roadPaved = true,
            waterMeter = true,
            generatorSet = true,
            price = 150_000,
            currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.MONTHLY,
            securityDeposit = 3,
            sections = ListingRules.SECTIONS.map { section ->
                ListingSectionSummary(
                    section = section,
                    status = if (section == ListingSection.LOCATION) ListingSectionStatus.VERIFIED else ListingSectionStatus.TO_VERIFY,
                    required = section != ListingSection.REMARKS,
                )
            },
            createdAt = CREATED,
            modifiedAt = CREATED,
        )

        val COMPLETE = LISTING.copy(
            latitude = 4.0335,
            longitude = 9.6942,
            sections = LISTING.sections.map { it.copy(status = ListingSectionStatus.VERIFIED) },
        )

        val IMAGES = listOf(
            ListingImage(id = 1, listingId = 501, url = "https://cdn/1.jpg", status = ImageStatus.PENDING_VERIFICATION),
            ListingImage(id = 2, listingId = 501, url = "https://cdn/2.jpg", status = ImageStatus.VERIFIED),
            ListingImage(id = 3, listingId = 501, url = "https://cdn/3.jpg", status = ImageStatus.REJECTED, errorMessage = "Floue"),
        )
    }

    private val formatter = TenantFormatter(TENANT)
    private val locations = ProfileFixtures.LOCATIONS.associateBy { it.id }

    private fun of(listing: Listing = LISTING, images: List<ListingImage>? = IMAGES) =
        ListingViews.of(listing, images, locations, formatter)

    private fun row(view: ListingView, section: ListingSection) = view.rows.first { it.section == section }

    @Test
    fun draft() {
        val view = of()

        assertEquals(501L, view.id)
        assertEquals(203L, view.mandateId)
        assertEquals("Annonce · Appartement, Bonapriso", view.title)
        assertEquals(ListingRules.Tag("Brouillon", "info"), view.tag)
        assertEquals("20 oct. 2026", view.createdAt)
        assertEquals(ListingView.PANEL_DRAFT, view.panel)
        assertNull(view.sentAt)
        assertTrue(view.editable)
        assertFalse(view.sendable)
        assertEquals("1 sections sur 6 vérifiées", view.progress)
        assertEquals(
            "Encore à faire : Position GPS, Le bien, Eau, électricité, accès, Loyer et conditions, " +
                "la photo refusée, 3 photos.",
            view.missing,
        )
    }

    @Test
    fun rows() {
        val view = of()

        assertEquals(ListingRules.SECTIONS, view.rows.map { it.section })
        assertEquals(
            listOf("location", "gps", "property", "amenities", "terms", "photos", "remarks").map { "/listings/501/$it" },
            view.rows.map { it.url },
        )
        assertEquals("Emplacement", row(view, ListingSection.LOCATION).label)
        assertEquals("Bonapriso · Derrière l'école publique", row(view, ListingSection.LOCATION).summary)
        assertEquals(ListingRules.Tag("Vérifié", "success"), row(view, ListingSection.LOCATION).state)
        assertEquals("À relever devant l'entrée du bien", row(view, ListingSection.GPS).summary)
        assertEquals(ListingRules.Tag("À vérifier", "warning"), row(view, ListingSection.GPS).state)
        assertEquals("Appartement · 2 chambres · 85 m²", row(view, ListingSection.PROPERTY).summary)
        assertEquals("Route bitumée · 2 équipements", row(view, ListingSection.AMENITIES).summary)
        assertEquals("150K FCFA / mois · caution 3 mois", row(view, ListingSection.TERMS).summary)
        assertEquals("2 photos · 5 au moins", row(view, ListingSection.PHOTOS).summary)
        assertEquals(ListingRules.Tag("À compléter", "warning"), row(view, ListingSection.PHOTOS).state)
        assertEquals("Aucune remarque", row(view, ListingSection.REMARKS).summary)
        assertEquals(ListingRules.Tag("Facultatif", "neutral"), row(view, ListingSection.REMARKS).state)
    }

    @Test
    fun `rows - other values`() {
        val listing = LISTING.copy(
            neighborhoodId = null,
            landmark = null,
            latitude = 4.03351234,
            longitude = 9.69429876,
            propertyType = PropertyType.STUDIO,
            rooms = 1,
            area = null,
            roadPaved = false,
            waterMeter = false,
            generatorSet = false,
            securityDeposit = null,
            agentRemarks = "Peinture à refaire",
        )
        val view = of(listing, emptyList())

        assertEquals("Annonce · Studio, Douala", view.title)
        assertEquals("Point de repère à noter", row(view, ListingSection.LOCATION).summary)
        assertEquals("4.03351, 9.69430", row(view, ListingSection.GPS).summary)
        assertEquals("Studio · 1 chambre", row(view, ListingSection.PROPERTY).summary)
        assertEquals("Accès à préciser · aucun équipement", row(view, ListingSection.AMENITIES).summary)
        assertEquals("150K FCFA / mois", row(view, ListingSection.TERMS).summary)
        assertEquals("Aucune photo · 5 au moins", row(view, ListingSection.PHOTOS).summary)
        assertEquals("Peinture à refaire", row(view, ListingSection.REMARKS).summary)
        assertEquals(ListingRules.Tag("Ajoutées", "success"), row(view, ListingSection.REMARKS).state)
    }

    @Test
    fun `missing - several refused photos, enough others`() {
        val images = (1L..6L).map { ListingImage(id = it, status = ImageStatus.VERIFIED) } +
            listOf(ListingImage(id = 7, status = ImageStatus.REJECTED), ListingImage(id = 8, status = ImageStatus.REJECTED))
        val listing = COMPLETE.copy(
            sections = COMPLETE.sections.map {
                if (it.section == ListingSection.PHOTOS) it.copy(status = ListingSectionStatus.TO_VERIFY) else it
            },
        )

        val view = of(listing, images)

        assertEquals("Encore à faire : les photos refusées.", view.missing)
        assertEquals("6 photos", row(view, ListingSection.PHOTOS).summary)
    }

    @Test
    fun `missing - photos unknown`() {
        val view = of(LISTING.copy(sections = COMPLETE.sections.map {
            if (it.section == ListingSection.PHOTOS) it.copy(status = ListingSectionStatus.TO_VERIFY) else it
        }), null)

        assertEquals("Encore à faire : Photos.", view.missing)
        assertEquals("", row(view, ListingSection.PHOTOS).summary)
    }

    @Test
    fun complete() {
        val view = of(COMPLETE)

        assertTrue(view.sendable)
        assertEquals("6 sections sur 6 vérifiées", view.progress)
        assertEquals("Tout est vérifié : vous pouvez envoyer l'annonce.", view.missing)
    }

    @Test
    fun sent() {
        val view = of(COMPLETE.copy(status = ListingStatus.UNDER_VERIFICATION, modifiedAt = SENT))

        assertEquals(ListingRules.Tag("En vérification", "warning"), view.tag)
        assertEquals(ListingView.PANEL_SENT, view.panel)
        assertEquals("22 oct. 2026", view.sentAt)
        assertFalse(view.editable)
        assertFalse(view.sendable)
        assertNull(view.generatedTitle)
    }

    @Test
    fun published() {
        val listing = COMPLETE.copy(
            status = ListingStatus.PUBLISHED,
            publishedAt = SENT,
            title = "Appartement 2 chambres à louer à Bonapriso",
            summary = "Appartement de 85 m² à Bonapriso, Douala.",
        )
        val view = of(listing)

        assertEquals(ListingView.PANEL_PUBLISHED, view.panel)
        assertEquals("22 oct. 2026", view.publishedAt)
        assertEquals("Appartement 2 chambres à louer à Bonapriso", view.generatedTitle)
        assertEquals("Appartement de 85 m² à Bonapriso, Douala.", view.generatedSummary)
    }

    @Test
    fun closed() {
        val view = of(COMPLETE.copy(status = ListingStatus.RENTED))

        assertNull(view.panel)
        assertFalse(view.editable)
    }

    @Test
    fun `no names`() {
        val view = ListingViews.of(LISTING, IMAGES, emptyMap(), formatter)

        assertEquals("Annonce · Appartement", view.title)
        assertEquals("Derrière l'école publique", row(view, ListingSection.LOCATION).summary)
    }
}
