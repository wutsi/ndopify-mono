package com.wutsi.ndopify.npc.ui.findproperty

class FindPropertyController {
    companion object {
        const val VIEW = "find-property"
        const val STEP_NEED = "need"
        const val STEP_AREA = "area"
        const val STEP_PROPERTY = "property"
        const val STEP_AMENITIES = "amenities"
        const val STEP_BUDGET = "budget"
        const val STEP_CONTACT = "contact"
        const val STEP_REVIEW = "review"
        const val STEP_CONFIRMATION = "confirmation"
        const val RETURN_TO_REVIEW = "review"

        val STEPS = listOf(STEP_NEED, STEP_AREA, STEP_PROPERTY, STEP_AMENITIES, STEP_BUDGET, STEP_CONTACT, STEP_REVIEW)
    }
}
