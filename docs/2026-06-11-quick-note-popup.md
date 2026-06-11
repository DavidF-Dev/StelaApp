# Quick-note popup (minimal editor over the screen) — implementation plan

> Status: **Implemented** · v1.4.0 · 2026-06-11. Built as the plan below; see the "As-built notes" at
> the end for the few places reality differed.

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
| Pinned-note **body tap** | `MainActivity` `/editor/{id}` | `QuickNoteActivity` (note id) |
| **Expand** (from popup) | — | `MainActivity` full editor, from draft |

`AndroidNotificationController` and `StelaWidget` build the new intents; `MainActivity` gains a path to
open the editor from a `pendingDraft`. The pinned-note **body tap** matches its **Edit** action (both →
popup) — consistent, and Expand still reaches the full editor.

**Secure-lock-screen fallback:** when the device is on a **secure** lock screen, the popup is *not*
shown — the trigger falls back to **today's behaviour**, opening the full **Editor screen** in
`MainActivity` (new note for the quick-add/widget triggers, `/editor/{id}` for the pinned-note triggers).
So each PendingIntent decides popup-vs-editor by querying lock state (`KeyguardManager.isKeyguardLocked`
/ `isDeviceSecure`) at fire time; everything works as before behind a secure lock, and gets the popup
otherwise.

## Invariants honoured

- **No `INTERNET`.** Local only.
- **Pin-on-save** routes through `NotePinner.pin`, gated on `POST_NOTIFICATIONS` like the editor.
- A note is never both archived and pinned (the popup never archives; pinning is unchanged).
- Dismissing the popup discards unsaved content (same as the editor's back = no save).

## Resolved decisions

- **Pinned-note body tap → popup** (matches its Edit action). *(2026-06-11)*
- **Secure lock screen → no popup**, fall back to today's full-editor behaviour. *(2026-06-11)*

## Open questions (resolved during implementation)

1. **Emoji picker layering** in a transparent activity — **fine.** The View-system `BottomSheetDialog`
   opens its own window above the Compose `ModalBottomSheet`; it renders, scrolls, searches, and the
   picked emoji flows back into the popup's Title field. Verified on the emulator.
2. **VM seeding from intent extras** — **needed explicit wiring.** A bare `createSavedStateHandle()` in
   an Activity does *not* surface intent extras (unlike a nav back-stack entry, which fills them from
   route args). `QuickNoteActivity` overrides `defaultViewModelCreationExtras` to put `noteId`/`pin`
   into `DEFAULT_ARGS_KEY`, which is what `createSavedStateHandle()` reads.

## Starting points (files)

- `app/src/main/java/dev/davidfdev/stela/ui/editor/EditorScreen.kt` — the title/description fields, the
  private `EmojiPickerBottomSheet`, and the current auto-focus `LaunchedEffect` (Phase 1 extracts these).
- `.../ui/editor/EditorViewModel.kt` — the shared view-model (Phase 2 adds draft seeding in `init`).
- `.../di/AppContainer.kt` — where `pendingDraft` (the Expand hand-off) lives.
- `.../MainActivity.kt` + `.../ui/StelaNavHost.kt` — open the full editor from a draft.
- `.../notifications/AndroidNotificationController.kt` + `.../ui/widget/StelaWidget.kt` — the
  PendingIntents/deep links to rewire (Phase 5).
- `AndroidManifest.xml` + `res/values/themes.xml` — register the transparent `QuickNoteActivity` + theme.

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

## As-built notes (2026-06-11)

A handful of details differed from the plan above; the structure and decisions otherwise held.

- **`NoteDraft` lives in `ui.editor`** (next to the view-model that consumes it); `AppContainer` holds
  the one-shot `pendingDraft`. The editor's `Factory` reads-and-clears it when constructing the VM.
- **Expand → editor lands on the list, not home** *(revised 2026-06-11)*: the Expand intent carries
  `MainActivity.EXTRA_FROM_POPUP_EXPAND`. The editor deep link's synthesised back stack is just
  `[Editor]` (no list under it), so on done the editor *navigates* to the list (clearing the stack via
  `popUpTo(graph.id)`) — a third `onEditorDone` mode (`goToListOnEditorDone`) alongside finish/pop, made
  one-shot so it governs only the expanded editor. The Expand intent also needs `FLAG_ACTIVITY_NEW_TASK`
  so `MainActivity` lands in the app's task, not the popup's empty-affinity task (which would tangle the
  two activities together). The user entered the app via the popup, so they end up on their note list.
- **VM seeding** went through an overridden `defaultViewModelCreationExtras` (see Open question 2),
  not a custom factory.
- **`noteLoaded`** became a derived property on `EditorUiState` (`!isEditing || createdAt != null`) so
  the editor's pin-pop and the shared `NoteFields` auto-focus read one definition.
- **Transparent theme:** `Theme.Stela.Transparent` (translucent window, transparent background,
  `backgroundDimEnabled=false` so the system doesn't dim on top of the `ModalBottomSheet`'s own scrim).
- **Floats over the current app, not the Stela task** *(2026-06-11 tweak)*: `QuickNoteActivity` declares
  `android:taskAffinity=""` (its own task) + `excludeFromRecents`, so a trigger shows the popup over
  whatever the user is doing rather than bringing `MainActivity`'s task to the foreground behind it.
- **One popup at a time** *(2026-06-11 tweak)*: `android:launchMode="singleTask"` — a fresh trigger lands
  in `onNewIntent` on the single instance, which `setIntent`s and bumps an `intentVersion` that re-keys
  the `EditorViewModel` (`viewModel(key = "quicknote-$version")`), re-seeding from the new intent and
  discarding the old popup's content. Structurally prevents two popups stacking.
- **Action row mirrors the editor** *(2026-06-11 tweak)*: extracted `RowScope.NoteEditorActions`
  (Expand · Share · Pin/Unpin · Archive · Delete · Save) shared by the editor's app bar and the popup.
  Share/Archive/Delete show only for an existing note; Pin/Unpin shows for new notes too. The popup
  confirms Archive/Delete with `AlertDialog`s (works fine over the `ModalBottomSheet`) and finishes after;
  Unpin toggles in place without closing. Pin routes through the same `POST_NOTIFICATIONS` gate +
  snackbar as the editor. The "Edit note" heading was dropped (existing notes show none, like the
  editor); "New note" stays. `EditorViewModel.archive` gained an `onComplete` so finishing the popup
  doesn't cancel the archive mid-flight.
- **The pinned notification dropped its Archive action** *(2026-06-11)* — it now carries Edit + Unpin
  only; archiving is reachable from the editor and the popup.
- **Content-overflow safety** *(2026-06-11 tweak)*: the description field grows from its default height
  to double, then scrolls within (`heightIn(min, max = 2×)`) — popup 96→192 dp, editor 200→400 dp; the
  popup's fields also sit in a `verticalScroll` column. The top-anchored actions stay reachable.
- **Emoji picker covers the popup** *(decided 2026-06-11)*: tapping the emoji opens the **shared**
  `EmojiPickerBottomSheet` (a `BottomSheetDialog`) over the popup, like a keyboard — kept deliberately
  to avoid diverging from the full editor's picker. Floating the popup *above* the picker would mean
  giving the popup its own inline emoji panel; not done.
- **Tests:** unit tests cover the draft seeding (new + existing) in `EditorViewModelTest`; an
  on-device `QuickNotePopupTest` launches `QuickNoteActivity` in-process (it's non-exported, so it
  can't be started from `adb`) and checks new-note save→pin and existing-note prefill.
