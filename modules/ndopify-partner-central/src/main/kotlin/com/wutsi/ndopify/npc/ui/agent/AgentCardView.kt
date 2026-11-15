package com.wutsi.ndopify.npc.ui.agent

/** Another agent as a card: photo or initials, name, WhatsApp, and the profile link back to [back]. */
data class AgentCardView(
    val id: Long,
    val name: String,
    val initials: String,
    val photoUrl: String?,
    val whatsappUrl: String?,
    val profileUrl: String,
)
