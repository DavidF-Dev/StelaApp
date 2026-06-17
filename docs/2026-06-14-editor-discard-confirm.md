# Editor discard-changes confirm — implementation plan

> Status: **Implemented** · v1.6.0 · 2026-06-14. Built as the plan below. Part of the
> [post-v1.5 improvements queue](2026-06-14-post-v1.5-improvements.md). As-built notes at the end.

## Goal

Pressing Back in the full editor currently navigates away without saving, silently dropping unsaved
edits. Guard it: when the note has **unsaved changes**, Back shows a "Discard changes?" confirm; when it
doesn't, Back leaves immediately (no dialog). Save stays interactable even with no changes, but saving a
note with no real changes must **not** bump its Modified Time.

## Scope — locked decisions

(Confirmed with the user 2026-06-14.)

- **Dirty → dialog; clean → no dialog.** Back-arrow and system back both route through the guard.
- **Dialog buttons: Discard / Keep editing.** Discard (error-coloured, like the delete confirm) leaves
  and drops edits; Keep editing dismisses. No third "Save" button.
- **Full editor only.** The quick-note popup keeps its frictionless scrim/back dismiss — no discard
  guard there (it's a quick jot).
- **Save stays enabled** regardless of dirty state (more forgiving than greying it out), but a save with
  **no real change is a no-op** that doesn't write or bump `updatedAt`.
- **A schedule-only change does not bump Modified Time.** `pinAt`/`unpinAt` edits persist via
  `setSchedule` (which already avoids bumping `updatedAt`, so the list doesn't reorder), consistent with
  the existing "scheduling is not a content edit" design. They still count as unsaved edits for the Back
  guard.

## Dirty detection

`EditorViewModel` already holds `loaded: Note?` (null for a new note). Expose dirtiness as a **pure,
computed** property on `EditorUiState`, driven by a baseline snapshot stored in the state (so it's unit
testable without reaching into the view-model's private `loaded`):

```kotlin
data class EditorSnapshot(
    val title: String, val description: String, val emoji: String,
    val pinAt: Long?, val unpinAt: Long?,
)

data class EditorUiState(
    /* …existing fields… */
    val savedSnapshot: EditorSnapshot? = null, // null = a new, never-saved note
) {
    val isDirty: Boolean get() {
        val s = savedSnapshot
        return if (s == null) {
            title.isNotBlank() || description.isNotBlank() || emoji.isNotBlank() ||
                pinAt != null || unpinAt != null
        } else {
            title != s.title || description != s.description || emoji != s.emoji ||
                pinAt != s.pinAt || unpinAt != s.unpinAt
        }
    }
}
```

What counts:
- **Editable fields:** `title`, `description`, `emoji`, and the Advanced `pinAt`/`unpinAt` — these are
  pending-until-save.
- **Not counted:** `isPinned` / `isArchived`. For an existing note these persist *immediately* (pin /
  unpin / archive / snooze call `pinner` and update `loaded`), so they're never "unsaved."

Keeping the snapshot honest:
- **On load** (existing note): set `savedSnapshot` from the loaded note's fields.
- **On live ops that persist a schedule change** — `pin()` (clears `pinAt`), `unpin()` (clears
  `unpinAt`), `snooze()` (sets `pinAt`) — update the snapshot's `pinAt`/`unpinAt` in the same
  `_uiState.update { … }`, so the now-persisted value isn't read as dirty.
- **New note** (and a new note expanded from the popup): `savedSnapshot` stays null → any entered
  content (incl. a carried-over draft) reads as dirty, which is correct.

## Save becomes a no-op when nothing changed

Rework the existing-note branch of `save()` so it only writes what actually changed:

```kotlin
val promotion = promoteLeadingEmoji(state.title, state.emoji, detectEmojiRanges(state.title))
val title = promotion?.title ?: state.title
val emoji = promotion?.emoji ?: state.emoji
val contentChanged = title != existing.title || state.description != existing.description ||
    emoji != existing.emoji
val scheduleChanged = state.pinAt != existing.pinAt || state.unpinAt != existing.unpinAt
if (contentChanged) {
    val updated = existing.copy(title = title, description = state.description, emoji = emoji)
    repository.update(updated)   // the only path that bumps updatedAt
    pinner.refresh(updated)
    loaded = updated
}
if (scheduleChanged) {
    pinner.applySchedule(existing.id, state.pinAt, state.unpinAt) // setSchedule: no updatedAt bump
}
onComplete()
```

- **Nothing changed** → neither branch runs → no DB write, `updatedAt` untouched, no list reorder — Save
  still closes the editor (`onComplete()`).
- **Content changed** → `update()` bumps `updatedAt` and refreshes the live notification (today's
  behaviour, now gated on an actual change).
- **Schedule only** → `applySchedule` persists it without bumping `updatedAt`.
- The new-note branch is unchanged (create + pin-on-save + applySchedule).

Note: comparison uses the **post-promotion** values, so opening a legacy note whose title leads with an
emoji and pressing Save still normalises it once (a real change). That normalisation does **not** make
the note read as dirty for the Back guard (which compares the raw editable fields), so Back on an
untouched note never prompts.

## Back wiring (`EditorScreen`)

```kotlin
var showDiscardDialog by remember { mutableStateOf(false) }
val attemptBack = { dismissKeyboard(); if (state.isDirty) showDiscardDialog = true else onBack() }
BackHandler { attemptBack() }
// nav-icon onClick = attemptBack
```

The discard `AlertDialog`: title "Discard changes?", body "Your unsaved changes will be lost.", confirm
**Discard** (error colour) → `{ showDiscardDialog = false; onBack() }`, dismiss **Keep editing**. Save /
Delete / Archive keep their existing exit paths (Save already persists; Delete has its own confirm).
`isDirty` rides in `state`, so no new `EditorScreen` parameters are needed.

## Files touched

- `ui/editor/EditorViewModel.kt` — `EditorSnapshot`, `EditorUiState.savedSnapshot` + `isDirty`, snapshot
  seeding on load, snapshot upkeep in `pin`/`unpin`/`snooze`, and the no-op `save()` rework.
- `ui/editor/EditorScreen.kt` — Back/ nav-icon guard + the discard dialog.
- `res/values/strings.xml` — `editor_discard_dialog_title` / `_message` / `_confirm` (Discard) /
  `_cancel` (Keep editing).

## Testing

- **Unit (`EditorViewModelTest`, fake DAO):**
  - New note: `isDirty` false initially, true after typing, and the save no-op path is irrelevant
    (a blank new note can't save).
  - Existing note: `isDirty` false after load; true after editing title / description / emoji / `pinAt`;
    back to false when the edit is reverted to the original value.
  - Live `pin` / `unpin` / `snooze` leave `isDirty` false.
  - Save with no change: `updatedAt` unchanged (assert via the fake DAO — `update` not invoked / stamp
    unchanged).
  - Save with a content change: `updatedAt` bumped.
  - Save with a schedule-only change: `updatedAt` unchanged, schedule persisted.
- **Instrumented (new `EditorDiscardTest`, or extend `EditorBackNavigationTest`):** edit a field → Back →
  discard dialog shows; Keep editing keeps the editor; Discard leaves. Back with no edits → leaves with
  no dialog. (Grant `POST_NOTIFICATIONS` + `markOnboardingComplete()` in `@BeforeClass`, per the
  standing patterns.)

## Edge cases

- **Whitespace:** typing then deleting back to the exact original clears `isDirty` (exact comparison).
- **Expand-from-popup:** the snapshot is the stored note, so the carried draft's edits read as dirty —
  Back from the expanded editor confirms, as intended.
- **Archived note:** edited like any other; archive/unarchive are live and don't affect `isDirty`.
- **Predictive back:** the existing `BackHandler` already participates; the guard simply chooses dialog
  vs. exit before the pop.

## As-built notes

Built exactly as planned. Specifics:

- `EditorSnapshot` + `EditorUiState.savedSnapshot`/`isDirty` added; baseline seeded on load via a private
  `Note.snapshot()` helper; `pin`/`unpin`/`snooze` fold the now-persisted `pinAt`/`unpinAt` into the
  snapshot in the same `_uiState.update`.
- `save()` existing-note branch splits into `contentChanged` (→ `update` + `refresh`, bumps `updatedAt`)
  and `scheduleChanged` (→ `applySchedule`, no bump); nothing changed → just `onComplete()`.
- `EditorScreen` routes the back arrow and system back through a shared `attemptBack` that shows the
  discard `AlertDialog` (Discard in error colour / Keep editing) when `state.isDirty`.
- **Tests:** six `EditorViewModelTest` cases (dirty true/false/revert; live pin+snooze not dirty; save
  no-op / content-bump / schedule-only-no-bump — using a new mutable clock in the fixture) and the
  instrumented `EditorDiscardTest` (confirm-then-keep-then-discard; clean note exits with no dialog).
- **Verified:** unit tests, full instrumented suite (54 tests, 0 failed), lint clean.
