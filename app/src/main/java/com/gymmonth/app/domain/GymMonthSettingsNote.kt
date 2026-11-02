package com.gymmonth.app.domain

import com.vaultwrite.KeyValueNote
import com.vaultwrite.VaultPaths
import java.time.Instant
import java.time.ZoneId

/**
 * `app_settings/GymMonth/settings.md` — non-secret GymMonth preferences mirrored to the vault.
 * App config lives under `app_settings/GymMonth/`, never in `Notes/`.
 */
object GymMonthSettingsNote {
    /** Vault folder `app_settings/<APP_FOLDER>/` for GymMonth config + catalogs. */
    const val APP_FOLDER = "GymMonth"
    val RELATIVE_PATH: String = VaultPaths.appSettingsRelativePath(APP_FOLDER, "settings.md")
    const val TITLE = "GymMonth settings"

    data class Values(
        val monthlyTargetVisits: Int,
        val visitCountingMode: VisitCountingMode,
        val gymReminderEnabled: Boolean,
        val vaultWriteEnabled: Boolean,
        val vaultAutoLogSynced: Boolean,
        val vaultRoot: String,
        val defaultGymId: String?,
        val defaultGymName: String?,
        val gymsCatalogPath: String,
    )

    /** Subset that "Load from vault" applies (vault root is intentionally not loaded). */
    data class Patch(
        val monthlyTargetVisits: Int? = null,
        val visitCountingMode: VisitCountingMode? = null,
        val gymReminderEnabled: Boolean? = null,
        val vaultWriteEnabled: Boolean? = null,
        val vaultAutoLogSynced: Boolean? = null,
        val defaultGymId: String? = null,
        val clearDefaultGym: Boolean = false,
    ) {
        val isEmpty: Boolean get() = this == Patch()
    }

    fun rows(v: Values): List<Pair<String, String>> = listOf(
        "monthlyTargetVisits" to v.monthlyTargetVisits.toString(),
        "visitCountingMode" to v.visitCountingMode.name,
        "gymReminderEnabled" to v.gymReminderEnabled.toString(),
        "vaultWriteEnabled" to v.vaultWriteEnabled.toString(),
        "vaultAutoLogSynced" to v.vaultAutoLogSynced.toString(),
        "vaultRoot" to v.vaultRoot,
        "dailyHeading" to VaultWorkoutFormat.HEADING,
        "defaultGymId" to (v.defaultGymId ?: ""),
        "defaultGymName" to (v.defaultGymName ?: ""),
        "gymsCatalog" to v.gymsCatalogPath,
    )

    fun render(v: Values, now: Instant, zone: ZoneId = VaultWorkoutFormat.VAULT_ZONE): String =
        KeyValueNote.render(
            title = TITLE,
            source = "GymMonth",
            tags = listOf("gymmonth", "settings"),
            rows = rows(v),
            updatedAt = now.atZone(zone).toOffsetDateTime(),
            intro = "Written by the GymMonth app (Settings → Save settings to vault). " +
                "Visits go to the Daily note under `${VaultWorkoutFormat.HEADING}`. " +
                "Edit values here and use Settings → Load settings from vault to apply them. No secrets.",
        )

    fun parse(markdown: String): Patch {
        val m = KeyValueNote.parse(markdown)
        fun bool(k: String): Boolean? = when (m[k]?.trim()?.lowercase()) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> null
        }
        val gym = m["defaultGymId"]?.trim()
        return Patch(
            monthlyTargetVisits = m["monthlyTargetVisits"]?.trim()?.toIntOrNull()?.coerceAtLeast(0),
            visitCountingMode = m["visitCountingMode"]?.trim()?.let { n ->
                VisitCountingMode.entries.firstOrNull { it.name.equals(n, ignoreCase = true) }
            },
            gymReminderEnabled = bool("gymReminderEnabled"),
            vaultWriteEnabled = bool("vaultWriteEnabled"),
            vaultAutoLogSynced = bool("vaultAutoLogSynced"),
            defaultGymId = gym?.takeIf { it.isNotEmpty() },
            clearDefaultGym = gym != null && gym.isEmpty(),
        )
    }
}
