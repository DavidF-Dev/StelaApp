# Description scroll thumb — implementation plan

> Status: **Implemented** · 2026-06-13 · v1.5.3. Follows the description-sizing change
> ([the keyboard-insets doc's addendum](2026-06-13-editor-entry-and-keyboard-insets.md)), which capped the
> Description at a fixed 2–6 lines that scrolls within.

## Goal

Give the Description box a slim scroll-position indicator — a custom-drawn thumb on its right inner edge
that appears only when the content overflows the six-line cap. A thumb needs the field's scroll offset, and
the `String`-based `OutlinedTextField` hides its internal scroll, so the Description migrates to the
state-based `BasicTextField` (`TextFieldState` + an owned `ScrollState`). Done in the shared `NoteFields`,
so the full editor and the quick-note popup both get it. The Title is untouched (single-line).

Behaviour parity is the bar: 2–6 line sizing, caret-follow above the keyboard, new-note focus/keyboard
raise, sentence capitalization, and save / share / pin / Expand all reading the latest text.

## Why a `BasicTextField`, not the Material wrapper

There is no built-in scrollbar in Compose, and Material3's `OutlinedTextField(state = …)` does not expose a
`scrollState`. The state-based `BasicTextField` does (`scrollState` parameter), and it scrolls *that* state
to keep the cursor visible — so caret-follow is preserved and we get an observable offset for the thumb. We
re-create the outlined look with `OutlinedTextFieldDefaults.decorator(…)`. (Verified against the resolved
versions: material3 1.4.0, foundation 1.11.2.)

## Sync contract (the substantive part — not two-way binding)

The `EditorViewModel` stays the single source of truth for persistence (`description: String`,
`onDescriptionChange`). The field owns editing. Two one-directional links:

- **Seed once (VM → field).** The VM mutates `description` only in `init` (an existing note's async load, or
  a popup-Expand draft); every later change originates from this field. So the field seeds exactly once,
  when the loaded value is available, guarded by a saved `seeded` flag so a recreation (rotation) doesn't
  re-seed and jump the cursor. `noteLoaded` is the trigger — `true` immediately for a new note / draft,
  flipping `true` for an existing note once its row is read (at which point `description` already holds the
  stored text).
- **Propagate (field → VM).** A `snapshotFlow { state.text }` pushes every edit to `onDescriptionChange`, so
  save / share / pin / Expand see current text. The seed re-emits the same value once (idempotent — no loop,
  since it cannot re-seed).

This is correct precisely because the field is the sole runtime mutator of `description`. (Confirmed in the
ViewModel: nothing but `init` sets it.)

## The thumb

Drawn over the field with `Modifier.drawWithContent` (no node of its own):

- Shown only when `scrollState.maxValue > 0` (content overflows).
- Thumb height = `track² / contentHeight`, position = `value / maxValue` along the track; track is the field
  height inset top/bottom to sit within the text area rather than spilling onto the floating label/border.
- ~4 dp wide, `onSurfaceVariant` at low alpha, right-edge inset. The vertical inset is the one cosmetic value
  to tune on-device. Always-on-when-overflowing for now; an `isScrollInProgress` fade is optional polish.

## Files

- `NoteFields.kt` — replace the Description `OutlinedTextField` with a private `DescriptionField`
  (owns `TextFieldState`, scroll state, decoration, thumb) + a private `Modifier.drawDescriptionScrollThumb`.
  Title unchanged. Public `DESCRIPTION_FIELD_TEST_TAG` for tests. KDoc updated.
- `EditorScreen.kt` / `QuickNotePopup.kt` — **no change** (still pass `description` + `onDescriptionChange`).
- `DescriptionFieldTest.kt` (new, androidTest) — seed + edit-propagation over `EditorScreen`.
- This doc + an addendum line in the keyboard-insets doc.

## Testing

- **Instrumented:** new `DescriptionFieldTest` (seed shows stored text; typing propagates to
  `onDescriptionChange`). The existing create/popup/selection flow tests are regression for the save path;
  none type into the description, so no selector changes are needed.
- **Manual (emulator, docked keyboard):** type past six lines → thumb appears, tracks scroll, vanishes when
  the content fits again; caret stays above the keyboard; the popup shows the same. A static thumb is
  screenshot-verifiable.

## Risks / watch-items

- The sync, not the thumb, is the risk — seed-once + one-way-out is correct only while the field is the sole
  runtime mutator of `description` (verified).
- `decorator` parameter list drift across Material3 versions — pinned to 1.4.0 here.
- Thumb track inset is a cosmetic tuning pass.

## Out of scope

- The full-screen description editor remains an optional follow-up; this migration is the foundation it would
  build on, but nothing here depends on it.
