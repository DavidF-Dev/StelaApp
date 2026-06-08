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

# Slice 5b — Quick-add toggle & three-state service (outline)

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

**Decisions to resolve when we reach 5b:** exact denial-handling (auto-disable
quick-add vs persistent prompt); whether state 2 uses a new channel or reuses
`quick_add`; where onboarding lives (first-run screen vs lazy on first need).

---

# Slice 5c — Resilience helpers (outline)

OEM-specific and manual-test-heavy. Best-effort by nature (§9).

- **Battery-optimization helper** — declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`;
  a Settings entry shows the current state (`PowerManager.isIgnoringBatteryOptimizations`)
  and launches `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **OEM-autostart helper** — a guidance screen that best-effort deep-links into known
  OEM autostart settings (Xiaomi/Oppo/Vivo/Huawei/Samsung) by component, with a
  graceful fallback when none match.
- **Re-assert-on-clear** — set a `deleteIntent` on pinned notifications; a receiver
  re-posts the pin when swiped (true self-heal), guarded against fighting a note the
  user has actually unpinned.
- **Channel-disabled detection** — detect `!areNotificationsEnabled()` or channel
  importance NONE and surface a prompt (banner on the list / at pin time) linking to
  notification settings, so pinning never fails silently (§7).

**Decisions to resolve at 5c:** which OEM autostart targets to attempt; how
aggressively re-assert-on-clear fires (immediate vs debounced); where the
channel-disabled prompt surfaces.

---

## Explicitly NOT in Phase 5
List querying — search/sort/filter (Phase 7); full silhouette icon set / colored
large icon, share, timestamps, multi-select (Phase 6). The deferred notification-text
refinements (pinned-note "Tap to edit or remove"; quick-add pin-on-create) remain
open and can be slotted where they fit.
