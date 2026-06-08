# Stela — Phase 5 (Resilience & Settings) Implementation Plan

> Companion to [2026-06-08-stela-design.md](2026-06-08-stela-design.md) §§5, 6, 7, 9, 12.
> Introduces the preferences store and the settings that depend on it, then the
> quick-add toggle's three-state service behavior, then the OS-resilience helpers.

**Goal:** Users can configure theme, list ordering, lock-screen visibility, and the
quick-add entry, and the app guides them through battery/OEM settings — all backed
by a persistent preferences store.

**Tech stack add:** `androidx.datastore:datastore-preferences`. No `INTERNET`.

---

## Confirmed decisions

- **Split into three slices:** 5a settings + theme, 5b quick-add three-state, 5c
  resilience. Each ends green.
- **Theme default: Follow System.**
- **Quick-add default: On** — so 5b must request `POST_NOTIFICATIONS` during
  onboarding and start the service even with zero pins.

## The spine: `SettingsRepository` over DataStore

A new preferences SSOT, parallel to `NoteRepository`:

```
Settings(
    themeMode: ThemeMode = SYSTEM,      // LIGHT | DARK | SYSTEM
    hideOnLockScreen: Boolean = false,
    quickAddEnabled: Boolean = true,    // consumed in 5b
    // Phase 7 adds: sortMode, filterMode — persisted view state, set from the list
    // screen (not the Settings screen). Replaces the dropped pinned-at-top toggle.
)
```

`SettingsRepository(dataStore)` exposes `val settings: Flow<Settings>` + `suspend`
setters. Consumed by: the theme (`MainActivity`), the `NotificationController`
(lock-screen visibility), `NotePinner`/`PinService` (quick-add, 5b), and the list
ViewModel (sort/filter, Phase 7). Built in `AppContainer`; a fake backs ViewModel
unit tests.

**Dropped:** the explicit "pinned-at-top" setting — superseded by the Phase 7
filter (filter by Pinned). Persisted sort/filter live in this same store as view
state, added in Phase 7.

---

# Slice 5a — Settings foundation + theme + display toggles

**Status (2026-06-08) — complete.** Tests: 29 JVM unit + 15 instrumented, all green;
no `INTERNET`. DataStore `SettingsRepository` (pure mapping + round-trip tested);
theme Light/Dark/Follow-System verified switching live and persisting; `SettingsScreen`
+ `SettingsViewModel`; hide-on-lock-screen applies `VISIBILITY_SECRET` (instrumented)
and re-asserts pinned notifications on change via a `StelaApp` observer. No
list-ordering work (moved to Phase 7).

Low-risk "preference → presentation." Mostly JVM-testable.

## Units
- **`androidx.datastore:datastore-preferences`** dependency.
- **`ThemeMode`** enum; **`Settings`** data class.
- **`SettingsRepository`** — DataStore-backed; pure `Preferences -> Settings`
  mapping extracted for JVM testing; round-trip covered by an instrumented test.
  A `FakeSettingsRepository` (in-memory `MutableStateFlow`) for ViewModel tests.
- **Theme** — `lightColorScheme()` + `darkColorScheme()`; `StelaTheme(darkTheme:
  Boolean, content)`. `MainActivity` collects `settings.themeMode` and resolves
  `SYSTEM` via `isSystemInDarkTheme()`. Window XML theme moves to a DayNight base so
  there's no pre-Compose flash; status-bar icon contrast follows the theme.
- **`SettingsScreen`** (now stateful) + **`SettingsViewModel`** — theme selector,
  hide-on-lock-screen switch, wired to `SettingsRepository`.
- **Hide-on-lock-screen** — `NotificationController` applies
  `VISIBILITY_SECRET`/`PRIVATE` per the setting when building pinned notifications;
  changing the setting **re-asserts** pinned notes so it takes effect immediately.

## Tasks (TDD where it bites)
1. **DataStore + Settings model + repository.** Pure mapping test (JVM);
   instrumented round-trip (set → read back). **Gate:** tests pass.
2. **Wire into `AppContainer`; `FakeSettingsRepository`.** **Gate:** build.
3. **Theme.** Light/dark schemes, `StelaTheme(darkTheme)`, `MainActivity`
   resolution, DayNight window theme. **Gate:** build; manual — switch theme and
   see it apply, no flash.
4. **`SettingsScreen` + `SettingsViewModel`** wired to the repository (TDD VM:
   toggles persist). **Gate:** unit tests; manual nav.
5. **Hide-on-lock-screen** — controller visibility + re-assert on change.
   **Gate:** instrumented/manual — toggle hides/shows on the lock screen.
6. **Verify** — build + unit + instrumented green; manual theme + lock-screen
   checks.

## 5a testing
JVM: settings mapping, `SettingsViewModel`. Instrumented: DataStore round-trip,
notification visibility. Manual: theme switch, lock-screen hide.

---

# Slice 5b — Quick-add toggle & three-state service

**Status (2026-06-08) — complete.** Tests: 31 JVM unit + 15 instrumented, all green;
no `INTERNET`. Three states verified live on the emulator: quick-add on → `quick_add`
shown; off + pinned → minimal `service_status` "Stela is running" line + `pinned_notes`;
off + nothing pinned → service stopped, no notifications.

**Decisions (resolved):**
- **State-2 notification:** its own `service_status` channel, `IMPORTANCE_MIN`
  ("Stela is running", body tap opens the list) — not a reuse of `quick_add`.
- **Onboarding:** lazy/contextual — `MainActivity` requests `POST_NOTIFICATIONS` on
  launch when quick-add is on and not yet granted; not a dedicated first-run screen.
- **Denial handling:** no auto-disable. `PinServiceController.start()` is a no-op
  without permission; the Settings quick-add toggle uses the shared permission gate
  with an "Open settings" Snackbar. Quick-add stays "on" as a preference but stays
  dormant until permission is granted, then a reconcile starts the service.

**Scope (§6):** quick-add becomes a real setting (default **on**). The service's
foreground notification has three states:
1. **on** → quick-add notification (current Phase 4 behavior).
2. **off but pinned ≥ 1** → minimal, silent "Stela is running" line (keeps the
   service alive without advertising quick-add).
3. **off and nothing pinned** → stop the service entirely.

**Architecture:**
- Add `quickAddEnabled` consumption: `ServiceLifecycle.shouldRun` finally receives
  the real value; `NotePinner.reconcile` and `PinService` read it from
  `SettingsRepository`.
- `NotificationController` gains a minimal "running" notification builder (state 2)
  — likely its own low-importance channel.
- A **settings observer** reconciles the service when `quickAddEnabled` changes
  (start/stop/swap notification), so toggling it takes effect immediately.
- **Onboarding permission (because default on):** first launch (or enabling
  quick-add) requests `POST_NOTIFICATIONS`; on denial, the service can't show its
  notification — reflect that honestly (prompt, or treat quick-add as effectively
  off until granted).

---

# Slice 5c — Resilience helpers

**Status (2026-06-08) — complete.** Tests: 35 JVM unit + 19 instrumented, all green;
no `INTERNET`, no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. `DeviceResilience` (battery
state + OEM mapping, JVM-tested); Settings "Keep notes alive" section (battery row
shows exempt state, opens settings; autostart row hidden on the generic emulator as
expected); re-assert-on-clear (deleteIntent + `REASSERT` action, instrumented — re-posts
when pinned, no-op when not); channel-disabled banner (instrumented Compose test).
Real-device-only (not verified here): OEM-autostart deep links, actual battery-kill
resistance. **Phase 5 complete (5a + 5b + 5c).**

The "fight the OS" set (§§7–9). OEM-specific and **largely emulator-untestable** —
the plan flags what is automatable vs real-device-only.

## Confirmed decisions
- **Battery exemption via the settings screen** (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`),
  not the direct dialog. So **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is NOT declared**
  (Play-safe; deviation from design §8, which is updated to match).
- **Re-assert-on-clear fires immediately** on swipe (deleteIntent → re-post).
- **Helpers live in the Settings screen only** — no first-run onboarding flow this slice.

## Units
- **`DeviceResilience`** (util) — `isIgnoringBatteryOptimizations(context)`, an
  intent to open battery-optimization settings, and a best-effort OEM-autostart
  intent from `Build.MANUFACTURER` (Xiaomi/Oppo/Realme/Vivo/Huawei/OnePlus) with a
  "resolves?" check. Keeps the OEM mess out of the UI; the pure manufacturer→target
  mapping is unit-tested.
- **Settings "Keep notes alive" section** — a **Battery optimization** row (shows
  exempt/not-exempt, opens settings) and an **OEM autostart** row (shown when a known
  manufacturer is detected; opens it, else guidance). State re-reads on resume.
- **Re-assert-on-clear** — `NotificationController` adds a `deleteIntent` to each
  pinned notification; `NotificationActionReceiver` gains a `REASSERT` action that
  re-posts the note **only if it is still pinned** (so Remove/unpin, which cancel
  rather than dismiss, never trigger it).
- **Channel-disabled banner** — a NoteList banner shown when
  `!areNotificationsEnabled()` or the `pinned_notes` channel importance is NONE,
  linking to notification settings. Re-checks on resume.

## Tasks
1. **`DeviceResilience` util (TDD where pure)** — battery state + intents; OEM
   manufacturer→autostart-target mapping unit-tested. **Gate:** unit tests + build.
2. **Settings "Keep notes alive" section** — battery + autostart rows, state on
   resume. **Gate:** build; manual nav (battery state shows; button opens settings).
3. **Re-assert-on-clear** — deleteIntent + `REASSERT` receiver action (re-post if
   still pinned). **Gate:** instrumented — fire the delete intent → re-posted; fire
   for an unpinned note → not re-posted.
4. **Channel-disabled banner** — list banner + resume re-check. **Gate:**
   instrumented/manual — disable notifications → banner appears.
5. **Verify** — build + unit + instrumented green; no `INTERNET`; no
   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Manual: battery/OEM rows on a real device
   (emulator can't exercise OEM autostart).

## Testing reality
- **Automatable:** `DeviceResilience` mapping (JVM); re-assert-on-clear (instrumented);
  channel-disabled banner (instrumented — toggle notifications); battery-state read.
- **Real-device only (flagged):** OEM-autostart deep links (generic emulator always
  falls through to guidance); actual battery-kill resistance.

---

## Explicitly NOT in Phase 5
List querying — search/sort/filter (Phase 7); full silhouette icon set / colored
large icon, share, timestamps, multi-select (Phase 6). The deferred notification-text
refinements (pinned-note "Tap to edit or remove"; quick-add pin-on-create) remain
open and can be slotted where they fit.
