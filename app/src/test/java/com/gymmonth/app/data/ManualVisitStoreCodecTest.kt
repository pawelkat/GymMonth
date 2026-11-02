package com.gymmonth.app.data

import com.gymmonth.app.domain.MANUAL_VISIT_ORIGIN_PACKAGE
import com.gymmonth.app.domain.ManualVisit
import com.gymmonth.app.domain.GymExerciseKind
import com.gymmonth.app.domain.SessionGymPlace
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualVisitStoreCodecTest {

    private val t0 = Instant.parse("2026-11-02T17:00:00Z")

    @Test
    fun createDefaultsTo60MinutesAndManualPrefix() {
        val v = ManualVisit.create(start = t0, gymPlaceId = "puregym", gymName = "PureGym")
        assertTrue(v.id.startsWith("manual-"))
        assertEquals(Duration.ofMinutes(60), Duration.between(v.start, v.end))
        val gv = v.toGymVisit()
        assertEquals(GymExerciseKind.STRENGTH_TRAINING, gv.exerciseType)
        assertEquals(MANUAL_VISIT_ORIGIN_PACKAGE, gv.dataOriginPackage)
        assertEquals("puregym", gv.gymPlaceId)
    }

    @Test
    fun addEncodeDecodeDeleteRoundTrip() {
        val a = ManualVisit.create(start = t0, gymPlaceId = "puregym", gymName = "PureGym")
        val b = ManualVisit.create(start = t0.minusSeconds(86_400), duration = Duration.ofMinutes(90))
        var list = GymJsonCodecs.addManual(emptyList(), a)
        list = GymJsonCodecs.addManual(list, b)
        val decoded = GymJsonCodecs.decodeManualVisits(GymJsonCodecs.encodeManualVisits(list))
        assertEquals(listOf(b, a), decoded)
        assertNull(decoded[0].gymName)

        val afterDelete = GymJsonCodecs.deleteManual(decoded, b.id)
        assertEquals(listOf(a), afterDelete)
        assertEquals(emptyList<ManualVisit>(), GymJsonCodecs.deleteManual(afterDelete, a.id))
    }

    @Test
    fun updatePlaceOnlyTouchesTarget() {
        val a = ManualVisit.create(start = t0)
        val b = ManualVisit.create(start = t0.plusSeconds(3600))
        val out = GymJsonCodecs.updateManualPlace(listOf(a, b), a.id, "custom", "Home gym")
        assertEquals("Home gym", out[0].gymName)
        assertNull(out[1].gymName)
    }

    @Test
    fun corruptBlobsDecodeEmpty() {
        assertEquals(emptyList<ManualVisit>(), GymJsonCodecs.decodeManualVisits("not json"))
        assertEquals(emptyMap<String, SessionGymPlace>(), GymJsonCodecs.decodePlaces("{bad"))
    }

    @Test
    fun placeTagsRoundTrip() {
        val map = mapOf("hc-1" to SessionGymPlace("hc-1", "puregym", "PureGym", 42L))
        assertEquals(map, GymJsonCodecs.decodePlaces(GymJsonCodecs.encodePlaces(map)))
    }
}
