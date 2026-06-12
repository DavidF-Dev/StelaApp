# Scheduled pins + auto-unpin (timed pinning) — implementation plan

> Status: **Planned** · 2026-06-12 · targets v1.5.0. The first real controls inside the editor-only
> **"Advanced"** area (see [2026-06-12-advanced-section.md](2026-06-12-advanced-section.md)). Design doc
> §12 planned-feature #4. Steps deliberately into light reminder territory, kept honest by the app's
> persistence ethos.

## Goal

Two per-note, time-based controls in the editor's **Advanced** section:

- **Pin at** — the note pins itself (posts its ongoing notification) at a chosen future time.
- **Unpin at** ("pin until") — a pinned note unpins itself at a chosen time. A *temporary* pin.

Either or both can be set. Together they define a window: "pin at 3pm, unpin at 5pm."

## Scope — locked decisions

- **Two controls: pin-at and unpin-at.** No recurring, no snooze (later extensions on this infra).
- **Inexact alarms.** `AlarmManager.setAndAllowWhileIdle` — fires *around* the chosen time, needs **no
  special permission** (no `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, no Play-policy friction). Honest
  wording in the UI ("around this time"); matches Stela's best-effort persistence promise.
- **No per-note notification overrides** (lock-screen / importance / removal) — out of scope.
- **Editor-only**, in the Advanced section. The popup never shows these (it reuses `NoteFields`, not
  `EditorScreen` — same enforcement as the Advanced container).
- **Auto-unpin literally unpins** (least destructive); following the Removal Preference is a possible later
  enhancement (open question Q6).

## The interval model (the key mental model)

Treat `pinAt` / `unpinAt` as defining the note's *intended pinned interval*, and make the system
**reconcile to the correct current state** rather than relying on alarms firing exactly. For "now":

| now vs schedule | intended state |
|---|---|
| `now < pinAt` | unpinned; a pin alarm is scheduled |
| `pinAt ≤ now < unpinAt` | pinned; an unpin alarm is scheduled |
| `now ≥ unpinAt` | unpinned; both times cleared |

This makes missed alarms (Doze, process kill, **reboot** — none of which guarantee delivery) self-correct:
a reconcile pass computes the right state and fixes it, instead of depending on a fired broadcast.
Inexact alarms are then just the *prompt* mechanism, with reconcile as the safety net.

## Data model

Add two nullable epoch-millis columns to `Note` (schema **v4**):

```kotlin
val pinAt: Long? = null,    // future time to auto-pin (null = none)
val unpinAt: Long? = null,  // future time to auto-unpin (null = none)
```

- **Migration 3→4:** `ALTER TABLE notes ADD COLUMN pinAt INTEGER` / `... unpinAt INTEGER` (nullable, no
  default). Register in `AppContainer` + add a `NoteDaoTest` migration test (schemas are exported/tested).
- **DAO:** a small `setSchedule(id, pinAt, unpinAt)` update (like `setPinned`/`setArchived`, leaves
  `updatedAt` untouched — scheduling isn't a content edit), plus the existing `getById`/`observeAll` carry
  the new fields.
- **Backup:** add `pinAt`/`unpinAt` to `NoteBackup` (format **v3**, both optional/defaulted). Import
  behaviour — see Q6.

## Architecture — the scheduler seam

Mirror the "single toucher" pattern (`NotificationController` is the sole `NotificationManager` toucher):

- **`PinScheduler`** (interface + `AlarmPinScheduler` impl) — **the sole `AlarmManager` toucher.**
  `schedulePin(noteId, atMillis)` · `scheduleUnpin(noteId, atMillis)` · `cancelPin(noteId)` ·
  `cancelUnpin(noteId)`. Uses `setAndAllowWhileIdle(RTC_WAKEUP, …)` with a deterministic, per-(note,kind)
  `PendingIntent` request code (distinct from notification ids) so an alarm can be replaced/cancelled.
- **`PinAlarmReceiver`** (manifest `exported="false"`, `goAsync` pattern like `NotificationActionReceiver`)
  — on fire, looks up the note and routes through `NotePinner` (pin or unpin), then clears the spent time
  field. Robust to a cold process start.
- **`NotePinner` integration** — `NotePinner` stays the single pin/unpin/archive/delete seam and gains
  schedule-clearing: manual **pin** clears `pinAt`, manual **unpin** clears `unpinAt`, **archive**/**delete**
  cancel *both* alarms and clear both fields (a note can't be archived+pinned, so it can't be
  archived+scheduled-to-pin either). `PinScheduler` is the AlarmManager wrapper it delegates to.
- **Reconcile pass** — `reconcileSchedules()` applies the interval-model table above across all notes:
  fires past-due transitions and (re)schedules future ones. Run it:
  - on **boot / app-update** — extend `BootReceiver` (alarms don't survive reboot), and
  - on **app start** — from `StelaApp` (covers process kill / missed Doze alarms).
  - *(optional)* on **time/zone change** — `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` (Q9).

No new permission. `RECEIVE_BOOT_COMPLETED` is already declared; no `INTERNET`, no foreground-service
change.

## Service lifecycle — unchanged

A scheduled-but-not-yet-pinned note is **not pinned** (`isPinned = false`), so it does **not** keep the
foreground service alive; the alarm is independent of the service. When `pinAt` fires → `NotePinner.pin`
→ `reconcileService` starts the service as usual. The `shouldRun(pinned ≥ 1 OR quick-add)` rule is
untouched.

## UI — the Advanced controls

Inside the (now non-empty) Advanced section in `EditorScreen`:

- **"Pin at"** row — empty → a "Set" affordance opening a **date picker → time picker** (Material3
  `DatePickerDialog` + `TimePicker`); set → shows the formatted time with a **clear** (×). Constrained to
  the future.
- **"Unpin at"** row — same pattern; constrained to after `pinAt` (if set) and the future.
- **State** lives in `EditorUiState` (`pinAt`/`unpinAt`) + `EditorViewModel`, seeded from the loaded note.
- **Applied on Save** (recommended — Q1): the times are note data; Save persists them and (re)runs the
  scheduler for this note. For a **new** note, scheduling happens after the note is created (it needs an
  id), consistent with pin-on-save. Existing notes also apply on Save (atomic, avoids half-applied state).
- **Honest copy** — "around this time" / a one-line note that timing is approximate and best-effort.

## Reliability framing (set expectations honestly)

Inexact alarms can drift (Doze), don't survive reboot (→ rescheduled by `BootReceiver`), and aggressive
OEMs may delay background alarms. The reconcile pass corrects state whenever the app next runs, so the
*end state* is reliable even when the *moment* isn't. This matches the existing honest-persistence promise
(self-heal, survive reboot, resist kill) — document it the same way.

## Invariants honoured

- **No `INTERNET`, no special permission** (inexact alarms only).
- **`NotificationController` stays the sole `NotificationManager` toucher; `PinScheduler` the sole
  `AlarmManager` toucher.**
- **A note is never both archived and pinned** — archiving cancels schedules too.
- **Service runs iff pinned ≥ 1 OR quick-add** — scheduled-unpinned notes don't count; rule unchanged.
- **Editor-only** — controls live in `EditorScreen`/Advanced, never the popup.
- **`PendingIntent`s use `FLAG_IMMUTABLE`**; notification id stays derived from `note.id` (alarm request
  codes are a separate deterministic scheme).

## Files

**New:** `pin/PinScheduler.kt` (+ `AlarmPinScheduler`), `pin/PinAlarmReceiver.kt`, schedule reconcile
logic (in the scheduler or a small coordinator), `ui/editor/` schedule-picker composables, an
`androidTest` migration test, unit tests for the interval/reconcile logic.

**Edited:** `data/Note.kt`, `data/NoteDao.kt`, `data/StelaDatabase.kt` (+ migration), `di/AppContainer.kt`,
`pin/NotePinner.kt`, `pin/BootReceiver.kt`, `StelaApp` (app-start reconcile), `data/Backup.kt` +
`BackupCodec`, `ui/editor/EditorViewModel.kt` + `EditorScreen.kt` (Advanced controls), `AndroidManifest.xml`
(register `PinAlarmReceiver`), `strings.xml`.

## Build order (slices)

1. **Data + scheduler infra (no UI).** Schema v4 + migration + DAO; `PinScheduler` + `PinAlarmReceiver`;
   `NotePinner` schedule-clearing; reconcile pass + boot/app-start hooks. Unit-test the interval/reconcile
   logic over a fake scheduler; instrumented migration test. *This is the bulk and the risk.*
2. **Advanced UI controls.** Pin-at / unpin-at pickers wired to the VM, applied on Save; honest copy.
3. **Backup + polish.** `pinAt`/`unpinAt` in the backup format; optional list/widget "scheduled" indicator
   (Q7); optional time-change reconcile (Q9).

## Testing

- **Unit:** the interval→state reconcile function (pure, table-driven over the three cases + boundaries);
  `NotePinner` clears/cancels schedules on pin/unpin/archive/delete (over a fake `PinScheduler`).
- **Instrumented:** the 3→4 migration; the Advanced pickers set/clear times and Save persists them
  (drive `EditorScreen` directly, as `AdvancedSectionTest` does). Firing a real alarm in a test is
  impractical — assert the reconcile/scheduler calls, not wall-clock delivery.

## Risk / effort

**Medium–large** — the biggest slice since the quick-note popup. The data/scheduler infra (Slice 1) is
where the complexity sits: the alarm/PendingIntent plumbing, the reconcile correctness, and the reboot
path. Contained by the interval model (reconcile is a pure function) and by reusing the existing seams
(`NotePinner`, the `goAsync` receiver pattern, `BootReceiver`). The UI is a normal picker slice.

## Open design questions

1. **Apply-on-save vs live.** Recommend schedule edits apply **on Save** (they're note data; atomic; works
   uniformly for new and existing notes). Live-apply for existing notes is the alternative — accept the
   recommendation, or prefer live?
2. **Pin toggle ↔ scheduled state.** While `pinAt` is set (future), the note is unpinned now, so the
   headline **Pin** toggle reads "unpinned". Tapping Pin should pin now **and cancel `pinAt`** (recommended).
   Where do we surface "Scheduled for 3pm" — only in Advanced, or also a hint near the pin toggle?
3. **Meaningless combinations.** `pinAt` on an already-pinned note is moot; `unpinAt` only matters for a
   note that is or will be pinned. Do we **disable/hide** pin-at when the note is already pinned, and only
   offer unpin-at then? (Recommended — context-sensitive controls.)
4. **Validation strictness.** Require `now < pinAt < unpinAt`? How hard should the pickers enforce it
   (clamp, disable invalid, or warn)? Recommend constraining the pickers (future-only; unpin-at min =
   pinAt).
5. **Catch-up semantics on reopen/boot.** Confirm the **interval model**: if the device was off across
   `pinAt`, reconcile pins on next run (if still before `unpinAt`); if it's already past `unpinAt`, it
   **never pins** and both clear. Agree, or do you want a missed pin to still fire late?
6. **What does "unpin at" do — and what about import?** (a) Auto-unpin = literally **unpin** (recommended),
   or follow the Removal Preference (could archive/delete)? (b) On **import**, do we keep schedules
   (reschedule future ones) or **drop them** so imported notes arrive inert/unpinned (recommended, matches
   today's "import appends unpinned")?
7. **List / widget indicator.** Show a "scheduled" marker (e.g. a clock) on a note that's set to pin
   later? Recommend **defer** to a polish slice; Advanced is the source of truth for v1.
8. **Picker UX.** Two-step Material3 **date → time** dialogs (recommended/standard), or hunt for a combined
   date-time control?
9. **Time/zone changes.** Also reconcile on `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED`, or rely on
   boot + app-start reconcile only? Recommend the latter for v1 (simpler); add the receiver if drift bites.
10. **Exact-alarm escape hatch.** Confirm we're committing to inexact-only (no settings toggle to opt into
    exact alarms). Recommended — keeps the permission story clean.
