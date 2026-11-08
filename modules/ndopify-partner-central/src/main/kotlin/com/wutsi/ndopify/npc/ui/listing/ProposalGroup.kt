package com.wutsi.ndopify.npc.ui.listing

/** The card groups of a published listing's *Propositions et visites* (the prototype's groups). */
enum class ProposalGroup(val label: String) {
    REQUESTED("À confirmer"),
    UPCOMING("Visites à venir"),
    INTERESTED("Intéressés"),
    WAITING("En attente"),
}

/** A group of proposal cards under its visible heading: "À confirmer (1)". */
data class ProposalGroupView(val label: String, val rows: List<ProposalRow>)
