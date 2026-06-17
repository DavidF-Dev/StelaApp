# Haptics on pin toggle — implementation plan

> Status: **Implemented** · v1.6.0 · 2026-06-14. Built as the plan below. The last item in
> the [post-v1.5 improvements queue](2026-06-14-post-v1.5-improvements.md). As-built notes at the end.

## Goal

A light haptic tick when the user toggles a note's pin, so pinning — the app's signature action — feels
tactile. Pure UI feedback: no permission, no setting.

## Scope — decisions (recommended; flag if you'd change any)

- **Two call sites:** the **list row** pin button (`NoteRow`) and the shared **editor / popup** pin
  button (`NoteEditorActions`, used by both the full editor and the quick-note popup).
- **Distinct on/off feedback:** `HapticFeedbackType.ToggleOn` when pinning, `ToggleOff` when unpinning —
  semantically correct and free. (If those constants aren't in the pinned Compose version, fall back to a
  single `HapticFeedbackType.LongPress` tick for both — to confirm at build time.)
- **Fires on the tap**, based on the *current* pin state, before any permission gate resolves — standard
  button feedback. (Pinning from the list/editor is gated by `POST_NOTIFICATIONS`; the tick still plays
  on tap even if the grant is later denied. Acceptable and conventional.)
- **Batch (multi-select) pin is excluded** for now — it's a bulk action, not the per-note toggle the
  stub described. Easy follow-up if wanted (one tick on `onBatchTogglePin`).
- **No manifest / permission change.** Compose haptics go through the view's `performHapticFeedback`,
  which needs no `VIBRATE` permission and already respects the system's touch-feedback setting (so it
  self-disables when the user has haptics off — no extra guard, mirroring how the pin-pop animation
  respects the animation-scale setting).

## Design

A tiny shared helper centralises the on/off type choice so both surfaces stay consistent:

```kotlin
// ui/Haptics.kt
fun HapticFeedback.performPinToggle(currentlyPinned: Boolean) =
    performHapticFeedback(if (currentlyPinned) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
```

Call sites:

- **`NoteRow`** (`NoteListScreen.kt`): grab `val haptic = LocalHapticFeedback.current`, and wrap the pin
  `IconButton`'s click:
  ```kotlin
  IconButton(onClick = { haptic.performPinToggle(note.isPinned); onTogglePin() }) { … }
  ```
- **`NoteEditorActions`** (`NoteFields.kt`): same, on the pin `TooltipIconButton`, keyed on
  `state.isPinned`:
  ```kotlin
  onClick = { haptic.performPinToggle(state.isPinned); onTogglePin() }
  ```

Nothing else changes — the existing `onTogglePin` lambdas (with their permission gating) are untouched.

## Files touched

- `ui/Haptics.kt` *(new)* — the `performPinToggle` helper.
- `ui/notelist/NoteListScreen.kt` — haptic on the row pin button.
- `ui/editor/NoteFields.kt` — haptic on the shared editor/popup pin button.

## Testing

Compose's `LocalHapticFeedback` is overridable, so a component test can inject a recording fake and
assert the right tick fires:

```kotlin
class RecordingHapticFeedback : HapticFeedback {
    val performed = mutableListOf<HapticFeedbackType>()
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { performed += hapticFeedbackType }
}
```

- **List row (`createComposeRule`, render `NoteListScreen`):** provide the fake via
  `CompositionLocalProvider(LocalHapticFeedback provides fake)`, click the "Pin" button on an unpinned
  note, assert `fake.performed` contains `ToggleOn`; for a pinned note, `ToggleOff`.
- **Editor (`createComposeRule`, render `EditorScreen`):** same, clicking the pin action — `ToggleOn`
  for an unpinned note's state.

(These assert the *intent* — that the correct haptic type is requested — which is the testable part; the
physical buzz isn't observable.)

## Edge cases

- **System haptics off:** `performHapticFeedback` is a no-op then — nothing to handle.
- **Compose version lacks `ToggleOn`/`ToggleOff`:** fall back to `LongPress` in the helper (single tick,
  no on/off distinction). Confirm which path applies when building.

## As-built notes

Built exactly as planned. `ToggleOn`/`ToggleOff` are present in the project's Compose version, so the
`performPinToggle` helper (`ui/Haptics.kt`) uses them — no fallback needed. Wired into `NoteRow`'s pin
`IconButton` and `NoteEditorActions`' pin `TooltipIconButton` (covering the full editor and the popup),
each firing before the existing `onTogglePin`. Batch pin left untouched, as scoped. **Tests:**
`PinHapticsTest` (3 component cases via an injected recording `HapticFeedback` — list-row pin → ToggleOn,
list-row unpin → ToggleOff, editor pin → ToggleOn). **Verified:** unit tests, full instrumented suite
(62 tests, 0 failed), lint clean — after a cold emulator reboot cleared transient flakiness (a degraded
emulator had produced inconsistent "compose busy" / touch-injection failures in unrelated selection
tests; all green on a fresh boot).
