package com.gymmonth.app.domain

/**
 * A gym the user can pick for manual visits or session tagging.
 * Bundled defaults come from `assets/default_gyms.json` (offline, like SwimWeek pools).
 */
data class GymPlace(
    val id: String,
    val name: String,
    val address: String? = null,
    val selectable: Boolean = true,
)

/** Local mapping of a session id → chosen gym (mirrors SwimWeek SessionPlace). */
data class SessionGymPlace(
    val sessionId: String,
    val gymPlaceId: String,
    val gymName: String,
    val updatedAtEpochMs: Long = 0L,
)

/** Id used for free-text gyms that are not in the default catalog. */
const val CUSTOM_GYM_PLACE_ID = "custom"
