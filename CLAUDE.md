# Stela — Notes as Notifications

A simple, **fully offline** Android note-taking app. Users create plain notes and
**pin** them as persistent notifications in the status bar. No ads, no analytics,
**no `INTERNET` permission**.

The authoritative design lives in [docs/2026-06-08-stela-design.md](docs/2026-06-08-stela-design.md).
Read it before making architectural decisions; this file is only a quick orientation.

## Tech stack

| Concern      | Choice                                              |
|--------------|-----------------------------------------------------|
| Language     | Kotlin                                              |
| UI           | Jetpack Compose (Material 3, dark mode)             |
| Storage      | Room (SQLite), offline                              |
| Background   | Foreground Service (`specialUse` type, API 34+)     |
| Boot restore | `BroadcastReceiver` on `BOOT_COMPLETED`             |
| Min SDK      | 26 (Android 8) · Target SDK: latest stable          |
| App id       | `dev.davidfdev.stela`                               |

## Architecture (one-line each)

- **`NoteRepository`** — single source of truth over Room; exposes notes as a `Flow` + CRUD. UI and service both read through it.
- **`NotificationController`** — the *only* class that touches `NotificationManager`. Builds ongoing notifications (Edit/Remove actions). Pin/unpin/refresh/re-assert.
- **`PinService`** — foreground service. Runs **iff** (≥1 pinned note) **OR** (quick-add enabled). Hosts the quick-add notification; re-asserts pins on start.
- **`BootReceiver`** — on `BOOT_COMPLETED`, starts `PinService` to re-pin flagged notes.
- **UI (Compose)** — NoteList · Editor · Settings. Talks to `NoteRepository`; pin/unpin via controller/service.

## Invariants — do not break

- **No `INTERNET` permission.** Ever. The app is offline by design.
- **Service lifecycle:** the service runs **iff** pinned-notes ≥ 1 OR quick-add enabled. One decision point drives start/stop.
- **`NotificationController` is the sole `NotificationManager` toucher.** Route all notification changes through it.
- **`PendingIntent`s use `FLAG_IMMUTABLE`** (API 31+).
- **Notification ID is derived deterministically from `note.id`** so a note always maps to the same notification.
- **Notification body tap does nothing** (by design); editing is via the explicit **Edit** action.
- **v1 ships a single default silhouette icon** — no icon picker. The `iconId` column exists (defaulted) only to keep the v2 icon set migration-free.

## Persistence reality

Modern Android cannot guarantee truly undismissable notifications or unkillable
processes. The honest promise: pinned notes **self-heal** (re-post if cleared),
**survive reboot** (via `BootReceiver`), and **resist background kill** (foreground
service). Onboarding guides battery-optimization exemption + OEM autostart.

## Conventions

- Follow TDD where practical: Room/DAO instrumented tests, `NoteRepository` unit
  tests over a fake DAO, pure-function unit test for the service start/stop rule.
- Keep the schema flat (forward-compatible with a future JSON export/import).
- Build phases are sequenced in §12 of the design doc — implement in order.

## Comments

- Write conservatively. Default to no comment; add one only when the WHY is non-obvious (a hidden constraint, a subtle invariant, a workaround for a specific bug).
- When a comment is warranted, keep `//` comments to a single concise line.
- Class and method `///` summaries may be multi-line.
- Describe what the target *is* / *does*; broader context belongs in external docs, not in code comments.
- Keep them self-contained and stable.
- **Comments shouldn't have dependencies. The acid test: a comment should not need to be edited unless the code immediately below it changes.**
- A reader who has never seen the rest of the codebase should be able to verify the comment against the local code alone.
- Do not refer to file paths or file names.