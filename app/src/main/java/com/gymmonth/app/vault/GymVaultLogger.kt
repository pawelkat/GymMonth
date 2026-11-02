package com.gymmonth.app.vault

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gymmonth.app.BuildConfig
import com.gymmonth.app.data.DefaultGymsRepository
import com.gymmonth.app.data.PreferencesStore
import com.gymmonth.app.data.VaultLoggedStore
import com.gymmonth.app.domain.GymMonthSettingsNote
import com.gymmonth.app.domain.GymVisit
import com.gymmonth.app.domain.MonthlyGymSummary
import com.gymmonth.app.domain.VaultWorkoutFormat
import com.gymmonth.app.util.ClockProvider
import com.vaultwrite.StorageAccess
import com.vaultwrite.VaultFileWriter
import com.vaultwrite.VaultWriteResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Writes GymMonth data into the local Obsidian vault via VaultWrite:
 * - visits → `Daily/YYYY-MM-DD.md` under `## Workouts`
 * - settings → `Notes/GymMonth settings.md`
 *
 * Local files only (no network). Never throws; callers show [Outcome] as a message.
 */
@Singleton
class GymVaultLogger @Inject constructor(
    @ApplicationContext context: Context,
    private val preferencesStore: PreferencesStore,
    private val loggedStore: VaultLoggedStore,
    private val defaultGyms: DefaultGymsRepository,
    private val clock: ClockProvider,
) {
    sealed class Outcome {
        data object Disabled : Outcome()
        data class NeedPermission(val path: String) : Outcome()
        data class Written(val relativePath: String, val createdFile: Boolean, val count: Int = 1) : Outcome()
        data class AlreadyThere(val relativePath: String) : Outcome()
        data class Failed(val message: String) : Outcome()
        data object NothingToDo : Outcome()
    }

    sealed class LoadOutcome {
        data class Applied(val patch: GymMonthSettingsNote.Patch) : LoadOutcome()
        data object Missing : LoadOutcome()
        data class NeedPermission(val path: String) : LoadOutcome()
        data class Failed(val message: String) : LoadOutcome()
    }

    private val storage = StorageAccess(context)
    private val mutex = Mutex()

    fun hasStorageAccess(): Boolean = storage.hasWriteAccess()

    fun manageAccessIntent(): Intent = storage.manageAllFilesIntent()

    fun manageAccessFallbackIntent(): Intent = storage.manageAllFilesFallbackIntent()

    /** Log one visit. [requireEnabled] false = explicit user action from session details. */
    suspend fun logVisit(visit: GymVisit, requireEnabled: Boolean = true): Outcome = safe {
        val prefs = preferencesStore.get()
        if (requireEnabled && !prefs.vaultWriteEnabled) return@safe Outcome.Disabled
        val writer = VaultFileWriter(prefs.vaultRoot)
        if (!storage.hasWriteAccess()) return@safe Outcome.NeedPermission(writer.root)
        val result = writeVisit(writer, visit)
        if (result is VaultWriteResult.Saved) loggedStore.markLogged(listOf(visit.id))
        result.toOutcome()
    }

    /**
     * After a Health Connect sync: log visits not yet written, if auto-log is on. Only visits
     * starting after auto-log was enabled, so turning it on doesn't backfill old Daily notes.
     */
    suspend fun logNewVisitsAfterSync(summary: MonthlyGymSummary): Outcome = safe {
        val prefs = preferencesStore.get()
        if (!prefs.vaultWriteEnabled || !prefs.vaultAutoLogSynced) return@safe Outcome.Disabled
        if (!storage.hasWriteAccess()) return@safe Outcome.NeedPermission(prefs.vaultRoot)
        val done = loggedStore.ids()
        val pending = summary.visits.filter {
            VaultWorkoutFormat.shouldLog(it) &&
                it.id !in done &&
                it.start.toEpochMilli() >= prefs.vaultAutoLogSinceEpochMs &&
                !it.start.isAfter(clock.now())
        }
        logAll(VaultFileWriter(prefs.vaultRoot), pending)
    }

    /** Explicit backfill: every loggable visit of [summary] (file dedupe skips existing bullets). */
    suspend fun logMonth(summary: MonthlyGymSummary): Outcome = safe {
        val prefs = preferencesStore.get()
        if (!storage.hasWriteAccess()) return@safe Outcome.NeedPermission(prefs.vaultRoot)
        logAll(VaultFileWriter(prefs.vaultRoot), summary.visits.filter(VaultWorkoutFormat::shouldLog))
    }

    suspend fun saveSettings(): Outcome = safe {
        val prefs = preferencesStore.get()
        val writer = VaultFileWriter(prefs.vaultRoot)
        if (!storage.hasWriteAccess()) return@safe Outcome.NeedPermission(writer.root)
        val gym = prefs.defaultGymId?.let(defaultGyms::findGym)
        val markdown = GymMonthSettingsNote.render(
            GymMonthSettingsNote.Values(
                monthlyTargetVisits = prefs.monthlyTargetVisits,
                visitCountingMode = prefs.visitCountingMode,
                gymReminderEnabled = prefs.gymReminderEnabled,
                vaultWriteEnabled = prefs.vaultWriteEnabled,
                vaultAutoLogSynced = prefs.vaultAutoLogSynced,
                vaultRoot = writer.root,
                defaultGymId = prefs.defaultGymId,
                defaultGymName = gym?.name,
                gymsCatalogPath = DefaultGymsRepository.VAULT_CATALOG_PATH,
            ),
            now = clock.now(),
        )
        when (val r = writer.writeNote(GymMonthSettingsNote.RELATIVE_PATH, markdown)) {
            is VaultWriteResult.Saved -> Outcome.Written(r.relativePath, r.createdFile)
            else -> r.toOutcome()
        }
    }

    suspend fun loadSettings(): LoadOutcome = try {
        withContext(Dispatchers.IO) {
            val prefs = preferencesStore.get()
            val writer = VaultFileWriter(prefs.vaultRoot)
            if (!storage.hasWriteAccess()) return@withContext LoadOutcome.NeedPermission(writer.root)
            val text = writer.readNote(GymMonthSettingsNote.RELATIVE_PATH)
                ?: return@withContext LoadOutcome.Missing
            val patch = GymMonthSettingsNote.parse(text)
            patch.monthlyTargetVisits?.let { preferencesStore.setMonthlyTargetVisits(it) }
            patch.visitCountingMode?.let { preferencesStore.setVisitCountingMode(it) }
            patch.gymReminderEnabled?.let { preferencesStore.setGymReminderEnabled(it) }
            patch.vaultWriteEnabled?.let { preferencesStore.setVaultWriteEnabled(it) }
            patch.vaultAutoLogSynced?.let { preferencesStore.setVaultAutoLogSynced(it, clock.now().toEpochMilli()) }
            when {
                patch.defaultGymId != null -> preferencesStore.setDefaultGymId(patch.defaultGymId)
                patch.clearDefaultGym -> preferencesStore.setDefaultGymId(null)
            }
            LoadOutcome.Applied(patch)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LoadOutcome.Failed(e.message ?: e::class.java.simpleName)
    }

    private suspend fun logAll(writer: VaultFileWriter, visits: List<GymVisit>): Outcome {
        if (visits.isEmpty()) return Outcome.NothingToDo
        var written = 0
        var created = false
        var lastPath = ""
        val ok = mutableListOf<String>()
        var failure: String? = null
        for (v in visits.sortedBy { it.start }) {
            when (val r = writeVisit(writer, v)) {
                is VaultWriteResult.Saved -> {
                    ok += v.id
                    lastPath = r.relativePath
                    if (!r.duplicate) written++
                    created = created || r.createdFile
                }
                is VaultWriteResult.Failed -> failure = r.message
                is VaultWriteResult.NeedStoragePermission -> failure = "no storage access"
            }
        }
        loggedStore.markLogged(ok)
        return when {
            written > 0 -> Outcome.Written(lastPath, created, written)
            failure != null -> Outcome.Failed(failure)
            else -> Outcome.AlreadyThere(lastPath)
        }
    }

    private fun writeVisit(writer: VaultFileWriter, visit: GymVisit): VaultWriteResult =
        writer.appendToDaily(
            date = VaultWorkoutFormat.dailyDate(visit),
            heading = VaultWorkoutFormat.HEADING,
            bullet = VaultWorkoutFormat.bullet(visit),
            dedupeKey = VaultWorkoutFormat.dedupeKey(visit.id),
        )

    private fun VaultWriteResult.toOutcome(): Outcome = when (this) {
        is VaultWriteResult.Saved ->
            if (duplicate) Outcome.AlreadyThere(relativePath) else Outcome.Written(relativePath, createdFile)
        is VaultWriteResult.NeedStoragePermission -> Outcome.NeedPermission(absolutePath)
        is VaultWriteResult.Failed -> Outcome.Failed(message)
    }

    private suspend fun safe(block: suspend () -> Outcome): Outcome = try {
        withContext(Dispatchers.IO) { mutex.withLock { block() } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.d(TAG, "vault write failed: ${e.message}")
        Outcome.Failed(e.message ?: e::class.java.simpleName)
    }

    private companion object {
        const val TAG = "GymVaultLogger"
    }
}
