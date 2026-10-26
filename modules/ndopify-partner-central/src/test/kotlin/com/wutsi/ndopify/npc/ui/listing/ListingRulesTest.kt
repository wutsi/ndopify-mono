package com.wutsi.ndopify.npc.ui.listing

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
