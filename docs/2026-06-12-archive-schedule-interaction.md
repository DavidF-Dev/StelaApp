# Archive ↔ scheduled pins — implementation plan

> Status: **Implemented** · 2026-06-12 · v1.5.0. Built as planned (as-built notes at the end).
> How a note's Advanced schedule (`pinAt` / `unpinAt`) interacts with archiving and restoring. An
> extension of [2026-06-12-scheduled-pins.md](2026-06-12-scheduled-pins.md). Supersedes that slice's
> "archiving clears the schedule and cancels its alarms."

## Goal

Archiving a note **remembers** its schedule instead of dropping it, and restoring **reconciles** it
(catch-up). A schedule on an archived note is **dormant**: its alarms still fire and `reconcile` still runs,
but on an archived note a fired/past-due time **only clears itself, with no pin/unpin** — exactly what
already happens when a `pinAt` fires on an already-pinned note (or `unpinAt` on an already-unpinned one).
So an archived note's schedule never goes *stale*; it just **expires and resets, harmlessly**.

The core invariant is untouched: **a note is never both archived and pinned**, because a dormant schedule
can't pin while archived.

## Decision (supersedes scheduled-pins Q2/Q3 archive handling)

- **Archiving keeps `pinAt`/`unpinAt` and leaves their alarms armed** (previously: cleared + cancelled).
- **An archived note's timers no-op on fire** — pin only when unarchived + unpinned; unpin only when
  pinned. (Already true in `PinAlarmReceiver`; see below.)
- **`reconcile` on an archived note** keeps future times, clears past-due ones, and **never changes the pin
  state**. So app-start `reconcileAll` clears an archived note's expired times with no effect.
- **Restoring reconciles** the note (catch-up): future times re-arm and resume; the note returns unpinned
  unless a `pinAt` is genuinely due.

## Why this is minimal

The alarm receiver already guards on archived state:

- `ACTION_PIN`: `if (!note.isPinned && !note.isArchived) pin(note)` then clears `pinAt`. An archived note
  is skipped and the spent `pinAt` is cleared — **no change needed**.
- `ACTION_UNPIN`: `if (note.isPinned) unpin(noteId)` then clears `unpinAt`. An archived note is unpinned,
  so it's skipped and the spent `unpinAt` is cleared — **no change needed**.

So the receiver already does "clear with no effect" for archived notes. The work is three small edits.

## Changes

### 1. `PinSchedule.resolve` — dormant for archived (keep future, clear past)

Today the archived branch wipes both times:

```kotlin
if (isArchived) return Resolution(targetPinned = isPinned, pinAt = null, unpinAt = null)
```

Change it to preserve future times and clear only past-due ones, never flipping the pin state:

```kotlin
if (isArchived) return Resolution(
    targetPinned = isPinned,                 // archived notes are unpinned; never auto-pin/unpin
    pinAt = pinAt?.takeIf { it > now },       // a past-due time clears with no effect
    unpinAt = unpinAt?.takeIf { it > now },
)
```

(The non-archived path below it is unchanged — it still applies fired transitions in time order.)

### 2. `NotePinner.archiveAll` — stop clearing the schedule

Drop the `clearSchedule(note.id)` call so archiving keeps `pinAt`/`unpinAt` and their armed alarms. The
existing unpin-on-archive already uses a **raw** `setPinned(false)` (not `unpinAll`), so it does **not**
trip the "unpin clears `unpinAt`" rule — the schedule survives intact. The now-unused private
`clearSchedule` helper is removed (it has no other caller; `deleteAll` cancels alarms directly).

### 3. `NotePinner.unarchiveAll` — reconcile on restore (catch-up)

```kotlin
suspend fun unarchiveAll(notes: List<Note>) {
    notes.forEach { note ->
        repository.setArchived(note.id, false)
        repository.getById(note.id)?.let { reconcile(it) }
    }
    reconcileService()   // a catch-up pin may have started the service
}
```

This is the single seam for every restore path — the Archived screen (`restoreSelected` →
`unarchiveAll`), the editor's Restore (`unarchive` → `unarchiveAll`), and the list's undo-archive
(`undoArchive` → `unarchiveAll`, which then additionally re-pins the notes that were pinned-now before
archiving; unchanged and still correct, since `reconcile` handles only schedule-driven state).

## No change needed

- **`PinAlarmReceiver`** — already no-ops on archived notes (above).
- **`reconcileAll`** — already iterates every note with a schedule (`pinAt != null || unpinAt != null`),
  not gated on archived. With the kept schedules it now also clears archived notes' expired times on app
  start, which is the intended dormant behaviour.
- **`undoArchive`** (`NoteListViewModel`) — works as-is on top of the reconciling `unarchiveAll`.

## A nuance to note (not a blocker)

Because alarms stay armed *and* app-start `reconcileAll` clears an archived note's expired times, by the
time a user restores, past-due times are almost always already cleared → no surprise catch-up pin. The one
narrow window is a time that goes past *while the app is open and the note is archived* but before its
inexact alarm fires; restoring in that window catches up and pins (the time genuinely just arrived). That's
correct behaviour and matches app-start reconcile semantics — flagged only for completeness. (If we ever
wanted restore to *never* catch up on a just-passed time, we'd clear expired times before reconciling on
unarchive; not proposed.)

## Files

**Edited:** `pin/PinSchedule.kt` (archived branch + class comment), `pin/NotePinner.kt` (`archiveAll`
drops `clearSchedule`; `unarchiveAll` reconciles + `reconcileService`; remove the dead `clearSchedule`
helper). Docs: `2026-06-12-scheduled-pins.md` (supersede the "archive clears/cancels schedule" lines),
`CLAUDE.md` (archived-note invariant gains a "may hold a dormant schedule" clause), and the design doc's
scheduled-pins entry.

**Unchanged:** `pin/PinAlarmReceiver.kt`, `NotePinner.reconcileAll`, the undo paths, backup (import still
drops schedules — kept as-is by decision).

## Invariants honoured

- **A note is never both archived and pinned** — a dormant schedule can't pin while archived; restore
  reconciles to the correct state.
- `PinScheduler` stays the sole `AlarmManager` toucher; no alarm plumbing changes (alarms are simply not
  cancelled on archive).
- No `INTERNET`, no new permission, no schema change (times already persist on the row).

## Testing

- **`PinScheduleTest`** — new archived cases: (archived, `pinAt` past) → `pinAt` cleared, not pinned;
  (archived, `pinAt` future) → kept, not pinned; (archived, `unpinAt` past) → cleared; (archived, both
  future) → both kept, pin state unchanged.
- **`NotePinnerTest`** — `archiveAll` keeps `pinAt`/`unpinAt` on the row and does **not** cancel the alarms
  (assert over the fake scheduler); `unarchiveAll` reconciles: a kept **future** `pinAt` re-arms its alarm
  on restore; a **past-due** `pinAt` that survived archive pins on restore (catch-up) and posts the
  notification; restoring a note whose window fully elapsed comes back unpinned with both times cleared.
- **Existing** archive/restore tests updated for the kept-schedule behaviour where they asserted clearing.

## Risk / effort

**Small.** One pure-function branch, one removed line + helper, one reconcile call. The risk surface is the
`PinSchedule` change (pure, table-tested) and making sure `archiveAll`'s raw unpin still doesn't clear
`unpinAt` (it doesn't). No new components, alarms, permissions, or schema.

## As-built notes (2026-06-12)

Built exactly as planned — the three edits, no surprises:

- **`PinSchedule.resolve`** — the archived branch now returns `pinAt`/`unpinAt` filtered to `it > now`
  (future kept, past-due cleared) with `targetPinned = isPinned` (never flips). The non-archived path is
  unchanged.
- **`NotePinner.archiveAll`** — dropped the `clearSchedule(note.id)` call; the per-note unpin stays a raw
  `setPinned(false)` so `unpinAt` survives. The private `clearSchedule` helper was removed (no other
  caller — `deleteAll` cancels alarms inline).
- **`NotePinner.unarchiveAll`** — now `setArchived(false)` then `reconcile(getById(...))` per note, then a
  single `reconcileService()` (a catch-up pin can start the service).
- **Unchanged as predicted:** `PinAlarmReceiver` (already guards `!note.isArchived` / `note.isPinned`),
  `reconcileAll`, `undoArchive`, backup.
- **Tests:** `PinScheduleTest` gained three archived cases (future-kept, both-future-kept, past-cleared);
  `NotePinnerTest`'s old `archive_dropsScheduleAndCancelsAlarms` became `archive_keepsScheduleAndArmedAlarm`
  plus three restore cases (re-arm future pin, catch-up past-due pin, fully-elapsed window clears).
- **Verified (2026-06-12):** `testDebugUnitTest` + `lintDebug` green (0 lint errors); full instrumented
  suite pending the next on-device run.
