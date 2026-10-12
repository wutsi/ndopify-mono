package com.wutsi.ndopify.npc.ui.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.service.security.CurrentParty
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KycControllerTest {
    companion object {
        const val PARTY_ID = 100L
    }

    private val kycClient = mock<NdKycClient>()
    private val currentParty = mock<CurrentParty>()
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    private val front = KycImage(id = "img-front", imageType = KycImageType.FRONT)
    private val back = KycImage(id = "img-back", imageType = KycImageType.BACK)
    private val selfie = KycImage(id = "img-selfie", imageType = KycImageType.SELFIE)

    private fun case(status: KycStatus = KycStatus.PENDING, images: List<KycImage> = emptyList(), errorCode: String? = null) =
        KycCase(id = "case-1", partyId = PARTY_ID, status = status, images = images, errorCode = errorCode)

    private fun given(case: KycCase?) {
        doReturn(case).whenever(kycClient).current(PARTY_ID)
    }

    @BeforeEach
    fun setUp() {
        doReturn(Party(id = PARTY_ID, firstName = "Ray", lastName = "Sponsible", email = "ray@gmail.com"))
            .whenever(currentParty).get()
        whenever(kycClient.imageUrl(any())).thenAnswer { invocation -> "https://s3/${invocation.getArgument<String>(0)}.jpg" }

        // The view is named after its URL ("kyc" at /kyc); the default resolver would take that for a loop.
        mvc = MockMvcBuilders.standaloneSetup(KycController(kycClient, currentParty))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
        session = MockHttpSession()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    @Test
    fun `new case - first step`() {
        given(case())

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("kyc", result.modelAndView?.viewName)
        assertEquals("wizard", model(result)["view"])
        assertEquals("recto", model(result)["step"])
        assertEquals(1, model(result)["stepNumber"])
        assertEquals(4, model(result)["stepCount"])
        assertEquals(false, model(result)["returning"])
        assertNull(model(result)["error"])
        assertEquals(emptyMap<String, String>(), model(result)["photoUrls"])
        assertEquals("ray@gmail.com", model(result)["email"])
        assertEquals("RS", model(result)["initials"])
    }

    @Test
    fun `never skips a missing photo`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(get("/kyc?etape=revue").session(session)).andReturn()

        assertEquals("verso", model(result)["step"])
    }

    @Test
    fun `unknown step - first step`() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?etape=hack").session(session)).andReturn()

        assertEquals("recto", model(result)["step"])
    }

    @Test
    fun `upload step shows its photo`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(get("/kyc?etape=recto").session(session)).andReturn()

        assertEquals(mapOf("FRONT" to "https://s3/img-front.jpg"), model(result)["photoUrls"])
    }

    @Test
    fun review() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?etape=revue").session(session)).andReturn()

        assertEquals("revue", model(result)["step"])
        assertEquals(4, model(result)["stepNumber"])
        assertEquals(
            mapOf("FRONT" to "https://s3/img-front.jpg", "BACK" to "https://s3/img-back.jpg", "SELFIE" to "https://s3/img-selfie.jpg"),
            model(result)["photoUrls"],
        )
    }

    @Test
    fun `returning to the review`() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?etape=verso&retour=revue").session(session)).andReturn()

        assertEquals("verso", model(result)["step"])
        assertEquals(true, model(result)["returning"])
    }

    @Test
    fun `no case yet - first step, nothing created`() {
        given(null)

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("recto", model(result)["step"])
        verify(kycClient, never()).create(any())
    }

    @Test
    fun `submitted - pending status`() {
        given(case(status = KycStatus.SUBMITTED, images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?etape=recto").session(session)).andReturn()

        assertEquals("status", model(result)["view"])
        assertEquals("attente", model(result)["status"])
        verify(kycClient, never()).imageUrl(any())
    }

    @Test
    fun `verified status`() {
        given(case(status = KycStatus.VERIFIED))

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("verifie", model(result)["status"])
    }

    @Test
    fun `rejected status with its reason`() {
        given(case(status = KycStatus.REJECTED, errorCode = KycErrorCode.EXPIRED))

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("rejete", model(result)["status"])
        assertEquals("votre carte d'identité n'est plus valide.", model(result)["rejectionReason"])
    }

    @Test
    fun confirmation() {
        given(case(status = KycStatus.SUBMITTED))
        session.setAttribute(SubmittedKyc.SESSION_ATTRIBUTE, SubmittedKyc(caseId = "case-1"))

        val result = mvc.perform(get("/kyc?etape=confirmation").session(session)).andReturn()

        assertEquals("confirmation", model(result)["view"])
    }

    @Test
    fun `confirmation without a submission - status`() {
        given(case(status = KycStatus.SUBMITTED))

        val result = mvc.perform(get("/kyc?etape=confirmation").session(session)).andReturn()

        assertEquals("status", model(result)["view"])
    }

    @Test
    fun `no party - signed out`() {
        doReturn(null).whenever(currentParty).get()

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("redirect:/logout", result.modelAndView?.viewName)
    }
}
