package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateConversationServiceTest {
    private val dao = mock<MandateRepository>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val service = MandateConversationService(dao, locationService, mailService)

    private val mandate = MandateEntity(
        id = 12L,
        tenantId = 1L,
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        cityId = 2370201L,
        neighborhoodIds = listOf(111L, 222L),
        propertyType = PropertyType.APARTMENT,
    )

    @BeforeTest
    fun setUp() {
        doReturn(Optional.of(mandate)).whenever(dao).findById(12L)
        doReturn("wutsi.com").whenever(mailService).fromDomain
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(LocationEntity(id = 222L, name = "Mvan")).whenever(locationService).findByIdOrNull(222L)
    }

    @Test
    fun `the first e-mail carries the root id and the built subject`() {
        doReturn(null).whenever(dao).findConversationSubjectById(12L)
        doReturn(1).whenever(dao).markConversationSubject(
            12L,
            "Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 12",
        )

        val conversation = service.of(12L)

        assertEquals("Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 12", conversation.subject)
        assertEquals("mandate-1-12@wutsi.com", conversation.messageId)
        assertNull(conversation.inReplyTo)
        assertEquals(emptyList<String>(), conversation.references)
    }

    @Test
    fun `a stored subject is never rebuilt`() {
        doReturn("Votre bien à Bonapriso (Douala) · mandat 12").whenever(dao).findConversationSubjectById(12L)

        // The mandate's criteria changed since the first e-mail: its city, neighbourhoods and type are different now
        val conversation = service.of(12L)

        assertEquals("Votre bien à Bonapriso (Douala) · mandat 12", conversation.subject)
        assertEquals("mandate-1-12@wutsi.com", conversation.inReplyTo)
        assertEquals(listOf("mandate-1-12@wutsi.com"), conversation.references)
        assertTrue(conversation.messageId.startsWith("mandate-1-12-"))
        assertTrue(conversation.messageId.endsWith("@wutsi.com"))
        verify(dao, never()).markConversationSubject(any(), any())
    }

    @Test
    fun `a lost race follows the stored subject`() {
        doReturn(null, "Votre recherche · mandat 12").whenever(dao).findConversationSubjectById(12L)
        doReturn(0).whenever(dao).markConversationSubject(any(), any())

        val conversation = service.of(12L)

        assertEquals("Votre recherche · mandat 12", conversation.subject)
        assertEquals("mandate-1-12@wutsi.com", conversation.inReplyTo)
        assertEquals(listOf("mandate-1-12@wutsi.com"), conversation.references)
    }

    @Test
    fun `a listing mandate names the owner's property`() {
        doReturn(Optional.of(mandate.copy(mandateType = MandateType.LISTING))).whenever(dao).findById(12L)
        doReturn(null).whenever(dao).findConversationSubjectById(12L)
        doReturn(1).whenever(dao).markConversationSubject(any(), any())

        service.of(12L)

        verify(dao).markConversationSubject(12L, "Votre bien à Bastos, Mvan (Yaounde) · mandat 12")
    }

    @Test
    fun `no property type, no place`() {
        doReturn(
            Optional.of(mandate.copy(propertyType = null, cityId = -1L, neighborhoodIds = emptyList())),
        ).whenever(dao).findById(12L)
        doReturn(null).whenever(dao).findConversationSubjectById(12L)
        doReturn(1).whenever(dao).markConversationSubject(any(), any())

        service.of(12L)

        verify(dao).markConversationSubject(12L, "Votre recherche · mandat 12")
    }

    @Test
    fun `no property type, the city alone`() {
        doReturn(Optional.of(mandate.copy(propertyType = null, neighborhoodIds = emptyList()))).whenever(dao).findById(12L)
        doReturn(null).whenever(dao).findConversationSubjectById(12L)
        doReturn(1).whenever(dao).markConversationSubject(any(), any())

        service.of(12L)

        verify(dao).markConversationSubject(12L, "Votre recherche à Yaounde · mandat 12")
    }

    @Test
    fun `two mandates, two conversations`() {
        val other = mandate.copy(id = 13L)
        doReturn(Optional.of(other)).whenever(dao).findById(13L)
        doReturn(null).whenever(dao).findConversationSubjectById(any())
        doReturn(1).whenever(dao).markConversationSubject(any(), any())

        val first = service.of(12L)
        val second = service.of(13L)

        assertEquals("mandate-1-12@wutsi.com", first.messageId)
        assertEquals("mandate-1-13@wutsi.com", second.messageId)
        assertEquals("Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 12", first.subject)
        assertEquals("Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 13", second.subject)
    }
}
