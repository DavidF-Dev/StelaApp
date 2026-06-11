# Archive notes — implementation plan

> Status: **Implemented** · 2026-06-11 (v1.3.0, unreleased) · built in the 8 phases below.
>
> **Verification:** `assembleDebug` + `lintDebug` green; JVM unit tests pass (new coverage:
> `NotePinner` archive/unarchive/pin-unarchives, `applyQuery` archived-exclusion, `BackupCodec`
> archived round-trip); the `MIGRATION_2_3` instrumented test passes. End-to-end on the emulator:
> created a pinned note, archived it from the editor (its notification cleared, the action flipped to
> Restore), it left the main list (onboarding empty state shown), and it appeared on the Archived
> screen reached from the list overflow.

## Goal

Give the user a **reversible** way to clear a note out of the way without deleting it. Archiving
unpins the note and hides it from the main list; it is recoverable at any time. Permanent **Delete**
stays in-app and unchanged.

## Why this shape (recap of the decision)

The notification surface is the worst place for an irreversible action — it can't confirm, and the
list's undo-delete snackbar can't reach a delete triggered from the tray while the app is closed. So
the notification (and the other quick surfaces) only ever do **reversible** things; the irreversible
**Delete** stays behind the deliberate in-app surfaces it already lives on. Archive is the reversible
disposal; it carries no time limit and no auto-purge (rejected: a self-emptying trash reintroduces
silent irreversible loss and adds a clock to an otherwise timeless offline app).

## Scope (locked)

- **Archive** is a reversible action available from the **notification**, the **editor**, and
  **multi-select**. It unpins and hides the note.
- **Delete** stays permanent and in-app (list + editor), with the existing undo snackbar. Archived
  notes can be deleted the same way as active notes.
- Archived notes get their **own destination** (a screen), not another list filter chip.
- **No 30-day trash.**
- **"Swipe to unpin" is unchanged** (swiping a pin still unpins; it does not archive).
- Archived notes **remain editable**.
- Archived notes are **included in backup** and **stay archived** through export/import.

## Core invariant (new)

**A note is never both archived and pinned.** Archiving unpins (and cancels the notification); pinning
an archived note unarchives it first. This keeps the service-lifecycle rule untouched — archived notes
are always unpinned, so they never count toward "≥1 pinned." The single seam (`NotePinner`) enforces
this so no caller can violate it.

## Data model

- **`Note`**: add `isArchived: Boolean = false`.
- **Schema v2 → v3**, `MIGRATION_2_3`: `ALTER TABLE notes ADD COLUMN isArchived INTEGER NOT NULL
  DEFAULT 0`. Register it on the database builder; bump `@Database(version = 3)`; export `3.json`
  (the KSP `room.schemaLocation`) and cover 2→3 in the instrumented migration test.
- **`NoteDao`**: add
  `@Query("UPDATE notes SET isArchived = :isArchived WHERE id = :id") suspend fun setArchived(id, isArchived)`
  — deliberately leaves `updatedAt` untouched (archiving is not a content edit, mirroring `setPinned`,
  so the list order is preserved on restore). `observeAll` and `countPinned` are unchanged.
- **`NoteRepository`**: add `setArchived(id, value)`. `notes` stays the **all-notes** flow (so export
  keeps archived notes for free, and the widget/service paths are untouched). Active vs archived is
  derived in-memory by the consumers, consistent with the existing single-flow + `applyQuery` pattern.

## Seam: `NotePinner` (the only place archive state changes alongside pin/notification/service)

- `archive(note)` / `archiveAll(notes)`: for each — if pinned, cancel its notification and clear the
  pin flag; then set `isArchived = true`. Reconcile the service once for the batch.
- `unarchive(note)` / `unarchiveAll(notes)`: set `isArchived = false`. No notification or service
  change (the note stays unpinned), so no reconcile needed.
- `pinAll` gains one line: clear `isArchived` as part of pinning, enforcing the invariant (pinning an
  archived note restores then pins it).
- `deleteAll` / `restore` are unchanged: deleting an archived note just removes the row (no
  notification to cancel), and undo's `restore` re-inserts the row verbatim — so an undone delete of an
  archived note comes back **archived** (its flag is part of the re-inserted entity).
- Naming: use **`unarchive`** for this feature; the existing `restore` (undo-delete re-insert) keeps its
  name.

## Notification

- Add a third action to the pinned notification: **Edit · Unpin · Archive** (Android shows up to three).
- New `ACTION_ARCHIVE` in `NotificationActionReceiver` → `getById(noteId)?.let { notePinner.archive(it) }`.
- New string `notification_action_archive`.
- Archiving from the tray cancels that note's notification (via the unpin step inside `archive`) and
  reconciles the service — same machinery as Unpin, one step further.

## UI — note list

- The main list shows **active** notes only: `applyQuery` filters out `isArchived` before the existing
  search/sort/filter pass. Pinned/Unpinned filters therefore scope to active notes (pinned are always
  active anyway). `isSourceEmpty` (the "No notes yet" onboarding state) is computed from the **active**
  subset, so archiving every note shows the empty state rather than "No matching notes."
- Multi-select gains an **Archive** action (`batchArchive()` → `pinner.archiveAll(selected)`), beside
  the existing batch pin/unpin and delete. Like batch delete, it shows an **undo** snackbar; undo
  unarchives and re-pins any notes that were pinned before archiving (archiving had unpinned them).
- Entry point to the archived destination: an **overflow menu item** ("Archived notes") on the list top
  bar (keeps the existing search / sort-filter / settings icons uncrowded). Open to refinement.

## UI — archived destination (new)

- New route `archived`, an `ArchivedScreen` + `ArchivedViewModel`, reachable from the list overflow.
- Lists archived notes (reuse the existing note-row composable), most-recently-updated first.
- Supports: **tap to open** the editor (archived notes are editable); **multi-select** with **Restore**
  (`unarchiveAll`) and **Delete** (same undo-snackbar flow as the list).
- Empty state: "No archived notes."
- Keep it lean: no search/sort/filter on this screen initially (archived sets are usually small); can be
  added later if wanted.

## UI — editor

- `EditorUiState` gains `isArchived`.
- Add an **Archive / Restore** action (shown only for an existing note, like Delete): "Archive" when
  active → `pinner.archive(loaded)` (sets archived, clears pin); "Restore" when archived →
  `pinner.unarchive(loaded)`.
- *(Tweak, 2026-06-11)* When the note is archived, a full-width **"Archived" banner** shows between
  the app bar and the title. The editor's actions (share · pin · archive/restore · delete) moved to a
  **`BottomAppBar`** with **Save** as its docked FAB, decluttering the top bar (which also keeps it
  clean for the banner). The `BottomAppBar` respects the nav-gesture insets, so its buttons sit above
  the home-gesture strip.
- Pin interaction: toggling **Pin** on an archived note routes through `pinner.pin`, which unarchives
  then pins; the editor reflects `isArchived = false, isPinned = true`. Archiving an active+pinned note
  flips the pin toggle off.

## Backup

- `NoteBackup`: add `isArchived: Boolean = false`; bump `BACKUP_VERSION` to **2**.
- `Note.toBackup()` writes `isArchived`; `NoteBackup.toNote()` **preserves** it (so archived stays
  archived on import). Import still assigns a fresh id and brings notes in **unpinned** (unchanged) — an
  archived note imports as archived + unpinned + new id.
- Backward compatible: v1 files lack the field → defaults to `false` (active); `ignoreUnknownKeys` and
  `encodeDefaults` already handle both directions.

## Build order (TDD where practical)

1. **Data** — `Note.isArchived`, `setArchived`, `MIGRATION_2_3` + schema export, repository method.
   Instrumented migration test (2→3); DAO test; repository test over the fake DAO.
2. **Seam** — `NotePinner.archive/unarchive` + the pin-unarchives rule. Unit tests over fakes assert the
   archived⇔pinned invariant and one service reconcile per batch.
3. **Notification** — Archive action + receiver routing + string.
4. **List** — exclude archived in `applyQuery` (+ pure test), active-scoped emptiness, batch Archive,
   overflow entry point.
5. **Archived screen** — route, screen, view-model; restore/delete/undo.
6. **Editor** — `isArchived` state, Archive/Restore action, pin-unarchives coordination.
7. **Backup** — `NoteBackup.isArchived`, `BACKUP_VERSION = 2`, codec round-trip test.
8. **Docs/CHANGELOG** + verification matrix.

## Invariants honoured

- **No `INTERNET`.** Archive is local-only.
- **Service runs iff ≥1 pinned OR quick-add** — archived ⇒ unpinned, so the rule is untouched.
- **`NotificationController` is the sole `NotificationManager` toucher** — the Archive action routes
  through `NotePinner` → controller, like Unpin.
- **Notification ID derived from `note.id`** — archive cancels by the same derived id.

## Verification matrix (manual)

Archive from the notification (note leaves the list and the tray, appears in Archived) · archive from
the editor · batch archive · restore from the Archived screen and from the editor · **pin an archived
note → it unarchives and pins** · delete an archived note → undo restores it archived · export then
import preserves archived state · widget shows no archived notes and is unaffected · boot re-pin
unaffected · empty states (all-archived shows onboarding; empty Archived screen).

## Risk

Medium — it touches the schema, the seam, the notification, two screens, the editor, and backup. The
main risks are the archived⇔pinned coordination, the 2→3 migration, and scoping list emptiness/select-
all to the active subset. All three are contained: the invariant lives solely in `NotePinner`, the
migration is a single defaulted column with a test, and the list derives its working set from the active
subset in one place (`applyQuery`).
