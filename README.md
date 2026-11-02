# GymMonth

Monthly gym **visit count** from **Google Health Connect**, shown on an AMOLED-optimized home-screen widget. Data from Galaxy Watch 7 is expected via **Samsung Health → Health Connect**.

Sibling of [SwimWeek](https://github.com/pawelkat/SwimWeek) — same architecture and look & feel, different metric (month visits vs week meters). No pools / CrowdMonitor in v1.

**Repo:** https://github.com/pawelkat/GymMonth

## Status

| PR | Scope | Status |
| --- | --- | --- |
| 0 | Project scaffold, Hilt, AMOLED Compose shell | Done |
| 1 | Domain models & month utilities | Done |
| 2 | Health Connect permissions + onboarding shell | Done |
| 3 | Aggregator + repository + cache | Done |
| 4 | Home UI + diagnostics + session list | Done |
| 5 | Glance AMOLED widget | Done |
| 6 | Background sync + month-boundary rollover | Done |
| 7 | Monthly target + progress ring | Planned |
| 8 | Gym place tagging + manual visits (PureGym default) | Done |
| 8c | Settings + Obsidian vault write (Daily `## Workouts`, settings note) | Done |
| 9 | Gym reminder notifications | Planned |
| 10 | 6-month history chart + polish | Planned |

Full plan: [`docs/PLAN.md`](docs/PLAN.md).

### Manual visits & gym tagging

- **Add visit** on Home logs a gym visit the watch missed (date, start time, duration — default 60 min — and gym). Stored on-device only (`ManualVisitStore`), never written to Health Connect, and counted in the monthly total (overlapping HC sessions are credited once).
- Session details lets you tag any session with a gym (default list from `app/src/main/assets/default_gyms.json` — currently **PureGym**, Grabenwisstrasse 5, 8604 Volketswil — or free text) and delete manual visits. HC sessions are read-only.

### Obsidian vault write (Settings)

Home → **Settings**. Local files only — no `INTERNET`, no Drive OAuth; DriveSync syncs the folder.

- **Vault root:** `/sdcard/DriveSyncFiles/Vault` (editable). Needs **All files access** (button in Settings, same as ToDaily).
- **Write visits to vault:** each visit becomes one bullet in `Daily/YYYY-MM-DD.md` (Europe/Zurich date/times) under **`## Workouts`**:
  `- Gym · Strength · PureGym · 18:00–19:00 · #gymmonth <!-- gymmonth:id=manual-… -->`
  The hidden `gymmonth:id=` comment dedupes (HC or manual id), so a visit is never logged twice. Missing Daily → created from the Templates/Daily shape; missing heading → appended.
  - on **Add visit** save (when enabled)
  - **Log to vault** in session details (any visit)
  - optional **auto-log new synced visits** (only visits after the toggle was turned on; never breaks HC sync)
  - **Log this month's visits** (backfill)
- **Save settings to vault** → overwrites `Notes/GymMonth settings.md` (frontmatter + `| Setting | Value |` table: monthlyTargetVisits, visitCountingMode, gymReminderEnabled, vaultWriteEnabled, vaultAutoLogSynced, vaultRoot, dailyHeading, default gym, catalog path; `updated` in Europe/Zurich). **Load settings from vault** applies the table back (vault root excluded). No secrets.
- **Gyms catalog:** `Notes/GymMonth/default_gyms.json` in the vault. Missing → copied once from `assets/default_gyms.json`; present → always used (edit it to add gyms); never overwritten by the APK; no access / invalid JSON → built-in list.

Implemented with the shared library [VaultWrite](https://github.com/pawelkat/VaultWrite) (`com.vaultwrite`).

## Requirements

- JDK 17+
- Android SDK 35 + build-tools
- Android Studio Ladybug+ or command-line Gradle

## Build

GymMonth includes the **VaultWrite** library module from a sibling checkout (`../VaultWrite`; on the S24 Termux: `/root/VaultWrite` next to `/root/gymmonth`):

```bash
git clone https://github.com/pawelkat/VaultWrite.git ../VaultWrite   # once
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest :vaultwrite:testDebugUnitTest
# other location: ./gradlew -PvaultWriteDir=/path/to/VaultWrite ...
```

(`settings.gradle.kts` does `include(":vaultwrite")` with `projectDir = <vaultWriteDir>/vaultwrite`; the module reuses GymMonth's AGP/Kotlin. CI checks out VaultWrite alongside.)

Debug APK (Gradle): `app/build/outputs/apk/debug/app-debug.apk`

### Install from device Downloads (SwimWeek convention)

After every build, copy with a **unique timestamped name** so the file browser picks up a new entry (overwrite/refresh is flaky on Samsung My Files):

```bash
STAMP=$(date +%Y%m%d-%H%M)
cp -f app/build/outputs/apk/debug/app-debug.apk \
  "/sdcard/Download/GymMonth-<label>-${STAMP}-debug.apk"
```

Example: `/sdcard/Download/GymMonth-pr0-domain-20260812-2005-debug.apk`  
Install from **Files → Downloads**.

### aarch64 / ARM hosts

Google’s Linux `aapt2` is x86_64-only. On ARM64, put the override in **user** Gradle props (not the project file — that breaks CI):

```bash
# ~/.gradle/gradle.properties
android.aapt2FromMavenOverride=/path/to/aapt2-wrapper
```

## Privacy defaults (v1)

- No `INTERNET` permission
- Vault write is opt-in and local-only (`MANAGE_EXTERNAL_STORAGE` for the DriveSync vault folder)
- `allowBackup=false` + DataStore excluded from backup/extraction
- Health Connect read-only (`READ_EXERCISE`; distance not required for visit count)

## License

Proprietary / TBD.
