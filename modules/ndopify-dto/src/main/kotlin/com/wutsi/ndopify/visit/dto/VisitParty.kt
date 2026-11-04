package com.wutsi.ndopify.visit.dto

/** Who took the visit's last decision: its customer, its agent or a job (the visit was not scheduled in time...). */
enum class VisitParty {
    CUSTOMER,
    AGENT,
    SYSTEM,
}
