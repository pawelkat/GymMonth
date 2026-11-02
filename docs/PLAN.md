# GymMonth — Development Plan (Sibling of SwimWeek)

**Status:** In progress — PR 0 scaffold + PR 1 domain done (`/root/gymmonth`)  
**Sibling app:** [SwimWeek](https://github.com/pawelkat/SwimWeek) (local: `/root/swimweek`)  
**Primary device stack:** Galaxy Watch 7 → Samsung Health → Health Connect → GymMonth  
**UI bar:** Match SwimWeek look & feel (AMOLED pure black, Compose Material3, Glance widget)

---

## 1. Product summary

| | **SwimWeek** | **GymMonth** |
|---|--------------|--------------|
| Metric | Weekly swim **distance** (m) | Monthly gym **visit count** |
| Period | Calendar week (Mon/Sun start) | Calendar **month** |
| Widget | AMOLED weekly distance + target ring | AMOLED **visits this month** (+ optional target ring) |
| Session place | Zurich pools (offline JSON + tagging) | Gym **name / place** (local store; user list or free text) |
| Alerts | Evening pool open + 1.5h before close | **Gym-go reminders** (schedule configurable) |
| Live web | CrowdMonitor occupancy (optional) | **None in v1** (keep simple; no INTERNET unless needed later) |

**One-line pitch:** Count how many times you hit the gym this month from Watch workouts, show it on an AMOLED widget, and nudge you to go.

---

## 2. Goals (v1)

1. Read **strength / gym-like** exercise sessions from Health Connect for the **current calendar month**.
2. Count **visits** (one visit ≈ one qualifying exercise session on a day, with overlap policy).
3. Show count on **Home** and **AMOLED Glance widget**.
4. Optional **monthly target** (e.g. 12 visits) with progress ring (reuse SwimWeek pattern).
5. **Onboarding** identical in spirit: HC install → permissions → Samsung Health bridge checklist.
6. **Gym reminder notifications** (user-defined days/times or “N days since last visit”).
7. Same visual language as SwimWeek (black bg, typography, buttons, diagnostics).

### Non-goals (v1)

- No multi-gym live occupancy APIs.
- No social/cloud sync.
- No writing workouts to Health Connect.
- No automatic gym geofencing (optional later).
- Do **not** share a single APK with SwimWeek — **separate app / package**.

---

## 3. Data model (domain)

```text
GymVisit
  id: String                 // HC session id
  start: Instant
  end: Instant
  exerciseType: GymExerciseKind
  dataOriginPackage: String
  title: String?             // HC title if any
  gymPlaceId / gymName: String?  // app-local mapping (like SessionPlace)
  overlapSuppressed: Boolean
  countsAsVisit: Boolean     // after policy

MonthlyGymSummary
  month: MonthRange          // year-month, zone-aware
  visitCount: Int            // unique visits after policy
  visits: List<GymVisit>
  lastSyncedAt: Instant
  sourceStatus: SourceStatus // reuse SwimWeek enum semantics
  userReportedMissingData: Boolean

MonthRange
  year, month, zone
  start / endExclusive Instant
  identityKey() → "2026-08"
```

### What counts as a “gym visit”?

**Recommended v1 heuristic** (configurable later in Settings):

Include Health Connect `ExerciseSessionRecord` when **any** of:

1. Exercise type is strength-related, e.g.:
   - `EXERCISE_TYPE_STRENGTH_TRAINING`
   - `EXERCISE_TYPE_WEIGHTLIFTING`
   - `EXERCISE_TYPE_CALISTHENICS`
   - `EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING` (optional toggle)
   - Other types user enables in Settings
2. **OR** session title/notes contain gym-ish keywords (optional, off by default): `gym`, `fitness`, `kraft`, etc.
3. **OR** user manually tags session as gym in app (local place map).

**Visit counting policy:**

- Default: **one visit per local calendar day** (multiple sessions same day = 1 visit), **or** one visit per session — **decide in PR1 and stick to it**.
- **Recommendation:** count **sessions** that pass filter (not unique days), but mark same-day multi-session in diagnostics; Settings toggle “Count at most one visit per day”.
- Apply **overlap credit-once** like SwimWeek `OverlapPolicy` so double-exports don’t double-count.

**Pipeline (mirror SwimWeek):**

```text
Watch 7 workout
  → Samsung Health
  → Health Connect (Exercise)
  → GymMonth read ExerciseSessionRecord
  → filter gym types
  → MonthRange by session start
  → overlap policy
  → MonthlyGymSummary cache (DataStore)
  → Home + Widget
```

---

## 4. Architecture (clone SwimWeek shape)

### Package / project

```text
applicationId: com.gymmonth.app
package:       com.gymmonth.app
Repo:          separate GitHub repo (e.g. pawelkat/GymMonth)
Local path:    /root/gymmonth   (suggested)
```

### Module layout (same as SwimWeek)

```text
app/
  domain/     MonthRange, GymVisit, MonthlyGymSummary, VisitPolicy, TargetProgress
  health/     HC client, permissions, GymVisitAggregator, GymVisitRepository, ChangesSync
  data/       PreferencesStore, SummaryCacheStore, GymPlaceStore, ChangesTokenStore
  sync/       WorkManager workers, SyncScheduler, midnight/month-boundary rollover
  widget/     Glance AMOLED widget
  alert/      GymReminderScheduler / Notifier / Worker
  ui/         theme (copy), home, onboarding, settings, diagnostics, session details
  di/         Hilt
```

### Reuse from SwimWeek (copy + rename, not multi-module shared lib in v1)

| SwimWeek piece | GymMonth adaptation |
|----------------|---------------------|
| Theme / AmoledBlack | Copy as-is |
| Onboarding + Samsung bridge | Copy; retarget copy to “gym / strength workouts” |
| HealthConnect* plumbing | Same permissions model: **Exercise** read; distance **optional** (not required for visit count) |
| ChangesSyncEngine + WorkManager | Same cadence |
| PreferencesStore patterns | Month start not needed; add reminder schedule + visit policy |
| SessionPlaceStore | → `GymPlaceStore` (sessionId → gym name) |
| Progress ring | Target = monthly visits (int), not meters |
| Diagnostics | Session list + origins |
| Evening pool logic | **Do not port** pools/CrowdMonitor |

**Permissions v1:**

- `READ_EXERCISE` (required)
- `READ_HEALTH_DATA_IN_BACKGROUND` (optional, widget refresh)
- `POST_NOTIFICATIONS` (reminders)
- `RECEIVE_BOOT_COMPLETED` (reschedule reminders)
- **No INTERNET** in v1 (unless you later add gym APIs)

---

## 5. UX / screens

### Home

- Big number: **visits this month** (e.g. `7`)
- Subtitle: `August 2026` / “This month’s gym visits”
- Optional ring vs monthly target
- Last 6 months bar chart (result vs target) — port `WeeklyStatsBarChart` → monthly
- Buttons: Refresh, Diagnostics, Settings, Setup guide, Add widget
- List of this month’s visits (tap → session details / gym name)

### Widget (AMOLED)

- Pure `#000000`
- Large visit count + small “visits” / month label
- Optional progress ring if target set
- Tap → MainActivity

### Session details

- Dropdown/title: gym place (favorites first, free text optional)
- Time, type, source package
- No Zurich pool hours

### Settings

- Monthly visit target (integer)
- Visit policy: one per session vs one per day
- Which exercise types count
- Units: N/A for visits (hide distance units)
- Gym reminder: enable, days of week, time of day, OR “remind if no visit in N days”
- Notifications permission

### Onboarding

Same 3 steps as SwimWeek with gym wording.

### Alerts (Gym go)

**v1 simplest useful design:**

1. **Scheduled reminder** — e.g. Mon/Wed/Fri 17:30  
2. Optional: skip if a visit already logged **today**  
3. WorkManager one-shot chain (like SwimWeek pool reminder) or AlarmManager later if exact time matters  

Copy notification channel pattern from SwimWeek `PoolReminderNotifier`.

---

## 6. PR plan (execute in order)

| PR | Scope | Done when |
|----|--------|-----------|
| **0** | Scaffold project from SwimWeek (copy tree, rename package/app name/icons) | ✅ `assembleDebug` as GymMonth (`com.gymmonth.app`) |
| **1** | Domain: `MonthRange`, models, visit policy unit tests | ✅ Tests green |
| **2** | HC permissions + onboarding shell | ✅ Can grant Exercise read |
| **3** | Aggregator + repository + cache for monthly visits | ✅ Home shows count after sync |
| **4** | Home UI + diagnostics + session list | ✅ Usable companion app |
| **5** | Glance AMOLED widget + updater | ✅ Widget shows month visits |
| **6** | Background sync + month-boundary rollover | ✅ Count updates overnight / HC changes |
| **7** | Monthly target + progress ring (home + widget) | Target works |
| **8** | Gym place tagging on session details | ✅ Places persist locally (`GymPlaceStore`; default gyms from `assets/default_gyms.json`, PureGym seeded) |
| **8b** | Manual gym visits (app-local) | ✅ Home → **Add visit**; stored in `ManualVisitStore` (`manual-<uuid>`), merged into monthly count after every HC aggregate; deletable from session details. **Never written to Health Connect.** |
| **8c** | Settings screen + Obsidian vault write (VaultWrite lib) | ✅ Home → **Settings**: target, counting mode, default gym, vault toggles, All files access, **Save/Load settings ↔ `Notes/GymMonth settings.md`**. Visits → `Daily/YYYY-MM-DD.md` under **`## Workouts`** (manual add, session **Log to vault**, optional auto-log after sync, month backfill). Gyms catalog lives in vault `Notes/GymMonth/default_gyms.json` (seeded once from assets, never overwritten, bundled fallback). Local files only, no INTERNET. |
| **9** | Gym reminder notifications + Settings | Notification fires on schedule |
| **10** | 6-month history chart + polish | Polish |

Do **not** start pools/CrowdMonitor/disturber work for GymMonth.

Manual visits are an intentional exception to “HC is the only source”: they live only in app DataStore (excluded from backup), count toward `visitCount` through the same overlap + visit-counting policy (`mergeManualVisits`), and HC sessions stay read-only.

---

## 7. Implementation notes (Watch 7)

Samsung Health often exports strength workouts as:

- `EXERCISE_TYPE_STRENGTH_TRAINING` or generic workout  
- Origin package: `com.sec.android.app.shealth` (or similar)

**Validation checklist (same spirit as SwimWeek):**

1. Complete strength workout on Watch 7  
2. Appears in Samsung Health  
3. Samsung Health → Health Connect write Exercise enabled  
4. GymMonth has Exercise read  
5. Refresh → visit appears in month  

If Watch logs only “Workout” without strength type, add Settings multi-select of exercise types + optional title filter.

---

## 8. Look & feel checklist (parity with SwimWeek)

- [ ] `AmoledBlack` background everywhere  
- [ ] Material3 dark theme (copy `ui/theme`)  
- [ ] Same button hierarchy (filled Refresh, outlined secondary)  
- [ ] Icon: dumbbell / weight (vector), not swimmer  
- [ ] Widget pure black, high-contrast white/primary text  
- [ ] Progress ring bitmap pattern reused  
- [ ] Strings English v1 (same as SwimWeek)  

---

## 9. Testing

- Unit: `MonthRange` boundaries, visit policy (same day, overlap), target fraction  
- Fake HC data source (copy SwimWeek test harness)  
- Manual: S24 Ultra + Watch 7 bridge  

---

## 10. Privacy

- On-device only  
- No backup of health-derived caches  
- Read-only HC  
- No INTERNET v1  
- Optional vault write (PR 8c): plain Markdown/JSON into the local DriveSync vault folder only (`MANAGE_EXTERNAL_STORAGE`, ToDaily pattern). Only derived visit lines (type, gym, times) and non-secret settings — never raw HC records.  
- Privacy rationale activity for HC  

---

## 11. How to transfer this plan to another session

### Option A — Recommended (file + short prompt)

1. Keep this file: **`/root/GymMonth-PLAN.md`** (or copy into the new repo as `docs/PLAN.md`).
2. Start a **new Grok / agent session** with workspace root `/root` or `/root/gymmonth`.
3. Paste a **bootstrap message** like:

```text
Implement GymMonth from the plan at /root/GymMonth-PLAN.md.
It is a sibling of SwimWeek at /root/swimweek — copy architecture and AMOLED look & feel,
but monthly gym visit count (not weekly swim distance). No pools/CrowdMonitor.
Start with PR 0: scaffold project at /root/gymmonth by cloning SwimWeek structure
and renaming package to com.gymmonth.app. Then do PR 1 domain models with tests.
Do not modify SwimWeek unless I ask.
```

4. If the new session can’t see `/root`, **attach or paste** this whole markdown file.

### Option B — GitHub issue / gist

1. Create empty repo `GymMonth`.  
2. Commit this file as `PLAN.md`.  
3. In the new session: `clone` the repo and say: “Execute PLAN.md PR 0–3.”

### Option C — Compact context if file is missing

Paste this one-liner + PR table:

```text
GymMonth = SwimWeek sibling. Galaxy Watch 7 → Samsung Health → Health Connect.
Metric: monthly gym visit count (strength/gym exercise sessions). AMOLED widget.
Reminders to go to the gym. Same Compose/Glance/Hilt stack. Package com.gymmonth.app.
No INTERNET v1. Scaffold from /root/swimweek, then MonthRange + aggregator + widget + reminders.
```

### What the other session should open first

1. This plan: `/root/GymMonth-PLAN.md`  
2. SwimWeek reference (read-only):  
   - `/root/swimweek/app/src/main/java/com/swimweek/app/domain/`  
   - `/root/swimweek/app/src/main/java/com/swimweek/app/health/`  
   - `/root/swimweek/app/src/main/java/com/swimweek/app/widget/`  
   - `/root/swimweek/app/src/main/java/com/swimweek/app/ui/theme/`  

### Success criteria for session handoff

The new session can answer “yes” to:

- [ ] Knows product difference (month visits vs week meters)  
- [ ] Knows package `com.gymmonth.app`  
- [ ] Knows not to port Zurich pools  
- [ ] Has ordered PRs 0–10  
- [ ] Has path to SwimWeek as template  

---

## 12. Open decisions (resolve in PR 0 / settings)

1. **Visit = session vs day?** (default proposal: session, optional one-per-day)  
2. **Default exercise types** included out of the box  
3. **Reminder model:** fixed schedule vs “stale days since last visit” (can support both later)  
4. **App icon / name** on launcher: “GymMonth”  
5. **Month boundary:** calendar month in device timezone (yes)

---

## 13. First commands for implementing session

```bash
# After PR 0 scaffold exists:
cd /root/gymmonth
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Copy APK to device Downloads with dated name (SwimWeek convention).

---

*Plan authored for handoff from SwimWeek session. Update this file when PR status changes.*
