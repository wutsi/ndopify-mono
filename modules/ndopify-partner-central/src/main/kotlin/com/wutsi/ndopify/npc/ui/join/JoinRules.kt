package com.wutsi.ndopify.npc.ui.join

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import org.springframework.ui.Model

/**
 * Field rules of the "Rejoindre Ndopify" wizard, shared by [JoinController] (to validate what a step posts) and
 * [PendingJoin] (to tell which steps are complete). Each returns the French error message, or null when valid.
 * Messages are the ones of the `rejoindre.js` prototype.
 */
object JoinRules {
    val EXPERIENCE_LABELS: Map<ExperienceLevel, String> = linkedMapOf(
        ExperienceLevel.NOVICE to "Moins d'un an",
        ExperienceLevel.JUNIOR to "1 à 3 ans",
        ExperienceLevel.INTERMEDIATE to "3 à 5 ans",
        ExperienceLevel.SENIOR to "Plus de 5 ans",
    )

    val AGENT_TYPE_LABELS: Map<AgentType, String> = linkedMapOf(
        AgentType.INDEPENDANT to "Indépendant",
        AgentType.REAL_ESTATE_AGENT to "Rattaché à une agence immobilière",
    )

    /** The radios' labels of the experience and agent type, shared by the join wizard and the profile page. */
    fun addLabels(model: Model) {
        model.addAttribute("experienceLabels", MandateLabels.byName(EXPERIENCE_LABELS))
        model.addAttribute("agentTypeLabels", MandateLabels.byName(AGENT_TYPE_LABELS))
    }

    const val MAX_NEIGHBORHOODS = 3

    private val NAME = Regex("^\\p{L}[\\p{L}' .-]*$")
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    // E.164, as produced by intl-tel-input on the page.
    private val PHONE = Regex("^\\+[1-9]\\d{6,14}$")

    // MTN Mobile Money and Orange Money accounts are Cameroonian.
    private val MOBILE_MONEY = Regex("^\\+237\\d{9}$")

    fun firstName(value: String?): String? = name(value, "Saisissez votre prénom.")

    fun lastName(value: String?): String? = name(value, "Saisissez votre nom.")

    fun email(value: String?): String? = when {
        value.isNullOrBlank() -> "Saisissez votre adresse email."
        !EMAIL.matches(value) -> "Cette adresse email semble incomplète. Exemple : nom@exemple.com"
        else -> null
    }

    fun whatsappNumber(value: String?): String? = when {
        value.isNullOrBlank() -> "Saisissez votre numéro WhatsApp."
        !PHONE.matches(value) -> "Ce numéro n'est pas un numéro mobile valide. Exemple : 6 77 12 34 56"
        else -> null
    }

    fun experienceLevel(value: ExperienceLevel?): String? =
        if (value == null || value == ExperienceLevel.UNKNOWN) "Indiquez depuis combien de temps vous exercez." else null

    fun agentType(value: AgentType?): String? =
        if (value == null || value == AgentType.UNKNOWN) {
            "Indiquez si vous êtes indépendant ou rattaché à une agence."
        } else {
            null
        }

    fun cityId(value: Long?): String? =
        if (value == null || value <= 0) "Choisissez votre ville principale d'intervention." else null

    fun neighborhoodIds(value: List<Long>): String? = when {
        value.isEmpty() -> "Choisissez au moins un quartier."
        value.size > MAX_NEIGHBORHOODS -> "Choisissez au plus $MAX_NEIGHBORHOODS quartiers."
        else -> null
    }

    fun mobileMoneyNumber(value: String?): String? = when {
        value.isNullOrBlank() -> "Saisissez votre numéro Mobile Money."
        !MOBILE_MONEY.matches(value) -> "Saisissez un numéro Mobile Money camerounais. Exemple : 6 77 12 34 56"
        else -> null
    }

    /** Removes the spaces, dots, dashes and parentheses of a phone number, and reads a leading `00` as `+`. */
    fun normalizePhone(value: String?): String {
        val compact = value.orEmpty().replace(Regex("[\\s.()-]"), "")
        return if (compact.startsWith("00")) "+" + compact.substring(2) else compact
    }

    private fun name(value: String?, emptyMessage: String): String? = when {
        value.isNullOrBlank() -> emptyMessage
        !NAME.matches(value) -> "Utilisez uniquement des lettres, des espaces, des tirets ou des apostrophes."
        else -> null
    }
}
