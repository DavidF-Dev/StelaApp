# Duplicate note — implementation plan

> Status: **Implemented** · v1.6.0 · 2026-06-14. Built as the plan below. Part of the
> [post-v1.5 improvements queue](2026-06-14-post-v1.5-improvements.md). As-built notes at the end.

## Goal

Let the user duplicate the note they're editing — a quick way to spin a variant or template off an
existing note. The copy is a fresh, independent note.

## Scope — locked decisions

(Confirmed with the user 2026-06-14.)

- **Entry point: the full editor's overflow (⋮) menu**, for existing notes only. Not the quick-note
  popup, not (for now) the list's multi-select bar.
- **Stay + snackbar (Undo):** duplicating keeps the user on the current note and shows a "Note
  duplicated" snackbar with **Undo** (which deletes the just-created copy). It deliberately does **not**
  navigate to the copy — that would abandon the open editor and collide with the unsaved-changes guard.
- **Exact copy of the title** (no "(copy)" suffix), per spec.
- **The copy is from the current on-screen fields** (title/description/emoji), with the same leading-emoji
  promotion as save — so "duplicate what I'm looking at," whether or not the original has unsaved edits.
  The original note is left untouched.
- **The copy is always a fresh working note:** active (not archived), **unpinned**, **no schedule**
  (`pinAt`/`unpinAt` null), fresh `createdAt`/`updatedAt` — even when duplicating an archived or pinned
  note. This falls straight out of `NoteRepository.create`, which sets exactly these.

## ViewModel

```kotlin
private var lastDuplicateId: Long? = null

/// Creates an independent copy of the current fields (fresh id, unpinned, unscheduled, active) and
/// invokes [onDuplicated] so the caller can offer Undo. The edited note itself is left untouched.
fun duplicate(onDuplicated: () -> Unit) {
    viewModelScope.launch {
        val state = _uiState.value
        val promotion = promoteLeadingEmoji(state.title, state.emoji, detectEmojiRanges(state.title))
        val title = promotion?.title ?: state.title
        val emoji = promotion?.emoji ?: state.emoji
        lastDuplicateId = repository.create(title, state.description, emoji = emoji)
        onDuplicated()
    }
}

/// Deletes the most recent duplicate (the Undo for [duplicate]).
fun undoDuplicate() {
    val id = lastDuplicateId ?: return
    lastDuplicateId = null
    viewModelScope.launch { repository.getById(id)?.let { pinner.delete(it) } }
}
```

`create` already produces an unpinned, unscheduled note with fresh timestamps and a new id, so there's
no extra normalisation. Undo routes through `pinner.delete` (the standard delete seam; a no-op for the
service since the copy is unpinned).

## UI wiring

- **`NoteEditorActions` / `NoteOverflowMenu`** (shared `NoteFields.kt`): add `onDuplicate: (() -> Unit)?
  = null`. When non-null and `state.isEditing`, show a **Duplicate** item (`Icons.Filled.ContentCopy`)
  in the overflow, after Share. The popup leaves it null (default) → editor-only, mirroring how
  `onExpand` is popup-only.
- **`EditorScreen`** gains an `onDuplicate: () -> Unit` parameter, passed through to `NoteEditorActions`.
- **`EditorRoute`** provides it, hosting the snackbar (it already has the `scope` + `snackbarHostState`):

  ```kotlin
  onDuplicate = {
      viewModel.duplicate {
          scope.launch {
              val result = snackbarHostState.showSnackbar(duplicatedMessage, undoLabel)
              if (result == SnackbarResult.ActionPerformed) viewModel.undoDuplicate()
          }
      }
  }
  ```

## Strings

- `action_duplicate` = "Duplicate"
- `editor_duplicated` = "Note duplicated" (snackbar)
- Undo reuses the existing `action_undo`.

## Files touched

- `ui/editor/EditorViewModel.kt` — `duplicate` / `undoDuplicate` + `lastDuplicateId`.
- `ui/editor/NoteFields.kt` — `onDuplicate` param + the overflow item.
- `ui/editor/EditorScreen.kt` — thread `onDuplicate`; `EditorRoute` wires the snackbar + Undo.
- `res/values/strings.xml` — `action_duplicate`, `editor_duplicated`.

## Testing

- **Unit (`EditorViewModelTest`):**
  - Duplicate creates a second note with the same title/description/emoji, **unpinned**, with a new id;
    the original row is unchanged and still present (two notes total).
  - Duplicate copies the **current edited** fields (edit the title, then duplicate → the copy has the
    edited title; the stored original is untouched until saved).
  - Duplicate of a pinned/scheduled note yields an **unpinned, unscheduled** copy (`pinAt`/`unpinAt`
    null, `isPinned` false).
  - Leading-emoji promotion applies to the copy.
  - `undoDuplicate` removes the copy (back to one note).
- **Instrumented (`DuplicateNoteTest`):** create + save a note, open it, open the ⋮ menu, tap Duplicate,
  assert the "Note duplicated" snackbar, return to the list and assert the title now appears on two rows.
  (Grant `POST_NOTIFICATIONS` + `markOnboardingComplete()` in `@BeforeClass`.)

## Edge cases

- **Blank title:** an existing note normally has a title; if the field has been cleared, Duplicate still
  copies the current (blank) title. Not specially blocked — harmless and rare.
- **Snackbar lifetime:** if the user leaves the editor immediately after duplicating, the snackbar (and
  its Undo) is dismissed with the screen — acceptable; the copy simply remains.

## As-built notes

Built exactly as planned. `EditorViewModel.duplicate(onDuplicated)` + `undoDuplicate()` (holding
`lastDuplicateId`); the overflow gained a nullable `onDuplicate` in the shared `NoteEditorActions` /
`NoteOverflowMenu` (`Icons.Filled.ContentCopy`, after Share), non-null only from the full editor so it
never shows in the popup; `EditorRoute` hosts the "Note duplicated" snackbar + Undo. Two pre-existing
component tests that construct `EditorScreen` directly (`DescriptionFieldTest`, `AdvancedSectionTest`)
got the new `onDuplicate = {}` argument. **Tests:** four `EditorViewModelTest` cases (independent
unpinned copy leaving the original; copies current edits + drops schedule; emoji promotion; undo) and the
instrumented `DuplicateNoteTest` (⋮ → Duplicate → snackbar → two rows). **Verified:** unit tests, full
instrumented suite (58 tests, 0 failed), lint clean.
