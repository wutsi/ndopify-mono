package com.wutsi.ndopify.npc.ui.home

/**
 * An offer, as its card on the Espace agent shows it. Anti-bypass: nothing about the customer (no id, no comments),
 * and the neighbourhoods are the only location, until the agent accepts.
 */
data class AssignmentView(
    val id: Long,
    val title: String,
    val sectorTerm: String,
    val sector: String?,
    val priceTerm: String,
    val price: String,
    val movingTerm: String,
    val moving: String?,
    /** Epoch ms, for home.js' countdown. */
    val expiresAt: Long,
    /** The time left when the page was rendered: what the page shows without JS. */
    val remaining: String,
    /** The "Voir les détails" rows: term → value. */
    val details: List<Pair<String, String>>,
)
