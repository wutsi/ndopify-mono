package com.wutsi.ndopify.npc.ui.match

/** The card groups of *Biens correspondants*, in the order the agent acts on them (the prototype's groups). */
enum class MatchGroup(val label: String) {
    REQUESTED("À confirmer"),
    UPCOMING("Visites à venir"),
    INTERESTED("Intéressés"),
    NEW("Nouveaux"),
    WAITING("Envoyés, en attente"),
    EXPIRED("Sans réponse"),
}

/** A group of cards under its visible heading: "À confirmer (1)". */
data class MatchGroupView(val label: String, val matches: List<MatchView>)
