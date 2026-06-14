# First-run onboarding — implementation plan

> Status: **Implemented** · v1.6.0 (unreleased) · 2026-06-14. Built as the plan below. Part of the
> [post-v1.5 improvements queue](2026-06-14-post-v1.5-improvements.md). As-built notes at the end.

## Goal

A short, skippable first-launch flow that (a) explains the notes-as-notifications concept, (b) requests
the notification permission with context, and (c) nudges the battery-optimisation exemption that the
app's persistence promise quietly depends on. Today none of this is explained: `POST_NOTIFICATIONS` is
requested **silently** on first launch (`QuickAddOnboarding` in `MainActivity`), and the
battery/autostart guidance lives only in Settings, so a new user never learns why their pinned notes
might vanish after a reboot or a battery-saver kill.

## Scope — locked decisions

(Confirmed with the user 2026-06-14.)

- **Three panes**, a horizontal pager with page-indicator dots:
  1. **Welcome / concept** — what Stela is: plain notes pinned to the status bar, always a glance away;
     fully offline, no ads, no tracking, no internet.
  2. **Stay visible (notifications)** — pinning shows a note as a notification, so Stela needs
     permission to post them. A primary **Allow notifications** button triggers the runtime request
     (API 33+); the pane reflects the granted state once allowed.
  3. **Survive in the background (keep-alive)** — Android may kill the app or drop notifications after a
     reboot; exempting Stela from battery optimisation helps pinned notes persist and re-pin. A primary
     **battery** action (always shown) and a **secondary autostart** action shown only on a known
     aggressive OEM — both **optional, never block finishing**. A final **Get started** button completes
     onboarding.
- **Skippable** — a **Skip** affordance on every pane completes onboarding immediately. The battery
  step in particular is explain-plus-optional-button; it never gates completion.
- **Replayable** — a **Show intro again** row (Settings → About section) re-runs the flow. Backed by a
  persisted boolean (not a version int) — simple; revisit versioning if a future release wants to
  re-onboard for new features.
- **State** reflects reality on every run: the notifications pane shows "granted ✓" and the keep-alive
  pane shows "already exempt ✓" when those are already true — so a replay (or a partial first run) reads
  correctly rather than re-asking.

## State & gating

### The flag

Add a persisted boolean to the existing DataStore-backed settings (parallel to the other prefs):

- `Settings.onboardingComplete: Boolean = false` (`settings/Settings.kt`).
- `SettingsKeys.ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")` + map it in
  `settingsFromPreferences` (`settings/SettingsRepository.kt`).
- `SettingsRepository.setOnboardingComplete(value: Boolean)` + the DataStore implementation, and the
  test `FakeSettingsRepository`.

### Gating in `MainActivity`

The onboarding flow is shown **above the `NavHost`**, gated on `!onboardingComplete`, so it sits over
the whole app and needs no nav-graph route:

- **Avoid a first-frame flash.** `onboardingComplete` defaults `false`, so a returning user must not
  briefly see onboarding before DataStore emits. Collect settings as **nullable** (`initialValue =
  null`) and keep the splash screen up until the first emission:
  `installSplashScreen().setKeepOnScreenCondition { settings == null }` (bridged to compose via a
  `mutableStateOf` set on first non-null value). The splash already covers cold start, so this is free.
- Once loaded: `if (!settings.onboardingComplete) OnboardingScreen(onComplete = ...) else StelaNavHost(...)`,
  both inside the existing `StelaTheme` / `Surface`. `onComplete` calls
  `settingsRepository.setOnboardingComplete(true)`; the recomposition swaps onboarding for the nav host.

### Reconcile the existing silent permission request

`QuickAddOnboarding` (in `MainActivity`) currently auto-requests `POST_NOTIFICATIONS` on first launch.
Onboarding's notifications pane now owns that request **with context**, so:

- **Remove `QuickAddOnboarding`**; the onboarding flow performs the request (via the same
  `ActivityResultContracts.RequestPermission` mechanism) and, on grant, calls
  `notePinner.reconcileService()` so the quick-add notification appears (preserving today's behaviour).
- Later quick-add re-enables from Settings are unaffected — they already request via
  `rememberNotificationPermissionGate`.
- API < 33: no runtime permission exists; the pane is informational (notifications are on by default),
  and the button is hidden or shown as already-enabled per `canPostNotifications`.

## UI

New package `ui/onboarding/`:

- `OnboardingScreen.kt` — a `HorizontalPager` (Compose Foundation) of the three panes, page dots, a
  top-end **Skip**, and per-pane primary actions. Themed by the surrounding `StelaTheme`; uses the
  brand colour for the header/illustration, consistent with the splash.
- Each pane is a simple icon + title + body + (optional) action button. Static copy from string
  resources (localisation convention — all UI strings externalised, see below).
- **Actions reuse existing seams**, no new platform code:
  - Notifications: the permission launcher + `canPostNotifications(context)` for state.
  - Battery & autostart: **reuse the same `OemGuidanceDialog` Settings uses** (manual steps + the
    honest "may not work on every device" caveat + a best-effort "Open" button), rather than firing the
    raw system intents. The raw intents are unreliable on some OEMs (the battery screen can even crash
    OxygenOS Settings), which is exactly why the guidance dialogs exist; onboarding must not reintroduce
    that risk. Battery state from `isIgnoringBatteryOptimizations(context)`; autostart row shown only
    when `DeviceResilience.autostartIntent() != null` (a known aggressive OEM — stock Android and
    Samsung get none). This requires hoisting `OemGuidanceDialog` out of `SettingsScreen.kt` (see
    Files touched).
- **Back navigation:** system back moves to the previous pane; on the first pane it finishes the
  activity **without** setting the flag (onboarding shows again next launch).

## "Show intro again"

A row in the Settings **About** section ("Show intro again") that calls
`SettingsViewModel.replayOnboarding()` → `setOnboardingComplete(false)`. Because the gate wraps all of
`MainActivity`'s content, flipping the flag while the user is in Settings recomposes the host and shows
onboarding over everything; finishing it returns them to the app. (About screen was considered as the
home for this row; Settings is chosen for discoverability and because the view-model is already there.)

## Strings & localisation

Add onboarding copy to `res/values/strings.xml` (titles, bodies, button labels, Skip, Get started, the
"Show intro again" row). Keep the persistence wording honest ("helps notes survive…", not "guarantees").

## Files touched

- `settings/Settings.kt`, `settings/SettingsRepository.kt` — the flag (field, key, mapping, setter).
- `test/.../settings/FakeSettingsRepository.kt` — support the new setter/field.
- `MainActivity.kt` — splash keep-condition; nullable settings; gate onboarding vs nav host; remove
  `QuickAddOnboarding` and move the contextual permission request + `reconcileService` into onboarding.
- `ui/onboarding/OnboardingScreen.kt` *(new)* — the pager and panes.
- `ui/resilience/OemGuidanceDialog.kt` *(new)* — hoist the `OemGuidanceDialog` composable out of
  `SettingsScreen.kt` so both Settings and onboarding share it (copy/caveat in one place).
- `ui/settings/SettingsScreen.kt` — use the hoisted dialog; add the "Show intro again" row.
- `ui/settings/SettingsViewModel.kt` — `replayOnboarding()` → `setOnboardingComplete(false)`.
- `res/values/strings.xml` — onboarding strings.

## Testing

- **Unit (JVM):** `settingsFromPreferences` maps `onboarding_complete` (present true/false, absent →
  default false); `FakeSettingsRepository` honours `setOnboardingComplete`.
- **Instrumented — new `OnboardingFlowTest`:** set `onboardingComplete = false` via the container's
  `SettingsRepository` (runBlocking) **before** launching `MainActivity` (use `createEmptyComposeRule`
  + manual launch so the flag is set first); assert the welcome pane shows, walk/skip through, then
  assert the note list (FAB) appears and the flag is now true. Grant `POST_NOTIFICATIONS` in
  `@BeforeClass` so the permission pane doesn't pop a system dialog mid-test.
- **Test-harness impact (important):** existing instrumented tests launch `MainActivity` and expect the
  list immediately. With onboarding gating the UI and the flag defaulting `false`, a fresh test install
  would show onboarding instead. Add a shared helper `markOnboardingComplete(context)` (writes the flag
  via the app container's `SettingsRepository`) and call it from `@BeforeClass` in the MainActivity-
  launching UI tests — `CreateNoteFlowTest`, `PinFromListTest`, `SelectionFlowTest`, `ListQueryFlowTest`,
  `EditorBackNavigationTest`, `AboutFlowTest`, `NoteListBannerTest`, `QuickNotePopupTest`,
  `ShareToStelaTest`, and any other that drives the main UI. `@BeforeClass` runs before the per-test
  rule launches the activity, matching the existing `grantNotificationPermission` pattern. (The popup/
  shortcut/DAO/codec tests that don't show the main UI are unaffected.)
- **Manual:** fresh install (clear app data) → onboarding appears; allow notifications; open battery
  settings; Get started → list. Replay via Settings → About → Show intro again. Verify no flash for a
  returning user (relaunch lands straight on the list under the splash).

## Edge cases / decisions

- **Pre-onboarding share / deep link:** an incoming share or notification deep link before onboarding
  is complete still shows onboarding first (the gate wraps everything). Acceptable and unlikely on a
  first run; not special-cased in v1.
- **Returning user, permission later revoked:** onboarding does **not** re-trigger (the flag is set);
  the existing in-app banner (`arePinnedNotificationsBlocked` → `NotificationsBlockedBanner`) already
  surfaces that case on the list.
- **Battery screen unreliable on some OEMs:** same honest caveat as Settings — the button is
  best-effort; copy doesn't promise it works everywhere.

## Resolved (was: open questions)

- **Illustrations vs icons** *(resolved 2026-06-14)*: v1 uses simple **Material icons + the brand
  colour** per pane (cheap, on-brand). Custom illustrations are deferred polish.
- **Pane 3 autostart depth** *(resolved 2026-06-14)*: a **conditional, secondary** autostart action
  shown only when `autostartIntent() != null`, opening the **shared `OemGuidanceDialog`** (same as
  Settings) — not full inline steps, and not omitted. Battery stays the primary keep-alive action.

## As-built notes

Built as planned, with one important correction found in device/test verification:

- **The splash keep-condition deadlocked the Compose test clock — removed.** The plan gated the gate by
  holding the splash (`setKeepOnScreenCondition { !settingsLoaded.value }`) until settings loaded. This
  works perfectly by hand (the real Choreographer pumps frames, the flag flips, the splash dismisses),
  but in an instrumented test it **hangs on the static splash colour**: `createAndroidComposeRule` blocks
  every action until the app is idle, while the pre-draw listener refuses to draw until the flag flips —
  and the flip needs a recomposition the awaiting test clock never pumps. A circular wait. **Fix:** drop
  the keep-condition (and the `settingsLoaded`/`SideEffect` machinery) entirely; while the nullable
  settings are still loading, draw a brand-indigo placeholder `Box` (matching the splash colour) in
  Compose, then swap to onboarding/list. The first frame always draws (idle-friendly), there's still no
  onboarding flash for returning users, and `installSplashScreen()` stays for the cold-start splash
  (auto-dismissing on first draw). Lesson: **never gate the splash on Compose state read by a pre-draw
  condition if the screen is exercised by Compose UI tests.**
- **Files:** `Settings`/`SettingsRepository` (flag), `OnboardingScreen.kt` (new), `OemGuidanceDialog.kt`
  (hoisted from Settings), `MainActivity` (gate + placeholder; removed `QuickAddOnboarding`),
  `SettingsScreen`/`SettingsViewModel` (Show intro again), strings.
- **Test-harness impact (as planned):** a shared `markOnboardingComplete()` helper, called from
  `@BeforeClass` in the nine `MainActivity`-launching UI tests, plus the new `OnboardingFlowTest`.
- **Verified:** unit tests, full instrumented suite (52 tests, 0 failed), lint clean; manual: first launch
  shows onboarding, relaunch goes straight to the list (no flash, no stuck splash).
