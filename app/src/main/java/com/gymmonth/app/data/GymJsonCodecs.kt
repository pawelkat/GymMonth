package com.gymmonth.app.data

import com.gymmonth.app.domain.GymPlace
import com.gymmonth.app.domain.ManualVisit
import com.gymmonth.app.domain.SessionGymPlace
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Pure JSON codecs for gym catalog / place tags / manual visits (unit-testable). */
object GymJsonCodecs {

    fun parseDefaultGyms(json: String): List<GymPlace> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("gyms") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id").trim()
                val name = o.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                add(
                    GymPlace(
                        id = id,
                        name = name,
                        address = o.optString("address").takeIf { it.isNotBlank() && it != "null" },
                        selectable = o.optBoolean("selectable", true),
                    ),
                )
            }
        }
    }

    fun encodePlaces(map: Map<String, SessionGymPlace>): String {
        val root = JSONObject()
        map.forEach { (id, p) ->
            root.put(
                id,
                JSONObject()
                    .put("gymPlaceId", p.gymPlaceId)
                    .put("gymName", p.gymName)
                    .put("updatedAtEpochMs", p.updatedAtEpochMs),
            )
        }
        return root.toString()
    }

    fun decodePlaces(blob: String?): Map<String, SessionGymPlace> {
        if (blob.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(blob)
            val out = mutableMapOf<String, SessionGymPlace>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val o = root.getJSONObject(id)
                out[id] = SessionGymPlace(
                    sessionId = id,
                    gymPlaceId = o.getString("gymPlaceId"),
                    gymName = o.getString("gymName"),
                    updatedAtEpochMs = o.optLong("updatedAtEpochMs", 0L),
                )
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encodeManualVisits(visits: List<ManualVisit>): String {
        val arr = JSONArray()
        visits.forEach { v ->
            val o = JSONObject()
                .put("id", v.id)
                .put("startEpochMs", v.start.toEpochMilli())
                .put("endEpochMs", v.end.toEpochMilli())
                .put("createdAtEpochMs", v.createdAtEpochMs)
            v.gymPlaceId?.let { o.put("gymPlaceId", it) }
            v.gymName?.let { o.put("gymName", it) }
            arr.put(o)
        }
        return arr.toString()
    }

    fun decodeManualVisits(blob: String?): List<ManualVisit> {
        if (blob.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(blob)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    runCatching {
                        ManualVisit(
                            id = o.getString("id"),
                            start = Instant.ofEpochMilli(o.getLong("startEpochMs")),
                            end = Instant.ofEpochMilli(o.getLong("endEpochMs")),
                            gymPlaceId = o.optString("gymPlaceId").takeIf { it.isNotBlank() },
                            gymName = o.optString("gymName").takeIf { it.isNotBlank() },
                            createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                        )
                    }.getOrNull()?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Pure list ops so store behavior is testable without DataStore. */
    fun addManual(list: List<ManualVisit>, visit: ManualVisit): List<ManualVisit> =
        (list.filterNot { it.id == visit.id } + visit).sortedBy { it.start }

    fun deleteManual(list: List<ManualVisit>, id: String): List<ManualVisit> =
        list.filterNot { it.id == id }

    fun updateManualPlace(
        list: List<ManualVisit>,
        id: String,
        gymPlaceId: String?,
        gymName: String?,
    ): List<ManualVisit> = list.map {
        if (it.id == id) it.copy(gymPlaceId = gymPlaceId, gymName = gymName) else it
    }
}
