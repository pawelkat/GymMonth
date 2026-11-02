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
| 8 | Gym place tagging | Planned |
| 9 | Gym reminder notifications | Planned |
| 10 | 6-month history chart + polish | Planned |

Full plan: [`docs/PLAN.md`](docs/PLAN.md).

## Requirements

- JDK 17+
- Android SDK 35 + build-tools
- Android Studio Ladybug+ or command-line Gradle

## Build

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

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
- `allowBackup=false` + DataStore excluded from backup/extraction
- Health Connect read-only (`READ_EXERCISE`; distance not required for visit count)

## License

Proprietary / TBD.
