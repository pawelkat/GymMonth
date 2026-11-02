package com.gymmonth.app.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GymMonthSettingsNoteTest {
    private val values = GymMonthSettingsNote.Values(
        monthlyTargetVisits = 12,
        visitCountingMode = VisitCountingMode.AT_MOST_ONE_PER_DAY,
        gymReminderEnabled = false,
        vaultWriteEnabled = true,
        vaultAutoLogSynced = true,
        vaultRoot = "/sdcard/DriveSyncFiles/Vault",
        defaultGymId = "puregym",
        defaultGymName = "PureGym",
        gymsCatalogPath = "app_settings/GymMonth/default_gyms.json",
    )

    @Test
    fun renderHasFrontmatterZurichTimestampAndTable() {
        val md = GymMonthSettingsNote.render(values, Instant.parse("2026-11-02T12:10:00Z"))
        assertTrue(md.startsWith("---\ntitle: GymMonth settings\nsource: GymMonth\nupdated: 2026-11-02T13:10:00+01:00\n"))
        assertTrue(md.contains("| monthlyTargetVisits | 12 |"))
        assertTrue(md.contains("| visitCountingMode | AT_MOST_ONE_PER_DAY |"))
        assertTrue(md.contains("| dailyHeading | ## Workouts |"))
        assertTrue(md.contains("| vaultRoot | /sdcard/DriveSyncFiles/Vault |"))
        assertTrue(md.contains("| defaultGymName | PureGym |"))
        assertTrue(md.contains("| gymsCatalog | app_settings/GymMonth/default_gyms.json |"))
        assertFalse(md.contains("token", ignoreCase = true))
    }

    @Test
    fun vaultPathsUseAppSettingsFolderNotNotes() {
        assertEquals("app_settings/GymMonth/settings.md", GymMonthSettingsNote.RELATIVE_PATH)
        assertEquals(
            "app_settings/GymMonth/default_gyms.json",
            com.gymmonth.app.data.DefaultGymsRepository.VAULT_CATALOG_PATH,
        )
        assertFalse(GymMonthSettingsNote.RELATIVE_PATH.startsWith("Notes/"))
    }

    @Test
    fun parseRoundTrip() {
        val md = GymMonthSettingsNote.render(values, Instant.EPOCH)
        val p = GymMonthSettingsNote.parse(md)
        assertEquals(12, p.monthlyTargetVisits)
        assertEquals(VisitCountingMode.AT_MOST_ONE_PER_DAY, p.visitCountingMode)
        assertEquals(false, p.gymReminderEnabled)
        assertEquals(true, p.vaultWriteEnabled)
        assertEquals(true, p.vaultAutoLogSynced)
        assertEquals("puregym", p.defaultGymId)
        assertFalse(p.clearDefaultGym)
    }

    @Test
    fun parseToleratesHandEdits() {
        val md = """
            | Setting | Value |
            | --- | --- |
            | monthlyTargetVisits | ten |
            | visitCountingMode | per_session |
            | vaultWriteEnabled | yes |
            | defaultGymId |  |
        """.trimIndent()
        val p = GymMonthSettingsNote.parse(md)
        assertEquals(null, p.monthlyTargetVisits)
        assertEquals(VisitCountingMode.PER_SESSION, p.visitCountingMode)
        assertEquals(true, p.vaultWriteEnabled)
        assertTrue(p.clearDefaultGym)
        assertTrue(GymMonthSettingsNote.parse("nothing here").isEmpty)
    }
}
