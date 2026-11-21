package com.wutsi.ndopify.listing.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.TelegramException
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ListingSentBackBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()
    private val botlet = ListingSentBackBotlet(tenantService, locationService, client)

    private val tenant = TenantEntity(id = 1L, name = "Ndopify", partnerCentralUrl = "https://partner.ndopify.com/")
    private val listing = ListingEntity(
        id = 101L,
        tenantId = 1L,
        cityId = 2370201L,
        neighborhoodId = 111L,
        errorCodes = listOf(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD),
    )
    private val text = """
        Votre annonce n° 101 — Bastos, Yaoundé a été renvoyée pour correction

        Ouvrez-la dans Partner Central pour voir ce qui est à corriger, puis renvoyez-la.
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaoundé")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
    }

    @Test
    fun `the message and its fix button are sent`() {
        botlet.send(listing, 1000L)

        verify(client).sendMessage(
            1000L,
            text,
            listOf(TelegramButton("Corriger l'annonce", url = "https://partner.ndopify.com/listings/101")),
        )
    }

    @Test
    fun `no partner central URL means no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(listing, 1000L)

        verify(client).sendMessage(1000L, text, emptyList())
    }

    @Test
    fun `no place means the id alone`() {
        val text = argumentCaptor<String>()
        botlet.send(listing.copy(cityId = -1L, neighborhoodId = null), 1000L)

        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.startsWith("Votre annonce n° 101 a été renvoyée pour correction\n"))
    }

    @Test
    fun `a Telegram refusal propagates`() {
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(client).sendMessage(eq(1000L), any(), any())

        assertFailsWith<TelegramException> { botlet.send(listing, 1000L) }
    }
}
