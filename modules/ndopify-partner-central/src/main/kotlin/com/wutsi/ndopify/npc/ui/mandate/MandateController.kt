package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.refdata.dto.Location
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.time.ZoneId

/**
 * The page of one of the agent's mandates (`mandate.html`), opened from *Mes mandats*: its status as steps, the
 * customer's contact details, its criteria, and the actions of its status. Any status but the five of
 * [MandateRules.SHOWN], or a mandate that can't be loaded, is the locked view.
 *
 * Out of scope for now (and not checked by ndopify-server either): that the mandate is the signed-in agent's.
 */
@Controller
@RequestMapping("/mandates")
class MandateController(
    private val currentAgent: CurrentAgent,
    private val mandateClient: NdMandateClient,
    private val contractClient: NdContractClient,
    private val customerClient: NdCustomerClient,
    private val assignmentClient: NdAssignmentClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
    @get:Value("\${ndopify.contract.signature.ttl-days}") private val ttlDays: Int,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateController::class.java)

        const val VIEW = "mandate"
        const val SIGN_OUT = "redirect:/logout"

        // GET /mandates/{id}?end=…: the close / cancel form, open without JS.
        const val END_CLOSE = "close"
        const val END_CANCEL = "cancel"
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    /** [end] is a String so that a hand-edited value is ignored instead of failing the page; same for [id]. */
    @GetMapping("/{id}")
    fun show(@PathVariable id: String, @RequestParam(required = false) end: String?, model: Model): String {
        val agent = currentAgent.get() ?: return SIGN_OUT
        ProfileRules.header(agent, model)

        val mandate = id.toLongOrNull()?.let { load(it) } ?: return locked(model)
        val view = view(mandate)
        model.addAttribute("mandate", view)
        model.addAttribute(
            "end",
            end?.takeIf { (it == END_CLOSE && view.closable) || (it == END_CANCEL && view.cancellable) },
        )
        return VIEW
    }

    private fun locked(model: Model): String {
        model.addAttribute("locked", true)
        return VIEW
    }

    /** The mandate, null when it can't be had or the page doesn't show its status. */
    private fun load(id: Long): Mandate? =
        try {
            mandateClient.get(id).takeIf { it.status in MandateRules.SHOWN }
        } catch (ex: RestClientException) {
            LOGGER.warn("No mandate $id: ${ex.message}")
            null
        }

    /** Each secondary lookup that fails leaves its lines out: only the mandate itself is required. */
    private fun view(mandate: Mandate): MandateView =
        MandateViews.of(
            mandate,
            contract(mandate),
            customer(mandate),
            accepted(mandate),
            locations(mandate),
            clock.millis(),
            ZoneId.of(tenantContext.getTenant().timeZone),
            ttlDays,
        )

    private fun contract(mandate: Mandate): Contract? {
        val id = mandate.contractId ?: return null
        return lookup("contract $id of mandate ${mandate.id}") { contractClient.get(id) }
    }

    private fun customer(mandate: Mandate): Customer? =
        lookup("customer ${mandate.customerId} of mandate ${mandate.id}") { customerClient.get(mandate.customerId) }

    private fun accepted(mandate: Mandate): AssignmentSummary? =
        lookup("accepted offer of mandate ${mandate.id}") { assignmentClient.accepted(mandate.id) }

    private fun locations(mandate: Mandate): Map<Long, Location> =
        lookup("location names of mandate ${mandate.id}") {
            sector.locations(listOf(mandate.cityId) + mandate.neighborhoodIds)
        } ?: emptyMap()

    private fun <T> lookup(what: String, call: () -> T?): T? =
        try {
            call()
        } catch (ex: RestClientException) {
            LOGGER.warn("No $what: ${ex.message}")
            null
        }
}
