# Quick-note Popup — Title Auto-focus & Caret-at-end

A small editing-ergonomics tweak to the shared `NoteFields`, plus the field change that makes it work.

## Behaviour

- **Quick-note popup:** opening it to edit an *existing* note now auto-focuses the Title (keyboard up)
  with the caret **at the end** of the text, so you can type immediately. New/blank notes already
  auto-focused; that is unchanged.
- **Full editor:** unchanged — it auto-focuses the Title only when it is empty (a new note, or an
  expanded popup left blank). An existing note's title stays unfocused.

The difference is opt-in via a new `NoteFields(alwaysFocusTitle: Boolean = false)` parameter: the popup
passes `true`, the editor uses the default. The focus condition is `noteLoaded && (title.isBlank() ||
alwaysFocusTitle)`.

## Why the Title became a `TextFieldState`

The Title was a plain `String`-backed `OutlinedTextField`, which gives no control over the caret —
programmatic focus landed the cursor at the **start** of existing text. To place the caret at the end,
the field now owns a `TextFieldState` (the same pattern the Description field already uses):

- Seeded once on load via `setTextAndPlaceCursorAtEnd(title)` (caret at end; a `seeded` flag prevents a
  recomposition from resetting it).
- Edits mirror back through `onTitleChange` (`snapshotFlow { state.text }.drop(1)`), so the
  `EditorViewModel` still sees a plain `String` — no changes to the VM or any other caller. `drop(1)`
  avoids the pre-seed empty value clobbering a still-loading title.
- Uses the `OutlinedTextField(state = …)` overload with `lineLimits = SingleLine`; label, emoji leading
  icon, capitalization, and focus requester are preserved.

One-way seeding is safe because the only external write to the title is the save-time leading-emoji
promotion, which happens as the screen is leaving.

## Verification

`assembleDebug` + `testDebugUnitTest` green. Caret-at-end confirmed on device. The affected instrumented
tests (title input / popup / list / selection / back-nav / `NoteFields` rendering — 17 tests across 9
classes) all pass.
