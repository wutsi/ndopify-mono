package com.wutsi.ndopify.web.ui.listing

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.client.ndopify.NdListingClient
import com.wutsi.ndopify.web.client.ndopify.NdLocationClient
import com.wutsi.ndopify.web.client.ndopify.NdMandateClient
import com.wutsi.ndopify.web.core.service.TenantFormatter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.view.RedirectView
import java.time.Clock

/**
 * A listing's public page, at its `url` (`/<city>/<neighborhood>/listing/<id>/<slug>`, see ndopify-server's
 * `ListingPublisherService`). Any other path to the same id (an old slug, a hand-typed link) is redirected there for
 * good (301), so the page has one address. ndopify-server returns a PUBLISHED, SOLD or RENTED listing without a token:
 * any other is a 404 ([com.wutsi.ndopify.web.ui.layout.HttpClientErrorHandler]).
 *
 * Its "Visiter ce logement" form posts the visitor's contact and moving period to [interest], which creates a SEARCH
 * mandate seeded from the listing (`NdMandateClient.create`, `originListingId`) and answers with the same confirmation
 * in every case: the page itself when it has to be shown again (`?interest=done#visit`). The honeypot, the form's
 * validation and the rate cap are the server's business too: a filled honeypot or a 409 `MANDATE_RATE_LIMITED` shows
 * the confirmation, nothing created. The "Poser une question" drawer is still the prototype's: nothing handles it.
 *
 * The tenant is the one [com.wutsi.ndopify.web.ui.layout.TenantAdvice] put in the model, read from there rather than
 * taken as a `@ModelAttribute` parameter, which request parameters could bind.
 */
@Controller
class ListingController(
    private val listingClient: NdListingClient,
    private val locationClient: NdLocationClient,
    private val mandateClient: NdMandateClient,
    private val clock: Clock,
) {
    companion object {
        const val VIEW = "listing"
    }

    @GetMapping(
        "/{city}/listing/{id}",
        "/{city}/listing/{id}/{slug}",
        "/{city}/{neighborhood}/listing/{id}",
        "/{city}/{neighborhood}/listing/{id}/{slug}",
    )
    fun index(@PathVariable id: Long, request: HttpServletRequest, model: Model): Any {
        val listing = listingClient.get(id)
        val url = listing.url
        if (url != null && url != request.requestURI) {
            return RedirectView(url).apply {
                setStatusCode(HttpStatus.MOVED_PERMANENTLY)
                setExposeModelAttributes(false)
            }
        }

        return page(
            id = id,
            model = model,
            form = ListingInterestForm(),
            errors = emptyMap(),
            done = request.getParameter("interest") == "done",
        )
    }

    /**
     * The "Visiter ce logement" form: creates the visitor's SEARCH mandate, seeded from the listing, then shows the
     * confirmation. A filled honeypot or a rate-limited request (409 `MANDATE_RATE_LIMITED`) also shows it, without
     * anything created: the page never says which case it was. An invalid form or a 400 shows the page again, its
     * popup open, with the French messages and the typed values.
     */
    @PostMapping("/listings/{id}/interest")
    fun interest(@PathVariable id: Long, @ModelAttribute form: ListingInterestForm, model: Model): Any {
        if (form.isHoneypotFilled()) {
            return confirmation(id)
        }
        val errors = ListingInterestRules.validate(form)
        if (errors.isNotEmpty()) {
            return page(id = id, model = model, form = form, errors = errors, done = false)
        }

        return try {
            mandateClient.create(form.toRequest(id))
            confirmation(id)
        } catch (ex: HttpClientErrorException.Conflict) {
            confirmation(id) // Rate limited: the same screen, nothing created
        } catch (ex: HttpClientErrorException.BadRequest) {
            page(
                id = id,
                model = model,
                form = form,
                errors = mapOf("global" to "Votre demande n'a pas pu être envoyée. Vérifiez vos informations."),
                done = false,
            )
        }
    }

    private fun page(
        id: Long,
        model: Model,
        form: ListingInterestForm,
        errors: Map<String, String>,
        done: Boolean,
    ): String {
        val listing = listingClient.get(id)
        val images = listingClient.images(id)
        val locations = locationClient.search(listOfNotNull(listing.cityId, listing.neighborhoodId))
        val tenant = model.getAttribute("tenant") as Tenant
        val formatter = TenantFormatter(tenant)
        model.addAttribute("page", ListingPages.of(listing, images, locations, tenant, formatter, formatter.today(clock)))
        model.addAttribute("interestForm", form)
        model.addAttribute("errors", errors)
        model.addAttribute("done", done)
        return VIEW
    }

    private fun confirmation(id: Long): RedirectView =
        RedirectView("${listingClient.get(id).url ?: "/"}?interest=done#visit").apply {
            setStatusCode(HttpStatus.FOUND)
            setExposeModelAttributes(false)
        }
}
