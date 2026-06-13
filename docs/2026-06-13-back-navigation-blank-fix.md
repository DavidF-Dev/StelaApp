# Back-navigation blank-screen fix — implementation notes

> Status: **Implemented** · 2026-06-13 · 1.5.0 (unreleased).

## Symptom

Rapidly pressing system Back twice on a non-root screen (Editor, Settings, Archived, About) left the
app showing a blank surface that required a full restart. The status bar and gesture bar stayed, but the
navigation host rendered nothing.

## Root cause

A second Back press landing *during a pop's exit transition* triggers a second pop. The first pop has
not settled — the leaving destination is still composed — so the second press pops a *further*
destination, including the start destination, emptying the back stack. The host is then left with no
destination to draw while the activity stays alive: a blank screen.

## Why the first two attempts failed (the key lesson)

The guard must test the **lifecycle of the destination's `NavBackStackEntry`** — during an A→B
transition the Activity stays `RESUMED` but the leaving entry drops to `STOPPED`, so the *entry* is the
discriminator, the Activity is not.

The first attempts read the lifecycle from `LocalLifecycleOwner`, which **defaults to the Activity's
lifecycle** (always `RESUMED` while foregrounded). That made the guard a no-op — and worse, the
`BackHandler` still *consumed* the second press and then popped unconditionally, so it actively produced
the blank. This was proven on-device: the editor (whose `onDone` was guarded with the **passed** entry)
stopped blanking, while Settings (guarded via `LocalLifecycleOwner`) still blanked.

## Fix

1. **Passed-entry guard on every child destination.** Each `composable { entry -> }` lambda builds a
   single back action (`guardedPop`) that pops only while `entry.lifecycle` is `RESUMED`, and uses it for
   both the screen's Back affordance (`onBack`) and a `BackHandler` that intercepts system back. The
   editor keeps its existing passed-entry guard on `onDone` (which already covers its back arrow, Save,
   Delete, and system back). Archived's selection-mode `BackHandler` stays inside the screen; because it
   composes *after* the lambda's handler it naturally wins while selection is active.
2. **Removed the `LocalLifecycleOwner`-based `GuardedBackHandler`** — the footgun that caused attempt #2.
3. **Detect-and-recover safety net.** A `LaunchedEffect` observes `navController.currentBackStack`; if the
   host is ever left with no current destination while alive, it re-asserts the list. This is safe and
   unambiguous because the stack is never *legitimately* empty here — a Back at the root finishes the
   activity rather than emptying the stack — so "no destination while alive" always means an over-pop.
4. **Predictive back kept.** `android:enableOnBackInvokedCallback="true"` stays. It is orthogonal to the
   fix now; the editor working with it enabled confirms it is not causing an animation-glitch variant.

## Files

- `ui/StelaNavHost.kt` — `guardedPop` + `Lifecycle.isResumed()` helpers; per-destination guarded back for
  Settings/About/Archived (and the existing editor `onDone` guard); detect-and-recover `LaunchedEffect`.
- `ui/editor/EditorScreen.kt` — system back is a plain `BackHandler` calling the guarded `onDone`.
- `ui/settings/SettingsScreen.kt`, `ui/about/AboutScreen.kt` — no own back handler; receive the guarded
  `onBack` from the host.
- `ui/archived/ArchivedScreen.kt` — keeps only its selection-mode `BackHandler`.
- `ui/GuardedBackHandler.kt` — **deleted**.
- `AndroidManifest.xml` — `enableOnBackInvokedCallback` (unchanged from the previous pass).

## Testing

- **Manual (emulator):** rapid double/triple Back on Editor, Settings, Archived, About lands on the prior
  screen and never blanks; at the list, Back exits the app. Check both gesture and 3-button nav. (Note:
  `adb`'s `input keyevent` can't reproduce human double-tap timing — the editor/Settings parity is the
  real proof.)
- **Regression:** Archived selection-mode Back still clears the selection; the editor keyboard still
  dismisses on Back; the cold notification-launch editor still finishes the task on Back. Existing
  `EditorBackNavigationTest` covers the single guarded Back from a resumed editor.

## Risks / watch-items

- The detect-and-recover net only catches the *empty-stack* variant. The editor working with predictive
  back enabled indicates the failure mode is empty-stack, not a stuck predictive-back animation.
- Predictive back changes exit animations app-wide; the `QuickNoteActivity` `ModalBottomSheet` dismiss
  was sanity-checked under it.
