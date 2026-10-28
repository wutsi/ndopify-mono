package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest
import com.wutsi.ndopify.npc.ui.listing.MandateListingView
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.ACCEPTED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.APPROVED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BASTOS
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val contractClient = mock<NdContractClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val assignmentClient = mock<NdAssignmentClient>()
    private val listingClient = mock<NdListingClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        // A Long? method of a mock answers 0, not null: no listing unless a test says so
        doReturn(null).whenever(listingClient).findIdByMandate(any(), any())
        doReturn(NOW).whenever(clock).millis()
        doReturn(PENDING).whenever(mandateClient).get(201)
        doReturn(CONTRACT).whenever(contractClient).get("c-201")
        doReturn(CUSTOMER).whenever(customerClient).get(555)
        doReturn(ACCEPTED).whenever(assignmentClient).accepted(201)

        val controller = MandateController(
            currentAgent,
            mandateClient,
            contractClient,
            customerClient,
            assignmentClient,
            listingClient,
            locationClient,
            tenantContext,
            clock,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun mandate(result: MvcResult) = model(result)["mandate"] as MandateView

    @Test
    fun show() {
        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("mandate", result.modelAndView?.viewName)
        val view = mandate(result)
        assertEquals(201L, view.id)
        assertEquals("22 oct. 2026", view.signatureDeadline)
        assertEquals("ray@gmail.com", view.contact?.email)
        assertEquals("10 oct. 2026", view.acceptedAt)
        assertEquals("Secteurs" to "Bonapriso, Akwa — Douala", view.keyCriteria[0])
        assertEquals("SN", model(result)["initials"])
        assertNull(model(result)["locked"])
        assertNull(model(result)["end"])
    }

    @Test
    fun `approved landlord - listing`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doReturn(ListingViewsTest.LISTING).whenever(listingClient).get(501)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertEquals("/listings/501", view.url)
        assertEquals("Continuer l'annonce", view.action)
    }

    @Test
    fun `approved landlord - listing unavailable`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)
        doThrow(ResourceAccessException("down")).whenever(listingClient).get(501)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertEquals("/listings/501", view.url)
        assertNull(view.text)
    }

    @Test
    fun `approved landlord - no listing yet`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertNull(view.url)
        assertEquals("Créer l'annonce", view.action)
        verify(listingClient, never()).get(any())
    }

    @Test
    fun `approved landlord - listing search unavailable`() {
        doReturn(APPROVED.copy(mandateType = MandateType.LISTING)).whenever(mandateClient).get(201)
        doThrow(ResourceAccessException("down")).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)

        val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

        assertEquals("Créer l'annonce", view.action)
    }

    @Test
    fun `approved search - no listing`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        assertNull(model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"])
        verify(listingClient, never()).findIdByMandate(any(), any())
        verify(listingClient, never()).get(any())
    }

    @Test
    fun `not found - locked`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("mandate", result.modelAndView?.viewName)
        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["mandate"])
    }

    @Test
    fun `malformed id - locked`() {
        val result = mvc.perform(get("/mandates/abc")).andReturn()

        assertEquals(true, model(result)["locked"])
        verify(mandateClient, never()).get(any())
    }

    @Test
    fun `pending assignment - locked`() {
        doReturn(MANDATE.copy(status = MandateStatus.PENDING_ASSIGNMENT)).whenever(mandateClient).get(201)

        assertEquals(true, model(mvc.perform(get("/mandates/201")).andReturn())["locked"])
    }

    @Test
    fun `contract unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(contractClient).get(any())

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertNull(view.signatureDeadline)
        assertEquals("ray@gmail.com", view.contact?.email)
    }

    @Test
    fun `no contract - not fetched`() {
        doReturn(MANDATE).whenever(mandateClient).get(201)

        mvc.perform(get("/mandates/201")).andReturn()

        verify(contractClient, never()).get(any())
    }

    @Test
    fun `customer unavailable`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(customerClient).get(any())

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertNull(view.contact)
        assertEquals("22 oct. 2026", view.signatureDeadline)
    }

    @Test
    fun `accepted assignment unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(assignmentClient).accepted(any())

        assertNull(mandate(mvc.perform(get("/mandates/201")).andReturn()).acceptedAt)
    }

    @Test
    fun `no accepted assignment`() {
        doReturn(null).whenever(assignmentClient).accepted(any())

        assertNull(mandate(mvc.perform(get("/mandates/201")).andReturn()).acceptedAt)
    }

    @Test
    fun `location names unavailable`() {
        whenever(locationClient.search(any())).thenThrow(ResourceAccessException("timeout"))

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertEquals("Secteurs" to "—", view.keyCriteria[0])
    }

    @Test
    fun `end - cancel open`() {
        assertEquals("cancel", model(mvc.perform(get("/mandates/201?end=cancel")).andReturn())["end"])
    }

    @Test
    fun `end - close ignored before approval`() {
        assertNull(model(mvc.perform(get("/mandates/201?end=close")).andReturn())["end"])
    }

    @Test
    fun `end - close open once approved`() {
        doReturn(PENDING.copy(status = MandateStatus.APPROVED)).whenever(mandateClient).get(201)

        assertEquals("close", model(mvc.perform(get("/mandates/201?end=close")).andReturn())["end"])
    }

    @Test
    fun `end - ignored once closed`() {
        doReturn(PENDING.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(201)

        assertNull(model(mvc.perform(get("/mandates/201?end=cancel")).andReturn())["end"])
    }

    @Test
    fun `end - unknown ignored`() {
        assertNull(model(mvc.perform(get("/mandates/201?end=xyz")).andReturn())["end"])
    }

    @Test
    fun `not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/login", mvc.perform(get("/mandates/201")).andReturn().modelAndView?.viewName)
    }

    // Send for approval, close, cancel

    private fun outcome(result: MvcResult) = result.flashMap["outcome"]

    @Test
    fun `request approval`() {
        doReturn(MANDATE).whenever(mandateClient).get(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("sent", outcome(result))
        verify(mandateClient).requestApproval(201)
    }

    @Test
    fun `request approval - again`() {
        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("resent", outcome(result))
    }

    @Test
    fun `request approval - conflict`() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(mandateClient).requestApproval(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `request approval - server error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).requestApproval(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("error", outcome(result))
    }

    @Test
    fun `request approval - mandate unavailable - still sent`() {
        doThrow(ResourceAccessException("timeout")).whenever(mandateClient).get(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("sent", outcome(result))
        verify(mandateClient).requestApproval(201)
    }

    @Test
    fun close() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        val result = mvc.perform(post("/mandates/201/close").param("finalPrice", "150 000")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("closed", outcome(result))
        verify(mandateClient).close(201, CloseMandateRequest(Money(150000, "XAF", PeriodicityType.MONTHLY)))
    }

    @Test
    fun `close - conflict`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(mandateClient).close(any(), any())

        val result = mvc.perform(post("/mandates/201/close").param("finalPrice", "150000")).andReturn()

        assertEquals("changed", outcome(result))
    }

    @Test
    fun `close - server error`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)
        doThrow(HttpServerErrorException(HttpStatus.BAD_GATEWAY)).whenever(mandateClient).close(any(), any())

        assertEquals("error", outcome(mvc.perform(post("/mandates/201/close").param("finalPrice", "150000")).andReturn()))
    }

    @Test
    fun `close - mandate gone`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(post("/mandates/201/close").param("finalPrice", "150000")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
        verify(mandateClient, never()).close(any(), any())
    }

    @Test
    fun `close - no longer the agent's`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(201)

        val result = mvc.perform(post("/mandates/201/close").param("finalPrice", "150000")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
        verify(mandateClient, never()).close(any(), any())
    }

    @Test
    fun `request approval - forbidden`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).requestApproval(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `forbidden - locked`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("mandate", result.modelAndView?.viewName)
        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["mandate"])
    }

    @Test
    fun `close - invalid amount`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        for (amount in listOf("", "abc", "0", "150k", "12abc", "1 500 000 F/mois")) {
            val result = mvc.perform(post("/mandates/201/close").param("finalPrice", amount)).andReturn()

            assertEquals("mandate", result.modelAndView?.viewName, amount)
            assertEquals("close", model(result)["end"])
            assertEquals(mapOf("finalPrice" to "Indiquez le loyer convenu, en FCFA par mois."), model(result)["errors"])
            assertEquals(amount, (model(result)["form"] as MandateEndForm).finalPrice)
            assertEquals(201L, mandate(result).id)
        }
        verify(mandateClient, never()).close(any(), any())
    }

    @Test
    fun cancel() {
        val result = mvc.perform(
            post("/mandates/201/cancel")
                .param("cancellationCode", "CHANGE_OF_PLANS")
                .param("cancellationReason", "  Il reste chez lui  "),
        ).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("cancelled", outcome(result))
        verify(mandateClient).cancel(201, CancelMandateRequest(MandateCancellationCode.CHANGE_OF_PLANS, "Il reste chez lui"))
    }

    @Test
    fun `cancel - blank reason sent as null`() {
        mvc.perform(post("/mandates/201/cancel").param("cancellationCode", "OTHER").param("cancellationReason", "  "))
            .andReturn()

        verify(mandateClient).cancel(201, CancelMandateRequest(MandateCancellationCode.OTHER, null))
    }

    @Test
    fun `cancel - conflict`() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(mandateClient).cancel(any(), any())

        val result = mvc.perform(post("/mandates/201/cancel").param("cancellationCode", "OTHER")).andReturn()

        assertEquals("changed", outcome(result))
    }

    @Test
    fun `cancel - no code`() {
        for (code in listOf(null, "UNKNOWN", "EXPIRED", "XYZ")) {
            val request = post("/mandates/201/cancel").param("cancellationReason", "Pourquoi pas")
            code?.let { request.param("cancellationCode", it) }

            val result = mvc.perform(request).andReturn()

            assertEquals("mandate", result.modelAndView?.viewName, code)
            assertEquals("cancel", model(result)["end"])
            assertEquals(mapOf("cancellationCode" to "Choisissez la raison de l'annulation."), model(result)["errors"])
            assertEquals("Pourquoi pas", (model(result)["form"] as MandateEndForm).cancellationReason)
        }
        verify(mandateClient, never()).cancel(any(), any())
    }

    @Test
    fun `cancel - reason too long`() {
        val result = mvc.perform(
            post("/mandates/201/cancel").param("cancellationCode", "OTHER").param("cancellationReason", "x".repeat(501)),
        ).andReturn()

        @Suppress("UNCHECKED_CAST")
        val errors = model(result)["errors"] as Map<String, String>
        assertTrue(errors.containsKey("cancellationReason"))
        verify(mandateClient, never()).cancel(any(), any())
    }

    @Test
    fun `cancel - already closed`() {
        doReturn(APPROVED.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(201)

        val result = mvc.perform(post("/mandates/201/cancel").param("cancellationCode", "")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `actions - not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        for (action in listOf("request-approval", "close", "cancel")) {
            assertEquals(
                "redirect:/login",
                mvc.perform(post("/mandates/201/$action")).andReturn().modelAndView?.viewName,
            )
        }
        verify(mandateClient, never()).requestApproval(any())
    }

    @Test
    fun `outcome - sent`() {
        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "sent")).andReturn()

        assertEquals("Mandat envoyé à ray@gmail.com pour signature.", model(result)["outcomeText"])
        assertEquals("success", model(result)["outcomeStyle"])
    }

    @Test
    fun `outcome - sent without contact`() {
        doThrow(ResourceAccessException("timeout")).whenever(customerClient).get(any())

        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "resent")).andReturn()

        assertEquals("Mandat renvoyé au client pour signature.", model(result)["outcomeText"])
    }

    @Test
    fun `outcome - saved while pending approval`() {
        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "saved-unsent")).andReturn()

        assertEquals(
            "Mandat modifié. L'ancienne version ne peut plus être signée : envoyez-le à nouveau pour que le client " +
                "signe la nouvelle.",
            model(result)["outcomeText"],
        )
    }

    @Test
    fun `request approval - customer e-mail of another member`() {
        doThrow(conflict(ErrorCode.CONTRACT_PARTY_MEMBERSHIP_CONFLICT)).whenever(mandateClient).requestApproval(201)

        val result = mvc.perform(post("/mandates/201/request-approval")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("email-conflict", outcome(result))
    }

    @Test
    fun `outcome - email conflict`() {
        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "email-conflict")).andReturn()

        assertEquals(
            "L'adresse e-mail du client est déjà utilisée par un autre compte : corrigez-la avant de demander sa signature.",
            model(result)["outcomeText"],
        )
        assertEquals("error", model(result)["outcomeStyle"])
    }

    @Test
    fun `outcome - unknown ignored`() {
        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "xyz")).andReturn()

        assertNull(model(result)["outcomeText"])
    }

    @Test
    fun `outcome - changed on a locked mandate`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "changed")).andReturn()

        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["outcomeText"])
    }

    // Edit

    private fun answers(result: MvcResult) = model(result)["answers"] as MandateEditForm

    private fun conflict(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult) = model(result)["errors"] as Map<String, String>

    /** A valid edit, [changes] replacing its values. */
    private fun edit(vararg changes: Pair<String, String>): MockHttpServletRequestBuilder {
        val params = mapOf(
            "neighborhoodIds" to BONAPRISO.toString(),
            "propertyCategory" to "RESIDENTIAL",
            "propertyType" to "APARTMENT",
            "rooms" to "3",
            "furnitureType" to "ANY",
            "parkings" to "1",
            "area" to "80",
            "amenities" to "waterWell",
            "priceAmount" to "150 000",
            "movingPeriod" to "WITHIN_1_MONTH",
            "comments" to "Près d'une école",
        ) + changes
        val request = post("/mandates/201/edit")
        params.forEach { (name, value) -> request.param(name, value) }
        return request
    }

    @Test
    fun `edit form`() {
        val result = mvc.perform(get("/mandates/201/edit")).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals("HOUSE", answers(result).propertyType)
        assertEquals("250\u00a0000", answers(result).priceAmount)
        assertEquals(emptyMap(), errors(result))
        assertEquals("Locataire · Maison", mandate(result).title)
        assertEquals("Douala", (model(result)["city"] as com.wutsi.ndopify.refdata.dto.Location).name)
        @Suppress("UNCHECKED_CAST")
        val neighborhoods = model(result)["neighborhoods"] as List<com.wutsi.ndopify.refdata.dto.Location>
        assertEquals(listOf("Bonapriso", "Akwa"), neighborhoods.map { it.name })
        assertEquals(false, model(result)["owner"])
        assertNull(model(result)["locked"])
    }

    @Test
    fun `edit form - approved - locked`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201/edit")).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals("approved", model(result)["locked"])
        assertNull(model(result)["answers"])
    }

    @Test
    fun `edit form - not found - locked`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        assertEquals("unavailable", model(mvc.perform(get("/mandates/201/edit")).andReturn())["locked"])
    }

    @Test
    fun `edit form - malformed id - locked`() {
        assertEquals("unavailable", model(mvc.perform(get("/mandates/abc/edit")).andReturn())["locked"])
    }

    @Test
    fun save() {
        val result = mvc.perform(edit()).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("saved-unsent", outcome(result)) // Its contract cancelled by ndopify-server: to send again
        val request = argumentCaptor<UpdateMandateRequest>()
        verify(mandateClient).update(com.nhaarman.mockitokotlin2.eq(201L), request.capture())
        assertEquals(listOf(BONAPRISO), request.firstValue.neighbourhoodIds)
        assertEquals(150000L, request.firstValue.price.amount)
        assertEquals(MovingPeriod.WITHIN_1_MONTH, request.firstValue.movingPeriod)
        assertEquals(PropertyCategory.RESIDENTIAL, request.firstValue.propertyCategory)
        assertEquals(true, request.firstValue.waterWell)
        assertEquals(null, request.firstValue.furnitureType)
    }

    @Test
    fun `save - sends the version the form was loaded with`() {
        doReturn(PENDING.copy(version = 5)).whenever(mandateClient).get(201)

        mvc.perform(edit("version" to "4")).andReturn()

        val request = argumentCaptor<UpdateMandateRequest>()
        verify(mandateClient).update(com.nhaarman.mockitokotlin2.eq(201L), request.capture())
        assertEquals(4L, request.firstValue.version)
    }

    @Test
    fun `save - changed since - the form again, with the answers, based on the new version`() {
        doReturn(PENDING.copy(version = 5)).whenever(mandateClient).get(201)
        doThrow(conflict(ErrorCode.MANDATE_VERSION_CONFLICT)).whenever(mandateClient).update(any(), any())

        val result = mvc.perform(edit("version" to "4")).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals(
            "Ce mandat a été modifié entre-temps. Vos réponses sont conservées : vérifiez-les, puis enregistrez à nouveau.",
            model(result)["error"],
        )
        assertEquals("150 000", answers(result).priceAmount)
        assertEquals(5L, answers(result).version)
        assertTrue(result.flashMap.isEmpty())
    }

    @Test
    fun `save - other conflict - error`() {
        doThrow(conflict(ErrorCode.MANDATE_NOT_APPROVED)).whenever(mandateClient).update(any(), any())

        val result = mvc.perform(edit()).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals(
            "Enregistrement impossible pour le moment. Réessayez dans quelques instants.",
            model(result)["error"],
        )
    }

    @Test
    fun `save - assigned`() {
        doReturn(PENDING.copy(status = MandateStatus.ASSIGNED)).whenever(mandateClient).get(201)

        val result = mvc.perform(edit()).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("saved", outcome(result))
    }

    @Test
    fun `save - signed or closed meanwhile - changed`() {
        listOf(ErrorCode.MANDATE_ALREADY_APPROVED, ErrorCode.MANDATE_ALREADY_CLOSED).forEach { code ->
            doThrow(conflict(code)).whenever(mandateClient).update(any(), any())

            val result = mvc.perform(edit()).andReturn()

            assertEquals("redirect:/mandates/201", result.modelAndView?.viewName, code)
            assertEquals("changed", outcome(result), code)
        }
    }

    @Test
    fun `save - approved since`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        val result = mvc.perform(edit()).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - mandate gone`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(edit()).andReturn()

        assertEquals("changed", outcome(result))
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - no longer the agent's`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(201)

        val result = mvc.perform(edit()).andReturn()

        assertEquals("changed", outcome(result))
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - invalid`() {
        val result = mvc.perform(edit("priceAmount" to "", "rooms" to "")).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals(setOf("priceAmount", "rooms"), errors(result).keys)
        assertEquals("80", answers(result).area)
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - unknown enum - invalid, not a 400`() {
        val result = mvc.perform(edit("movingPeriod" to "SOON", "propertyCategory" to "XYZ")).andReturn()

        assertEquals(200, result.response.status)
        assertEquals(setOf("movingPeriod", "propertyCategory", "propertyType"), errors(result).keys)
    }

    @Test
    fun `save - neighbourhood outside the city`() {
        val result = mvc.perform(edit("neighborhoodIds" to BASTOS.toString())).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals("Choisissez des quartiers de la ville sélectionnée.", errors(result)["neighborhoodIds"])
        verify(mandateClient, never()).update(any(), any())
    }

    @Test
    fun `save - server error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).update(any(), any())

        val result = mvc.perform(edit()).andReturn()

        assertEquals("mandate/edit", result.modelAndView?.viewName)
        assertEquals(
            "Enregistrement impossible pour le moment. Réessayez dans quelques instants.",
            model(result)["error"],
        )
        assertEquals("150 000", answers(result).priceAmount)
    }

    @Test
    fun `edit - not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/login", mvc.perform(get("/mandates/201/edit")).andReturn().modelAndView?.viewName)
        assertEquals("redirect:/login", mvc.perform(edit()).andReturn().modelAndView?.viewName)
    }

    @Test
    fun `close - amount with separators`() {
        doReturn(APPROVED).whenever(mandateClient).get(201)

        mvc.perform(post("/mandates/201/close").param("finalPrice", "150.000")).andReturn()

        verify(mandateClient).close(201, CloseMandateRequest(Money(150000, "XAF", PeriodicityType.MONTHLY)))
    }
}
