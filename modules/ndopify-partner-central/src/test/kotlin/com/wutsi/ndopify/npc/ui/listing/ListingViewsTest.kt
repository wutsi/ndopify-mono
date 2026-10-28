package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingSectionSummary
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
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

        /** The landlord's answers the listing was created from. */
        val MANDATE = Mandate(
            id = 203,
            agentId = ProfileFixtures.AGENT.id,
            status = MandateStatus.APPROVED,
            mandateType = MandateType.LISTING,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            cityId = DOUALA,
            neighborhoodIds = listOf(BONAPRISO),
            propertyType = PropertyType.APARTMENT,
            rooms = "3",
            area = 85,
            waterMeter = true,
            price = Money(150_000, "XAF", PeriodicityType.MONTHLY),
            securityDeposit = 2,
            availableFrom = LocalDate.of(2026, 11, 1),
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
    fun `area in the tenant's format`() {
        val view = of(LISTING.copy(area = 1200))

        assertEquals("Appartement · 2 chambres · 1\u00a0200 m²", row(view, ListingSection.PROPERTY).summary)
    }

    @Test
    fun `required sections are the server's`() {
        val sections = LISTING.sections.map {
            if (it.section == ListingSection.REMARKS) it.copy(required = true) else it
        }
        val view = of(LISTING.copy(sections = sections))

        assertEquals("1 sections sur 7 vérifiées", view.progress)
        assertTrue(view.missing.endsWith(", Vos remarques."))
        assertEquals(ListingRules.Tag("À vérifier", "warning"), row(view, ListingSection.REMARKS).state)
    }

    @Test
    fun `no names`() {
        val view = ListingViews.of(LISTING, IMAGES, emptyMap(), formatter)

        assertEquals("Annonce · Appartement", view.title)
        assertEquals("Derrière l'école publique", row(view, ListingSection.LOCATION).summary)
    }

    @Test
    fun section() {
        val view = ListingViews.section(LISTING, ListingSection.PROPERTY, locations, formatter)

        assertEquals(501L, view.listingId)
        assertEquals(203L, view.mandateId)
        assertEquals("property", view.slug)
        assertEquals("Le bien", view.heading)
        assertEquals("Annonce · Appartement, Bonapriso · mandat n° 203", view.meta)
        assertEquals("Le bien · Annonce · Appartement, Bonapriso — Ndopify Partner Central", view.pageTitle)
        assertTrue(view.editable)
        assertNull(view.readonlyText)
    }

    @Test
    fun `section - sent`() {
        val listing = LISTING.copy(status = ListingStatus.UNDER_VERIFICATION, modifiedAt = SENT)
        val view = ListingViews.section(listing, ListingSection.GPS, locations, formatter)

        assertFalse(view.editable)
        assertEquals(
            "Annonce en vérification depuis le 22 oct. 2026 : voici ce que vous avez relevé, il ne peut plus être modifié.",
            view.readonlyText,
        )
    }

    @Test
    fun `section - published`() {
        val listing = LISTING.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT)

        assertEquals(
            "Annonce publiée le 22 oct. 2026 : voici ce que vous avez relevé, il ne peut plus être modifié.",
            ListingViews.section(listing, ListingSection.GPS, locations, formatter).readonlyText,
        )
    }

    @Test
    fun `sent back - to fix`() {
        val listing = COMPLETE.copy(
            status = ListingStatus.DRAFT,
            errorCodes = listOf(ErrorCode.LISTING_MISSING_NEIGHBORHOOD, ErrorCode.LISTING_NOT_ENOUGH_IMAGES),
            verifiedAt = SENT,
        )
        val view = of(listing)

        assertEquals(ListingView.PANEL_TO_FIX, view.panel)
        assertEquals(ListingRules.Tag("À corriger", "warning"), view.tag)
        assertEquals(
            listOf(
                "Le quartier du bien n'est pas indiqué.",
                "Pas assez de photos acceptées : il en faut 5 au moins.",
            ),
            view.rejections,
        )
        assertEquals(ListingRules.Tag("À corriger", "warning"), row(view, ListingSection.LOCATION).state)
        assertEquals(ListingRules.Tag("À corriger", "warning"), row(view, ListingSection.PHOTOS).state)
        assertEquals(ListingRules.Tag("Vérifié", "success"), row(view, ListingSection.GPS).state)
        assertTrue(view.editable)
        assertEquals(of(COMPLETE).sendable, view.sendable)
        assertNull(view.sentAt)
    }

    @Test
    fun `draft - no reason`() {
        val view = of(COMPLETE.copy(status = ListingStatus.DRAFT))

        assertEquals(ListingView.PANEL_DRAFT, view.panel)
        assertTrue(view.rejections.isEmpty())
    }

    @Test
    fun `pending verification - sent`() {
        val view = of(COMPLETE.copy(status = ListingStatus.PENDING_VERIFICATION, modifiedAt = SENT))

        assertEquals(ListingView.PANEL_SENT, view.panel)
        assertEquals("22 oct. 2026", view.sentAt)
        assertFalse(view.editable)
    }

    @Test
    fun `published - public url`() {
        val listing = COMPLETE.copy(status = ListingStatus.PUBLISHED, url = "/douala/bonapriso/listing/501/appartement")

        assertEquals(
            "https://ndopify.com/douala/bonapriso/listing/501/appartement",
            ListingViews.of(listing, IMAGES, locations, formatter, "https://ndopify.com/").publicUrl,
        )
        assertNull(ListingViews.of(listing, IMAGES, locations, formatter, null).publicUrl)
        assertNull(ListingViews.of(listing.copy(url = null), IMAGES, locations, formatter, "https://ndopify.com").publicUrl)
        assertNull(
            ListingViews.of(listing.copy(status = ListingStatus.PUBLISHING), IMAGES, locations, formatter, "https://ndopify.com")
                .publicUrl,
        )
    }

    @Test
    fun `section - sent back`() {
        val listing = LISTING.copy(status = ListingStatus.DRAFT, errorCodes = listOf(ErrorCode.LISTING_MISSING_LAT_LONG))
        val view = ListingViews.section(listing, ListingSection.GPS, locations, formatter)

        assertTrue(view.editable)
        assertNull(view.readonlyText)
    }

    @Test
    fun `section - closed`() {
        val listing = LISTING.copy(status = ListingStatus.RENTED)

        assertEquals(
            "Annonce fermée : voici ce que vous avez relevé, il ne peut plus être modifié.",
            ListingViews.section(listing, ListingSection.GPS, locations, formatter).readonlyText,
        )
    }

    @Test
    fun `owner hints - what the agent changed`() {
        val hints = ListingViews.ownerHints(ListingSectionForm.of(LISTING, formatter), MANDATE, locations, formatter)

        assertEquals(
            mapOf(
                "rooms" to "Indiqué par le propriétaire : 3",
                "amenities" to "Indiqué par le propriétaire : Compteur d'eau",
                "securityDeposit" to "Indiqué par le propriétaire : 2 mois",
                "availableFrom" to "Indiqué par le propriétaire : 01 nov. 2026",
            ),
            hints,
        )
    }

    @Test
    fun `owner hints - every field`() {
        val form = ListingSectionForm(
            neighborhoodId = "3002",
            propertyCategory = "COMMERCIAL",
            propertyType = "OFFICE",
            furnitureType = "UNFURNISHED",
            rooms = "3",
            parkings = "1",
            area = "90",
            roadAccess = "roadPaved",
            price = "160 000",
            securityDeposit = "2",
            advanceRent = "1",
            availableFrom = "2026-11-01",
        )
        val mandate = MANDATE.copy(furnitureType = FurnitureType.FULLY_FURNISHED, parkings = "2+", advanceRent = 3)

        assertEquals(
            mapOf(
                "neighborhoodId" to "Indiqué par le propriétaire : Bonapriso",
                "propertyCategory" to "Indiqué par le propriétaire : Résidentiel",
                "propertyType" to "Indiqué par le propriétaire : Appartement",
                "furnitureType" to "Indiqué par le propriétaire : Meublé",
                "parkings" to "Indiqué par le propriétaire : 2+",
                "area" to "Indiqué par le propriétaire : 85 m²",
                "amenities" to "Indiqué par le propriétaire : Compteur d'eau",
                "price" to "Indiqué par le propriétaire : 150\u00a0000\u00a0FCFA / mois",
                "advanceRent" to "Indiqué par le propriétaire : 3 mois",
            ),
            ListingViews.ownerHints(form, mandate, locations, formatter),
        )
    }

    @Test
    fun `owner hints - nothing the landlord didn't answer`() {
        val form = ListingSectionForm(rooms = "2", landmark = "École")
        val mandate = Mandate(id = 203)

        assertEquals(emptyMap(), ListingViews.ownerHints(form, mandate, locations, formatter))
        assertEquals(emptyMap(), ListingViews.ownerHints(form, null, locations, formatter))
    }

    @Test
    fun photos() {
        val view = ListingViews.photos(IMAGES, editable = true)

        assertEquals(listOf(1L, 2L, 3L), view.photos.map { it.id })
        assertEquals(listOf("Photo 1", "Photo 2", "Photo 3"), view.photos.map { it.alt })
        assertEquals(listOf("Envoyée", "Vérifiée", "Floue"), view.photos.map { it.note })
        assertEquals(listOf(false, false, true), view.photos.map { it.rejected })
        assertEquals("Supprimer la photo 3", view.photos[2].deleteLabel)
        assertEquals("2 photos · supprimez la photo refusée et reprenez-la", view.count)
        assertFalse(view.full)
    }

    @Test
    fun `photos - counts`() {
        fun count(good: Int, refused: Int = 0, editable: Boolean = true): String {
            val images = (1..good).map { ListingImage(id = it.toLong(), status = ImageStatus.VERIFIED) } +
                (1..refused).map { ListingImage(id = 100L + it, status = ImageStatus.REJECTED) }
            return ListingViews.photos(images, editable).count
        }

        assertEquals("0 photo · encore 5 au moins", count(0))
        assertEquals("1 photo · encore 4 au moins", count(1))
        assertEquals("5 photos · c'est suffisant, ajoutez-en si utile", count(5))
        assertEquals("6 photos · supprimez les photos refusées et reprenez-la", count(6, refused = 2))
        assertEquals("3 photos", count(3, editable = false))
    }

    @Test
    fun `photos - full at 20`() {
        val images = (1L..20L).map { ListingImage(id = it, status = ImageStatus.VERIFIED) }

        assertTrue(ListingViews.photos(images, editable = true).full)
        assertEquals("Refusée", ListingViews.photos(listOf(ListingImage(id = 1, status = ImageStatus.REJECTED)), true).photos[0].note)
    }

    private val approved = Mandate(id = 203, status = MandateStatus.APPROVED, mandateType = MandateType.LISTING)

    @Test
    fun `mandate - no listing yet`() {
        val view = ListingViews.forMandate(approved, null, null, formatter)!!

        assertNull(view.url)
        assertEquals("/mandates/203/listing", view.createUrl)
        assertEquals("Créer l'annonce", view.action)
        assertEquals(
            "Visitez le bien pour vérifier ses caractéristiques et prendre les photos : " +
                "l'annonce est publiée une fois vérifiée.",
            view.text,
        )
        assertTrue(view.primary)
    }

    @Test
    fun `mandate - draft`() {
        val view = ListingViews.forMandate(approved, 501, LISTING, formatter)!!

        assertEquals("/listings/501", view.url)
        assertEquals("Continuer l'annonce", view.action)
        assertEquals(
            "Annonce en brouillon : encore à faire, Position GPS, Le bien, Eau, électricité, accès, " +
                "Loyer et conditions, Photos.",
            view.text,
        )
        assertTrue(view.primary)
    }

    @Test
    fun `mandate - draft ready`() {
        val view = ListingViews.forMandate(approved, 501, COMPLETE, formatter)!!

        assertEquals("Annonce en brouillon : prête à être envoyée.", view.text)
    }

    @Test
    fun `mandate - sent`() {
        val listing = COMPLETE.copy(status = ListingStatus.UNDER_VERIFICATION, modifiedAt = SENT)
        val view = ListingViews.forMandate(approved, 501, listing, formatter)!!

        assertEquals("Annonce envoyée le 22 oct. 2026, en vérification.", view.text)
        assertEquals("Voir l'annonce", view.action)
        assertFalse(view.primary)
    }

    @Test
    fun `mandate - sent back`() {
        val listing = COMPLETE.copy(status = ListingStatus.DRAFT, errorCodes = listOf(ErrorCode.LISTING_MISSING_LAT_LONG))
        val view = ListingViews.forMandate(approved, 501, listing, formatter)!!

        assertEquals("Annonce à corriger : voyez les points signalés.", view.text)
        assertEquals("Corriger l'annonce", view.action)
        assertTrue(view.primary)
    }

    @Test
    fun `mandate - published`() {
        val listing = COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT)
        val view = ListingViews.forMandate(approved, 501, listing, formatter)!!

        assertEquals("Annonce publiée le 22 oct. 2026.", view.text)
        assertEquals("Voir l'annonce", view.action)
    }

    @Test
    fun `mandate - listing unavailable`() {
        val view = ListingViews.forMandate(approved, 501, null, formatter)!!

        assertEquals("/listings/501", view.url)
        assertNull(view.text)
        assertEquals("Voir l'annonce", view.action)
        assertFalse(view.primary)
    }

    @Test
    fun `mandate - none for a search or before approval`() {
        assertNull(ListingViews.forMandate(approved.copy(mandateType = MandateType.SEARCH), null, null, formatter))
        assertNull(ListingViews.forMandate(approved.copy(status = MandateStatus.PENDING_APPROVAL), null, null, formatter))
    }
}
