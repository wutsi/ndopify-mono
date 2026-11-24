package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.mandate.dto.TitleDocumentType

/**
 * Step "property". The area and a building sale's two counts are text: they may come with thousands separators
 * ("1 200"), and a count's "Indifférent" is its own checkbox.
 */
data class PropertyForm(
    val propertyType: String? = null,
    val rooms: String? = null,
    val furnitureType: String? = null,
    val parkings: String? = null,
    val levels: String? = null,
    val units: String? = null,
    val levelsAny: Boolean = false,
    val unitsAny: Boolean = false,
    val area: String? = null,
    val titleDocuments: List<TitleDocumentType> = emptyList(),
)
