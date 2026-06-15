# Per-note "Alert when pinned" (sound / vibration on pin) — implementation plan

> Status: **Implemented** · 2026-06-15. Built as planned across the three slices (as-built notes at the
> end). A per-note **Advanced** setting that makes a note's notification
> *announce itself* (sound and/or vibration, following the system/channel settings) at the moment it pins.
> Primary motivation: a **scheduled pin** (`pinAt`, or a snooze) currently posts silently — a note set to
> pin in 3 hours appears with no sound or haptic cue. Builds directly on
> [2026-06-12-scheduled-pins.md](2026-06-12-scheduled-pins.md), [2026-06-12-snooze.md](2026-06-12-snooze.md),
> and the editor-only Advanced area ([2026-06-12-advanced-section.md](2026-06-12-advanced-section.md)).
> Design doc §7 (channels) is the constraint anchor.

## Goal

A per-note, opt-in toggle in the editor's **Advanced** section — *"Alert when pinned"* — that, when on,
makes the note's ongoing notification play the default sound and/or vibrate **once** at the moment it
becomes pinned. "Following the System setting" means it inherits the channel's default sound/vibration,
which the user controls in system notification settings and which respects Do Not Disturb for free.

Default **off**. Existing notes and new notes are silent-on-pin unless the user opts in.

## The core constraint (why a second channel)

On API 26+ a **notification channel is immutable after creation** — the app cannot toggle sound/vibration
on the existing `pinned_notes` channel at runtime; only the user can, via system settings. The current
`pinned_notes` channel is deliberately silent (`IMPORTANCE_DEFAULT`, but `setSound(null, null)` +
`setVibrationEnabled(false)`), and every pinned post adds `setSilent(true)` + `setOnlyAlertOnce(true)`.

So "make it alert" requires a **second channel** that leaves sound/vibration at their defaults. A note that
has the flag set lives on the alerting channel **for its whole lifetime** (a note never switches channels —
Android ignores channel changes on an update, so consistency matters); whether it actually *makes noise* on
a given post is then governed per-post by `setSilent(...)`.

## Scope — locked decisions

- **Per-note, opt-in, default off.** A boolean `alertOnPin` column, surfaced as one toggle in the editor's
  Advanced section. No global setting.
- **Two channels for pinned notes.** Add `pinned_notes_alerting` (`IMPORTANCE_DEFAULT`, default
  sound + vibration). The existing silent `pinned_notes` is unchanged. A note routes to one or the other by
  its `alertOnPin` flag.
- **Follow the system/channel settings** for sound and vibration. Do **not** force a specific sound or
  vibration pattern — let the channel default carry it (user-adjustable, DND-respecting).
- **Alert once, at the pin moment only.** Never on the silent reposts (swipe self-heal, boot re-pin of an
  already-pinned note, lock-screen-pref reassert, content refresh).
- **Editor-only control**, in the Advanced section — same enforcement as the scheduled-pin controls (the
  popup reuses `NoteFields`, not `EditorScreen`, so it never shows Advanced; but a note *configured* in the
  editor still alerts when pinned from the popup, because alerting is driven by the persisted flag, not the
  UI surface).
- **No new permission.** `POST_NOTIFICATIONS` already covers it. No `INTERNET`, no foreground-service or
  alarm change. Offline invariants untouched.

## The alert rule (which pin transitions alert)

Alert **iff** a *single* note crosses unpinned→pinned via a user's single pin action **or** a scheduled
alarm, **and** the note's `alertOnPin` flag is set. Everything else is silent.

| Pin trigger | Call site | Alert? |
|---|---|---|
| Scheduled `pinAt` alarm fire | `PinAlarmReceiver` ACTION_PIN → `NotePinner.pin` | **yes** |
| Snooze re-pin (a `pinAt` fire) | `PinAlarmReceiver` ACTION_PIN → `NotePinner.pin` | **yes** |
| Editor Screen pin (existing note) | `EditorViewModel.pin()` → `NotePinner.pin` | **yes** |
| Editor Popout pin (existing note) | `EditorViewModel.pin()` (shared VM) → `NotePinner.pin` | **yes** |
| New note pin-on-save (editor / popup) | `EditorViewModel.save()` → `NotePinner.pin` | **yes** |
| List single-row pin | `NoteListViewModel.pin(note)` → `NotePinner.pin` | **yes** |
| List **batch** (multi-select) pin | `NoteListViewModel.batchTogglePin()` → `NotePinner.pinAll` | **no** |
| Undo-archive re-pin | `NoteListViewModel.undoArchive()` → `NotePinner.pinAll` | **no** |
| Undo-delete re-pin | `NoteListViewModel.undoDelete()` → `NotePinner.restore` → `controller.pin` | **no** |
| Reconcile catch-up (boot / app-start / unarchive) | `NotePinner.reconcile()` → `pin` | **no** *(see Q1)* |
| Swipe self-heal / boot re-pin / lock-screen reassert | reassert paths → `controller.pin` | **no** (repost, not a transition) |
| Content edit refresh | `controller.refresh` | **no** |

Notes on the non-obvious rows:
- **New note pin-on-save** alerts (sub-case of the editor/popout rule). Safe because `alertOnPin` defaults
  off — a fresh note only alerts if the user explicitly toggled it on during creation.
- **Reconcile catch-up is silent (recommended).** A `pinAt` that lapsed during downtime is pinned *late*
  when the app next runs; alerting then risks a burst of sounds at boot at an unexpected moment. The
  on-time alarm fire is the real "announce." A corollary: a `pinAt` set to a time already in the past fires
  via reconcile-on-save (not an alarm) and is therefore silent — acceptable, the user is present at save.
- **Batch never alerts**, even if exactly one note is selected — "single vs batch" is the `pin()` seam vs the
  `pinAll()` seam, not a runtime count.

## Architecture — the seam

The pin path already funnels through `NotePinner` and a single `controller.pin(note)`. Thread an explicit
`alert` signal, defaulting to **false** (silent), opted into only at the alerting call sites — so any new or
forgotten call site is silent by default.

- **`NotificationController.pin(note, alert: Boolean = false)`** (interface change).
  `AndroidNotificationController.post(note, alert)`:
  - `channel = if (note.alertOnPin) CHANNEL_PINNED_ALERTING else CHANNEL_PINNED`
  - `setSilent(!(alert && note.alertOnPin))` — noisy only when explicitly alerting *and* the note opted in.
  - keep `setOnlyAlertOnce(true)` as a backstop (an update to an already-showing notification won't re-alert).
  - everything else (icon, ongoing, actions, visibility, delete intent) unchanged.
- **`NotePinner.pin(note, alert: Boolean = false)`** and **`pinAll(notes, alert: Boolean = false)`**.
  - `pinAll` computes `val alertThis = alert && notes.size == 1` and passes `alertThis` per note to
    `controller.pin`. This **structurally guarantees the batch invariant** — a multi-note `pinAll` can never
    alert, regardless of caller. `pin(note, alert)` delegates to `pinAll(listOf(note), alert)`.
  - Alerting callers pass `alert = true`: `EditorViewModel.pin()`, `EditorViewModel.save()` (new-note pin),
    `NoteListViewModel.pin()`, `PinAlarmReceiver` ACTION_PIN.
  - Silent callers keep the default: `batchTogglePin` (`pinAll`), `undoArchive` (`pinAll`), `undoDelete`
    (`restore` → `controller.pin` default false), `reconcile` (`pin(note)` default false), and the reassert
    paths (`reassertPinned`, ACTION_REASSERT → `controller.pin` default false).
- **`FakeNotificationController` / `FakePinScheduler`** and `NotePinnerTest` doubles gain the `alert`
  parameter; assert the flag is forwarded only on the alerting seams.

## Channel design

In `AndroidNotificationController.init`, add alongside the three existing channels:

```kotlin
val pinnedAlerting = NotificationChannelCompat.Builder(CHANNEL_PINNED_ALERTING, IMPORTANCE_DEFAULT)
    .setName(context.getString(R.string.channel_pinned_alerting_name))
    .setShowBadge(false)
    // No setSound(null)/setVibrationEnabled(false): inherit channel defaults (system sound + vibration).
    .build()
```

- `IMPORTANCE_DEFAULT` is the minimum that permits sound and vibration (DEFAULT makes a sound but no
  heads-up; `IMPORTANCE_HIGH` would add a heads-up banner — see Q2).
- Name it so it reads clearly in system settings as distinct from the silent channel, e.g.
  *"Pinned note alerts"* vs the existing *"Pinned notes."* The user gains one extra row in the app's channel
  list — an accepted, minor cost.
- If the user disables the alerting channel, an opted-in note silently degrades to a normal silent pin
  (still posts, just no sound) — consistent with the existing channel-disabled story (design §7).

## Data model

Add one boolean column to `Note` (schema **v5**):

```kotlin
val alertOnPin: Boolean = false,   // sound/vibrate once when this note pins (default off)
```

- **Migration 4→5:** `ALTER TABLE notes ADD COLUMN alertOnPin INTEGER NOT NULL DEFAULT 0`. Register in
  `AppContainer`'s migration list; add the 4→5 case to `MigrationTest`; the exported schema JSON updates.
- **DAO:** a small `setAlertOnPin(id, value)` update (like `setPinned`/`setArchived`; leaves `updatedAt`
  untouched — it isn't a content edit). `getById`/`observeAll` carry the new field automatically.
- **Backup:** add `alertOnPin: Boolean = false` to `NoteBackup`; bump `BACKUP_VERSION` to **4**. Older files
  (v3 and earlier) decode with `alertOnPin = false`. Import behaviour follows the existing rule — imported
  notes arrive inert/unpinned, but the *flag* is data and round-trips like emoji/archived (see Q3).

## UI — the Advanced control

In `ScheduleControls` (the Advanced section's content slot in `EditorScreen`), add a toggle row beneath the
Pin-at / Unpin-at rows:

- A label *"Alert when pinned"* with supporting text *"Play a sound or vibration when this note pins"*, and a
  trailing `Switch`. Always enabled (it applies to any pin — manual or scheduled), unlike the schedule rows
  which gate on pin state.
- State lives in `EditorUiState.alertOnPin` + `EditorViewModel` (`onAlertOnPinChange`), seeded from the
  loaded note.
- Add `alertOnPin` to `EditorSnapshot` and `isDirty`, so the toggle participates in dirty-tracking and the
  back-to-discard confirm. Persist it on **Save** — for a new note via `repository.create(..., alertOnPin)`
  (or a follow-up `setAlertOnPin` after the id exists, matching how the schedule is applied post-create);
  for an existing note, fold it into the `contentChanged`/`setAlertOnPin` path. *(Decide whether toggling it
  alone counts as a content edit that bumps `updatedAt` — recommend **no**, like the schedule. See Q4.)*
- Strings externalised: `channel_pinned_alerting_name`, the toggle label and supporting text.

## Invariants honoured

- **No `INTERNET`, no new permission, no foreground-service / alarm change.**
- **`NotificationController` stays the sole `NotificationManager` toucher** — the new channel and the
  alerting decision both live inside it; callers only pass a boolean intent.
- **`PinScheduler` stays the sole `AlarmManager` toucher** — alerting is orthogonal to scheduling.
- **A note is never both archived and pinned** — unchanged; an archived note never pins, so never alerts.
- **Service runs iff pinned ≥ 1 OR quick-add** — unchanged; alerting doesn't affect the lifecycle rule.
- **`PendingIntent`s use `FLAG_IMMUTABLE`; notification id stays derived from `note.id`** — unchanged. The
  alerting note keeps the *same* id; only its channel and per-post silent flag differ.
- **Editor-only control** — the toggle lives in `EditorScreen`/Advanced, never the popup.

## Files

**Edited:** `data/Note.kt` (+ `alertOnPin`), `data/NoteDao.kt` (`setAlertOnPin`), `data/StelaDatabase.kt`
(version 5 + `MIGRATION_4_5`), `di/AppContainer.kt` (register migration), `data/Backup.kt` + `BackupCodec`
(format v4), `notifications/NotificationController.kt` (interface `pin(note, alert)`),
`notifications/AndroidNotificationController.kt` (new channel + `post(note, alert)`),
`notifications/FakeNotificationController.kt`, `pin/NotePinner.kt` (`pin`/`pinAll` alert param + per-site
wiring), `pin/PinAlarmReceiver.kt` (pass `alert = true`), `ui/editor/EditorViewModel.kt`
(`alertOnPin` state, snapshot, dirty, save) + `ui/notelist/NoteListViewModel.kt` (single-pin passes
`alert = true`), `ui/editor/ScheduleControls.kt` (toggle row), `res/values/strings.xml`.

**New tests:** 4→5 `MigrationTest` case; `NotificationControllerTest` cases (alerting channel exists at
`DEFAULT` importance and is not silenced; an `alertOnPin` note posts to it and a non-flagged note doesn't);
`NotePinnerTest` cases (alert forwarded only on single-note `pin`, never on `pinAll`/`restore`/`reconcile`);
`EditorViewModelTest` (flag persists + participates in dirty); `BackupCodecTest` (v4 round-trip; v3 decodes
to `false`).

## Build order (slices)

1. **Data + channel + seam (no UI).** Schema v5 + migration + `setAlertOnPin`; the alerting channel; the
   `alert` parameter through `NotificationController.pin` / `NotePinner.pin`/`pinAll` and the four alerting
   call sites; `PinAlarmReceiver` passes `alert = true`. Unit/instrumented tests for the channel and the
   seam (assert forwarding, not wall-clock sound). *This is the bulk and the only risk.*
2. **Advanced UI toggle.** `alertOnPin` in `EditorUiState`/VM/snapshot/dirty, the `ScheduleControls` row,
   persisted on Save.
3. **Backup + polish.** `alertOnPin` in the backup format (v4); copy review; verify channel naming reads
   clearly in system settings.

## Testing

- **Unit:** `NotePinner` forwards `alert` only on the single-note `pin` seam and never on `pinAll`
  (size > 1), `restore`, or `reconcile` (over the fake controller). `EditorViewModel` dirty/persist for the
  flag. `BackupCodec` v4 round-trip + v3-defaults-false.
- **Instrumented:** the 4→5 migration; `NotificationControllerTest` channel + routing assertions; the
  Advanced toggle sets the flag and Save persists it (drive `EditorScreen` directly). Real sound/vibration
  delivery is not asserted (impractical and device-dependent) — assert the channel/silent decision instead.
- **Manual matrix:** scheduled pin fires with sound on an opted-in note; manual editor/popup/list-single pin
  alerts; batch pin is silent; swipe-heal and boot re-pin are silent; disabling the alerting channel
  degrades to a silent pin.

## Risk / effort

**Small–medium.** No new permission, no alarm/service change, and the pin path already funnels through one
seam. The only subtleties are (a) the immutable-channel constraint (solved by a second channel + lifetime
channel consistency) and (b) ensuring `onlyAlertOnce` + the explicit `alert` flag suppress every repost path
so a swiped/healed note doesn't re-buzz — both contained by routing the decision through the single
controller and defaulting `alert` to false everywhere except the four alerting call sites.

## Open decisions

1. **Reconcile catch-up: silent or alert?** *Recommend silent* (avoids a boot-time burst; the on-time alarm
   is the real announce). This is the one genuine judgment call — confirm before building Slice 1. *(The plan
   above assumes silent: `reconcile` keeps the `alert = false` default.)*
2. **`IMPORTANCE_DEFAULT` vs `IMPORTANCE_HIGH`.** DEFAULT = sound/vibration, tray only. HIGH adds a heads-up
   banner — arguably nice for surfacing a scheduled pin, but more intrusive for an ongoing notification.
   *Recommend DEFAULT* (matches "make a sound or vibrate"); revisit if the reveal feels too quiet.
3. **Backup import of the flag.** Round-trip `alertOnPin` as note data (like emoji/archived), even though
   imported notes arrive unpinned — so re-importing preserves the user's choice. *Recommend yes.*
4. **Does toggling the flag bump `updatedAt`?** *Recommend no* (treat like the schedule — not a content
   edit), so flipping it doesn't reorder a Modified-sorted list.
5. **Manual-pin alerting confirmed in scope** (editor/popout/list-single). If it later feels noisy, the
   `alert` flag is per-call-site, so narrowing to automatic-only is a one-line change at the call sites.

## Resolved decisions (2026-06-15)

All five open questions were confirmed as recommended before building: reconcile catch-up stays **silent**
(Q1); the alerting channel is **`IMPORTANCE_DEFAULT`** (Q2); the flag **round-trips** through backup (Q3);
toggling it **does not bump `updatedAt`** (Q4); **manual pins alert** in scope (Q5).

## As-built notes (2026-06-15)

Built exactly as planned; specifics worth recording:

- **The alert decision is split across two seams.** `NotePinner` decides *intent* — `pin`/`pinAll` take an
  `alert` flag, and `pinAll` ANDs it with `notes.size == 1` so a batch structurally can never alert. The
  **controller** decides whether to actually make noise: `post(note, alert)` ANDs the intent with
  `note.alertOnPin`, routes the note to `CHANNEL_PINNED_ALERTING` vs the silent `CHANNEL_PINNED`, and sets
  `setSilent(!(alert && note.alertOnPin))`. `FakeNotificationController` mirrors that same AND so JVM tests
  assert real alerting behaviour (`alertedPins`).
- **Alerting call sites** pass `alert = true`: `EditorViewModel.pin()` and `save()` (new-note pin-on-save),
  `NoteListViewModel.pin()` (single row), `PinAlarmReceiver` ACTION_PIN (scheduled/snooze fire). Everything
  else — `pinAll` batch, `restore`, `reconcile`, `reassertPinned`, the swipe/boot reasserts,
  `PinService` re-assert-on-start — keeps the `alert = false` default and stays silent.
- **The alerting channel needs `setVibrationEnabled(true)` explicitly.** A fresh channel defaults vibration
  *off*; sound is on by the builder default. So the channel is `IMPORTANCE_DEFAULT` + default sound +
  vibration-enabled, all user-adjustable in system settings.
- **Data:** `Note.alertOnPin` (default false), schema **v5** (`MIGRATION_4_5`), `NoteDao.setAlertOnPin`,
  `NoteRepository.create(..., alertOnPin)` for the create path. Backup format **v4** round-trips the flag
  (decodes false on v3-and-earlier files); imported notes still arrive unpinned/unscheduled but keep the
  flag, like the archived flag.
- **UI:** a `PinAlertControl` (a `Switch` row) in `ScheduleControls`, rendered in the editor's Advanced
  section below the schedule rows; always enabled. `EditorUiState.alertOnPin` + `EditorSnapshot` participate
  in dirty-tracking; a flag-only save persists via `setAlertOnPin` without bumping `updatedAt`.
- **Tests:** 4→5 `MigrationTest`; `NotificationControllerTest` (alerting channel exists at `DEFAULT`, sound
  set, vibration on; opted-in note routes to the alerting channel, others stay silent); `NotePinnerTest`
  (single opted-in pin alerts; not-opted-in, batch, restore, and reconcile catch-up stay silent);
  `EditorViewModelTest` (flag persists for new/existing notes, no `updatedAt` bump, drives dirty);
  `NoteListViewModelTest` (single pin alerts, batch stays silent); `BackupCodecTest` (v4 round-trip,
  v3-defaults-false).
- **Verified (2026-06-15):** `assembleDebug`, `testDebugUnitTest` (203 green), `lintDebug` (0 errors), and
  the new instrumented `MigrationTest` / `NotificationControllerTest` / `AdvancedSectionTest` green on the
  Pixel_8 emulator. (`EditorBackNavigationTest.systemBack_…` fails identically on a clean baseline — a
  pre-existing window-focus flake, unrelated to this change.)
