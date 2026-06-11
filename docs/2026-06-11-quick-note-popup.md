# Quick-note popup (minimal editor over the screen) — implementation plan

> Status: **Planned / deferred** · 2026-06-11 · its own slice (likely v1.4.0). Spec only — not implemented.
>
> Recommendation: **cut v1.3.0 first**, then build this. It introduces a new editing *surface* and entry
> point that must stay behaviour-consistent with the full editor; it deserves its own focused slice.

## Goal

A lightweight **bottom-sheet popup** that floats over whatever is on screen (home, another app) for
quickly creating — or quickly editing — a note, without bringing the full app forward. Minimal by
design: **emoji · title · description · save**, plus an **Expand** button into the full editor. No pin
toggle, archive, delete, share, or timestamps (those live in the full editor).

## Scope — locked decisions

- **Pins on save** (new notes) — preserves the quick-add "jot and pin from the tray" contract. No pin
  toggle is shown; saving a new note pins it.
- **Bottom-sheet** presentation (not a centred dialog) — more room, and it pairs naturally with the
  emoji bottom sheet.
- **Triggers that open the popup:** the quick-add notification's **New note** action, the quick-add
  notification **body tap**, and the home-screen widget **＋** (all → new note); plus the pinned-note
  notification's **Edit** action (→ that existing note).
- **Works for new *and* existing notes**, staying minimal in both. The **Expand** button is shown in
  both cases.
- **Expand** opens the full editor carrying across the in-progress fields (title/description/emoji); if
  the title is already filled, the full editor does **not** auto-focus it.

## Why it's a new Activity

UI can't be drawn "over the screen" from a notification without an Activity (the overlay-permission
route is the wrong tool). So: a dedicated **`QuickNoteActivity`** — a **transparent / no-display-on-
launch** themed **`AppCompatActivity`** (AppCompat is required by the emoji picker's `BottomSheetDialog`,
same as `MainActivity`) — hosting a Compose **`ModalBottomSheet`**. The sheet's scrim dims the screen
behind; dismissing it (scrim tap / drag / back) finishes the Activity. The notification/widget
PendingIntents target this Activity instead of `MainActivity`.

## Code sharing (the maintenance goal)

The popup and the full editor must share their core so editing logic lives in one place:

- **`EditorViewModel` — shared as-is.** It already holds title/description/emoji/pin/archive + `save`,
  keyed by `NOTE_ID_KEY`/`PIN_KEY` via `SavedStateHandle`. The popup creates an instance scoped to
  `QuickNoteActivity`, seeded from intent extras; it simply uses a *subset* of the API
  (`onTitleChange`/`onDescriptionChange`/`onEmojiChange`/`save`). No second view-model.
- **Extract `NoteFields`** — the emoji-leading **Title** field + **Description** field (the two
  `OutlinedTextField`s and the auto-focus behaviour) into a shared composable used by both
  `EditorScreen` and the popup.
- **Extract `EmojiPickerBottomSheet`** — currently private in `EditorScreen`; move to a shared
  (internal) location so both surfaces invoke the same picker.
- **Save/create logic** is already centralised in `NoteRepository`/`NotePinner` — both surfaces reuse
  `EditorViewModel.save`, which creates + pins (gated on notification permission).

Net: the popup (`QuickNotePopup`) and `EditorScreen` become thin shells over the shared
`EditorViewModel` + `NoteFields` + `EmojiPickerBottomSheet`.

## The Expand hand-off

"Expand" passes the *unsaved* in-progress edit to the full editor. To avoid encoding long/odd text into
nav-route args, use a small **process-scoped draft** held in `AppContainer`:

- `NoteDraft(noteId: Long?, title, description, emoji, pinOnSave: Boolean)` + a `var pendingDraft` (or a
  one-shot holder) on `AppContainer`.
- **Popup → Expand:** write `pendingDraft` from the current fields, launch `MainActivity` routed to the
  editor, and finish the popup.
- **`EditorViewModel.init`:** if a `pendingDraft` is present, seed `_uiState` from it (and, for an
  existing note, load the DB note first then **override** the edited fields from the draft), then clear
  the draft. New note → `noteId = null`, `pinOnSave` seeds the pin intent (new notes already default to
  pinned). Existing note → `noteId` set, draft overlays the unsaved edits.

This carries content cleanly for both new and existing notes, with no persistence until the user saves.

## Auto-focus rule (generalised, shared)

Replace the editor's current `!isEditing` auto-focus condition with: **focus the title (and raise the
keyboard) iff the note is loaded and the title is blank.** This single rule covers every case:

- new note, empty title → focus;
- expanded/prefilled title ("Foo") → no focus;
- existing note (title already set) → no focus.

Gate on the existing `noteLoaded` signal so an existing note's async load doesn't momentarily look
"blank". This lives in the shared `NoteFields`, so popup and editor behave identically.

## Wiring (PendingIntents)

| Trigger | Today | After |
|---------|-------|-------|
| Quick-add **New note** action | `MainActivity` `/new?pin=true` | `QuickNoteActivity` (new, pin) |
| Quick-add **body tap** | `MainActivity` `/new?pin=true` | `QuickNoteActivity` (new, pin) |
| Widget **＋** | `MainActivity` `/new?pin=true` | `QuickNoteActivity` (new, pin) |
| Pinned-note **Edit** action | `MainActivity` `/editor/{id}` | `QuickNoteActivity` (note id) |
| Pinned-note **body tap** | `MainActivity` `/editor/{id}` | *(open question — see below)* |
| **Expand** (from popup) | — | `MainActivity` full editor, from draft |

`AndroidNotificationController` and `StelaWidget` build the new intents; `MainActivity` gains a path to
open the editor from a `pendingDraft`.

## Invariants honoured

- **No `INTERNET`.** Local only.
- **Pin-on-save** routes through `NotePinner.pin`, gated on `POST_NOTIFICATIONS` like the editor.
- A note is never both archived and pinned (the popup never archives; pinning is unchanged).
- Dismissing the popup discards unsaved content (same as the editor's back = no save).

## Open questions / decisions to settle before building

1. **Pinned-note body tap:** keep it → full editor (so "Edit action = quick popup", "body tap = open
   fully"), or also → popup for consistency? They do the same thing today. *Recommendation: also → popup*
   (consistent; Expand still reaches the full editor), but worth confirming.
2. **Lock screen:** suppress the popup over a secure lock screen, or allow it for a brand-new empty note?
3. **Emoji picker layering** in a transparent activity (bottom sheet over the popup sheet) — verify it
   renders/scrolls correctly from `QuickNoteActivity`.
4. **VM seeding from intent extras:** confirm `createSavedStateHandle()` in `QuickNoteActivity` picks up
   `noteId`/`pin` from the intent (may need explicit `defaultViewModelCreationExtras`/args wiring).

## Build order (phased)

1. **Extract shared UI** — `NoteFields` + `EmojiPickerBottomSheet` out of `EditorScreen`; switch the
   editor to use them (no behaviour change). Generalise auto-focus to "title blank when loaded".
2. **Draft hand-off** — `NoteDraft` + `AppContainer.pendingDraft`; `EditorViewModel.init` consumes it;
   `MainActivity` opens the editor from a draft. (Editor↔editor first, no popup yet.)
3. **QuickNoteActivity + popup** — transparent AppCompat activity, `ModalBottomSheet` hosting
   `NoteFields` + Save + Expand, shared `EditorViewModel`. New-note path only.
4. **Existing-note path** — open the popup for a note id (Edit action), Expand carries unsaved edits.
5. **Rewire PendingIntents** — quick-add New note/body tap + widget ＋ → popup (new); pinned Edit (and
   maybe body tap) → popup (existing).
6. **Verify** — manual matrix (create from each trigger; pin-on-save; expand new & existing with carry-
   over; auto-focus prefilled vs empty; dismiss discards; emoji in popup; rotation; back).

## Risk / effort

**Medium–large.** The new transparent activity, the shared-component extraction, the draft hand-off, and
the four-surface rewiring are each modest but add up, and the popup must stay behaviour-consistent with
the editor. Contained by sharing the view-model and UI rather than duplicating them. **Its own slice;
do after v1.3.0 ships.**
