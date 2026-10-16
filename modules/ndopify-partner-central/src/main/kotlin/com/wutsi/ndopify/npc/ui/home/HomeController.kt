package com.wutsi.ndopify.npc.ui.home

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.kyc.KycRules
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.party.dto.KycStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.client.RestClientException

/**
 * The Espace agent (`index.html`), where every agent lands: for now its profile section (photo, name, area,
 * biography summary, and an invitation to add what's missing). An agent who isn't verified yet also gets a banner
 * leading to the KYC page, worded after its case ([kyc]).
 */
@Controller
class HomeController(
    private val currentAgent: CurrentAgent,
    private val kycClient: NdKycClient,
    locationClient: NdLocationClient,
    tenantContext: TenantContext,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(HomeController::class.java)

        const val VIEW = "index"
        const val SIGN_OUT = "redirect:/logout"

        // The KYC banner: documents to send, case under review, case rejected.
        const val KYC_TODO = "todo"
        const val KYC_PENDING = "pending"
        const val KYC_REJECTED = "rejected"
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    @GetMapping("/")
    fun show(model: Model): String {
        val agent = currentAgent.get() ?: return SIGN_OUT

        ProfileRules.header(agent, model)
        model.addAttribute("fullName", ProfileRules.fullName(agent))
        model.addAttribute("photoUrl", agent.party.photoUrl?.ifBlank { null })
        model.addAttribute("biography", agent.biography?.trim()?.ifEmpty { null })
        model.addAttribute("area", area(agent))
        model.addAttribute("kyc", kyc(agent))
        return VIEW
    }

    /**
     * The KYC banner, null once verified. The party's status only changes when the case is verified or rejected,
     * so the case says whether the documents were sent. Without it, the banner still leads to the KYC page.
     */
    private fun kyc(agent: Agent): String? {
        if (agent.party.kycStatus == KycStatus.VERIFIED) return null
        val status =
            try {
                KycRules.status(kycClient.current(agent.party.id))
            } catch (ex: RestClientException) {
                LOGGER.warn("No KYC case for party ${agent.party.id}: ${ex.message}")
                null
            }
        return when (status) {
            null -> KYC_TODO
            KycRules.STATUS_REJECTED -> KYC_REJECTED
            else -> KYC_PENDING
        }
    }

    /** Left out (the section still shows) when the location names can't be had. */
    private fun area(agent: Agent): String? =
        try {
            val locations = sector.locations(listOfNotNull(agent.cityId) + agent.neighborhoodIds)
            ProfileRules.area(agent.cityId?.let { locations[it] }, agent.neighborhoodIds.mapNotNull { locations[it] })
        } catch (ex: RestClientException) {
            LOGGER.warn("No location names for agent ${agent.id}: ${ex.message}")
            null
        }
}
