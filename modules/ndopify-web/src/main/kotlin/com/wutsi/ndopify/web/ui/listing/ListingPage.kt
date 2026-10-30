package com.wutsi.ndopify.web.ui.listing

/**
 * The public page of a listing, as [ListingPages] builds it: every text already formatted, so `listing.html` only
 * prints it. Lists are empty, and texts null, when the listing doesn't say: the template leaves them out.
 */
data class ListingPage(
    val id: Long,
    val title: String,
    val metaDescription: String,
    val canonicalUrl: String?,
    val typeBadge: String,
    val place: String,
    val landmark: String?,
    val verifiedAt: String?,
    val photos: List<Photo>,
    val keyFacts: List<Fact>,
    val description: List<String>,
    val specs: List<Pair<String, String>>,
    val amenities: List<AmenityGroup>,
    val price: String,
    val terms: List<Pair<String, String>>,
    val asideTerms: List<String>,
    val schemaType: String,
    val area: Int?,
    val rooms: Int?,
    val bathrooms: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val city: String?,
    val countryCode: String,
    val visitDays: List<VisitDay>,
    val visitTimes: List<Pair<String, String>>,
)

data class Photo(val src: String, val alt: String)

/** A key fact ("2 chambres"): [icon] is the sprite's symbol without `i-` ("bed"); [count] is null for "Meublé". */
data class Fact(val icon: String, val count: String?, val label: String)

data class AmenityGroup(val title: String, val items: List<String>)

/** A day of the visit popup: [value] is the ISO date posted, the others its label ("sam." "31" "oct."). */
data class VisitDay(val value: String, val dayName: String, val day: String, val month: String)
