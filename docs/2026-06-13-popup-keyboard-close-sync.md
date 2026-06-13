# Popup Keyboard Close Sync — Investigation & Plan

**Problem:** When the quick-note popup closes with the keyboard open, the popup's hide animation
plays and the keyboard's hide animation trails *after* it — they are not in sync. The full editor
does not have this problem.

**Decision:** The first attempt (gate `finish()` on the IME animation) was abandoned after
investigation showed the cause is structural, not a missing API call. The working tree has been
reverted to the committed baseline. The genuine fix is **Option A: re-host the popup content in the
activity's own window** instead of a Material3 `ModalBottomSheet`. This plan covers that.

> **Git:** commits are left to the user per the repo's git policy.

---

## What we learned (root cause)

The full editor closes the keyboard in sync because it lives in **one normal, full-screen activity
window** (`MainActivity`, edge-to-edge): `focusManager.clearFocus()` + `keyboardController.hide()`
work, and `WindowInsets.ime` animates, so the keyboard retracts in place over the destination.

The popup is a Material3 `ModalBottomSheet` hosted in a **separate, translucent** activity
(`QuickNoteActivity`). On-device tracing (Pixel_8) established:

- **The IME inset never animates in the sheet's composition.** Every close trace logged
  `WindowInsets.ime.getBottom()` stuck at its full value (e.g. 883) until window teardown, then
  TIMEOUT — so no `snapshotFlow`/inset-observation gate can ever fire.
- **No in-composition call hides the keyboard while the sheet is alive.** We tried, and screenshotted
  the result of, all of: `clearFocus()`, `keyboardController.hide()`,
  `WindowInsetsControllerCompat.hide(ime())`, and `InputMethodManager.hideSoftInputFromWindow()`.
  The keyboard stayed fully up; it only retracted when the activity `finish()`ed (window teardown),
  at which point the *system* animates it away over the revealed app — that is the trailing keyboard.
- **`clearFocus(force = true)` *does* hide the keyboard — but it cancels the sheet's hide animation.**
  Changing the IME inset makes `ModalBottomSheet` recalculate its anchors and cancel the in-flight
  `sheetState.hide()`, so the sheet snaps away (close fired ~16 ms after the tap in the trace) and the
  keyboard is orphaned again. So "force-hide + animate the sheet" are mutually exclusive here.

**Why:** Material3's `ModalBottomSheet` renders in its **own window** (a Popup/Dialog-type window),
and that window — compounded by the host activity being `windowIsTranslucent` — is not wired for
animated IME insets or reliable programmatic IME control. This is a widely reported, structural
limitation, not a bug in our code.

### Corroborating research

- The classic View-world version of this bug (a bottom-sheet **dialog** fighting the keyboard) is
  consistently traced to the dialog window being `windowIsFloating`, which breaks
  `SOFT_INPUT_ADJUST_RESIZE` / IME inset handling.
- `clearFocus()` is known-unreliable inside a sheet; the common workaround is to move focus to a tiny
  dummy focusable — matching our finding that only the forced clear worked.
- There is **no** established recipe for a keyboard-in-sync *animated dismissal* out of a separate
  sheet window. Teams who needed it reliable moved the sheet **into the same window** (Compose
  Unstyled / Composables, the `ModalSheet` library's `FullScreenPopup`, foreground-bottomsheet).

Sources:
[Reylar (Medium)](https://medium.com/@reylaaar/fixing-modalbottomsheet-keyboard-ui-issues-in-jetpack-compose-material-3-d984c6577a6b),
[Composables — bottom sheets that just work](https://composables.com/blog/bottom-sheets-that-just-work),
[BottomSheetDialogFragment keyboard (gist)](https://gist.github.com/thoinv/dcd66d85b358ed05b48380e9228ef9e4),
[nphausg](https://nphausg.medium.com/android-bottomsheetfragment-keyboard-problem-790c2d3e8621),
[ModalSheet library](https://github.com/oleksandrbalan/modalsheet),
[Android — handle IME visibility](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility).

### Reference behaviour

StatusNote (the app this is modelled on) closes its popup **instantly** — the popup and keyboard
vanish together with no animation. That is the "nothing animates, so nothing can desync" case, and is
a valid fallback (see Task 4).

---

## Decision: Option A — re-host the popup in the activity's own window

Replace the Material3 `ModalBottomSheet` with our own bottom-anchored card + scrim + slide animation
drawn directly in `QuickNoteActivity`'s single Compose window. In a normal (non-dialog) window the
editor's keyboard handling works verbatim, so the close can hide the keyboard and slide the card away
**in the same window**, in sync — no separate window to tear down, no anchor-cancellation.

What `ModalBottomSheet` currently provides and we must replicate (most already customised):

| Provided by ModalBottomSheet | Replacement |
|---|---|
| Scrim + tap-to-dismiss | Our own full-size scrim `Box`, `clickable` to dismiss (animate alpha) |
| Bottom-anchored rounded surface | Bottom-aligned `Surface` (rounded top corners) in a `Box` |
| Enter/exit slide animation | `AnimatedVisibility` / `MutableTransitionState` with `slideIn/OutVertically` |
| Drag (already disabled) | nothing (it's a plain `Box` now) |
| System back / predictive back | `BackHandler { dismiss() }` (app already opts into predictive back) |
| Keyboard lift | `Modifier.imePadding()` (+ `navigationBarsPadding()`) on the card |

### The key risk to de-risk FIRST

`QuickNoteActivity` is **translucent** (`windowIsTranslucent`, required so the popup floats over other
apps). Translucency is itself cited as a cause of broken IME inset/control. So before the full
refactor we must confirm that the activity's *own* window — translucent + `enableEdgeToEdge()` —
gives working `keyboardController.hide()` and animated `WindowInsets.ime`. If it does, Option A works.
If translucency still breaks it, Option A as-is won't fix the sync and we fall back to Task 4
(instant close, matching StatusNote).

---

## Task 0: Spike — verify IME control in the activity's own translucent window — DONE ✅

**Files:** `app/src/main/java/dev/davidfdev/stela/ui/quicknote/QuickNoteActivity.kt` (temporary, reverted).

- [x] **Step 1:** Added `enableEdgeToEdge()` in `QuickNoteActivity.onCreate`.
- [x] **Step 2:** Temporarily replaced `QuickNotePopup` with a throwaway bottom-aligned `Column`
  (`imePadding()`) holding a focused `TextField` (auto-shown keyboard) + a "Hide KB" button running
  `focusManager.clearFocus(); keyboardController.hide()`, logging `WindowInsets.ime.getBottom()` via
  `snapshotFlow`.
- [x] **Step 3:** On device (Pixel_8), tapping the button drove the inset smoothly to zero:
  `883 → 844 → 502 → 373 → 282 → 219 → 166 → 125 → 94 → 68 → 47 → 32 → 19 → 10 → 5 → 1 → 0`
  over ~300 ms, and the screen stayed (keyboard hidden **in-window**, content settled to the bottom).
- [x] **Decision gate: PASSED.** In the activity's own (translucent, edge-to-edge) window,
  `clearFocus()`/`keyboardController.hide()` both hide the keyboard *and* the IME inset animates and is
  observable — the exact things that failed in the `ModalBottomSheet` window. The root cause was the
  separate sheet/dialog window, **not** the translucency. Option A is viable → proceed to Task 1.
- [x] **Step 4:** Reverted the throwaway screen. `enableEdgeToEdge()` is folded into Task 1 (the tree
  is left at the clean baseline during the pause).

---

## Task 1: Replace ModalBottomSheet with own-window scrim + sheet — DONE ✅

**Files:** `QuickNoteActivity.kt` (add `enableEdgeToEdge()`), `QuickNotePopup.kt`.

- [x] **Step 0:** Added `enableEdgeToEdge()` in `QuickNoteActivity.onCreate`.
- [x] **Step 1:** Enter/exit driven by `remember { MutableTransitionState(false) }` set to `true` on
  first composition.
- [x] **Step 2:** Replaced the `ModalBottomSheet { ... }` body with a root `Box(fillMaxSize)` + a
  no-ripple `clickable` scrim `Box` (`scrim.copy(alpha = 0.32f)`, `AnimatedVisibility` fade) + a
  bottom-aligned `AnimatedVisibility` (`slideIn/OutVertically`, 250 ms tween) wrapping a `Surface`
  (rounded top, `tonalElevation = 1.dp`) that holds the existing action `Row` + `NoteFields`. Bottom
  inset via `windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))`; the card has a
  no-op `clickable` so taps don't fall through to the scrim.
- [x] **Step 3:** `NoteFields`, `NoteEditorActions`, dialogs, and snackbar host unchanged.
- [x] **Step 4:** `assembleDebug` + `testDebugUnitTest` green.

---

## Task 2: Wire dismissal (keyboard-synced) and back handling — DONE ✅

**Files:** `app/src/main/java/dev/davidfdev/stela/ui/quicknote/QuickNotePopup.kt`.

- [x] **Step 1:** `dismiss()` = `dismissKeyboard()` (`clearFocus()` + `keyboardController.hide()`, which
  works in this window) then `visibleState.targetState = false`. Guarded by a `closing` flag.
- [x] **Step 2:** A `LaunchedEffect(currentState, isIdle)` calls `onFinished()` once
  `closing && isIdle && !currentState` (exit slide complete).
- [x] **Step 3:** Back arrow, scrim tap, `BackHandler`, and `viewModel.save/delete/archive` callbacks
  all route through `dismiss()`. `onExpand` keeps its hide + navigate (hands off to `MainActivity`).
- [x] **Step 4:** `assembleDebug` green.

---

## Task 3: Verify on device — DONE ✅

Verified on the Pixel_8 emulator, popup launched over the home screen via `NewNoteShortcutActivity`.

- [x] **Sync proven by IME-inset trace (not screenshots).** Temporarily logged `WindowInsets.ime` over a
  close: it animated smoothly **883 → 0 over ~334 ms in-window**, and `FINISH` fired at **ime = 0** —
  i.e. the keyboard fully retracts alongside the card's slide and the window is torn down only after.
  This is the exact behaviour that was impossible in the `ModalBottomSheet` window (inset stuck at 883
  until teardown). Instrumentation removed afterwards.
- [x] **Dismiss paths all finish cleanly** (resumed activity → launcher): scrim tap, back arrow, and
  Save (typed a title → note created + pinned + popup closed).
- [x] **Open still renders correctly** (rounded-top card, focused title, keyboard up — unchanged look).
- [ ] **Remaining for a human:** watch the close on a real device to confirm it *looks* in sync, and
  spot-check the existing-note edit path (open via a pinned note's notification — not easily driven by
  `adb`). Per the project's known limitation, the sub-second animation can't be judged from static
  `adb` screencaps, but the inset trace is strong objective evidence it is synced.

---

## Task 4: Fallback — instant close (only if Task 0 fails)

If a translucent window can't deliver synced animation, match StatusNote: on dismiss, hide the
keyboard and `finish()` immediately with no exit animation, so the popup and keyboard vanish together.
This is a few lines and removes the desync by removing the animation.

---

## Status

- **Investigation:** complete (root cause = separate/translucent sheet window; confirmed on device and
  by research).
- **Revert:** done — `QuickNotePopup.kt` restored to committed baseline; `ui/ImeAnimation.kt` removed.
  Tree builds clean.
- **Task 0 spike:** DONE ✅ — decision gate PASSED. The activity's own translucent+edge-to-edge window
  hides the keyboard with an animated, observable IME inset (`883 → 0` over ~300 ms). Root cause
  confirmed as the separate sheet window; Option A is viable. Spike code reverted; tree at clean
  baseline.
- **Option A (Tasks 1–3):** DONE ✅ — `ModalBottomSheet` replaced with an own-window scrim + bottom
  card; dismiss hides the keyboard and slides the card away together, finishing only after both. On
  device the IME inset animates `883 → 0` in-window and `finish()` fires at `ime = 0` (synced, no
  orphaned keyboard). `assembleDebug` + `testDebugUnitTest` green; instrumentation removed.
- **Open behaviour (Problem A):** unchanged by design — the keyboard still appears a beat after the
  card on open, as in the reference app.
- **Outstanding:** human eyeball confirmation on a real device; commits left to the user.
