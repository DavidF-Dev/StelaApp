# Expand → Editor: stray Description focus on reused window

> Status: **Implemented** · 2026-06-14 · 1.5.0 (unreleased).

## Symptom

Expanding the quick-note popup into the full editor (overflow → "Open in full editor") sometimes opened
the editor with the **Description focused and the keyboard up**, even though the editor never auto-focuses
an existing note. It was intermittent and only reproduced after the editor had been used earlier in the
session: focus the Description in the editor, leave the app, then Edit that note from its notification and
Expand — the editor reappeared with the Description focused.

## Root cause

Expand is the only path that crosses an **Activity boundary**: the popup (`QuickNoteActivity`) finishes and
brings `MainActivity` forward. `MainActivity` is `singleTop` and, in this scenario, still alive on that same
note's editor. The deep link lands back on the already-open editor (see `isEditorAlreadyOpenFor`), so the
editor composition is **reused** — and the window **restores focus to its last-focused field** as it
regains focus. A restored focus on a text field re-raises the keyboard (both activities are `adjustResize`).

Why the editor's own auto-focus rule didn't counteract it: the title auto-focus runs in
`LaunchedEffect(noteLoaded)`, which only fires when `noteLoaded` **changes**. With the composition reused,
the note is already loaded, so `noteLoaded` never transitions and the effect's clear-or-focus body never
runs. (A new-note Expand uses the `/new` destination — a fresh composition where `noteLoaded` starts true —
so its title auto-focus does run and assertively claims focus, which is why that case was never affected.)

The restore is **early and beatable**: a post-composition `requestFocus()` overrides it (new note), and a
post-restore `clearFocus()` would too — the problem was purely that nothing ran at the right time on the
reused-composition path.

## Fix

Two layers, both small:

1. **`MainActivity` clears focus on the focus-gain that triggers the restore (the real fix).** A one-shot
   flag `clearFocusOnWindowFocusGain` is set in `onCreate`/`onNewIntent` via `isExpandOfExistingNote(intent)`
   — true only for the from-expand marker **and** an `/editor/{id}` path (a new note's `/new` is excluded so
   it keeps auto-focusing its title). `onWindowFocusChanged(hasFocus = true)` consumes the flag and, via
   `decorView.post { … }` (deferred past the framework's own restore on that same focus gain), calls
   `currentFocus?.clearFocus()` and hides the IME. The `post` is what wins the timing.

2. **Shared `NoteFields` makes the auto-focus symmetric (defensive).** The `LaunchedEffect(noteLoaded)` now
   focuses the title + shows the keyboard for a blank title / `alwaysFocusTitle`, and **otherwise actively
   clears focus and hides the keyboard**. This handles the cases where `noteLoaded` does transition; the
   popup is unaffected because it always passes `alwaysFocusTitle = true` (always the focus branch).

The two compose cleanly: layer 1 covers the reused-composition Expand path (where layer 2's effect never
re-fires); layer 2 covers fresh-composition opens.

## Testing

Build only is automated; window-focus/IME behaviour is not reliably instrumentable (as with the earlier
editor-keyboard-insets and overflow-keyboard fixes). Verified manually on the emulator:

- **Edit note → Expand** (after focusing the Description in the editor and leaving the app): editor opens
  with **nothing focused, keyboard down**. (Was: Description focused.)
- **New note → Expand**: still opens with the **Title focused** (layer 1 deliberately skips `/new`).
- Unchanged: list → New note (Title focused), list → Edit note (nothing focused), notification New note
  (popup, Title focused), notification Edit note (popup, Title focused, caret at end).
