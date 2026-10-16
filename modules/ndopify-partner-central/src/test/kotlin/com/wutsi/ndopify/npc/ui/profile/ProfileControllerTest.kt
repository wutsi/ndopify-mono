package com.wutsi.ndopify.npc.ui.profile

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdPartyClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.kyc.KycRules
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BASTOS
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.MONTREAL
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.Location
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
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

class ProfileControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val agentClient = mock<NdAgentClient>()
    private val partyClient = mock<NdPartyClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10) + ByteArray(64)
    private val pdf = "%PDF-1.4\n".toByteArray() + ByteArray(64)

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()

        val controller = ProfileController(currentAgent, agentClient, partyClient, locationClient, tenantContext)
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult) = model(result)["errors"] as Map<String, String>

    /** A valid profile, with [overrides] replacing some of its fields. */
    private fun valid(vararg overrides: Pair<String, List<String>>): MockHttpServletRequestBuilder {
        val params = mapOf(
            "whatsappNumber" to listOf("+237 6 99 00 00 02"),
            "experienceLevel" to listOf("JUNIOR"),
            "agentType" to listOf("REAL_ESTATE_AGENT"),
            "cityId" to listOf(DOUALA.toString()),
            "neighborhoodIds" to listOf(AKWA.toString(), AKWA.toString()),
            "biography" to listOf("  Nouvelle bio.  "),
        ) + overrides
        val request = post("/profile")
        params.forEach { (name, values) -> request.param(name, *values.toTypedArray()) }
        return request
    }

    // GET /profile

    @Test
    fun edit() {
        val result = mvc.perform(get("/profile")).andReturn()

        assertEquals("profile/edit", result.modelAndView?.viewName)
        assertEquals(ProfileForm.of(AGENT), model(result)["answers"])
        assertEquals(emptyMap<String, String>(), errors(result))
        assertNull(model(result)["error"])
        assertEquals("Serge Ngono", model(result)["fullName"])
        assertEquals("SN", model(result)["initials"])
        assertEquals(AGENT.party.photoUrl, model(result)["userPhotoUrl"])
        assertEquals(AGENT.party.photoUrl, model(result)["photoUrl"])
        assertEquals(500, model(result)["maxBiography"])
        assertEquals("Douala", (model(result)["city"] as Location).name)
        assertEquals(listOf("Bonapriso", "Akwa"), (model(result)["neighborhoods"] as List<*>).map { (it as Location).name })
    }

    @Test
    fun `edit - open before the KYC is verified`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()

        val result = mvc.perform(get("/profile")).andReturn()

        assertEquals("profile/edit", result.modelAndView?.viewName)
    }

    @Test
    fun `edit - not an agent`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/logout", mvc.perform(get("/profile")).andReturn().modelAndView?.viewName)
    }

    // POST /profile

    @Test
    fun save() {
        val result = mvc.perform(valid()).andReturn()

        assertEquals("redirect:/", result.modelAndView?.viewName)
        assertEquals("profile", result.flashMap[ProfileRules.SAVED])

        val request = argumentCaptor<UpdateAgentRequest>()
        verify(agentClient).update(eq(20L), request.capture())
        assertEquals(
            UpdateAgentRequest(
                whatsappNumber = "+237699000002",
                agentType = AgentType.REAL_ESTATE_AGENT,
                experienceLevel = ExperienceLevel.JUNIOR,
                cityId = DOUALA,
                neighborhoodIds = listOf(AKWA),
                biography = "Nouvelle bio.",
            ),
            request.firstValue,
        )
    }

    @Test
    fun `save - empty biography clears it`() {
        mvc.perform(valid("biography" to listOf(""))).andReturn()

        val request = argumentCaptor<UpdateAgentRequest>()
        verify(agentClient).update(eq(20L), request.capture())
        assertEquals("", request.firstValue.biography)
    }

    @Test
    fun `save - not verified yet - back to the Espace agent too`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.SUBMITTED))).whenever(currentAgent).get()

        val result = mvc.perform(valid()).andReturn()

        assertEquals("redirect:/", result.modelAndView?.viewName)
        assertEquals("profile", result.flashMap[ProfileRules.SAVED])
    }

    @Test
    fun `save - missing answers`() {
        val result = mvc.perform(post("/profile")).andReturn()

        assertEquals("profile/edit", result.modelAndView?.viewName)
        assertEquals(
            setOf("whatsappNumber", "experienceLevel", "agentType", "cityId", "neighborhoodIds"),
            errors(result).keys,
        )
        verify(agentClient, never()).update(any(), any())
    }

    @Test
    fun `save - invalid answers`() {
        val result = mvc.perform(
            valid(
                "whatsappNumber" to listOf("12"),
                "neighborhoodIds" to listOf(BONAPRISO, AKWA, BASTOS, 4L).map { it.toString() },
                "biography" to listOf("x".repeat(501)),
            ),
        ).andReturn()

        assertEquals(setOf("whatsappNumber", "neighborhoodIds", "biography"), errors(result).keys)
        // Shown again as posted, the neighbourhoods capped to the 3 the select can hold.
        assertEquals("12", (model(result)["answers"] as ProfileForm).whatsappNumber)
        assertEquals(3, (model(result)["neighborhoods"] as List<*>).size)
        verify(agentClient, never()).update(any(), any())
    }

    @Test
    fun `save - city of another country`() {
        val result = mvc.perform(valid("cityId" to listOf(MONTREAL.toString()))).andReturn()

        assertEquals(setOf("cityId"), errors(result).keys)
        verify(agentClient, never()).update(any(), any())
    }

    @Test
    fun `save - neighbourhood of another city`() {
        val result = mvc.perform(valid("neighborhoodIds" to listOf(BASTOS.toString()))).andReturn()

        assertEquals(setOf("neighborhoodIds"), errors(result).keys)
        verify(agentClient, never()).update(any(), any())
    }

    @Test
    fun `save - server error`() {
        doThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST)).whenever(agentClient).update(any(), any())

        val result = mvc.perform(valid()).andReturn()

        assertEquals("profile/edit", result.modelAndView?.viewName)
        assertTrue(model(result)["error"] is String)
        assertEquals(emptyMap<String, String>(), errors(result))
        assertTrue(result.flashMap.isEmpty())
    }

    @Test
    fun `save - not an agent`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/logout", mvc.perform(valid()).andReturn().modelAndView?.viewName)
        verify(agentClient, never()).update(any(), any())
    }

    // GET /profile/photo

    @Test
    fun photo() {
        val result = mvc.perform(get("/profile/photo")).andReturn()

        assertEquals("profile/photo", result.modelAndView?.viewName)
        assertEquals(AGENT.party.photoUrl, model(result)["photoUrl"])
        assertEquals("SN", model(result)["initials"])
        assertEquals(AGENT.party.photoUrl, model(result)["userPhotoUrl"])
        assertNull(model(result)["error"])
    }

    @Test
    fun `photo - too large`() {
        val result = mvc.perform(get("/profile/photo?error=too-large")).andReturn()

        assertTrue((model(result)["error"] as String).contains("5 Mo"))
    }

    @Test
    fun `photo - not an agent`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/logout", mvc.perform(get("/profile/photo")).andReturn().modelAndView?.viewName)
    }

    // POST /profile/photo

    private fun upload(bytes: ByteArray, contentType: String = "image/jpeg") =
        multipart("/profile/photo").file(MockMultipartFile("photo", "photo.jpg", contentType, bytes))

    @Test
    fun upload() {
        // The bytes decide the type, not what the browser claims.
        val result = mvc.perform(upload(jpeg, "application/octet-stream")).andReturn()

        assertEquals("redirect:/", result.modelAndView?.viewName)
        assertEquals("photo", result.flashMap[ProfileRules.SAVED])
        verify(partyClient).uploadPhoto(eq(100L), eq(jpeg), eq("image/jpeg"))
    }

    @Test
    fun `upload - not verified yet - back to the Espace agent too`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()

        val result = mvc.perform(upload(jpeg)).andReturn()

        assertEquals("redirect:/", result.modelAndView?.viewName)
        verify(partyClient).uploadPhoto(eq(100L), any(), eq("image/jpeg"))
    }

    @Test
    fun `upload - not an image`() {
        val result = mvc.perform(upload(pdf)).andReturn()

        assertEquals("profile/photo", result.modelAndView?.viewName)
        assertTrue((model(result)["error"] as String).contains("JPG, PNG ou WEBP"))
        verify(partyClient, never()).uploadPhoto(any(), any(), any())
    }

    @Test
    fun `upload - too large`() {
        val result = mvc.perform(upload(jpeg + ByteArray(KycRules.MAX_PHOTO_BYTES.toInt()))).andReturn()

        assertTrue((model(result)["error"] as String).contains("5 Mo"))
        verify(partyClient, never()).uploadPhoto(any(), any(), any())
    }

    @Test
    fun `upload - no file keeps the current photo`() {
        val result = mvc.perform(multipart("/profile/photo")).andReturn()

        assertEquals("redirect:/", result.modelAndView?.viewName)
        assertTrue(result.flashMap.isEmpty())
        verify(partyClient, never()).uploadPhoto(any(), any(), any())
    }

    @Test
    fun `upload - no file and no photo yet`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(photoUrl = null))).whenever(currentAgent).get()

        val result = mvc.perform(multipart("/profile/photo")).andReturn()

        assertEquals("profile/photo", result.modelAndView?.viewName)
        assertEquals("Ajoutez une photo pour continuer.", model(result)["error"])
    }

    @Test
    fun `upload - server unreachable`() {
        doThrow(ResourceAccessException("timeout")).whenever(partyClient).uploadPhoto(any(), any(), any())

        val result = mvc.perform(upload(jpeg)).andReturn()

        assertEquals("profile/photo", result.modelAndView?.viewName)
        assertTrue(model(result)["error"] is String)
        assertTrue(result.flashMap.isEmpty())
    }

    @Test
    fun `upload - not an agent`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/logout", mvc.perform(upload(jpeg)).andReturn().modelAndView?.viewName)
        verify(partyClient, never()).uploadPhoto(any(), any(), any())
    }
}
