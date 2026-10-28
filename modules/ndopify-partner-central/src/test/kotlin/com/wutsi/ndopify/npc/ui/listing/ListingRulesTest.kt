package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingRulesTest {
    @Test
    fun sections() {
        assertEquals(
            listOf(
                ListingSection.LOCATION,
                ListingSection.GPS,
                ListingSection.PROPERTY,
                ListingSection.AMENITIES,
                ListingSection.TERMS,
                ListingSection.PHOTOS,
                ListingSection.REMARKS,
            ),
            ListingRules.SECTIONS,
        )
        assertEquals("Eau, électricité, accès", ListingRules.LABELS[ListingSection.AMENITIES])
        assertEquals(ListingRules.SECTIONS.toSet(), ListingRules.LABELS.keys)
    }

    @Test
    fun saved() {
        assertEquals(ListingRules.FORM_SECTIONS.toSet(), ListingRules.SAVED.keys)
        assertEquals("Position GPS enregistrée.", ListingRules.SAVED[ListingSection.GPS])
    }

    @Test
    fun slug() {
        assertEquals("location", ListingRules.slug(ListingSection.LOCATION))
        assertEquals("photos", ListingRules.slug(ListingSection.PHOTOS))
    }

    @Test
    fun section() {
        assertEquals(ListingSection.GPS, ListingRules.section("gps"))
        assertNull(ListingRules.section("photos"))
        assertNull(ListingRules.section("unknown"))
        assertNull(ListingRules.section("nope"))
    }

    @Test
    fun tag() {
        assertEquals(ListingRules.Tag("Brouillon", "info"), ListingRules.tag(ListingStatus.DRAFT))
        assertEquals(ListingRules.Tag("En vérification", "warning"), ListingRules.tag(ListingStatus.PENDING_VERIFICATION))
        assertEquals(ListingRules.Tag("En vérification", "warning"), ListingRules.tag(ListingStatus.UNDER_VERIFICATION))
        assertEquals(ListingRules.Tag("En vérification", "warning"), ListingRules.tag(ListingStatus.VERIFIED))
        assertEquals(ListingRules.Tag("En vérification", "warning"), ListingRules.tag(ListingStatus.PUBLISHING))
        assertEquals(ListingRules.Tag("Publiée", "success"), ListingRules.tag(ListingStatus.PUBLISHED))
        assertEquals(ListingRules.Tag("Fermée", "neutral"), ListingRules.tag(ListingStatus.RENTED))
        assertEquals(ListingRules.Tag("Fermée", "neutral"), ListingRules.tag(ListingStatus.SOLD))
        assertEquals(ListingRules.Tag("Fermée", "neutral"), ListingRules.tag(ListingStatus.EXPIRED))
        assertEquals(ListingRules.Tag("Fermée", "neutral"), ListingRules.tag(ListingStatus.CANCELLED))
        assertEquals(ListingRules.Tag("En attente", "warning"), ListingRules.tag(ListingStatus.PENDING))
    }

    @Test
    fun editable() {
        assertTrue(ListingRules.editable(Listing(status = ListingStatus.DRAFT)))
        assertFalse(ListingRules.editable(Listing(status = ListingStatus.UNDER_VERIFICATION)))
        assertTrue(ListingRules.editable(Listing(status = ListingStatus.DRAFT, errorCodes = listOf(ErrorCode.INTERNAL_ERROR))))
    }

    @Test
    fun `tag - sent back`() {
        val listing = Listing(status = ListingStatus.DRAFT, errorCodes = listOf(ErrorCode.LISTING_MISSING_LAT_LONG))

        assertEquals(ListingRules.Tag("À corriger", "warning"), ListingRules.tag(listing))
        assertEquals(ListingRules.Tag("Brouillon", "info"), ListingRules.tag(Listing(status = ListingStatus.DRAFT)))
        assertEquals(ListingRules.Tag("Publiée", "success"), ListingRules.tag(Listing(status = ListingStatus.PUBLISHED)))
    }

    @Test
    fun rejections() {
        val listing = Listing(
            status = ListingStatus.DRAFT,
            errorCodes = listOf(
                ErrorCode.LISTING_NOT_ENOUGH_IMAGES,
                ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD,
                ErrorCode.INTERNAL_ERROR,
                "urn:unknown",
            ),
        )

        assertEquals(
            listOf(
                ListingRules.Rejection("Pas assez de photos acceptées : il en faut 5 au moins.", ListingSection.PHOTOS),
                ListingRules.Rejection("La position GPS relevée n'est pas dans le quartier indiqué.", ListingSection.GPS),
                ListingRules.Rejection("La vérification de l'annonce n'a pas abouti.", null),
            ),
            ListingRules.rejections(listing),
        )
    }

    @Test
    fun `rejections - draft without code`() {
        assertTrue(ListingRules.rejections(Listing(status = ListingStatus.DRAFT)).isEmpty())
    }

    @Test
    fun `rejections - not a draft`() {
        val listing = Listing(status = ListingStatus.PUBLISHING, errorCodes = listOf(ErrorCode.INTERNAL_ERROR))

        assertTrue(ListingRules.rejections(listing).isEmpty())
    }

    @Test
    fun complete() {
        val listing = ListingViewsTest.LISTING

        assertTrue(ListingRules.done(listing, ListingSection.LOCATION))
        assertFalse(ListingRules.done(listing, ListingSection.GPS))
        assertFalse(ListingRules.complete(listing))
        assertTrue(ListingRules.complete(ListingViewsTest.COMPLETE))
    }

    @Test
    fun `complete - remarks are optional`() {
        val listing = ListingViewsTest.COMPLETE
        val sections = listing.sections.map {
            if (it.section == ListingSection.REMARKS) it.copy(status = ListingSectionStatus.TO_VERIFY) else it
        }

        assertTrue(ListingRules.complete(listing.copy(sections = sections)))
    }

    @Test
    fun `complete - a section the server says is required`() {
        val listing = ListingViewsTest.COMPLETE
        val sections = listing.sections.map {
            if (it.section == ListingSection.REMARKS) it.copy(status = ListingSectionStatus.TO_VERIFY, required = true) else it
        }

        assertFalse(ListingRules.complete(listing.copy(sections = sections)))
        assertTrue(ListingRules.required(listing.copy(sections = sections), ListingSection.REMARKS))
        assertFalse(ListingRules.required(listing, ListingSection.REMARKS))
    }

    @Test
    fun `required - a section the server doesn't list`() {
        assertTrue(ListingRules.required(Listing(), ListingSection.GPS))
    }

    @Test
    fun `complete - without checklist`() {
        assertFalse(ListingRules.complete(Listing(status = ListingStatus.DRAFT)))
    }

    @Test
    fun sent() {
        assertFalse(ListingRules.sent(ListingStatus.DRAFT))
        assertTrue(ListingRules.sent(ListingStatus.UNDER_VERIFICATION))
        assertTrue(ListingRules.sent(ListingStatus.VERIFIED))
        assertTrue(ListingRules.sent(ListingStatus.PUBLISHING))
        assertFalse(ListingRules.sent(ListingStatus.PUBLISHED))
    }
}
