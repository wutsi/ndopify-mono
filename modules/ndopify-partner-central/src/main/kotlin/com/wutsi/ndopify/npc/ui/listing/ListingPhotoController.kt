package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * The photos of a listing's checklist (`listing/photos.html`): at least [ListingRules.MIN_PHOTOS], none refused, for
 * the section to count; [ListingRules.MAX_PHOTOS] at most. Each upload is sent straight to the listing (the listing is
 * the state, nothing is kept in the session), PENDING_VERIFICATION; a photo is deleted (or replaced) before the
 * listing is sent, never after.
 *
 * Without JS the form posts one or several files (`photo`): each is sniffed with Tika (the browser's content type is a
 * claim, the bytes are the truth) and capped at 5 Mo, and over the multipart limit `KycUploadErrorHandler` comes back
 * here with `?error=too-large`; `listing.js` shrinks and sends them one by one. The outcome of an upload or a
 * deletion that failed is the flash attribute [PHOTO_ERROR].
 */
@Controller
@RequestMapping("/listings/{id}/photos")
class ListingPhotoController(
    private val currentAgent: CurrentAgent,
    private val listingClient: NdListingClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingPhotoController::class.java)
        private val TIKA = Tika()

        const val VIEW = "listing/photos"
        const val PHOTO_ERROR = "photoError"
        const val ERROR_TOO_LARGE = "too-large"

        private const val TOO_LARGE = "Ces photos sont trop lourdes : envoyez-les une par une, 5 Mo au plus chacune."
        private const val NO_FILE = "Choisissez une photo à envoyer."
        private const val TOO_MANY = "${ListingRules.MAX_PHOTOS} photos au plus."
        private const val UPLOAD_FAILED = "Envoi impossible pour le moment. Réessayez dans quelques instants."
        private const val DELETE_FAILED = "Suppression impossible pour le moment. Réessayez dans quelques instants."
        private const val PHOTOS_UNAVAILABLE = "Photos indisponibles pour le moment. Réessayez dans quelques instants."
    }

    private val sector = SectorValidator(locationClient, tenantContext)
    private val access = ListingAccess(listingClient)

    /** [id] is a String so that a hand-edited value is the locked view instead of failing the page. */
    @GetMapping
    fun show(
        @PathVariable id: String,
        @RequestParam(name = "error", required = false) uploadError: String?,
        model: Model,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)

        val result = id.toLongOrNull()?.let { access.load(it, agent) } ?: ListingAccess.Locked
        val listing = (result as? ListingAccess.Found)?.listing
            ?: return unavailable(result, model)
        val images = lookup("images of listing ${listing.id}") { listingClient.images(listing.id) }
        val error = model.getAttribute(PHOTO_ERROR) as? String
            ?: if (uploadError == ERROR_TOO_LARGE) TOO_LARGE else null

        val formatter = TenantFormatter(tenantContext.getTenant())
        val locations = lookup("location names of listing ${listing.id}") {
            sector.locations(listOfNotNull(listing.cityId, listing.neighborhoodId))
        } ?: emptyMap()
        model.addAttribute("section", ListingViews.section(listing, ListingSection.PHOTOS, locations, formatter))
        model.addAttribute("photos", ListingViews.photos(images.orEmpty(), ListingRules.editable(listing)))
        model.addAttribute(PHOTO_ERROR, error ?: if (images == null) PHOTOS_UNAVAILABLE else null)
        return VIEW
    }

    /** Uploads what fits, in order, and reports the first problem: a file that isn't a photo, too large, or too many. */
    @PostMapping
    fun upload(
        @PathVariable("id") value: String,
        @RequestParam(name = "photo", required = false) files: List<MultipartFile>?,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val id = value.toLongOrNull() ?: return ListingController.HOME
        val listing = draft(id, agent, redirect) ?: return "redirect:/listings/$id"

        val photos = files.orEmpty().filterNot { it.isEmpty }
        if (photos.isEmpty()) return back(id, NO_FILE, redirect)
        val images = lookup("images of listing $id") { listingClient.images(id) }
            ?: return back(id, UPLOAD_FAILED, redirect)

        val room = ListingRules.MAX_PHOTOS - images.size
        var error: String? = if (photos.size > room) TOO_MANY else null
        for (file in photos.take(room.coerceAtLeast(0))) {
            val name = file.originalFilename?.ifBlank { null } ?: "Cette photo"
            val content = file.bytes
            val mimeType = TIKA.detect(content)
            val problem = when {
                mimeType !in ListingRules.PHOTO_MIME_TYPES -> "$name n'est pas une photo JPEG, PNG ou WEBP."
                file.size > ListingRules.MAX_PHOTO_BYTES -> "$name dépasse 5 Mo."
                else -> null
            }
            if (problem != null) {
                error = error ?: problem
                continue
            }
            try {
                listingClient.upload(listing.id, content, mimeType, name)
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Photo $name of listing $id not uploaded: ${ex.statusCode}")
                if (ex.statusCode.value() == 409) return changed(id, redirect)
                error = error ?: "$name n'est pas une photo JPEG, PNG ou WEBP."
            } catch (ex: RestClientException) {
                LOGGER.error("Photo $name of listing $id not uploaded", ex)
                return back(id, error ?: UPLOAD_FAILED, redirect)
            }
        }
        return back(id, error, redirect)
    }

    /** ndopify-server doesn't check which listing an image is of: only one of this listing's is deleted. */
    @PostMapping("/{imageId}/delete")
    fun delete(
        @PathVariable("id") value: String,
        @PathVariable("imageId") image: String,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val id = value.toLongOrNull() ?: return ListingController.HOME
        draft(id, agent, redirect) ?: return "redirect:/listings/$id"

        val imageId = image.toLongOrNull() ?: return back(id, null, redirect)
        val images: List<ListingImage> = lookup("images of listing $id") { listingClient.images(id) }
            ?: return back(id, DELETE_FAILED, redirect)
        // Not (or no longer) one of its photos: already deleted, ex: from another tab
        if (images.none { it.id == imageId }) return back(id, null, redirect)

        try {
            listingClient.deleteImage(imageId)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("Photo $imageId of listing $id not deleted: ${ex.statusCode}")
            if (ex.statusCode.value() == 409) return changed(id, redirect)
        } catch (ex: RestClientException) {
            LOGGER.error("Photo $imageId of listing $id not deleted", ex)
            return back(id, DELETE_FAILED, redirect)
        }
        return back(id, null, redirect)
    }

    /** The agent's DRAFT listing; null when it's not (or no longer) one, with the checklist's outcome to show. */
    private fun draft(id: Long, agent: Agent, redirect: RedirectAttributes): Listing? {
        val outcome = when (val result = access.load(id, agent)) {
            is ListingAccess.Found -> if (ListingRules.editable(result.listing)) return result.listing else ListingController.CHANGED
            ListingAccess.Locked -> ListingController.CHANGED
            ListingAccess.Unavailable -> ListingController.FAILED
        }
        redirect.addFlashAttribute(ListingController.OUTCOME, outcome)
        return null
    }

    private fun changed(id: Long, redirect: RedirectAttributes): String {
        redirect.addFlashAttribute(ListingController.OUTCOME, ListingController.CHANGED)
        return "redirect:/listings/$id"
    }

    private fun back(id: Long, error: String?, redirect: RedirectAttributes): String {
        error?.let { redirect.addFlashAttribute(PHOTO_ERROR, it) }
        return "redirect:/listings/$id/photos"
    }

    private fun unavailable(result: ListingAccess.Result, model: Model): String {
        model.addAttribute(if (result == ListingAccess.Unavailable) ListingController.UNAVAILABLE else "locked", true)
        return VIEW
    }

    private fun <T> lookup(what: String, call: () -> T?): T? =
        try {
            call()
        } catch (ex: RestClientException) {
            LOGGER.warn("No $what: ${ex.message}")
            null
        }
}
