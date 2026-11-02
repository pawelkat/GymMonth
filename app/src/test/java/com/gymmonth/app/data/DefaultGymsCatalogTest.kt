package com.gymmonth.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGymsCatalogTest {

    private fun assetJson(): String {
        val candidates = listOf(
            File("src/main/assets/default_gyms.json"),
            File("app/src/main/assets/default_gyms.json"),
        )
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun bundledCatalogContainsPureGym() {
        val gyms = GymJsonCodecs.parseDefaultGyms(assetJson())
        val pure = gyms.firstOrNull { it.id == "puregym" }
        assertNotNull(pure)
        assertEquals("PureGym", pure!!.name)
        assertEquals("Grabenwisstrasse 5, 8604 Volketswil", pure.address)
        assertTrue(pure.selectable)
        assertEquals("puregym", gyms.first().id)
    }

    @Test
    fun bundledCatalogHasNoMembershipOrPaymentData() {
        val raw = assetJson().lowercase()
        listOf("iban", "6016p16434", "invoice", "chf", "member number").forEach {
            assertFalse("asset must not contain '$it'", raw.contains(it))
        }
    }

    @Test
    fun skipsEntriesWithoutIdOrName() {
        val json = """{"gyms":[{"id":"","name":"X"},{"id":"a","name":"A","selectable":false},{"id":"b"}]}"""
        val gyms = GymJsonCodecs.parseDefaultGyms(json)
        assertEquals(1, gyms.size)
        assertEquals("a", gyms[0].id)
        assertFalse(gyms[0].selectable)
    }
}
