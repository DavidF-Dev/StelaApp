# Stela — Phase 3 (Pinning) Implementation Plan

> Companion to [2026-06-08-stela-design.md](2026-06-08-stela-design.md) §12.
> Builds the notification pinning surface: `NotificationController`, ongoing
> notifications with Edit/Remove actions, the `pinned_notes` channel, the
> `POST_NOTIFICATIONS` runtime permission, and in-app pin/unpin controls.

**Goal:** A user can pin/unpin a note and see it as an ongoing notification with
working Edit and Remove actions, with notification permission handled gracefully.

**Tech stack:** builds on Phase 1 (Compose, Room, MVVM). Adds the platform
`NotificationManager`, `NotificationCompat`, a `BroadcastReceiver`, and the
`POST_NOTIFICATIONS` runtime permission.

---

## Scope boundary (Phase 3 vs Phase 4)

Phase 3 posts ongoing notifications **directly from the app process** — there is
**no foreground service yet**. Honest promise at the end of Phase 3: pinned notes
appear as ongoing notifications with Edit/Remove and stay correct **while the app
process is alive**.

**Deferred to Phase 4:** `PinService`, the service-lifecycle invariant, the
`quick_add` channel, re-assert-on-clear / self-healing, survive-process-death,
`BootReceiver`. **Deferred to Phase 5:** channel-disabled detection. **Phase 6:**
full silhouette icon set, optional colored large icon.

## Confirmed decisions

- **Pin controls:** a per-row pin toggle on the **NoteList** *and* a pin toggle in
  the **Editor** app bar.
- **List order:** unchanged — sorted by modified time; pinned rows show a **pin
  indicator** only (no float-to-top). Pinning is **not** a content edit and **must
  not bump `updatedAt`**.
- **Orchestration:** a thin **`NotePinner`** coordinator (repository + controller);
  the ViewModel and the action receiver both call it. Phase 4 inserts the service
  here, in one place.
- **Edit action:** opens `MainActivity` via a deep link to `editor/{noteId}`.
- **Remove action:** fires into `NotificationActionReceiver` (not the UI), which
  unpins (note is **not** deleted).
- **Channel:** `pinned_notes` only (default importance, silent, no badge).
- **Permission:** request `POST_NOTIFICATIONS` at **first pin** (API 33+); API < 33
  pins directly.

## Invariants honored

No `INTERNET`. `NotificationController` is the sole `NotificationManager` toucher.
All `PendingIntent`s use `FLAG_IMMUTABLE`. Notification id is derived
deterministically from `note.id`. Body tap does nothing (no content intent).

## Units

- **`NotificationIds.notificationId(noteId: Long): Int`** — pure, deterministic.
- **`NotificationController`** (interface) + **`AndroidNotificationController`**
  (impl) — creates the `pinned_notes` channel; `pin(note)` / `unpin(noteId)` /
  `refresh(note)`. The interface lets `NotePinner` be unit-tested with a fake.
- **`NotePinner`** — `pin(note)` / `unpin(noteId)`: persists pin state via the
  repository, then posts/cancels via the controller.
- **`NotificationActionReceiver : BroadcastReceiver`** — handles the Remove action
  with `goAsync()`; reads the note id from the intent, calls `NotePinner.unpin`.
  `exported=false`.
- **`NoteRepository.setPinned(noteId, isPinned)`** + DAO `setPinned` query — updates
  the flag only, leaving `updatedAt` untouched.
- **`res/drawable/ic_stela_pin.xml`** — the single default monochrome silhouette
  small icon.

## Control flow

1. **Pin (in app):** UI → `NotePinner.pin(note)` → `setPinned(true)` + `controller.pin`.
2. **Unpin (in app):** UI → `NotePinner.unpin(id)` → `setPinned(false)` + `controller.unpin`.
3. **Remove (notification):** action `PendingIntent` → `NotificationActionReceiver`
   → `NotePinner.unpin(id)`.
4. **Edit (notification):** action `PendingIntent` → `MainActivity` deep link → editor.

## Tasks (TDD-ordered increments)

### 1. Manifest permission
Declare `POST_NOTIFICATIONS`. **Gate:** merged manifest shows it and still **no
`INTERNET`**.

### 2. Default silhouette icon
Add `ic_stela_pin.xml` (monochrome vector, alpha mask). **Gate:** `assembleDebug`.

### 3. `notificationId` (pure, TDD)
JVM test: deterministic and stable for a given `note.id`; distinct ids differ.
Implement. **Gate:** unit test passes.

### 4. `setPinned` on repository + DAO (TDD)
JVM test over `FakeNoteDao`: `setPinned(id, true)` flips the flag and **leaves
`updatedAt` unchanged**; list order unchanged. Add DAO `@Query` update + repo
method. Update `FakeNoteDao`. **Gate:** unit tests pass.

### 5. `NotificationController` + `pinned_notes` channel (instrumented)
`AndroidNotificationController`: create channel; `pin`/`unpin`/`refresh` building
the ongoing notification (silhouette icon, title, BigText description, Edit +
Remove actions, ongoing, only-alert-once, no content intent). Instrumented test
(with `GrantPermissionRule` on API 33+): `pin` then assert the deterministic id is
in `NotificationManager.activeNotifications`; `unpin` removes it. **Gate:**
instrumented test passes.

### 6. `NotePinner` coordinator (TDD)
JVM test with a fake `NotificationController`: `pin` persists then posts; `unpin`
persists then cancels. Implement. **Gate:** unit tests pass.

### 7. `NotificationActionReceiver` (Remove)
Receiver with `goAsync()` → `NotePinner.unpin(id)`; register `exported=false`;
controller wires the Remove action `PendingIntent` (`FLAG_IMMUTABLE`) to it.
**Gate:** instrumented test — posting then firing the Remove intent cancels and
clears the flag.

### 8. Edit deep link
`MainActivity`/`StelaNavHost` accept a deep link to `editor/{noteId}`; controller
wires the Edit action to it. **Gate:** instrumented — Edit intent lands on the
editor for the right note.

### 9. UI: pin controls + permission flow
NoteList row pin toggle + Editor app-bar pin toggle, driven through the ViewModels
→ `NotePinner`. First pin on API 33+ requests `POST_NOTIFICATIONS`
(`rememberLauncherForActivityResult`); denial shows a Snackbar with "Open
settings". **Gate:** instrumented smoke — pin from the list shows the indicator and
an active notification.

### 10. Verify
`assembleDebug testDebugUnitTest connectedDebugAndroidTest` green; manifest
invariant re-checked.

## Testing summary
- **JVM:** `notificationId`, `setPinned` (no `updatedAt` bump), `NotePinner` (fake
  controller).
- **Instrumented:** controller posting/cancelling, Remove receiver, Edit deep link,
  pin-from-UI smoke. `POST_NOTIFICATIONS` via `GrantPermissionRule` (API 33+).

## Explicitly NOT in Phase 3
`PinService` and the service-lifecycle invariant, `quick_add` channel,
re-assert/self-healing, `BootReceiver`, channel-disabled detection, full icon set,
colored large icon — Phases 4–6.
