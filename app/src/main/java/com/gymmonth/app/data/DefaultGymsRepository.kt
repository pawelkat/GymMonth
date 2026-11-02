package com.gymmonth.app.data

import android.content.Context
import com.gymmonth.app.domain.GymPlace
import com.vaultwrite.StorageAccess
import com.vaultwrite.VaultCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Default gyms catalog (offline; no INTERNET).
 *
 * Source of truth is the user-editable vault copy at [VAULT_CATALOG_PATH]
 * (`/sdcard/DriveSyncFiles/Vault/Notes/GymMonth/default_gyms.json`):
 * - missing → seeded once from the bundled `assets/default_gyms.json`
 * - present → always used, never overwritten by the APK copy
 * - no storage access / unreadable / invalid → bundled asset (no crash)
 *
 * [gymsFlow] starts with the bundled list; [reload] swaps in the vault copy.
 */
@Singleton
class DefaultGymsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesStore: PreferencesStore,
) {
    data class CatalogStatus(
        val source: VaultCatalog.Source,
        val vaultPath: String?,
        val error: String? = null,
    )

    private val bundledText: String by lazy {
        runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrDefault("""{"gyms":[]}""")
    }

    private val bundledGyms: List<GymPlace> by lazy { parseOrEmpty(bundledText) }

    private val _gyms = MutableStateFlow<List<GymPlace>?>(null)
    private val _status = MutableStateFlow(CatalogStatus(VaultCatalog.Source.BUNDLED, null))

    val gymsFlow: StateFlow<List<GymPlace>?> = _gyms.asStateFlow()
    val statusFlow: StateFlow<CatalogStatus> = _status.asStateFlow()

    fun all(): List<GymPlace> = _gyms.value ?: bundledGyms.also { _gyms.compareAndSet(null, it) }

    fun selectableGyms(): List<GymPlace> = all().filter { it.selectable }

    fun findGym(id: String): GymPlace? = all().firstOrNull { it.id == id }

    /** Load the vault catalog (seeding it once if missing). Safe to call often. */
    suspend fun reload(): CatalogStatus = withContext(Dispatchers.IO) {
        val status = runCatching {
            val root = preferencesStore.get().vaultRoot
            val loaded = VaultCatalog.loadOrSeed(
                vaultRoot = root,
                relativePath = VAULT_CATALOG_PATH,
                vaultAvailable = StorageAccess(context).hasWriteAccess(),
                bundled = { bundledText },
                accept = { parseOrEmpty(it).isNotEmpty() },
            )
            val gyms = parseOrEmpty(loaded.text).ifEmpty { bundledGyms }
            _gyms.value = gyms
            CatalogStatus(loaded.source, loaded.vaultPath, loaded.error)
        }.getOrElse {
            _gyms.value = bundledGyms
            CatalogStatus(VaultCatalog.Source.BUNDLED, null, it.message)
        }
        _status.value = status
        status
    }

    private fun parseOrEmpty(json: String): List<GymPlace> =
        runCatching { GymJsonCodecs.parseDefaultGyms(json) }.getOrDefault(emptyList())

    companion object {
        const val ASSET_NAME = "default_gyms.json"

        /** Vault-relative path of the user-editable gyms catalog. */
        const val VAULT_CATALOG_PATH = "Notes/GymMonth/default_gyms.json"
    }
}
