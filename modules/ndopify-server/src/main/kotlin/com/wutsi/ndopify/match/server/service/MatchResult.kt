package com.wutsi.ndopify.match.server.service

/** Why [Matcher] ruled a (search mandate, listing) pair out. One rule wins: the first one the chain hits. */
enum class MatchRuleOut {
    OWN_PROPERTY,
    LISTING_TYPE,
    CITY,
    CATEGORY,
    PROPERTY_TYPE,
    SIBLING_ROOMS,
    CURRENCY,
    PERIODICITY,
    OVER_BUDGET,
}

/** The outcome of [Matcher.evaluate]: a [score][Scored], or the [rule][RuledOut] that rejected the pair. */
sealed interface MatchResult {
    data class Scored(val score: MatchScore) : MatchResult

    /** [detail] holds the deciding values as `key=value` pairs, for logs only. */
    data class RuledOut(val rule: MatchRuleOut, val detail: String) : MatchResult
}
