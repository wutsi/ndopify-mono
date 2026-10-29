package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.mandate.dto.Mandate

private const val WATER = "Accès à l'eau"
private const val ELECTRICITY = "Accès à l'électricité"
private const val ROAD = "Accès routier"
private const val SECURITY = "Sécurité"

/**
 * The boolean criteria of a mandate or a listing, in the find-property form's order. [key] is the field's name in the
 * DTOs and the forms' checkbox value, [label] the mandates' wording, [group] the "Voir les détails" row it goes in
 * (prototype's AMENITY_GROUPS).
 */
enum class Amenity(
    val key: String,
    val label: String,
    val group: String,
    private val ofMandate: (Mandate) -> Boolean,
    private val ofListing: (Listing) -> Boolean,
) {
    WATER_METER("waterMeter", "Compteur d'eau", WATER, Mandate::waterMeter, Listing::waterMeter),
    DEDICATED_WATER_METER(
        "dedicatedWaterMeter",
        "Compteur d'eau individuel",
        WATER,
        Mandate::dedicatedWaterMeter,
        Listing::dedicatedWaterMeter,
    ),
    WATER_TANK("waterTank", "Citerne", WATER, Mandate::waterTank, Listing::waterTank),
    WATER_WELL("waterWell", "Forage", WATER, Mandate::waterWell, Listing::waterWell),
    ELECTRICITY_METER(
        "electricityMeter",
        "Compteur électrique",
        ELECTRICITY,
        Mandate::electricityMeter,
        Listing::electricityMeter,
    ),
    DEDICATED_ELECTRICITY_METER(
        "dedicatedElectricityMeter",
        "Compteur électrique individuel",
        ELECTRICITY,
        Mandate::dedicatedElectricityMeter,
        Listing::dedicatedElectricityMeter,
    ),
    GENERATOR_SET("generatorSet", "Groupe électrogène", ELECTRICITY, Mandate::generatorSet, Listing::generatorSet),
    SOLAR_PANEL("solarPanel", "Panneaux solaires", ELECTRICITY, Mandate::solarPanel, Listing::solarPanel),
    ROAD_PAVED("roadPaved", "Route bitumée", ROAD, Mandate::roadPaved, Listing::roadPaved),
    TRACK_ACCESSIBLE_BY_CAR(
        "trackAccessibleByCar",
        "Piste accessible en voiture",
        ROAD,
        Mandate::trackAccessibleByCar,
        Listing::trackAccessibleByCar,
    ),
    TRACK_INACCESSIBLE_BY_CAR(
        "trackInaccessibleByCar",
        "Piste accessible à pied seulement",
        ROAD,
        Mandate::trackInaccessibleByCar,
        Listing::trackInaccessibleByCar,
    ),
    SECURED_FENCED("securedFenced", "Clôture sécurisée", SECURITY, Mandate::securedFenced, Listing::securedFenced),
    SECURITY_GUARD("securityGuard", "Gardien de sécurité", SECURITY, Mandate::securityGuard, Listing::securityGuard),
    VIDEO_SURVEILLANCE(
        "videoSurveillance",
        "Vidéosurveillance",
        SECURITY,
        Mandate::videoSurveillance,
        Listing::videoSurveillance,
    ),
    ;

    companion object {
        /** Every key, in form order. */
        val KEYS: List<String> = entries.map { it.key }

        /** Label by key, in form order. */
        val LABELS: Map<String, String> = entries.associate { it.key to it.label }

        /** The mandate's checked amenities, in form order. */
        fun of(mandate: Mandate): Set<Amenity> = entries.filterTo(linkedSetOf()) { it.ofMandate(mandate) }

        /** The listing's checked amenities, in form order. */
        fun of(listing: Listing): Set<Amenity> = entries.filterTo(linkedSetOf()) { it.ofListing(listing) }
    }
}
