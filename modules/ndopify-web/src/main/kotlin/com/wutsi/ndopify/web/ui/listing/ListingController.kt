package com.wutsi.ndopify.web.ui.listing

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.client.ndopify.NdListingClient
import com.wutsi.ndopify.web.client.ndopify.NdLocationClient
import com.wutsi.ndopify.web.core.service.TenantFormatter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView
import java.time.Clock

/**
 * A listing's public page, at its `url` (`/<city>/<neighborhood>/listing/<id>/<slug>`, see ndopify-server's
 * `ListingPublisherService`). Any other path to the same id (an old slug, a hand-typed link) is redirected there for
 * good (301), so the page has one address. ndopify-server only returns a PUBLISHED listing without a token: any other
 * is a 404 ([com.wutsi.ndopify.web.ui.layout.HttpClientErrorHandler]).
 *
 * The "Demander une visite" and "Poser une question" calls to action are left out for now. Their popup and drawer,
 * still in the page, are the prototype's: `listing.js` fakes their answers, and nothing here handles their forms.
 *
 * The tenant is the one [com.wutsi.ndopify.web.ui.layout.TenantAdvice] put in the model, read from there rather than
 * taken as a `@ModelAttribute` parameter, which request parameters could bind.
 */
@Controller
class ListingController(
    private val listingClient: NdListingClient,
    private val locationClient: NdLocationClient,
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

        val images = listingClient.images(id)
        val locations = locationClient.search(listOfNotNull(listing.cityId, listing.neighborhoodId))
        val tenant = model.getAttribute("tenant") as Tenant
        val formatter = TenantFormatter(tenant)
        model.addAttribute("page", ListingPages.of(listing, images, locations, tenant, formatter, formatter.today(clock)))
        return VIEW
    }
}
