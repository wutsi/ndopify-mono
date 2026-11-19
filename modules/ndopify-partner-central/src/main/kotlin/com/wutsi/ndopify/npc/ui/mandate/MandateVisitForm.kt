package com.wutsi.ndopify.npc.ui.mandate

/** The visit page's answers: the accepted property, a day and a time from the lists, and the visit's type. */
data class MandateVisitForm(
    val matchId: String? = null,
    val date: String? = null,
    val time: String? = null,
    val type: String? = null,
)
