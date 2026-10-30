package com.wutsi.ndopify.npc.ui.join

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.CreateAgentResponse
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinControllerTest {
    companion object {
        const val YAOUNDE = 2220957L
        const val DOUALA = 2232593L
        const val MONTREAL = 6077243L
        const val CENTRE = 23701L
    }

    private val agentClient = mock<NdAgentClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    private val locations = listOf(
        Location(id = YAOUNDE, name = "Yaoundé", type = LocationType.CITY, country = "CM"),
        Location(id = DOUALA, name = "Douala", type = LocationType.CITY, country = "CM"),
        Location(id = MONTREAL, name = "Montréal", type = LocationType.CITY, country = "CA"),
        Location(id = CENTRE, name = "Centre", type = LocationType.STATE, country = "CM"),
        Location(id = 1001, parentId = YAOUNDE, name = "Bastos", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 1002, parentId = YAOUNDE, name = "Essos", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 1003, parentId = YAOUNDE, name = "Mendong", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 1004, parentId = YAOUNDE, name = "Odza", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 2001, parentId = DOUALA, name = "Akwa", type = LocationType.NEIGHBORHOOD, country = "CM"),
    )

    private val complete = PendingJoin(
        firstName = "Ray",
        lastName = "Sponsible",
        email = "ray@gmail.com",
        whatsappNumber = "+237670000001",
        experienceLevel = ExperienceLevel.JUNIOR,
        agentType = AgentType.INDEPENDANT,
        cityId = YAOUNDE,
        neighborhoodIds = listOf(1001, 1002),
        mobileMoneyNumber = "+237670000002",
    )

    @BeforeEach
    fun setUp() {
        doReturn(Tenant(id = 1, countryCode = "CM")).whenever(tenantContext).getTenant()
        whenever(locationClient.search(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<SearchLocationRequest>(0)
            SearchLocationResponse(locations = locations.filter { request.ids.contains(it.id) })
        }

        val controller = JoinController(agentClient, locationClient, tenantContext)
        // The view is named after its URL ("join" at /join); the default resolver would take that for a loop.
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
        session = MockHttpSession()
    }

    // GET /join

    @Test
    fun show() {
        val result = mvc.perform(get("/join").session(session)).andReturn()

        assertEquals("join", result.modelAndView?.viewName)
        assertEquals("identity", model(result)["step"])
        assertEquals(1, model(result)["stepNumber"])
        assertEquals(5, model(result)["stepCount"])
        assertEquals(PendingJoin(), model(result)["answers"])
        assertEquals(emptyMap<String, String>(), model(result)["errors"])
        assertEquals(false, model(result)["returning"])
    }

    @Test
    fun showNeverSkipsAnIncompleteStep() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(experienceLevel = null))

        val result = mvc.perform(get("/join?step=payment").session(session)).andReturn()

        assertEquals("experience", model(result)["step"])
    }

    @Test
    fun showUnknownStep() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/join?step=nimportequoi").session(session)).andReturn()

        assertEquals("identity", model(result)["step"])
    }

    @Test
    fun showEarlierStep() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/join?step=experience").session(session)).andReturn()

        assertEquals("experience", model(result)["step"])
        assertEquals(2, model(result)["stepNumber"])
        assertEquals(complete, model(result)["answers"])
    }

    @Test
    fun showSector() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/join?step=area").session(session)).andReturn()

        assertEquals("area", model(result)["step"])
        assertEquals("Yaoundé", (model(result)["city"] as Location).name)
        assertEquals(listOf("Bastos", "Essos"), names(model(result)["neighborhoods"]))
    }

    @Test
    fun showSectorWithoutAnswers() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(cityId = null, neighborhoodIds = emptyList()))

        val result = mvc.perform(get("/join?step=area").session(session)).andReturn()

        assertEquals("area", model(result)["step"])
        assertNull(model(result)["city"])
        assertEquals(emptyList<Location>(), model(result)["neighborhoods"])
        verify(locationClient, never()).search(any())
    }

    @Test
    fun showPayment() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/join?step=payment").session(session)).andReturn()

        assertEquals("payment", model(result)["step"])
        assertEquals(true, model(result)["canUseWhatsapp"])
    }

    @Test
    fun showPaymentWithForeignWhatsapp() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(whatsappNumber = "+15145550000"))

        val result = mvc.perform(get("/join?step=payment").session(session)).andReturn()

        assertEquals(false, model(result)["canUseWhatsapp"])
    }

    @Test
    fun showReview() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/join?step=review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals(5, model(result)["stepNumber"])
        assertEquals("1 à 3 ans", model(result)["experienceLabel"])
        assertEquals("Indépendant", model(result)["agentTypeLabel"])
        assertEquals("Yaoundé", (model(result)["city"] as Location).name)
        assertEquals(listOf("Bastos", "Essos"), names(model(result)["neighborhoods"]))
        assertEquals(false, model(result)["returning"])
    }

    @Test
    fun showReturningToReview() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/join?step=identity&returnTo=review").session(session)).andReturn()

        assertEquals("identity", model(result)["step"])
        assertEquals(true, model(result)["returning"])
    }

    @Test
    fun showReturningToIncompleteReview() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(mobileMoneyNumber = null))

        val result = mvc.perform(get("/join?step=identity&returnTo=review").session(session)).andReturn()

        assertEquals(false, model(result)["returning"])
    }

    @Test
    fun showConfirmation() {
        session.setAttribute(JoinedAgent.SESSION_ATTRIBUTE, JoinedAgent(email = "ray@gmail.com"))

        val result = mvc.perform(get("/join?step=confirmation").session(session)).andReturn()

        assertEquals("join", result.modelAndView?.viewName)
        assertEquals("confirmation", model(result)["step"])
        assertEquals("ra•••@gmail.com", model(result)["maskedEmail"])
    }

    @Test
    fun showConfirmationWithoutApplication() {
        val result = mvc.perform(get("/join?step=confirmation").session(session)).andReturn()

        assertEquals("identity", model(result)["step"])
    }

    // POST /join/identity

    @Test
    fun identity() {
        val result = mvc.perform(
            post("/join/identity")
                .session(session)
                .param("firstName", " Ray ")
                .param("lastName", "N'Diaye-Mbappé")
                .param("email", " Ray@Gmail.COM ")
                .param("whatsappNumber", "00237 6 70-00.00 01"),
        ).andReturn()

        assertEquals("redirect:/join?step=experience", result.modelAndView?.viewName)
        assertEquals(
            PendingJoin(
                firstName = "Ray",
                lastName = "N'Diaye-Mbappé",
                email = "ray@gmail.com",
                whatsappNumber = "+237670000001",
            ),
            pending(),
        )
    }

    @Test
    fun identityKeepsLaterAnswers() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/join/identity")
                .session(session)
                .param("firstName", "Roger")
                .param("lastName", "Sponsible")
                .param("email", "ray@gmail.com")
                .param("whatsappNumber", "+237670000001"),
        ).andReturn()

        assertEquals("redirect:/join?step=experience", result.modelAndView?.viewName)
        assertEquals(complete.copy(firstName = "Roger"), pending())
    }

    @Test
    fun identityInvalid() {
        val result = mvc.perform(
            post("/join/identity")
                .session(session)
                .param("firstName", "")
                .param("lastName", "R2D2")
                .param("email", "ray@gmail")
                .param("whatsappNumber", "6 70"),
        ).andReturn()

        assertEquals("join", result.modelAndView?.viewName)
        assertEquals("identity", model(result)["step"])
        assertEquals(
            mapOf(
                "firstName" to "Saisissez votre prénom.",
                "lastName" to "Utilisez uniquement des lettres, des espaces, des tirets ou des apostrophes.",
                "email" to "Cette adresse email semble incomplète. Exemple : nom@exemple.com",
                "whatsappNumber" to "Ce numéro n'est pas un numéro mobile valide. Exemple : 6 77 12 34 56",
            ),
            model(result)["errors"],
        )
        // The page shows what was typed, but nothing reaches the session.
        assertEquals("R2D2", (model(result)["answers"] as PendingJoin).lastName)
        assertNull(session.getAttribute(PendingJoin.SESSION_ATTRIBUTE))
    }

    @Test
    fun identityMissingEverything() {
        val result = mvc.perform(post("/join/identity").session(session)).andReturn()

        assertEquals(
            mapOf(
                "firstName" to "Saisissez votre prénom.",
                "lastName" to "Saisissez votre nom.",
                "email" to "Saisissez votre adresse email.",
                "whatsappNumber" to "Saisissez votre numéro WhatsApp.",
            ),
            model(result)["errors"],
        )
    }

    @Test
    fun identityReturningToReview() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/join/identity")
                .session(session)
                .param("returnTo", "review")
                .param("firstName", "Ray")
                .param("lastName", "Sponsible")
                .param("email", "ray@gmail.com")
                .param("whatsappNumber", "+237670000009"),
        ).andReturn()

        assertEquals("redirect:/join?step=review", result.modelAndView?.viewName)
        assertEquals("+237670000009", pending()?.whatsappNumber)
    }

    @Test
    fun identityReturningToIncompleteReview() {
        val result = mvc.perform(
            post("/join/identity")
                .session(session)
                .param("returnTo", "review")
                .param("firstName", "Ray")
                .param("lastName", "Sponsible")
                .param("email", "ray@gmail.com")
                .param("whatsappNumber", "+237670000001"),
        ).andReturn()

        assertEquals("redirect:/join?step=experience", result.modelAndView?.viewName)
    }

    @Test
    fun identityStartsANewApplication() {
        session.setAttribute(JoinedAgent.SESSION_ATTRIBUTE, JoinedAgent(email = "old@gmail.com"))

        mvc.perform(
            post("/join/identity")
                .session(session)
                .param("firstName", "Ray")
                .param("lastName", "Sponsible")
                .param("email", "ray@gmail.com")
                .param("whatsappNumber", "+237670000001"),
        ).andReturn()

        assertNull(session.getAttribute(JoinedAgent.SESSION_ATTRIBUTE))
    }

    // POST /join/experience

    @Test
    fun experience() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, identityOnly())

        val result = mvc.perform(
            post("/join/experience")
                .session(session)
                .param("experienceLevel", "SENIOR")
                .param("agentType", "REAL_ESTATE_AGENT"),
        ).andReturn()

        assertEquals("redirect:/join?step=area", result.modelAndView?.viewName)
        assertEquals(ExperienceLevel.SENIOR, pending()?.experienceLevel)
        assertEquals(AgentType.REAL_ESTATE_AGENT, pending()?.agentType)
    }

    @Test
    fun experienceInvalid() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, identityOnly())

        val result = mvc.perform(
            post("/join/experience")
                .session(session)
                .param("experienceLevel", "UNKNOWN"),
        ).andReturn()

        assertEquals("experience", model(result)["step"])
        assertEquals(
            mapOf(
                "experienceLevel" to "Indiquez depuis combien de temps vous exercez.",
                "agentType" to "Indiquez si vous êtes indépendant ou rattaché à une agence.",
            ),
            model(result)["errors"],
        )
        assertEquals(identityOnly(), pending())
    }

    @Test
    fun experienceBeforeIdentity() {
        val result = mvc.perform(
            post("/join/experience")
                .session(session)
                .param("experienceLevel", "SENIOR")
                .param("agentType", "INDEPENDANT"),
        ).andReturn()

        assertEquals("redirect:/join", result.modelAndView?.viewName)
        assertNull(session.getAttribute(PendingJoin.SESSION_ATTRIBUTE))
    }

    // POST /join/area

    @Test
    fun sector() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(cityId = null, neighborhoodIds = emptyList()))

        val result = mvc.perform(
            post("/join/area")
                .session(session)
                .param("cityId", YAOUNDE.toString())
                .param("neighborhoodIds", "1003", "1004", "1003"),
        ).andReturn()

        assertEquals("redirect:/join?step=payment", result.modelAndView?.viewName)
        assertEquals(YAOUNDE, pending()?.cityId)
        assertEquals(listOf(1003L, 1004L), pending()?.neighborhoodIds)
    }

    @Test
    fun sectorMissing() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(post("/join/area").session(session)).andReturn()

        assertEquals("area", model(result)["step"])
        assertEquals(
            mapOf(
                "cityId" to "Choisissez votre ville principale d'intervention.",
                "neighborhoodIds" to "Choisissez au moins un quartier.",
            ),
            model(result)["errors"],
        )
        assertEquals(complete, pending())
    }

    @Test
    fun sectorTooManyNeighborhoods() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/join/area")
                .session(session)
                .param("cityId", YAOUNDE.toString())
                .param("neighborhoodIds", "1001", "1002", "1003", "1004"),
        ).andReturn()

        assertEquals(mapOf("neighborhoodIds" to "Choisissez au plus 3 quartiers."), model(result)["errors"])
        // Only the first 3 are looked up to show the step again.
        val request = argumentCaptor<SearchLocationRequest>()
        verify(locationClient).search(request.capture())
        assertEquals(listOf(YAOUNDE, 1001L, 1002L, 1003L), request.firstValue.ids)
    }

    @Test
    fun sectorUnknownCity() {
        assertCityRejected(999L)
    }

    @Test
    fun sectorCityOfAnotherCountry() {
        assertCityRejected(MONTREAL)
    }

    @Test
    fun sectorNotACity() {
        assertCityRejected(CENTRE)
    }

    @Test
    fun sectorNeighborhoodOfAnotherCity() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/join/area")
                .session(session)
                .param("cityId", YAOUNDE.toString())
                .param("neighborhoodIds", "1001", "2001"),
        ).andReturn()

        assertEquals(
            mapOf("neighborhoodIds" to "Choisissez des quartiers de la ville sélectionnée."),
            model(result)["errors"],
        )
        assertEquals(complete, pending())
    }

    @Test
    fun sectorUnknownNeighborhood() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/join/area")
                .session(session)
                .param("cityId", YAOUNDE.toString())
                .param("neighborhoodIds", "999"),
        ).andReturn()

        assertEquals(
            mapOf("neighborhoodIds" to "Choisissez des quartiers de la ville sélectionnée."),
            model(result)["errors"],
        )
    }

    @Test
    fun sectorNotANeighborhood() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/join/area")
                .session(session)
                .param("cityId", YAOUNDE.toString())
                .param("neighborhoodIds", DOUALA.toString()),
        ).andReturn()

        assertEquals(
            mapOf("neighborhoodIds" to "Choisissez des quartiers de la ville sélectionnée."),
            model(result)["errors"],
        )
    }

    @Test
    fun sectorBeforeExperience() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, identityOnly())

        val result = mvc.perform(
            post("/join/area")
                .session(session)
                .param("cityId", YAOUNDE.toString())
                .param("neighborhoodIds", "1001"),
        ).andReturn()

        assertEquals("redirect:/join?step=experience", result.modelAndView?.viewName)
        assertEquals(identityOnly(), pending())
    }

    // POST /join/payment

    @Test
    fun payment() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(mobileMoneyNumber = null))

        val result = mvc.perform(
            post("/join/payment")
                .session(session)
                .param("mobileMoneyNumber", "+237 6 70 00 00 05"),
        ).andReturn()

        assertEquals("redirect:/join?step=review", result.modelAndView?.viewName)
        assertEquals("+237670000005", pending()?.mobileMoneyNumber)
    }

    @Test
    fun paymentSameAsWhatsapp() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(mobileMoneyNumber = null))

        val result = mvc.perform(
            post("/join/payment")
                .session(session)
                .param("sameAsWhatsapp", "true")
                .param("mobileMoneyNumber", "+237699999999"),
        ).andReturn()

        assertEquals("redirect:/join?step=review", result.modelAndView?.viewName)
        assertEquals("+237670000001", pending()?.mobileMoneyNumber)
    }

    @Test
    fun paymentSameAsForeignWhatsapp() {
        val pending = complete.copy(whatsappNumber = "+15145550000", mobileMoneyNumber = null)
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, pending)

        val result = mvc.perform(
            post("/join/payment")
                .session(session)
                .param("sameAsWhatsapp", "true"),
        ).andReturn()

        assertEquals("payment", model(result)["step"])
        assertEquals(
            mapOf("mobileMoneyNumber" to "Saisissez un numéro Mobile Money camerounais. Exemple : 6 77 12 34 56"),
            model(result)["errors"],
        )
        assertEquals(pending, pending())
    }

    @Test
    fun paymentMissing() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(mobileMoneyNumber = null))

        val result = mvc.perform(post("/join/payment").session(session)).andReturn()

        assertEquals(mapOf("mobileMoneyNumber" to "Saisissez votre numéro Mobile Money."), model(result)["errors"])
    }

    @Test
    fun paymentBeforeSector() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(neighborhoodIds = emptyList()))

        val result = mvc.perform(
            post("/join/payment")
                .session(session)
                .param("mobileMoneyNumber", "+237670000005"),
        ).andReturn()

        assertEquals("redirect:/join?step=area", result.modelAndView?.viewName)
    }

    // POST /join/review

    @Test
    fun submit() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)
        doReturn(CreateAgentResponse(agentId = 42)).whenever(agentClient).create(any())

        val result = mvc.perform(post("/join/review").session(session)).andReturn()

        assertEquals("redirect:/join?step=confirmation", result.modelAndView?.viewName)
        val request = argumentCaptor<CreateAgentRequest>()
        verify(agentClient).create(request.capture())
        assertEquals(
            CreateAgentRequest(
                firstName = "Ray",
                lastName = "Sponsible",
                email = "ray@gmail.com",
                whatsappNumber = "+237670000001",
                mobileMoneyNumber = "+237670000002",
                agentType = AgentType.INDEPENDANT,
                experienceLevel = ExperienceLevel.JUNIOR,
                cityId = YAOUNDE,
                neighborhoodIds = listOf(1001, 1002),
            ),
            request.firstValue,
        )
        assertNull(session.getAttribute(PendingJoin.SESSION_ATTRIBUTE))
        assertEquals(JoinedAgent(email = "ray@gmail.com"), session.getAttribute(JoinedAgent.SESSION_ATTRIBUTE))
    }

    @Test
    fun submitIncomplete() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete.copy(agentType = null))

        val result = mvc.perform(post("/join/review").session(session)).andReturn()

        assertEquals("redirect:/join?step=experience", result.modelAndView?.viewName)
        verify(agentClient, never()).create(any())
    }

    @Test
    fun submitAgentAlreadyExists() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)
        doThrow(conflict(ErrorCode.AGENT_ALREADY_EXISTS)).whenever(agentClient).create(any())

        val result = mvc.perform(post("/join/review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals(true, model(result)["existingAccount"])
        assertEquals("Un compte utilise déjà cette adresse email.", model(result)["error"])
        assertEquals(complete, pending())
        assertNull(session.getAttribute(JoinedAgent.SESSION_ATTRIBUTE))
    }

    @Test
    fun submitMobileMoneyNumberNotValid() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)
        val badRequest = HttpClientErrorException(HttpStatus.BAD_REQUEST)
        badRequest.setBodyConvertFunction { ErrorResponse(Error(code = ErrorCode.PAYMENT_METHOD_NUMBER_NOT_VALID)) }
        doThrow(badRequest).whenever(agentClient).create(any())

        val result = mvc.perform(post("/join/review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals(true, model(result)["invalidMobileMoneyNumber"])
        assertEquals(false, model(result)["existingAccount"])
        assertEquals(
            "Ce numéro Mobile Money n'est pas valide : il n'appartient à aucun opérateur Mobile Money.",
            model(result)["error"],
        )
        assertEquals(complete, pending())
        assertNull(session.getAttribute(JoinedAgent.SESSION_ATTRIBUTE))
    }

    @Test
    fun submitRejected() {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)
        // No readable error body: the generic message.
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(agentClient).create(any())

        val result = mvc.perform(post("/join/review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals(false, model(result)["existingAccount"])
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
        assertEquals(complete, pending())
    }

    private fun assertCityRejected(cityId: Long) {
        session.setAttribute(PendingJoin.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/join/area")
                .session(session)
                .param("cityId", cityId.toString())
                .param("neighborhoodIds", "1001"),
        ).andReturn()

        assertEquals("area", model(result)["step"])
        assertEquals(
            mapOf("cityId" to "Cette ville n'est pas proposée. Choisissez-en une dans la liste."),
            model(result)["errors"],
        )
        assertEquals(complete, pending())
    }

    private fun identityOnly() = PendingJoin(
        firstName = complete.firstName,
        lastName = complete.lastName,
        email = complete.email,
        whatsappNumber = complete.whatsappNumber,
    )

    private fun conflict(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }

    private fun pending(): PendingJoin? = session.getAttribute(PendingJoin.SESSION_ATTRIBUTE) as PendingJoin?

    @Suppress("UNCHECKED_CAST")
    private fun names(locations: Any?): List<String> = (locations as List<Location>).map { it.name }

    private fun model(result: MvcResult): Map<String, Any?> = result.modelAndView!!.model.also {
        assertTrue(result.response.status < 400, "HTTP ${result.response.status}")
    }
}
