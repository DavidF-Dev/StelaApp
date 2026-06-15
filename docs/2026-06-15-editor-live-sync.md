# Editor live-sync: keep the editor consistent with background pin/schedule changes

**Status:** implemented (2026-06-15)
**Date:** 2026-06-15
**Related:** Bug 1 follow-up (orphaned-notification fix already shipped in `NotePinner.deleteAll` / `archiveAll`).

Implemented as planned. The "dirty schedule edit on a now-disabled row" edge refinement was included:
`refreshAuthoritativeFields` drops a pending `pinAt` edit when the note pins (its row disables) and a
pending `unpinAt` edit when no pin remains to end. Tests cover the live-reflect, the content-save
no-revert regression, the drop/preserve edge cases, and the external-delete close (with self-delete
guarded so navigation isn't double-fired).

## Problem

The editor reads its note **once** in `init` (`repository.getById`) and caches it in `loaded`. While
the editor is open, a scheduled alarm can fire in the background and change the note (auto-pin clears
`pinAt` and sets `isPinned`; auto-unpin clears `unpinAt` and clears `isPinned`). The editor never sees
this, so:

1. **Display goes stale** — the Pin toggle and the "Pin at" / "Unpin at" rows show the pre-change state.
2. **A content-edit save corrupts the row.** `NoteRepository.update` does a *full-row upsert* built from
   the stale `loaded`, so saving an edited description writes the stale `isPinned`/`pinAt`/`unpinAt` back
   over the background change — silently un-pinning the note in the DB while its notification keeps
   showing.
3. **A schedule edit against stale pin state thwarts intent** — e.g. setting "Pin at" on a note that is
   secretly already pinned. The pinner self-heals this into a sane (if odd) state, so it is not true
   corruption, but it is confusing.

## Approach (Option A + B)

- **B — content-only write (kills the corruption):** add a field-scoped content update so a content save
  *never* round-trips pin/schedule fields from memory.
- **A — observe the note (fixes the display and the intent problem):** collect the note as a `Flow` and,
  on changes after the initial load, refresh only the **authoritative** fields (pin/archive/schedule),
  leaving the user's in-progress text untouched. The "Pin at" row then disables itself the instant the
  note is pinned, so the user cannot set a contradictory schedule.

The two share the same machinery a force-close would need (observing the row), but preserve in-progress
edits instead of yanking the screen away.

## Authority model — which fields are DB-authoritative vs unsaved-editable

| Field                     | Persists when?                       | On external change          |
|---------------------------|--------------------------------------|-----------------------------|
| `isPinned`, `isArchived`  | immediately (pin/unpin/archive)      | always adopt DB value       |
| `updatedAt`, `createdAt`  | immediately                          | always adopt DB value       |
| `pinAt`, `unpinAt`        | **only on Save** (unsaved-editable)  | adopt DB value *iff clean*  |
| `title`, `description`, `emoji` | only on Save (unsaved-editable) | never touched by the observer |

"Clean" = the field still equals its `savedSnapshot` baseline (no pending unsaved edit).

## Changes

### 1. DAO — `NoteDao`

- Add a single-row observable:
  ```kotlin
  @Query("SELECT * FROM notes WHERE id = :id")
  fun observeById(id: Long): Flow<Note?>
  ```
- Add a content-only update that bumps `updatedAt` (mirrors the existing field-scoped updates, but this
  one *does* bump the modified time because content is a real edit):
  ```kotlin
  @Query("UPDATE notes SET title = :title, description = :description, emoji = :emoji, updatedAt = :updatedAt WHERE id = :id")
  suspend fun setContent(id: Long, title: String, description: String, emoji: String, updatedAt: Long)
  ```

### 2. Repository — `NoteRepository`

- `fun observeById(id: Long): Flow<Note?> = dao.observeById(id)`
- `suspend fun updateContent(noteId: Long, title: String, description: String, emoji: String) =
   dao.setContent(noteId, title, description, emoji, now())` — stamps `updatedAt` here, consistent with
   how `update`/`create` stamp time.

### 3. ViewModel — `EditorViewModel`

**Replace the one-shot load with a Flow collection** (only when `noteId != null`):

- **First emission** seeds the full state exactly as `init` does today (including the `draft` overlay for
  title/description/emoji and `savedSnapshot = note.snapshot()`). Detect "first" via `loaded == null`.
- **Subsequent emissions** are external changes: update only
  - `isPinned`, `isArchived`, `updatedAt` → always from the note;
  - `pinAt`/`unpinAt` → from the note **iff that field is clean** (`state.x == savedSnapshot?.x`),
    otherwise keep the user's pending edit;
  - `savedSnapshot.pinAt`/`unpinAt` → always advanced to the note's values, so dirty-tracking stays
    correct (a preserved dirty edit still reads dirty against the new baseline; a clean field stays
    clean);
  - **never** touch `title`/`description`/`emoji`.
  - Keep `loaded = note` up to date so the content-save path (below) builds on fresh pin/schedule fields.

  Edge — *dirty schedule edit on a row the new pin state disables* (e.g. a pending "Pin at" edit when the
  note auto-pins): the "Pin at" row disables when `isPinned` becomes true, leaving the pending value
  unreachable. Recommended refinement: when the new pin state makes a schedule row inapplicable
  (`pinAt` row when `isPinned`; `unpinAt` row when `!isPinned && pinAt == null`), force-sync that field to
  the DB value (drop the now-meaningless pending edit) rather than preserving it. This keeps the displayed
  state and a subsequent save self-consistent. Small, self-healing either way — implement if cheap.

**Keep the explicit optimistic updates** in `pin()`/`unpin()`/`archive()`/`snooze()`. They give immediate
synchronous feedback; the observer then re-emits the same values, which converge (no flicker). The
observer is the safety net for *external* changes, not a replacement for the in-VM updates.

**Content-save path** (`save`, existing-note branch): replace
```kotlin
val updated = existing.copy(title = title, description = state.description, emoji = emoji)
repository.update(updated)
pinner.refresh(updated)
loaded = updated
```
with a content-only write that cannot clobber pin/schedule:
```kotlin
repository.updateContent(existing.id, title, state.description, emoji)
val refreshed = existing.copy(title = title, description = state.description, emoji = emoji)  // existing is fresh (observer-kept)
pinner.refresh(refreshed)   // refresh no-ops unless isPinned, which is now accurate
loaded = refreshed
```
The schedule branch is unchanged: it already routes through `pinner.applySchedule`, which is field-scoped
and reconciles, so it cannot corrupt. Because `existing`/`loaded` is now observer-fresh, `scheduleChanged`
is computed against DB truth.

**External deletion** (the one legitimate force-close): if `observeById` emits `null` after the first load
(e.g. a notification Remove action with Removal Preference = Delete fires while the editor is open),
the note no longer exists and saving would resurrect or error. Expose a one-shot close signal the screen
acts on:
- add `val closed: StateFlow<Boolean>` (or a `Channel`/`SharedFlow` one-shot) set true on a post-load
  `null` emission;
- `EditorScreen` collects it in a `LaunchedEffect` and calls the existing `onDone` to pop.
Scope this strictly to deletion — content/pin/schedule changes never close the editor.

### 4. Screen — `EditorScreen`

- Collect the new `closed` signal and invoke `onDone()` when set. No other changes; the screen already
  renders from `uiState`, so the live field refreshes flow through automatically.

### 5. Quick-note popup

`QuickNotePopup` / `QuickNoteActivity` share `EditorViewModel`, so they inherit the live-sync behaviour.
Verify the popup tolerates a live pin/schedule refresh (it should — it renders from the same state) and
that the `closed` signal does something sensible there (dismiss the popup). No expected code change beyond
wiring `closed` if the popup hosts the VM independently.

## Tests

- **DAO (instrumented, Room):** `observeById` emits on insert/update/delete; `setContent` updates the
  three fields and `updatedAt` and leaves pin/schedule/archive untouched.
- **Repository (JVM, `FakeNoteDao`):** `updateContent` bumps `updatedAt` and preserves
  `isPinned`/`pinAt`/`unpinAt`; `observeById` reflects mutations. (`FakeNoteDao` is `StateFlow`-backed, so
  `observeById` can `map` the rows — emits live for free. Add the two new methods to the fake.)
- **ViewModel (JVM):** the fixture already uses a real `NoteRepository` over `FakeNoteDao`, so background
  changes are simulated by mutating the repo mid-test and `advanceUntilIdle()`:
  - background auto-pin (`repository.setPinned(id, true)` + `clearPinAt`) → `uiState.isPinned` flips to
    true, `pinAt` clears, **without** marking the note dirty;
  - then a content edit + `save` → DB stays `isPinned = true` (regression for the corruption);
  - background change while a `pinAt` edit is pending → the pending edit is preserved (clean-field rule)
    while `isPinned`/`unpinAt` refresh;
  - external delete (`repository.delete`) → `closed` signal fires;
  - existing `isDirty` / no-op-save / schedule-only-save tests still pass (the observer must not spuriously
    mark dirty or bump `updatedAt`).

## Docs to update

- `CHANGELOG.md` — add a "Fixed" entry: editing a note that pinned/unpinned itself in the background no
  longer shows stale pin state and no longer reverts that change on save.
- `CLAUDE.md` — note that the editor observes its note live and that content saves are field-scoped
  (`updateContent`), so a background pin/schedule change is reflected and never overwritten.
- Code comments: the `updateContent` DAO/repo methods (why content bumps `updatedAt` while pin/schedule
  don't), and the ViewModel observer (why only authoritative/clean fields refresh and text is preserved).

## Out of scope / non-goals

- No force-close on pin/unpin/schedule changes — only on external **deletion**.
- No new user-facing "reload" affordance (Option C) — the live refresh is silent, consistent with how the
  Pin toggle already updates immediately on pin/unpin.
