package com.wutsi.ndopify.npc.ui.home

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.config.Parallel
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.kyc.KycRules
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.mandate.MandateCards
import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import com.wutsi.ndopify.npc.ui.mandate.MandateSummaries
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.npc.ui.visit.VisitCards
import com.wutsi.ndopify.npc.ui.visit.VisitRules
import com.wutsi.ndopify.npc.ui.visit.VisitSlots
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.util.Date

/**
 * The Espace agent (`index.html`), where every agent lands: an invitation to add the photo or the biography while
 * one is missing (the agent doesn't need to see its own profile on each visit), the mandates offered to it
 * ([offers], no section without any), each to accept or decline (see `AssignmentController`), and its mandates in
 * progress (*Mes mandats*, [mandates]), each opening its page (see `MandateController`), a landlord's signed one with
 * its listing's status ([listings]) and, once published, its activity ([listingStats]), a signed search with its
 * one-line activity ([mandateStats]). An agent who isn't verified yet gets neither, but a banner leading to the KYC page,
 * worded after its case ([kyc]).
 */
@Controller
class HomeController(
    private val currentAgent: CurrentAgent,
    private val kycClient: NdKycClient,
    private val assignmentClient: NdAssignmentClient,
    private val mandateClient: NdMandateClient,
    private val listingClient: NdListingClient,
    private val visitClient: NdVisitClient,
    private val customerClient: NdCustomerClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
    private val parallel: Parallel,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(HomeController::class.java)

        const val VIEW = "index"

        // The KYC banner: documents to send, case under review, case rejected.
        const val KYC_TODO = "todo"
        const val KYC_PENDING = "pending"
        const val KYC_REJECTED = "rejected"
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    @GetMapping("/")
    fun show(@RequestParam(required = false) decline: String?, model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()

        ProfileRules.header(agent, model)
        model.addAttribute("photoMissing", agent.party.photoUrl.isNullOrBlank())
        model.addAttribute("biographyMissing", agent.biography.isNullOrBlank())
        model.addAttribute("kyc", kyc(agent))
        // The triage header has no counts until the sections are loaded (or for an agent who isn't verified).
        model.addAttribute("offerCount", 0)
        model.addAttribute("urgentOfferCount", 0)
        model.addAttribute("requestedCount", 0)
        model.addAttribute("todayCount", 0)
        if (agent.party.kycStatus == KycStatus.VERIFIED) {
            val now = clock.millis()

            // On the request thread, before any task: this initialises TenantContext's cache and the formatter.
            val tenant = tenantContext.getTenant()
            val formatter = TenantFormatter(tenant)

            // Wave 1: the independent reads.
            val openCall = parallel.async { openAssignments(agent, now) }
            val mandatesCall = parallel.async { mandates(agent) }
            val visitsCall = parallel.async { loadVisits(agent) }
            val open = openCall.join()
            val mandates = mandatesCall.join()
            val visits = visitsCall.join()

            // Wave 2: what the first wave unlocks.
            val offersCall = parallel.async { offers(agent, open) }
            val mandateStatsCall = parallel.async { mandates?.let { mandateStats(it) } }
            val listingsCall = parallel.async { mandates?.let { listings(agent, it) } }
            val mandateCustomersCall = parallel.async { mandates?.let { loadMandateCustomers(it) } ?: emptyMap() }
            val visitListingsCall = parallel.async { visits?.let { loadVisitListings(it) } }
            val visitCustomersCall = parallel.async { visits?.let { loadVisitCustomers(it) } ?: emptyMap() }
            val offers = offersCall.join()
            val mandateStats = mandateStatsCall.join()
            val listings = listingsCall.join()
            val mandateCustomers = mandateCustomersCall.join()
            val visitListings = visitListingsCall.join()
            val visitCustomers = visitCustomersCall.join()

            // Wave 3: the two reads that join the rest.
            val namesCall = parallel.async {
                names(
                    (offers.orEmpty().map { (_, mandate) -> mandate } + mandates.orEmpty())
                        .flatMap { mandate -> listOf(mandate.cityId) + mandate.neighborhoodIds } +
                        visitListings.orEmpty().values.flatMap { list -> listOfNotNull(list.cityId, list.neighborhoodId) },
                )
            }
            val listingStatsCall = parallel.async { listings?.let { listingStats(it.values) } }
            val names = namesCall.join()
            val stats = listingStatsCall.join()

            // The Model and the view stay on the request thread.
            val assignments = offers?.map { (assignment, mandate) ->
                AssignmentViews.of(assignment, mandate, names, now, formatter)
            }
            model.addAttribute("assignments", assignments ?: emptyList<AssignmentView>())
            model.addAttribute("assignmentsUnavailable", assignments == null)
            // GET /?decline={id}: the decline confirmation without JS, for one of the listed offers only. A String,
            // so that a hand-edited id is ignored instead of failing the page.
            val declineId = decline?.toLongOrNull()
            model.addAttribute("declining", assignments?.firstOrNull { it.id == declineId }?.id)

            val card = { mandate: Mandate ->
                MandateCards.of(mandate, listings, names, formatter, mandateStats, stats, mandateCustomers)
            }
            // A triage list: the actionable mandates first, five cards, the rest folded.
            val ordered = mandates?.let { MandateCards.order(it) }.orEmpty()
            val folded = ordered.drop(MandateCards.PAGE_SIZE)
            model.addAttribute("mandates", ordered.take(MandateCards.PAGE_SIZE).map(card))
            model.addAttribute("foldedMandates", folded.map(card))
            model.addAttribute(
                "foldedMandatesLabel",
                if (folded.size == 1) "Voir l'autre mandat" else "Voir les ${folded.size} autres mandats",
            )
            model.addAttribute("mandatesUnavailable", mandates == null)
            val visitsView = VisitCards.of(visits, visitListings, visitCustomers, names, now, tenant, formatter)
            model.addAttribute("visits", visitsView)

            // The triage header: the agent's day and what is waiting, so the first screen answers "what now?".
            val today = VisitSlots.localDate(Date(now), tenant)
            model.addAttribute("todayLabel", formatter.date(today, "EEEE d MMMM"))
            model.addAttribute("offerCount", assignments?.size ?: 0)
            model.addAttribute("urgentOfferCount", assignments?.count { it.urgent } ?: 0)
            model.addAttribute("requestedCount", visitsView.requested.size)
            model.addAttribute(
                "todayCount",
                visitsView.upcoming.count { card ->
                    card.at?.let { VisitSlots.localDate(Date(it), tenant) == today } == true
                },
            )
        }
        return VIEW
    }

    /** The agent's PENDING offers still open, soonest expiry first; null when they can't be had. */
    private fun openAssignments(agent: Agent, now: Long): List<AssignmentSummary>? =
        try {
            assignmentClient.pending(agent.id)
                .filter { AssignmentViews.isOpen(it, now) }
                .sortedBy { it.expiresAt.time }
        } catch (ex: RestClientException) {
            LOGGER.warn("No offers for agent ${agent.id}: ${ex.message}")
            null
        }

    /**
     * The mandates of [open] offers (one search for all of them); null when they can't be had, or when [open] is null.
     * An offer whose mandate isn't returned (no longer offered since) is left out.
     */
    private fun offers(agent: Agent, open: List<AssignmentSummary>?): List<Pair<AssignmentSummary, Mandate>>? =
        when {
            open == null -> null
            open.isEmpty() -> emptyList()
            else ->
                try {
                    val mandates = mandateClient.offered(agent.id).associateBy { it.id }
                    open.mapNotNull { assignment ->
                        mandates[assignment.mandateId]?.let { summary -> assignment to MandateSummaries.toMandate(summary) }
                    }
                } catch (ex: RestClientException) {
                    LOGGER.warn("No offers for agent ${agent.id}: ${ex.message}")
                    null
                }
        }

    /** The agent's mandates in progress, in the search's order; null when they can't be had. */
    private fun mandates(agent: Agent): List<Mandate>? =
        try {
            mandateClient.active(agent.id).map { summary -> MandateSummaries.toMandate(summary) }
        } catch (ex: RestClientException) {
            LOGGER.warn("No mandates for agent ${agent.id}: ${ex.message}")
            null
        }

    /**
     * The listings of the landlords' signed mandates by mandate id, in one search (none without such a mandate); null
     * when they can't be had: the cards then show no listing line.
     */
    private fun listings(agent: Agent, mandates: List<Mandate>): Map<Long, ListingSummary>? {
        val ids = mandates.filter { MandateRules.listed(it) }.map { it.id }
        if (ids.isEmpty()) return emptyMap()
        return try {
            listingClient.searchByMandates(ids, agent.id).associateBy { it.mandateId }
        } catch (ex: RestClientException) {
            LOGGER.warn("No listings for the mandates of agent ${agent.id}: ${ex.message}")
            null
        }
    }

    /** The approved searches' activity, by mandate id, in one call; null when it can't be had. */
    private fun mandateStats(mandates: List<Mandate>): Map<Long, MandateStats>? {
        val ids = mandates.filter { MatchRules.matched(it) }.map { it.id }
        if (ids.isEmpty()) return emptyMap()
        return try {
            mandateClient.stats(ids)
        } catch (ex: RestClientException) {
            LOGGER.warn("No stats for the mandates ${ids.joinToString()}: ${ex.message}")
            null
        }
    }

    /**
     * How each PUBLISHED listing is doing (its interested clients and visits), by listing id, in one call; null when it
     * can't be had: the cards then show no activity line.
     */
    private fun listingStats(listings: Collection<ListingSummary>): Map<Long, ListingStats>? {
        val ids = listings.filter { it.status == ListingStatus.PUBLISHED }.map { it.id }
        if (ids.isEmpty()) return emptyMap()
        return try {
            listingClient.stats(ids)
        } catch (ex: RestClientException) {
            LOGGER.warn("No stats for the listings ${ids.joinToString()}: ${ex.message}")
            null
        }
    }

    /** The visits still open, soonest first within requested and upcoming; null when they can't be had. */
    private fun loadVisits(agent: Agent): List<VisitSummary>? =
        try {
            visitClient.search(agent.id, VisitRules.OPEN)
        } catch (ex: RestClientException) {
            LOGGER.warn("No visits for agent ${agent.id}: ${ex.message}")
            null
        }

    /** The visits' listings by id, in one search; null when they can't be had (the cards keep their number only). */
    private fun loadVisitListings(visits: List<VisitSummary>): Map<Long, ListingSummary>? =
        try {
            listingClient.searchByIds(visits.map { it.listingId }.distinct()).associateBy { it.id }
        } catch (ex: RestClientException) {
            LOGGER.warn("No listings for the visits of agent: ${ex.message}")
            null
        }

    /** The visits' customers by id, in one search (none without [visits]); empty when they can't be had. */
    private fun loadVisitCustomers(visits: List<VisitSummary>): Map<Long, CustomerSummary> =
        try {
            customerClient.search(visits.map { it.customerId }.distinct())
                .mapNotNull { customer -> customer.id?.let { it to customer } }
                .toMap()
        } catch (ex: RestClientException) {
            LOGGER.warn("No customers for the visits of agent: ${ex.message}")
            emptyMap()
        }

    /**
     * The mandates' customers by id, in one search, for the cards' titles; empty when there are none or they can't be
     * had (the cards then keep the party and type as their title).
     */
    private fun loadMandateCustomers(mandates: List<Mandate>): Map<Long, CustomerSummary> {
        val ids = mandates.map { it.customerId }.filter { it > 0 }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return try {
            customerClient.search(ids)
                .mapNotNull { customer -> customer.id?.let { it to customer } }
                .toMap()
        } catch (ex: RestClientException) {
            LOGGER.warn("No customers for the mandates: ${ex.message}")
            emptyMap()
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
}
