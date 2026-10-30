package com.wutsi.ndopify.npc.ui.listing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.IMAGES
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.LISTING
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingPhotoControllerTest {
    companion object {
        // Enough of each format for Tika to recognize it
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0)
        val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val PDF = "%PDF-1.4\n%âãÏÓ\n".toByteArray()

        fun photo(name: String, content: ByteArray, type: String = "image/jpeg") = MockMultipartFile("photo", name, type, content)
    }

    private val currentAgent = mock<CurrentAgent>()
    private val listingClient = mock<NdListingClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(LISTING).whenever(listingClient).get(501)
        doReturn(IMAGES).whenever(listingClient).images(501)

        mvc = MockMvcBuilders.standaloneSetup(ListingPhotoController(currentAgent, listingClient, locationClient, tenantContext))
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun error(result: MvcResult) = result.flashMap[ListingPhotoController.PHOTO_ERROR]

    @Test
    fun show() {
        val result = mvc.perform(get("/listings/501/photos")).andReturn()

        assertEquals("listing/photos", result.modelAndView?.viewName)
        assertEquals("Photos", (model(result)["section"] as SectionView).heading)
        assertEquals(3, (model(result)["photos"] as PhotosView).photos.size)
        assertNull(model(result)[ListingPhotoController.PHOTO_ERROR])
    }

    @Test
    fun `show - too large`() {
        val result = mvc.perform(get("/listings/501/photos").param("error", "too-large")).andReturn()

        assertEquals(
            "Ces photos sont trop lourdes : envoyez-les une par une, 5 Mo au plus chacune.",
            model(result)[ListingPhotoController.PHOTO_ERROR],
        )
    }

    @Test
    fun `show - photos unavailable`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).images(501)

        val result = mvc.perform(get("/listings/501/photos")).andReturn()

        assertEquals(0, (model(result)["photos"] as PhotosView).photos.size)
        assertEquals(
            "Photos indisponibles pour le moment. Réessayez dans quelques instants.",
            model(result)[ListingPhotoController.PHOTO_ERROR],
        )
    }

    @Test
    fun `show - another agent's listing is locked`() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val result = mvc.perform(get("/listings/501/photos")).andReturn()

        assertEquals(true, model(result)["locked"])
        verify(listingClient, never()).images(any())
    }

    @Test
    fun upload() {
        val result = mvc.perform(
            multipart("/listings/501/photos")
                .file(photo("salon.jpg", JPEG))
                .file(photo("cuisine.png", PNG, "application/octet-stream")),
        ).andReturn()

        // The bytes are the truth, not the browser's content type
        verify(listingClient).upload(501, JPEG, "image/jpeg", "salon.jpg")
        verify(listingClient).upload(501, PNG, "image/png", "cuisine.png")
        assertEquals("redirect:/listings/501/photos", result.modelAndView?.viewName)
        assertNull(error(result))
    }

    @Test
    fun `upload - not a photo`() {
        val result = mvc.perform(
            multipart("/listings/501/photos").file(photo("bail.jpg", PDF)).file(photo("salon.jpg", JPEG)),
        ).andReturn()

        verify(listingClient, times(1)).upload(any(), any(), any(), any())
        verify(listingClient).upload(501, JPEG, "image/jpeg", "salon.jpg")
        assertEquals("bail.jpg n'est pas une photo JPEG, PNG ou WEBP.", error(result))
    }

    @Test
    fun `upload - over 5 Mo`() {
        val big = JPEG + ByteArray(5 * 1024 * 1024)

        val result = mvc.perform(multipart("/listings/501/photos").file(photo("salon.jpg", big))).andReturn()

        verify(listingClient, never()).upload(any(), any(), any(), any())
        assertEquals("salon.jpg dépasse 5 Mo.", error(result))
    }

    @Test
    fun `upload - no file`() {
        val result = mvc.perform(multipart("/listings/501/photos").file(photo("", ByteArray(0)))).andReturn()

        verify(listingClient, never()).upload(any(), any(), any(), any())
        assertEquals("Choisissez une photo à envoyer.", error(result))
    }

    @Test
    fun `upload - 20 photos at most`() {
        doReturn((1L..19L).map { ListingImage(id = it, status = ImageStatus.VERIFIED) }).whenever(listingClient).images(501)

        val result = mvc.perform(
            multipart("/listings/501/photos").file(photo("a.jpg", JPEG)).file(photo("b.jpg", JPEG)),
        ).andReturn()

        verify(listingClient).upload(501, JPEG, "image/jpeg", "a.jpg")
        verify(listingClient, never()).upload(eq(501L), any(), any(), eq("b.jpg"))
        assertEquals("20 photos au plus.", error(result))
    }

    @Test
    fun `upload - sent meanwhile`() {
        doReturn(LISTING.copy(status = ListingStatus.UNDER_VERIFICATION)).whenever(listingClient).get(501)

        val result = mvc.perform(multipart("/listings/501/photos").file(photo("salon.jpg", JPEG))).andReturn()

        verify(listingClient, never()).upload(any(), any(), any(), any())
        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
        assertEquals(ListingController.CHANGED, result.flashMap[ListingController.OUTCOME])
    }

    @Test
    fun `upload - another agent's listing`() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val result = mvc.perform(multipart("/listings/501/photos").file(photo("salon.jpg", JPEG))).andReturn()

        verify(listingClient, never()).upload(any(), any(), any(), any())
        assertEquals(ListingController.CHANGED, result.flashMap[ListingController.OUTCOME])
    }

    @Test
    fun `upload - refused by the server`() {
        doThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST)).whenever(listingClient).upload(any(), any(), any(), any())

        val result = mvc.perform(multipart("/listings/501/photos").file(photo("salon.jpg", JPEG))).andReturn()

        assertEquals("salon.jpg n'est pas une photo JPEG, PNG ou WEBP.", error(result))
    }

    @Test
    fun `upload - failure`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).upload(any(), any(), any(), any())

        val result = mvc.perform(multipart("/listings/501/photos").file(photo("salon.jpg", JPEG))).andReturn()

        assertEquals("redirect:/listings/501/photos", result.modelAndView?.viewName)
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", error(result))
    }

    @Test
    fun `upload - malformed id`() {
        val result = mvc.perform(multipart("/listings/abc/photos").file(photo("salon.jpg", JPEG))).andReturn()

        assertEquals("redirect:/", result.modelAndView?.viewName)
    }

    @Test
    fun delete() {
        val result = mvc.perform(post("/listings/501/photos/3/delete")).andReturn()

        verify(listingClient).deleteImage(3)
        assertEquals("redirect:/listings/501/photos", result.modelAndView?.viewName)
    }

    @Test
    fun `delete - not one of the listing's images`() {
        val result = mvc.perform(post("/listings/501/photos/42/delete")).andReturn()

        verify(listingClient, never()).deleteImage(any())
        assertEquals("redirect:/listings/501/photos", result.modelAndView?.viewName)
    }

    @Test
    fun `delete - sent meanwhile`() {
        doReturn(LISTING.copy(status = ListingStatus.PUBLISHED)).whenever(listingClient).get(501)

        val result = mvc.perform(post("/listings/501/photos/3/delete")).andReturn()

        verify(listingClient, never()).deleteImage(any())
        assertEquals(ListingController.CHANGED, result.flashMap[ListingController.OUTCOME])
    }

    @Test
    fun `delete - another agent's listing`() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val result = mvc.perform(post("/listings/501/photos/3/delete")).andReturn()

        verify(listingClient, never()).deleteImage(any())
        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
    }

    @Test
    fun `delete - failure`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).deleteImage(3)

        val result = mvc.perform(post("/listings/501/photos/3/delete")).andReturn()

        assertEquals("Suppression impossible pour le moment. Réessayez dans quelques instants.", error(result))
    }

    @Test
    fun `delete - malformed image id`() {
        val result = mvc.perform(post("/listings/501/photos/abc/delete")).andReturn()

        verify(listingClient, never()).deleteImage(any())
        assertTrue(result.modelAndView?.viewName!!.startsWith("redirect:/listings/501"))
        verify(listingClient, never()).upload(any(), any(), anyOrNull(), any())
    }
}
