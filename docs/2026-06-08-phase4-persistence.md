# Stela — Phase 4 (Persistence) Implementation Plan

> Companion to [2026-06-08-stela-design.md](2026-06-08-stela-design.md) §§2, 6, 8, 9, 12.
> Turns "pins while the app runs" (Phase 3) into durable pins: a foreground
> service keeps the process alive, re-asserts pins on start, hosts the quick-add
> entry, and a boot receiver restores pins after reboot.

**Goal:** Pinned notes survive backgrounding and reboot. The service runs exactly
when there is something to keep alive, hosts the quick-add notification, and
re-asserts pinned notes on start.

**Tech stack:** adds an Android foreground service (`specialUse`), a
`BroadcastReceiver` on `BOOT_COMPLETED`, and the foreground-service / boot
permissions. No new libraries. **Still no `INTERNET`.**

---

## Status (2026-06-08) — complete

All increments done. Tests: **24 JVM unit** + **12 instrumented**, all green; merged
manifest carries the FGS/boot permissions and still **no `INTERNET`**.

- Rule: `ServiceLifecycle.shouldRun`; `countPinned` on DAO/repository.
- Service: `PinService` (`specialUse`, typed `startForeground` on API 34+),
  re-asserts pins on start, self-stops when nothing is pinned.
- Coordination: `NotePinner` reconciles the service via `ServiceController` after
  every pin/unpin (start on first pin, stop on last unpin).
- Quick-add: `quick_add` channel + notification ("New Stela note" / "Tap to create
  a new note", body tap inert, **New note** + **View notes** actions).
- Boot: `BootReceiver` restarts the service on `BOOT_COMPLETED`.

Manual matrix verified on the API 36 emulator: pinning starts the foreground
service (`isForeground=true`, type `SPECIAL_USE`, quick-add hosted); unpinning the
last note stops it and clears all notifications; **a full reboot re-asserts the pin
and quick-add via `BootReceiver` without opening the app**.

Deferred (documented): quick-add "pin the new note" flow; pinned-note content line
"Tap to edit or remove". See the quick-add unit and "Explicitly NOT" below.

---

## Scope boundary (Phase 4 vs Phase 5)

**In Phase 4:** `PinService` (foreground, `specialUse`), re-assert-on-start, the
reused quick-add notification, `BootReceiver`, and the single start/stop rule.

**Deferred to Phase 5:** the quick-add **on/off toggle** and its three-state
behavior (§6), **re-assert-on-clear** (instant self-heal on swipe),
battery-optimization / OEM-autostart helpers, channel-disabled detection, and the
DataStore that backs the toggle.

## Confirmed decisions

- **Lifecycle in Phase 4: run iff pinned ≥ 1.** The quick-add toggle is a Phase 5
  setting, so the rule's `quickAddEnabled` input is `false` for now. The quick-add
  notification is still *shown while the service runs* (it is the service's
  mandatory FGS notification); it just isn't yet a reason to keep running with zero
  pins. That avoids needing notification permission at launch.
- **Single decision point in `NotePinner` via a `ServiceController`.** After each
  pin/unpin, `NotePinner` evaluates the rule and starts/stops the service through
  an injected abstraction (testable with a fake).
- **Self-heal = re-assert on service start + boot only.** A swiped notification
  heals on the next start or data change. Instant re-assert-on-clear (deleteIntent)
  is the explicit Phase 5 item. *(Phase 5 added it for pinned notes; the foreground-service
  notification itself gained the same deleteIntent self-heal in v1.2.0, once Android 14 made
  ongoing notifications swipeable — `NotificationActionReceiver.ACTION_REASSERT_SERVICE`.)*

## Invariants honored

No `INTERNET`. `NotificationController` builds every notification (the service uses
its quick-add notification for `startForeground`). `PendingIntent`s use
`FLAG_IMMUTABLE`. The service runs **iff** the rule says so — one rule, evaluated at
the pin/unpin seam and as a boot-time guard.

## Units

- **`ServiceLifecycle.shouldRun(pinnedCount: Int, quickAddEnabled: Boolean): Boolean`**
  — pure: `pinnedCount > 0 || quickAddEnabled`. The design's start/stop rule (§10).
- **`NoteDao.countPinned()` / `NoteRepository.countPinned()`** — pinned-note count
  for the rule.
- **`ServiceController`** (interface) + **`PinServiceController`** (impl) —
  `start()` (`startForegroundService`) / `stop()` (`stopService`).
- **`NotePinner`** (extended) — after pin/unpin, reconcile: read `countPinned`,
  evaluate `shouldRun(count, quickAddEnabled = false)`, start or stop the service.
- **`PinService`** — foreground `specialUse` service. `onStartCommand`:
  `startForeground` with the quick-add notification, then re-assert all pinned notes
  via `NotificationController`; if the rule is false (e.g. booted with no pins),
  `stopSelf`. Per-API `startForeground` (typed on API 34+, two-arg below).
- **`BootReceiver`** — on `BOOT_COMPLETED`, `startForegroundService(PinService)`.
- **`NotificationController`** (extended) — create the `quick_add` channel (low
  importance) and build the quick-add notification. Requested presentation
  (2026-06-08): title **"New Stela note"**, content **"Tap to create a new note"**,
  **body tap opens a fresh editor** (changed 2026-06-08 from no-op), two actions —
  **New note** (fresh editor, then pin it) and **View notes** (note list). All `PendingIntent`s `FLAG_IMMUTABLE`. *The "pin
  the new note" step needs a defined flow (likely pin-on-save) and may be addressed
  after the core service lands.*
- **Manifest** — `<service specialUse>` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
  justification; `<receiver>` for `BOOT_COMPLETED`; permissions `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`.

## Control flow

1. **Pin:** UI/Remove → `NotePinner.pin` → persist + `controller.pin` + reconcile →
   `ServiceController.start()` → `PinService` re-asserts + hosts quick-add.
2. **Unpin (last one):** `NotePinner.unpin` → persist + `controller.unpin` +
   reconcile → rule false → `ServiceController.stop()` → quick-add disappears.
3. **Boot:** `BootReceiver` → start `PinService` → re-assert pins; if none, `stopSelf`.
4. **Process-kill recovery:** the foreground service keeps the process alive, so
   pinned notifications persist; if the OS still kills it, the next start re-asserts.

## `specialUse` & Play readiness

The `specialUse` FGS type is the biggest publishing risk (§9). Draft justification
(in the manifest `<property>` now, ready for eventual review): *"Keeps user-pinned
notes posted as ongoing notifications and hosts a quick-add entry. Stela is an
offline notes-as-notifications app with no other background work, networking, or
data collection."*

## Tasks (increments)

1. **Manifest & permissions** — add the three FGS/boot permissions, the
   `<service specialUse>` + justification property, and the `<receiver>`. **Gate:**
   `assembleDebug`; merged manifest shows them and still **no `INTERNET`**.
2. **`shouldRun` rule (TDD, JVM)** — table of (pinnedCount, quickAddEnabled) →
   expected. **Gate:** unit test.
3. **`countPinned` (TDD)** — DAO query + repository method; JVM test over the fake,
   instrumented over Room. **Gate:** tests pass.
4. **quick_add channel + quick-add notification (instrumented)** — controller
   builds the quick-add notification and creates the channel; tap deep-links to a
   new editor. **Gate:** instrumented — channel exists; notification builds.
5. **`PinService` (instrumented / manual)** — `startForeground` + re-assert +
   self-stop-when-empty; per-API `startForeground`. **Gate:** instrumented start
   posts quick-add and re-asserts a pinned note where feasible; otherwise manual.
6. **`ServiceController` + `NotePinner` reconcile (TDD)** — fake `ServiceController`;
   assert start on first pin, stop on last unpin, no stop while pins remain.
   **Gate:** unit tests.
7. **`BootReceiver`** — `BOOT_COMPLETED` → start service. **Gate:** unit/manual —
   delivering the broadcast starts the service; reboot restores pins (manual).
8. **Verify** — `assembleDebug testDebugUnitTest connectedDebugAndroidTest` green;
   re-check no `INTERNET`; run the manual matrix below.

## Testing

- **Automated (JVM):** `shouldRun`; `NotePinner` reconcile with a fake
  `ServiceController` + `countPinned`.
- **Automated (instrumented, best-effort):** quick_add channel + quick-add
  notification; service start posts quick-add and re-asserts a pin.
- **Manual matrix (flagged, not automatable reliably):** reboot restores pins;
  swiping the app from recents keeps pins (FGS alive); unpinning the last note
  stops the service and removes the quick-add. FGS background-start limits and the
  protected `BOOT_COMPLETED` broadcast make these manual.

## Explicitly NOT in Phase 4
Quick-add on/off toggle and its three-state behavior, re-assert-on-clear
(deleteIntent), battery-optimization / OEM-autostart helpers, channel-disabled
detection, DataStore preferences — all Phase 5. Full icon set / colored large icon
— Phase 6.

**Notification-text refinements (done in Phase 6 — see the "Notification refinements +
LICENSE" slice in [2026-06-08-phase6-polish.md](2026-06-08-phase6-polish.md)):** the
pinned-note content line **"Tap to edit or remove"** (shown when the description is empty),
and the quick-add **"New note → pin it"** flow. The core service,
boot restore, and a functional quick-add (title/content + New note / View notes
actions, body tap inert) land in this phase; the pin-on-create nuance can follow.
