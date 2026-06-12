# Scheduled pins + auto-unpin (timed pinning) — implementation plan

> Status: **Implemented** · 2026-06-12 · v1.5.0. Built as planned (decisions and as-built notes at the
> end). The first real controls inside the editor-only **"Advanced"** area (see
> [2026-06-12-advanced-section.md](2026-06-12-advanced-section.md)). Design doc §12 planned-feature #4.
> Steps deliberately into light reminder territory, kept honest by the app's persistence ethos.

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

## The model (two independent one-shot timers + reconcile)

Each time is an **independent one-shot transition** that fires and then clears itself:

- **`pinAt` fires:** if the note is unpinned → pin it; if already pinned → **no-op**. Clear `pinAt`.
- **`unpinAt` fires:** if the note is pinned → unpin it; if already unpinned → **no-op**. Clear `unpinAt`.

No context-sensitive coupling between them or the manual Pin toggle — they're just timers (decision Q2/Q3).

A **reconcile pass** makes this robust to missed alarms (Doze, process kill, **reboot** — none guarantee
delivery) by computing the note's correct *current* state from the schedule and **snapping to it** (Q5):

| now vs schedule | reconciled state |
|---|---|
| `now < pinAt` | leave as-is; schedule the pin alarm |
| `pinAt ≤ now < unpinAt` | ensure **pinned**; clear `pinAt`; schedule the unpin alarm |
| `now ≥ unpinAt` (whole window past) | ensure **unpinned**; clear both times |

So a long-missed window simply lands on the correct end state (e.g. past `unpinAt` → never pins, both
cleared) with no churn. Inexact alarms are the *prompt*; reconcile is the safety net.

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
- **`NotePinner` integration** — `NotePinner` stays the single pin/unpin/archive/delete seam.
  - **Manual pin/unpin leave the schedule alone** — the timers simply no-op if they later fire into the
    state already reached (the Q2/Q3 simplification; no cancel-on-manual-toggle logic). *(Superseded
    2026-06-12 by the snooze slice: pinning now clears `pinAt`, unpinning clears `unpinAt`. See
    [2026-06-12-snooze.md](2026-06-12-snooze.md).)*
  - **archive** clears both times **and** cancels both alarms (a note can't be archived+pinned, so an
    archived note must never be auto-pinned — preserves the core invariant). Archiving therefore drops a
    note's schedule.
  - **delete/deleteAll** cancel both alarms (the row's times go with it).
  - **restore** (undo-delete) re-inserts and **reconciles** the note, so any future times reschedule.
  - **Save** persists the chosen times then reconciles the note (fire-now-if-past, else schedule).
  - `PinScheduler` is the AlarmManager wrapper it delegates to; the reconcile decision is a pure function.
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
  the future. *(As built: the row is **disabled while the note is pinned** — a pinned note has no pending
  future pin — see the disabled-row note below. The original Q2/Q3 "always shown, never disabled" is
  superseded.)*
- **"Unpin at"** row — same pattern; constrained to after `pinAt` (if set) and the future. *(As built:
  **disabled while the note is unpinned** — nothing to auto-unpin.)*
- **State** lives in `EditorUiState` (`pinAt`/`unpinAt`) + `EditorViewModel`, seeded from the loaded note.
- **Applied on Save** (recommended — Q1): the times are note data; Save persists them and (re)runs the
  scheduler for this note. For a **new** note, scheduling happens after the note is created (it needs an
  id), consistent with pin-on-save. Existing notes also apply on Save (atomic, avoids half-applied state).

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
2. **Advanced UI controls.** Pin-at / unpin-at pickers wired to the VM, applied on Save.
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

## Resolved decisions (2026-06-12)

1. **Apply on Save.** Schedule edits are note data; Save persists them then reconciles the note. Uniform
   for new and existing notes.
2/3. **No context-sensitive controls.** Both pickers always show. `pinAt` and `unpinAt` are independent
   one-shot timers: firing into an already-reached state is a **no-op** (pin-at when already pinned → no-op;
   unpin-at when already unpinned → no-op). No special pin-toggle coupling, no hiding/disabling.
   *(Superseded 2026-06-12: once the snooze slice made pin clear `pinAt` and unpin clear `unpinAt`, a
   pinned note can never carry a pending `pinAt` and vice-versa, so each row is now **disabled** in the
   state where its time is always empty — "Pin at" while pinned, "Unpin at" while unpinned.)*
4. **Validation:** pickers constrain to the future; unpin-at's minimum is `pinAt` when one is set.
5. **Reconcile snaps to the correct current state** (the table above). A window entirely in the past lands
   unpinned and clears both — a long-missed pin does **not** fire late.
6. **(a)** "Unpin at" **literally unpins** (does not follow the Removal Preference). **(b)** On **import**,
   schedules are **dropped** (imported notes arrive inert/unpinned, matching today's import); may revisit.
7. **List/widget indicator — deferred** to a later polish slice; Advanced is the v1 source of truth.
8. **Picker UX:** two-step Material3 **date → time** dialogs.
9. **Time/zone-change reconcile — deferred**; rely on boot + app-start reconcile for v1.
10. **Inexact-only**, no exact-alarm opt-in. Keeps the permission story clean.

## As-built notes (2026-06-12)

Built across the three planned slices; specifics where reality differed or is worth recording:

- **`PinSchedule`** is the pure reconcile decision (an `object`, like `ServiceLifecycle`): given
  `(isPinned, isArchived, pinAt, unpinAt, now)` it returns the target pin state and the surviving times.
  Fired transitions apply in time order, so a whole window in the past lands unpinned with no churn.
- **`PinScheduler`** (interface) + **`AlarmPinScheduler`** (the sole `AlarmManager` toucher) +
  **`NoopPinScheduler`** (the default, for code paths/tests that don't arm alarms). Alarm request code =
  `notificationId(noteId)` (= `noteId.toInt()`); pin vs unpin differ by intent action, so they're distinct
  PendingIntents. `FLAG_IMMUTABLE`.
- **`PinAlarmReceiver`** (manifest `exported="false"`, `goAsync`) pins/unpins via `NotePinner` and clears
  the spent time. **`NotePinner`** gained `applySchedule` (Save), `reconcileAll` (boot/app start), a private
  `reconcile`, and schedule-clearing on archive/delete/restore. A `now: () -> Long` was injected for
  testability. *(The snooze slice later made manual pin clear `pinAt` and manual unpin clear `unpinAt`.)*
- **Reconcile hooks:** `BootReceiver` (alarms don't survive reboot) and `StelaApp.onCreate` both call
  `reconcileAll`.
- **Data:** `Note.pinAt` / `Note.unpinAt` (nullable), schema **v4** (`MIGRATION_3_4`), `NoteDao.setSchedule`.
  Backup format **v3**: times export but, like pin state, **drop on import** (notes arrive inert).
- **UI:** `ScheduleControls` (a new file) renders the Pin-at / Unpin-at rows and a two-step Material3
  **date → time** picker; it's the content slot of the now-generic `AdvancedSection`. State lives in
  `EditorUiState` (`pinAt`/`unpinAt`), applied on Save via `pinner.applySchedule`. The date picker disables
  past days; a same-day earlier time is allowed and simply fires at once (a past time = pin now). Strings
  externalised.
- **Disabled rows** *(2026-06-12, follow-up)*: `ScheduleControls` takes `isPinned` and **disables** the
  "Pin at" row while pinned and the "Unpin at" row while unpinned (greyed label/value at 0.38 alpha, Set/
  Change button disabled). This follows directly from the snooze-slice clearing rules: a pinned note always
  has `pinAt == null` and an unpinned note always has `unpinAt == null`, so a disabled row always reads
  "Not set" — no value is ever stranded behind a disabled control. Tied to `EditorViewModel.pin/unpin` also
  clearing the matching time in `EditorUiState`/`loaded`, so the row clears live (and a later Save doesn't
  re-write the stale time).
- **Tests:** `PinScheduleTest` (the pure decision, table-driven), `NotePinnerTest` additions (apply/arm,
  past-pin fires, archive cancels, reconcile), an `EditorViewModelTest` schedule-persistence case, a
  `BackupCodecTest` drop-on-import case, and the 3→4 `MigrationTest`. Full picker interaction is covered by
  the manual matrix, not instrumented.
- **Verified (2026-06-12):** `assembleDebug`, `testDebugUnitTest` (125), `lintDebug` (0 errors), and all
  **40** instrumented tests green; emulator check of the editor's date→time picker flow (future-only days,
  set/clear/change).
