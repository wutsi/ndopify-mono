package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock

/**
 * One section of a listing's checklist (`listing/section.html`), saved by the agent on site: location, GPS position,
 * the property, amenities, terms or remarks (the photos have their own page, [ListingPhotoController]). The form is
 * pre-filled from the listing (the landlord's answers until the agent saves it), with the landlord's answer under
 * each field the agent changed.
 *
 * Saving posts the listing's `version` the form was shown with: changed meanwhile (409 LISTING_VERSION_CONFLICT),
 * the form comes back with the agent's answers, now based on the new version. A listing sent meanwhile, or not the
 * agent's, goes back to the checklist with [ListingController.CHANGED]; once sent, the section is only shown.
 */
@Controller
@RequestMapping("/listings/{id}")
class ListingSectionController(
    private val currentAgent: CurrentAgent,
    private val listingClient: NdListingClient,
    private val mandateClient: NdMandateClient,
    private val locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingSectionController::class.java)

        const val VIEW = "listing/section"

        /** The flash outcome of a section saved: `saved-location`, worded by [ListingController]. */
        const val SAVED_PREFIX = "saved-"

        // The city's neighbourhoods of the location's <select>
        private const val MAX_NEIGHBORHOODS = 100

        private const val SAVE_FAILED = "Enregistrement impossible pour le moment. Réessayez dans quelques instants."
        private const val SAVE_CONFLICT =
            "Cette annonce a été modifiée entre-temps. Vos réponses sont conservées : vérifiez-les, puis enregistrez à nouveau."
        private const val NEIGHBORHOOD_INVALID = "Choisissez le quartier du bien."
    }

    private val sector = SectorValidator(locationClient, tenantContext)
    private val access = ListingAccess(listingClient)

    /** [id] is a String so that a hand-edited value is the locked view instead of failing the page. */
    @GetMapping("/{slug}")
    fun show(@PathVariable id: String, @PathVariable slug: String, model: Model): String {
        val section = ListingRules.section(slug) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val agent = currentAgent.get() ?: return ListingController.SIGN_OUT
        ProfileRules.header(agent, model)

        val listingId = id.toLongOrNull() ?: return unavailable(ListingAccess.Locked, model)
        return when (val result = access.load(listingId, agent)) {
            is ListingAccess.Found -> render(
                result.listing,
                section,
                ListingSectionForm.of(result.listing, formatter()),
                emptyMap(),
                null,
                model,
            )
            else -> unavailable(result, model)
        }
    }

    /** Unknown choices (hand-edited form) are field errors: [binding] keeps a value that doesn't bind from a 400. */
    @PostMapping("/{slug}")
    fun save(
        @PathVariable("id") value: String,
        @PathVariable slug: String,
        @ModelAttribute form: ListingSectionForm,
        binding: BindingResult,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val section = ListingRules.section(slug) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val agent = currentAgent.get() ?: return ListingController.SIGN_OUT
        val id = value.toLongOrNull() ?: return ListingController.HOME

        val listing = when (val result = access.load(id, agent)) {
            is ListingAccess.Found -> result.listing.takeIf { ListingRules.editable(it) }
                ?: return back(id, ListingController.CHANGED, redirect)
            ListingAccess.Locked -> return back(id, ListingController.CHANGED, redirect)
            ListingAccess.Unavailable -> return back(id, ListingController.FAILED, redirect)
        }

        ProfileRules.header(agent, model)
        val errors = ListingRules.errors(section, form, formatter().today(clock), formatter())
            .ifEmpty { neighborhoodErrors(listing, section, form) }
        if (errors.isNotEmpty()) return render(listing, section, form, errors, null, model)

        try {
            listingClient.update(id, ListingRules.request(section, form))
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("Listing $id not saved (${section.name}): ${ex.statusCode}")
            return when {
                errorCode(ex) == ErrorCode.LISTING_VERSION_CONFLICT -> conflict(id, agent, section, form, model, redirect)
                ex.statusCode.value() in setOf(403, 404, 409) -> back(id, ListingController.CHANGED, redirect)
                else -> render(listing, section, form, emptyMap(), SAVE_FAILED, model)
            }
        } catch (ex: RestClientException) {
            LOGGER.error("Listing $id not saved (${section.name})", ex)
            return render(listing, section, form, emptyMap(), SAVE_FAILED, model)
        }
        return back(id, SAVED_PREFIX + slug, redirect)
    }

    /** Changed since the form was shown: saving again, now based on the version just loaded, is the agent's call. */
    private fun conflict(
        id: Long,
        agent: Agent,
        section: ListingSection,
        form: ListingSectionForm,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val listing = (access.load(id, agent) as? ListingAccess.Found)?.listing?.takeIf { ListingRules.editable(it) }
            ?: return back(id, ListingController.CHANGED, redirect)
        return render(listing, section, form.copy(version = listing.version), emptyMap(), SAVE_CONFLICT, model)
    }

    /** The listing's city never changes: only a neighbourhood of it. */
    private fun neighborhoodErrors(
        listing: Listing,
        section: ListingSection,
        form: ListingSectionForm,
    ): Map<String, String> {
        if (section != ListingSection.LOCATION) return emptyMap()
        val id = form.neighborhoodId?.trim()?.toLongOrNull() ?: return emptyMap()
        val errors = try {
            sector.errors(listing.cityId, listOf(id))
        } catch (ex: RestClientException) {
            LOGGER.warn("Neighbourhood $id of listing ${listing.id} not checked: ${ex.message}")
            emptyMap() // ndopify-server checks it too
        }
        return if (errors.isEmpty()) emptyMap() else mapOf("neighborhoodId" to NEIGHBORHOOD_INVALID)
    }

    private fun render(
        listing: Listing,
        section: ListingSection,
        answers: ListingSectionForm,
        errors: Map<String, String>,
        error: String?,
        model: Model,
    ): String {
        val formatter = formatter()
        val mandate = lookup("mandate ${listing.mandateId} of listing ${listing.id}") { mandateClient.get(listing.mandateId) }
        val ids = listOfNotNull(listing.cityId, listing.neighborhoodId, answers.neighborhoodId?.trim()?.toLongOrNull()) +
            mandate?.neighborhoodIds.orEmpty()
        val locations = lookup("location names of listing ${listing.id}") { sector.locations(ids) } ?: emptyMap()

        model.addAttribute("section", ListingViews.section(listing, section, locations, formatter))
        model.addAttribute("answers", answers)
        model.addAttribute("errors", errors)
        model.addAttribute("error", error)
        model.addAttribute("ownerHints", ListingViews.ownerHints(answers, mandate, locations, formatter))
        model.addAttribute("currency", formatter.currency())
        when (section) {
            ListingSection.LOCATION -> {
                model.addAttribute("city", locations[listing.cityId]?.name)
                model.addAttribute("neighborhoods", neighborhoods(listing, locations))
            }
            ListingSection.PROPERTY -> model.addAttribute("types", ListingViews.TYPES)
            ListingSection.AMENITIES -> model.addAttribute("amenityLabels", ListingViews.AMENITY_LABELS)
            ListingSection.TERMS -> {
                val today = formatter.today(clock)
                model.addAttribute("today", today.toString())
                model.addAttribute("maxDate", today.plusDays(ListingRules.MAX_AVAILABILITY_DAYS).toString())
            }
            else -> {}
        }
        return VIEW
    }

    // The city's neighbourhoods, by name; the listing's own one at least when they can't be loaded
    private fun neighborhoods(listing: Listing, locations: Map<Long, Location>): List<Location> {
        val all = lookup("neighbourhoods of city ${listing.cityId}") {
            locationClient.search(
                SearchLocationRequest(
                    parentId = listing.cityId,
                    types = listOf(LocationType.NEIGHBORHOOD),
                    limit = MAX_NEIGHBORHOODS,
                ),
            ).locations
        } ?: listOfNotNull(listing.neighborhoodId?.let { locations[it] })
        return all.sortedBy { it.name }
    }

    private fun unavailable(result: ListingAccess.Result, model: Model): String {
        model.addAttribute(if (result == ListingAccess.Unavailable) ListingController.UNAVAILABLE else "locked", true)
        return VIEW
    }

    private fun back(id: Long, outcome: String, redirect: RedirectAttributes): String {
        redirect.addFlashAttribute(ListingController.OUTCOME, outcome)
        return "redirect:/listings/$id"
    }

    private fun formatter(): TenantFormatter = TenantFormatter(tenantContext.getTenant())

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }

    private fun <T> lookup(what: String, call: () -> T?): T? =
        try {
            call()
        } catch (ex: RestClientException) {
            LOGGER.warn("No $what: ${ex.message}")
            null
        }
}
