package com.wutsi.ndopify.npc.ui.agent

import com.wutsi.ndopify.listing.dto.ListingSummary

/** One page of the agent's PUBLISHED listings, and the whole set's page count. */
data class AgentListingsPage(
    val listings: List<ListingSummary>,
    val page: Int,
    val pages: Int,
)
