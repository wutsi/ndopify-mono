package com.wutsi.ndopify.npc.ui.profile

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import com.wutsi.ndopify.web.core.service.TenantContext

/** The signed-in agent and the locations of the profile tests. */
object ProfileFixtures {
    const val DOUALA = 2232593L
    const val YAOUNDE = 2220957L
    const val MONTREAL = 6077243L
    const val BONAPRISO = 3001L
    const val AKWA = 3002L
    const val BASTOS = 1001L

    val LOCATIONS = listOf(
        Location(id = DOUALA, name = "Douala", type = LocationType.CITY, country = "CM"),
        Location(id = YAOUNDE, name = "Yaoundé", type = LocationType.CITY, country = "CM"),
        Location(id = MONTREAL, name = "Montréal", type = LocationType.CITY, country = "CA"),
        Location(id = BONAPRISO, parentId = DOUALA, name = "Bonapriso", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = AKWA, parentId = DOUALA, name = "Akwa", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = BASTOS, parentId = YAOUNDE, name = "Bastos", type = LocationType.NEIGHBORHOOD, country = "CM"),
    )

    val AGENT = Agent(
        id = 20L,
        party = Party(
            id = 100L,
            firstName = "Serge",
            lastName = "Ngono",
            email = "serge@gmail.com",
            kycStatus = KycStatus.VERIFIED,
            photoUrl = "https://cdn.ndopify.com/party/100/photo/a.jpg",
        ),
        agentType = AgentType.INDEPENDANT,
        experienceLevel = ExperienceLevel.SENIOR,
        whatsappNumber = "+237670000001",
        cityId = DOUALA,
        neighborhoodIds = listOf(BONAPRISO, AKWA),
        biography = "Démarcheur à Douala depuis 6 ans.",
    )

    fun mockLocations(locationClient: NdLocationClient, tenantContext: TenantContext) {
        doReturn(TenantFixtures.TENANT).whenever(tenantContext).getTenant()
        whenever(locationClient.search(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<SearchLocationRequest>(0)
            SearchLocationResponse(locations = LOCATIONS.filter { request.ids.contains(it.id) })
        }
    }
}
