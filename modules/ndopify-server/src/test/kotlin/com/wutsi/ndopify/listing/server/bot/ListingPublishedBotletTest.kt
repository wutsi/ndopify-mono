package com.wutsi.ndopify.listing.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.TelegramException
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import java.io.OutputStream
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListingPublishedBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val imageService = mock<ListingImageService>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val client = mock<TelegramClient>()
    private val botlet = ListingPublishedBotlet(
        tenantService,
        locationService,
        imageService,
        storageProvider,
        client,
    )

    private val tenant = TenantEntity(id = 1L, name = "Ndopify", publicPortalUrl = "https://www.ndopify.com/")
    private val listing = ListingEntity(
        id = 1L,
        tenantId = 1L,
        cityId = 2370201L,
        neighborhoodId = 111L,
        title = "Appartement à louer, 3 chambres, Yaounde (Bastos)",
        url = "/yaounde/bastos/listing/1/appartement-a-louer-3-chambres-yaounde-bastos",
    )
    private val cover = ListingImageEntity(
        id = 7L,
        listingId = 1L,
        path = "listings/1/7.jpg",
        url = "https://cdn.ndopify.com/7.jpg",
        mimeType = "image/jpeg",
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )
    private val bytes = byteArrayOf(1, 2, 3)

    private val caption = """
        Votre annonce à Yaounde est publiée

        Le bien de Ray Sponsible est en ligne.

        Annonce: Appartement à louer, 3 chambres, Yaounde (Bastos)
        Ville: Yaounde
        Quartier: Bastos

        Prochaine étape : partagez le lien de l'annonce et organisez les visites.
    """.trimIndent()
    private val button = TelegramButton(
        "Voir l'annonce",
        url = "https://www.ndopify.com/yaounde/bastos/listing/1/appartement-a-louer-3-chambres-yaounde-bastos",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(cover).whenever(imageService).cover(listing)
        doReturn(storage).whenever(storageProvider).get()
        doAnswer { invocation -> (invocation.arguments[1] as OutputStream).write(bytes) }
            .whenever(storage).get(eq("listings/1/7.jpg"), any())
    }

    @Test
    fun `the hero image is uploaded with the caption and the button`() {
        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendPhoto(
            eq(1000L),
            argThat { contentEquals(bytes) },
            eq("1.jpg"),
            eq("image/jpeg"),
            eq(caption),
            eq(listOf(button)),
        )
        verify(client, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `no cover image sends the text only`() {
        doReturn(null).whenever(imageService).cover(listing)

        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendMessage(1000L, caption, listOf(button))
        verify(client, never()).sendPhoto(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a failed storage read still sends the text`() {
        doThrow(IllegalStateException("no such object"))
            .whenever(storage).get(eq("listings/1/7.jpg"), any())

        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendMessage(1000L, caption, listOf(button))
    }

    @Test
    fun `a refused photo still sends the text`() {
        doThrow(TelegramException("wrong file identifier/HTTP URL specified"))
            .whenever(client).sendPhoto(any(), any(), any(), any(), any(), any())

        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendMessage(1000L, caption, listOf(button))
    }

    @Test
    fun `no public page means no button`() {
        doReturn(tenant.copy(publicPortalUrl = null)).whenever(tenantService).findById(1L)
        doReturn(null).whenever(imageService).cover(listing)

        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendMessage(1000L, caption, emptyList())
    }

    @Test
    fun `a tenant without a resolvable city has no dangling place`() {
        doReturn(null).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(null).whenever(imageService).cover(listing)

        botlet.send(listing, customer, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.startsWith("Votre annonce est publiée"))
    }

    @Test
    fun `a blank title falls back to the listing id`() {
        doReturn(null).whenever(imageService).cover(listing)
        val text = argumentCaptor<String>()

        botlet.send(listing.copy(title = null), customer, agent, 1000L)

        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Annonce: Bien n° 1"))
    }

    @Test
    fun `no neighbourhood omits its line`() {
        doReturn(null).whenever(imageService).cover(listing)
        val text = argumentCaptor<String>()

        botlet.send(listing.copy(neighborhoodId = null), customer, agent, 1000L)

        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("Quartier:"))
    }
}
