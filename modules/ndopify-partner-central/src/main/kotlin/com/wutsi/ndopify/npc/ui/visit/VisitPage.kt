package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.dto.VisitType

/**
 * The visit page (`templates/visit.html`), as `VisitViews.of` words it: the visit, its customer and listing (both
 * optional), and every time already in the tenant's zone. It has no field for the code: the agent never gets it.
 */
data class VisitPage(
    val id: Long,
    val panel: VisitRules.Panel,
    val tag: VisitRules.Tag,
    val type: VisitType,
    val typeLabel: String,
    val customerName: String,
    val customerFirstName: String,
    /** E.164 for `tel:`. */
    val phone: String?,
    /** Digits for `https://wa.me/`. */
    val phoneDigits: String?,
    val mandateId: Long,
    val listingId: Long,
    val listingTitle: String?,
    val listingMeta: String?,
    val listingUrl: String,
    val address: String?,
    val mapUrl: String?,
    val requestedAt: String,
    val scheduledAt: String?,
    val windowFrom: String?,
    val expiresAt: String?,
    val expiresIn: String?,
    val note: String?,
    val checkedInAt: String?,
    val checkOutFrom: String?,
    val checkOutReady: Boolean,
    val checkedOutAt: String?,
    val duration: String?,
    val startDistance: String?,
    val checkInFlagged: Boolean,
    val distance: String?,
    val distanceKnown: Boolean,
    val agentComment: String?,
    val interest: VisitInterest?,
    val feedbackTag: String?,
    val feedbackTone: String?,
    val feedbackReason: String?,
    val feedbackComment: String?,
    val cancelledTitle: String?,
    val cancelledText: String?,
    val cancellationReason: String?,
    val plannedAt: String?,
    val expiredText: String?,
    val noShowTitle: String?,
    val noShowText: String?,
    val canCancel: Boolean,
    val canCheckIn: Boolean,
    val canNoShow: Boolean,
    val days: List<VisitDay>,
    val times: List<Pair<String, String>>,
    val selectedDay: String,
    val selectedTime: String,
    val videoPossible: Boolean,
)
