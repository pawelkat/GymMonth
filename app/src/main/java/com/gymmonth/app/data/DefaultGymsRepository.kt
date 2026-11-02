package com.gymmonth.app.data

import android.content.Context
import com.gymmonth.app.domain.GymPlace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads bundled default gyms from `assets/default_gyms.json` (offline; no INTERNET).
 */
@Singleton
class DefaultGymsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cached: List<GymPlace>? = null

    fun all(): List<GymPlace> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load().also { cached = it }
        }
    }

    fun selectableGyms(): List<GymPlace> = all().filter { it.selectable }

    fun findGym(id: String): GymPlace? = all().firstOrNull { it.id == id }

    private fun load(): List<GymPlace> = runCatching {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        GymJsonCodecs.parseDefaultGyms(json)
    }.getOrDefault(emptyList())

    companion object {
        const val ASSET_NAME = "default_gyms.json"
    }
}
