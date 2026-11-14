package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingSectionSummary
import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

        private fun day(day: Int): Date = Date.from(Instant.parse("2026-10-%02dT10:00:00Z".format(day)))

        /** The published listing's matches sent to a client: 204 and 207 are the agent's searches, 305 another agent's. */
        val PROPOSALS = listOf(
            Match(id = 9001, mandateId = 207, listingId = 501, status = MatchStatus.PROPOSED, proposedAt = day(31)),
            Match(id = 9002, mandateId = 204, listingId = 501, status = MatchStatus.ACCEPTED, proposedAt = day(28), answeredAt = day(29)),
            Match(id = 9003, mandateId = 305, listingId = 501, status = MatchStatus.ACCEPTED, proposedAt = day(29), answeredAt = day(30)),
            Match(id = 9004, mandateId = 204, listingId = 501, status = MatchStatus.REJECTED, proposedAt = day(25), answeredAt = day(26)),
        )

        /** The agent's own mandates among the matches' (305 isn't: another agent's). */
        val SEARCHES = listOf(
            MandateSummary(id = 204, customerId = 14, agentId = ProfileFixtures.AGENT.id, mandateType = MandateType.SEARCH),
            MandateSummary(id = 207, customerId = 17, agentId = ProfileFixtures.AGENT.id, mandateType = MandateType.SEARCH),
        )

        val CUSTOMERS = listOf(
            CustomerSummary(id = 14, party = Party(firstName = "Sandrine", lastName = "Eboa")),
            CustomerSummary(id = 17, party = Party(firstName = "Carine", lastName = "Ndongo")),
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

        assertEquals(ListingRules.Tag("Publiée", "success"), view.tag)
        assertNull(view.panel)
        assertEquals("22 oct. 2026", view.publishedAt)
    }

    @Test
    fun closed() {
        val view = of(COMPLETE.copy(status = ListingStatus.RENTED))

        assertNull(view.panel)
        assertFalse(view.editable)
    }

    @Test
    fun `hero image`() {
        assertEquals("https://cdn/2.jpg", of(LISTING.copy(heroImageId = 2)).heroImageUrl)
    }

    @Test
    fun `hero image - unavailable`() {
        assertNull(of(LISTING).heroImageUrl)
        assertNull(of(LISTING.copy(heroImageId = 99)).heroImageUrl)
        assertNull(of(LISTING.copy(heroImageId = 2), null).heroImageUrl)
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
    fun `closed - public url`() {
        val url = "/douala/bonapriso/listing/501/appartement"

        assertEquals(
            "https://ndopify.com$url",
            ListingViews.of(COMPLETE.copy(status = ListingStatus.RENTED, url = url), IMAGES, locations, formatter, "https://ndopify.com/")
                .publicUrl,
        )
        assertEquals(
            "https://ndopify.com$url",
            ListingViews.of(COMPLETE.copy(status = ListingStatus.SOLD, url = url), IMAGES, locations, formatter, "https://ndopify.com/")
                .publicUrl,
        )
        assertNull(
            ListingViews.of(COMPLETE.copy(status = ListingStatus.EXPIRED, url = url), IMAGES, locations, formatter, "https://ndopify.com")
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
    fun `owner hints - the landlord asked no caution`() {
        val form = ListingSectionForm.of(LISTING, formatter)
        val mandate = MANDATE.copy(securityDeposit = 0)

        assertEquals(
            emptyMap(),
            ListingViews.ownerHints(form.copy(securityDeposit = ""), mandate, locations, formatter)
                .filterKeys { it == "securityDeposit" },
        )
        assertEquals(
            "Indiqué par le propriétaire : Aucune",
            ListingViews.ownerHints(form.copy(securityDeposit = "2"), mandate, locations, formatter)["securityDeposit"],
        )
    }

    @Test
    fun `rows - no caution`() {
        val view = of(LISTING.copy(securityDeposit = 0))

        assertEquals("150K\u00a0FCFA / mois · sans caution", row(view, ListingSection.TERMS).summary)
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
        assertEquals(ListingLineView("Annonce", "/mandates/203", ListingRules.Tag("À créer", "neutral")), view.line)
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

        assertEquals(ListingLineView("Annonce n° 501", "/listings/501", ListingRules.Tag("Brouillon", "info")), view.line)
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

        assertEquals(ListingRules.Tag("En vérification", "warning"), view.line.tag)
        assertEquals("Annonce envoyée le 22 oct. 2026, en vérification.", view.text)
        assertEquals("Voir l'annonce", view.action)
        assertFalse(view.primary)
    }

    @Test
    fun `mandate - sent back`() {
        val listing = COMPLETE.copy(status = ListingStatus.DRAFT, errorCodes = listOf(ErrorCode.LISTING_MISSING_LAT_LONG))
        val view = ListingViews.forMandate(approved, 501, listing, formatter)!!

        assertEquals(ListingRules.Tag("À corriger", "warning"), view.line.tag)
        assertEquals("Annonce à corriger : voyez les points signalés.", view.text)
        assertEquals("Corriger l'annonce", view.action)
        assertTrue(view.primary)
    }

    @Test
    fun `mandate - published - the line only`() {
        val listing = COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT)
        val view = ListingViews.forMandate(approved, 501, listing, formatter)!!

        assertEquals(
            ListingLineView("Annonce n° 501", "/listings/501", ListingRules.Tag("Publiée le 22 oct. 2026", "success")),
            view.line,
        )
        assertNull(view.text)
        assertNull(view.action)
        assertNull(view.stats)
    }

    @Test
    fun `mandate - published - clients interested`() {
        val listing = COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT)
        val stats = ListingStatsView.of(ListingStats(501, 1, 0, 1))

        assertEquals("1 personne intéressée · 1 visite", ListingViews.forMandate(approved, 501, listing, formatter, stats)?.stats)
        assertNull(ListingViews.forMandate(approved, 501, COMPLETE, formatter, stats)?.stats)
    }

    @Test
    fun `mandate - listing unavailable`() {
        val view = ListingViews.forMandate(approved, 501, null, formatter)!!

        assertEquals(ListingLineView("Annonce n° 501", "/listings/501", null), view.line)
        assertEquals("/listings/501", view.url)
        assertNull(view.text)
        assertEquals("Voir l'annonce", view.action)
        assertFalse(view.primary)
    }

    @Test
    fun `line - card of a landlord's signed mandate`() {
        val summary = ListingSummary(id = 501, mandateId = 203, status = ListingStatus.PUBLISHED, publishedAt = SENT)

        assertEquals(
            // No number: one line on a phone
            ListingLineView("Annonce", "/listings/501", ListingRules.Tag("Publiée le 22 oct. 2026", "success")),
            ListingViews.line(approved, summary, formatter),
        )
        assertEquals(ListingRules.Tag("Brouillon", "info"), ListingViews.line(approved, summary.copy(status = ListingStatus.DRAFT), formatter)?.tag)
        assertEquals("/mandates/203", ListingViews.line(approved, null, formatter)?.url)
        assertNull(ListingViews.line(approved.copy(mandateType = MandateType.SEARCH), null, formatter))
    }

    @Test
    fun `mandate - none for a search or before approval`() {
        assertNull(ListingViews.forMandate(approved.copy(mandateType = MandateType.SEARCH), null, null, formatter))
        assertNull(ListingViews.forMandate(approved.copy(status = MandateStatus.PENDING_APPROVAL), null, null, formatter))
    }

    private fun visit(
        id: Long = 7001,
        matchId: Long = 9002,
        mandateId: Long = 204,
        status: VisitStatus = VisitStatus.REQUESTED,
        createdAt: Date? = null,
    ): VisitSummary {
        val base = when (status) {
            VisitStatus.CONFIRMED -> VisitFixtures.confirmed()
            VisitStatus.CHECKED_IN -> VisitFixtures.checkedIn()
            else -> VisitFixtures.requested()
        }
        return VisitFixtures.summary(
            base.copy(
                id = id,
                matchId = matchId,
                mandateId = mandateId,
                listingId = 501,
                customerId = 14,
                status = status,
                createdAt = createdAt ?: base.createdAt,
            ),
        )
    }

    private fun clients(visits: List<VisitSummary>? = emptyList()) =
        ListingViews.clients(
            PROPOSALS,
            SEARCHES,
            CUSTOMERS,
            visits,
            ProfileFixtures.AGENT.id,
            TENANT,
            VisitFixtures.NOW,
            formatter,
        )

    @Test
    fun proposals() {
        val clients = clients()
        val view = clients.proposals

        assertEquals(2, clients.interested)
        assertEquals("1 refusé", view.outcomes)
        assertFalse(view.visitsUnavailable)
        // Interested first, the latest answer first; the refused one only counted
        assertEquals(listOf("Client d'un autre agent", "Sandrine Eboa", "Carine Ndongo"), view.rows.map { it.client })
        assertEquals(listOf(null, "/mandates/204#matches-title", "/mandates/207#matches-title"), view.rows.map { it.url })
        assertEquals(listOf(9003L, 9002L, 9001L), view.rows.map { it.matchId })
        assertEquals(listOf("Intéressé", "Intéressé", "En attente"), view.rows.map { it.tag.label })
        assertEquals(
            listOf("Intéressé le 30 oct. 2026", "Intéressé le 29 oct. 2026", "Envoyé le 31 oct. 2026"),
            view.rows.map { it.sent },
        )
        assertTrue(view.rows.all { it.visit == null })
        assertEquals(listOf("Intéressés", "En attente"), view.groups.map { it.label })
    }

    @Test
    fun `an open visit stays in its proposal card`() {
        val view = clients(listOf(visit())).proposals

        val row = view.rows.first()
        assertEquals(9002L, row.matchId)
        assertEquals("Sandrine Eboa", row.client)
        assertEquals(ProposalGroup.REQUESTED, row.group)
        assertEquals("Envoyé le 28 oct. 2026", row.sent)
        assertEquals(VisitStatus.REQUESTED, row.visit?.status)
        assertEquals(listOf("À confirmer", "Intéressés", "En attente"), view.groups.map { it.label })
    }

    @Test
    fun `the refused client is counted, not listed`() {
        val view = clients().proposals

        assertEquals("1 refusé", view.outcomes)
        assertTrue(view.rows.none { it.matchId == 9004L })
    }

    @Test
    fun `visits unavailable - every card stays, with a warning`() {
        val view = clients(null).proposals

        assertTrue(view.visitsUnavailable)
        assertEquals(3, view.rows.size)
        assertTrue(view.rows.all { it.visit == null })
    }

    @Test
    fun `confirmed and checked-in visits are upcoming`() {
        val confirmed = clients(listOf(visit(status = VisitStatus.CONFIRMED))).proposals.rows.first { it.matchId == 9002L }
        val checkedIn = clients(listOf(visit(status = VisitStatus.CHECKED_IN))).proposals.rows.first { it.matchId == 9002L }

        assertEquals(ProposalGroup.UPCOMING, confirmed.group)
        assertEquals("Visite confirmée", confirmed.visit?.kindLabel)
        assertEquals(ProposalGroup.UPCOMING, checkedIn.group)
        assertEquals("Visite en cours", checkedIn.visit?.kindLabel)
        assertEquals("1 visite à venir", clients(listOf(visit(status = VisitStatus.CONFIRMED))).stats?.label)
    }

    @Test
    fun `a completed visit is counted, its proposal stays without a visit block`() {
        val view = clients(listOf(visit(status = VisitStatus.COMPLETED)))

        assertTrue(view.proposals.rows.all { it.visit == null })
        assertEquals("1 visite terminée", view.stats?.label)
        assertEquals(ProposalGroup.INTERESTED, view.proposals.rows.first { it.matchId == 9002L }.group)
        assertEquals("/mandates/204#matches-title", view.proposals.rows[1].url)
    }

    @Test
    fun `several visits each stay in their card`() {
        val view = clients(listOf(visit(), visit(id = 7002, matchId = 9001, mandateId = 207))).proposals

        assertEquals(2, view.rows.count { it.visit != null })
        assertEquals(listOf("À confirmer", "Intéressés"), view.groups.map { it.label })
        assertEquals("1 refusé", view.outcomes)
    }

    @Test
    fun `a stale visit is not attached`() {
        val view = clients(listOf(visit(createdAt = VisitFixtures.date("2026-10-15T10:00:00Z")))).proposals

        assertTrue(view.rows.all { it.visit == null })
        assertEquals(3, view.rows.size)
        assertEquals("/mandates/204#matches-title", view.rows[1].url)
    }

    @Test
    fun `another agent's visit is ignored`() {
        val view = clients(listOf(visit(matchId = 9003, mandateId = 305))).proposals

        assertTrue(view.rows.all { it.visit == null })
        assertEquals(3, view.rows.size)
        assertNull(view.rows[0].url)
    }

    @Test
    fun `proposals - another agent's mandate is protected even when returned`() {
        val other = SEARCHES[0].copy(agentId = 999)

        val view = ListingViews.clients(PROPOSALS, listOf(other), CUSTOMERS, emptyList(), ProfileFixtures.AGENT.id, TENANT, VisitFixtures.NOW, formatter).proposals

        assertTrue(view.rows.all { it.client == "Client d'un autre agent" && it.url == null })
    }

    @Test
    fun `proposals - without the customer's name, its mandate's number`() {
        val view = ListingViews.clients(PROPOSALS, SEARCHES, emptyList(), emptyList(), ProfileFixtures.AGENT.id, TENANT, VisitFixtures.NOW, formatter).proposals

        assertEquals(listOf("Client d'un autre agent", "Mandat n° 204", "Mandat n° 207"), view.rows.map { it.client })
    }

    @Test
    fun `proposals - none sent`() {
        val view = ListingViews.clients(emptyList(), emptyList(), emptyList(), emptyList(), ProfileFixtures.AGENT.id, TENANT, VisitFixtures.NOW, formatter).proposals

        assertNull(view.outcomes)
        assertTrue(view.rows.isEmpty())
        assertTrue(view.groups.isEmpty())
    }

    @Test
    fun `published owner - the sheet, the public url and the stats`() {
        val listing = COMPLETE.copy(
            status = ListingStatus.PUBLISHED,
            publishedAt = SENT,
            description = "Texte public",
            url = "/douala/bonapriso/listing/501/appartement",
        )
        val view = ListingViews.of(
            listing, IMAGES, locations, formatter, "https://ndopify.com",
            ListingStats(501, 2, 1, 2),
        )

        assertNotNull(view.published)
        assertEquals("Texte public", view.published.description)
        assertEquals("https://ndopify.com/douala/bonapriso/listing/501/appartement", view.publicUrl)
        assertEquals("2 personnes intéressées · 3 visites", view.stats)
        assertEquals(7, view.rows.size)
        assertEquals("/mandates/203", view.backUrl)
        assertFalse(view.coBrokerage)
    }

    @Test
    fun `published co-broker - no public url, stats, checklist or private location`() {
        val listing = COMPLETE.copy(
            status = ListingStatus.PUBLISHED,
            publishedAt = SENT,
            description = "Texte public",
            accessDirections = "Porte de droite",
            url = "/douala/bonapriso/listing/501/appartement",
        )
        val agent = ListingAgentView(30, "Clarisse Mbarga", "CM", null, null, "+237699123456", "/agent?id=30")
        val view = ListingViews.of(
            listing, IMAGES, locations, formatter, "https://ndopify.com", ListingStats(501, 2, 1, 2),
            coBrokerage = true, agent = agent, backUrl = "/mandates/204#matches-title",
            backLabel = "← Retour aux biens correspondants",
        )

        assertEquals("Appartement, Bonapriso", view.title)
        assertTrue(view.coBrokerage)
        assertNull(view.publicUrl)
        assertNull(view.stats)
        assertTrue(view.rows.isEmpty())
        assertEquals("", view.progress)
        assertEquals("", view.missing)
        assertFalse(view.editable)
        assertNull(view.published?.privateLocation)
        assertEquals(agent, view.agent)
        assertEquals("/mandates/204#matches-title", view.backUrl)
        assertEquals("← Retour aux biens correspondants", view.backLabel)
    }

    @Test
    fun `draft - the checklist stays the page`() {
        val view = of()

        assertNull(view.published)
        assertFalse(view.coBrokerage)
        assertEquals(7, view.rows.size)
    }
}
