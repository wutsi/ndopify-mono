package com.wutsi.ndopify.npc.ui.profile

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdPartyClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.join.JoinRules
import com.wutsi.ndopify.npc.ui.kyc.KycRules
import com.wutsi.ndopify.npc.ui.location.SectorValidator
import com.wutsi.ndopify.web.core.service.TenantContext
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * The agent's profile pages, open whatever its KYC status (so it can be completed while the KYC is reviewed):
 * - `profile/edit` (`GET /profile`, `POST /profile`): WhatsApp number, experience, agent type, area and biography.
 *   The name isn't editable: it's the one of the verified identity document. The e-mail is shown, not editable
 *   (yet): changing it must check that the agent owns both the old and the new address.
 * - `profile/photo` (`GET /profile/photo`, `POST /profile/photo`): the profile photo, shown to customers.
 * Both go back to the Espace agent with a success message.
 */
@Controller
@RequestMapping("/profile")
class ProfileController(
    private val currentAgent: CurrentAgent,
    private val agentClient: NdAgentClient,
    private val partyClient: NdPartyClient,
    locationClient: NdLocationClient,
    tenantContext: TenantContext,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ProfileController::class.java)
        private val TIKA = Tika()

        const val VIEW_EDIT = "profile/edit"
        const val VIEW_PHOTO = "profile/photo"
        const val ERROR_TOO_LARGE = "too-large"

        private const val SAVE_FAILED = "Enregistrement impossible pour le moment. Réessayez dans quelques instants."
        private const val SAVE_CONFLICT =
            "Votre profil a été modifié entre-temps. Vos réponses sont conservées : vérifiez-les, puis enregistrez à nouveau."
    }

    private val sector = SectorValidator(locationClient, tenantContext)

    @GetMapping
    fun edit(model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        return edit(agent, ProfileForm.of(agent), emptyMap(), null, model)
    }

    @PostMapping
    fun save(@ModelAttribute form: ProfileForm, model: Model, redirect: RedirectAttributes): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val answers = form.copy(
            whatsappNumber = JoinRules.normalizePhone(form.whatsappNumber),
            neighborhoodIds = form.neighborhoodIds.distinct(),
            biography = form.biography.trim(),
        )
        val errors = errors(
            "whatsappNumber" to JoinRules.whatsappNumber(answers.whatsappNumber),
            "experienceLevel" to JoinRules.experienceLevel(answers.experienceLevel),
            "agentType" to JoinRules.agentType(answers.agentType),
            "cityId" to JoinRules.cityId(answers.cityId),
            "neighborhoodIds" to JoinRules.neighborhoodIds(answers.neighborhoodIds),
            "biography" to ProfileRules.biography(answers.biography),
        ).ifEmpty { sector.errors(answers.cityId!!, answers.neighborhoodIds) }
        if (errors.isNotEmpty()) return edit(agent, answers, errors, null, model)

        try {
            agentClient.update(
                agent.id,
                UpdateAgentRequest(
                    whatsappNumber = answers.whatsappNumber,
                    agentType = answers.agentType,
                    experienceLevel = answers.experienceLevel,
                    cityId = answers.cityId,
                    neighborhoodIds = answers.neighborhoodIds,
                    // "" (not null) clears it.
                    biography = answers.biography,
                    version = answers.version,
                ),
            )
        } catch (ex: HttpClientErrorException) {
            if (errorCode(ex) != ErrorCode.AGENT_VERSION_CONFLICT) {
                LOGGER.error("Server error: ${ex.message}", ex)
                return edit(agent, answers, emptyMap(), SAVE_FAILED, model)
            }
            // Changed since the form was shown (ex: in another tab): saving again, based on the agent just loaded, is
            // the agent's call
            LOGGER.warn("Agent ${agent.id} not saved: changed since version ${answers.version}")
            return edit(agent, answers.copy(version = agent.version), emptyMap(), SAVE_CONFLICT, model)
        } catch (ex: RestClientException) {
            LOGGER.error("Server error: ${ex.message}", ex)
            return edit(agent, answers, emptyMap(), SAVE_FAILED, model)
        }

        redirect.addFlashAttribute(ProfileRules.SAVED, ProfileRules.SAVED_PROFILE)
        return ProfileRules.HOME
    }

    @GetMapping("/photo")
    fun photo(@RequestParam(name = "error", required = false) uploadError: String?, model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        val error = if (uploadError == ERROR_TOO_LARGE) "Cette photo dépasse la taille maximale de 5 Mo." else null
        return photo(agent, error, model)
    }

    @PostMapping("/photo")
    fun upload(
        @RequestParam(required = false) photo: MultipartFile?,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        if (photo == null || photo.isEmpty) {
            // "Enregistrer" without a new photo keeps the current one.
            if (agent.party.photoUrl.isNullOrBlank()) return photo(agent, "Ajoutez une photo pour continuer.", model)
            return ProfileRules.HOME
        }

        val content = photo.bytes
        // The browser's content type is a claim; the bytes are the truth.
        val mimeType = TIKA.detect(content)
        KycRules.photo(photo.size, mimeType)?.let { error -> return photo(agent, error, model) }
        try {
            partyClient.uploadPhoto(agent.party.id, content, mimeType)
        } catch (ex: RestClientException) {
            LOGGER.error("Server error: ${ex.message}", ex)
            return photo(agent, SAVE_FAILED, model)
        }

        redirect.addFlashAttribute(ProfileRules.SAVED, ProfileRules.SAVED_PHOTO)
        return ProfileRules.HOME
    }

    private fun edit(
        agent: Agent,
        answers: ProfileForm,
        errors: Map<String, String>,
        error: String?,
        model: Model,
    ): String {
        ProfileRules.header(agent, model)
        model.addAttribute("fullName", ProfileRules.fullName(agent))
        model.addAttribute("email", agent.party.email.ifBlank { null })
        model.addAttribute("photoUrl", agent.party.photoUrl?.ifBlank { null })
        model.addAttribute("answers", answers)
        model.addAttribute("errors", errors)
        model.addAttribute("error", error)
        model.addAttribute("maxBiography", ProfileRules.MAX_BIOGRAPHY)
        JoinRules.addLabels(model)

        // Names of the chosen city and neighbourhoods: the selects' initial options. Capped: a form shown again with
        // its errors may carry whatever ids were posted.
        val neighborhoodIds = answers.neighborhoodIds.take(JoinRules.MAX_NEIGHBORHOODS)
        val locations = sector.locations(listOfNotNull(answers.cityId) + neighborhoodIds)
        model.addAttribute("city", answers.cityId?.let { locations[it] })
        model.addAttribute("neighborhoods", neighborhoodIds.mapNotNull { locations[it] })
        return VIEW_EDIT
    }

    private fun photo(agent: Agent, error: String?, model: Model): String {
        ProfileRules.header(agent, model)
        model.addAttribute("photoUrl", agent.party.photoUrl?.ifBlank { null })
        model.addAttribute("error", error)
        return VIEW_PHOTO
    }

    private fun errors(vararg errors: Pair<String, String?>): Map<String, String> =
        errors.mapNotNull { (field, message) -> message?.let { field to it } }.toMap()

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }
}
