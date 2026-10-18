package com.wutsi.ndopify.npc.ui.home

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.kyc.KycRules
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.mandate.MandateCardView
import com.wutsi.ndopify.npc.ui.mandate.MandateCards
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.Location
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.time.ZoneId

/**
 * The Espace agent (`index.html`), where every agent lands: its profile section (photo, name, area, biography
 * summary, and an invitation to add what's missing), the mandates offered to it ([offers]), each to accept or
 * decline (see `AssignmentController`), and its mandates in progress (*Mes mandats*, [mandates]), each opening its
 * page (see `MandateController`). An agent who isn't verified yet gets neither, but a banner leading to the KYC page,
 * worded after its case ([kyc]).
 */
@Controller
class HomeController(
    private val currentAgent: CurrentAgent,
    private val kycClient: NdKycClient,
    private val assignmentClient: NdAssignmentClient,
    private val mandateClient: NdMandateClient,
    private val contractClient: NdContractClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(HomeController::class.java)

        const val VIEW = "index"
        const val SIGN_OUT = "redirect:/logout"

        // The KYC banner: documents to send, case under review, case rejected.
        const val KYC_TODO = "todo"
        const val KYC_PENDING = "pending"
        const val KYC_REJECTED = "rejected"

        // Sent to the customer: the card's next step needs the contract's dates.
        private val SENT = setOf(MandateStatus.PENDING_APPROVAL, MandateStatus.APPROVED)
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    @GetMapping("/")
    fun show(@RequestParam(required = false) decline: String?, model: Model): String {
        val agent = currentAgent.get() ?: return SIGN_OUT

        ProfileRules.header(agent, model)
        model.addAttribute("fullName", ProfileRules.fullName(agent))
        model.addAttribute("photoUrl", agent.party.photoUrl?.ifBlank { null })
        model.addAttribute("biography", agent.biography?.trim()?.ifEmpty { null })
        model.addAttribute("area", area(agent))
        model.addAttribute("kyc", kyc(agent))
        if (agent.party.kycStatus == KycStatus.VERIFIED) {
            val now = clock.millis()
            val offers = offers(agent, now)
            val mandates = mandates(agent)
            val names = names(
                (offers.orEmpty().map { (_, mandate) -> mandate } + mandates.orEmpty().map { (mandate, _) -> mandate })
                    .flatMap { mandate -> listOf(mandate.cityId) + mandate.neighborhoodIds },
            )

            val assignments = offers?.map { (assignment, mandate) -> AssignmentViews.of(assignment, mandate, names, now) }
            model.addAttribute("assignments", assignments ?: emptyList<AssignmentView>())
            model.addAttribute("assignmentsUnavailable", assignments == null)
            // GET /?decline={id}: the decline confirmation without JS, for one of the listed offers only. A String,
            // so that a hand-edited id is ignored instead of failing the page.
            val declineId = decline?.toLongOrNull()
            model.addAttribute("declining", assignments?.firstOrNull { it.id == declineId }?.id)

            val zone = ZoneId.of(tenantContext.getTenant().timeZone)
            model.addAttribute(
                "mandates",
                mandates?.map { (mandate, contract) -> MandateCards.of(mandate, contract, names, now, zone) }
                    ?: emptyList<MandateCardView>(),
            )
            model.addAttribute("mandatesUnavailable", mandates == null)
        }
        return VIEW
    }

    /**
     * The offers still open, newest first; null when they can't be had. An offer whose mandate can't be loaded is
     * left out, so one bad mandate doesn't hide the others.
     */
    private fun offers(agent: Agent, now: Long): List<Pair<AssignmentSummary, Mandate>>? {
        val open =
            try {
                assignmentClient.pending(agent.id).filter { AssignmentViews.isOpen(it, now) }
            } catch (ex: RestClientException) {
                LOGGER.warn("No offers for agent ${agent.id}: ${ex.message}")
                return null
            }

        return open.mapNotNull { assignment ->
            try {
                assignment to mandateClient.get(assignment.mandateId)
            } catch (ex: RestClientException) {
                LOGGER.warn("No mandate ${assignment.mandateId} for offer ${assignment.id}: ${ex.message}")
                null
            }
        }
    }

    /**
     * The agent's mandates in progress, in the search's order, each with its contract once sent to the customer (for
     * the signature deadline or the end of validity); null when they can't be had. The summary lacks the moving date,
     * so each mandate is loaded: one that can't be is left out, and one whose contract can't be is shown without it.
     */
    private fun mandates(agent: Agent): List<Pair<Mandate, Contract?>>? {
        val summaries =
            try {
                mandateClient.active(agent.id)
            } catch (ex: RestClientException) {
                LOGGER.warn("No mandates for agent ${agent.id}: ${ex.message}")
                return null
            }

        return summaries.mapNotNull { summary ->
            try {
                mandateClient.get(summary.id)
            } catch (ex: RestClientException) {
                LOGGER.warn("No mandate ${summary.id} for agent ${agent.id}: ${ex.message}")
                null
            }
        }.map { mandate -> mandate to contract(mandate) }
    }

    private fun contract(mandate: Mandate): Contract? {
        val id = mandate.contractId
        if (id == null || mandate.status !in SENT) return null
        return try {
            contractClient.get(id)
        } catch (ex: RestClientException) {
            LOGGER.warn("No contract $id for mandate ${mandate.id}: ${ex.message}")
            null
        }
    }

    /** One lookup for every card; without the names, the cards still show (no sector). */
    private fun names(ids: List<Long>): Map<Long, Location> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            try {
                sector.locations(ids.distinct())
            } catch (ex: RestClientException) {
                LOGGER.warn("No location names for the offers and mandates: ${ex.message}")
                emptyMap()
            }
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
