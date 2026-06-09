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
| UI           | Jetpack Compose (Material 3; Light/Dark/System theme) |
| Storage      | Room (SQLite), offline                              |
| Preferences  | Jetpack DataStore (theme, quick-add, lock-screen)   |
| Background   | Foreground Service (`specialUse` type, API 34+)     |
| Boot restore | `BroadcastReceiver` on `BOOT_COMPLETED`             |
| Min SDK      | 26 (Android 8) · Target SDK: latest stable          |
| App id       | `dev.davidfdev.stela`                               |

## Architecture (one-line each)

- **`NoteRepository`** — single source of truth over Room; exposes notes as a `Flow` + CRUD. UI and service both read through it.
- **`SettingsRepository`** — single source of truth for preferences over DataStore (theme, quick-add, lock-screen). UI, theme, controller, and service read through it.
- **`NotificationController`** — the *only* class that touches `NotificationManager`. Builds ongoing pinned notifications (Edit/Remove actions) plus the quick-add and minimal "running" service notifications. Pin/unpin/refresh/re-assert.
- **`NotePinner`** — the single seam for pin/unpin: persists the flag, posts/cancels the notification, and reconciles the service (start/stop/swap). UI and the Remove action both route through it.
- **`PinService`** — foreground service. Runs **iff** (≥1 pinned note) **OR** (quick-add enabled). Shows the quick-add notification, or a minimal "running" line when quick-add is off but notes are pinned; re-asserts pins on start.
- **`BootReceiver`** — on `BOOT_COMPLETED`, starts `PinService` to re-pin flagged notes.
- **UI (Compose)** — NoteList · Editor · Settings (theme, quick-add, lock-screen). Talks to the repositories; pin/unpin via `NotePinner`.

## Invariants — do not break

- **No `INTERNET` permission.** Ever. The app is offline by design.
- **Service lifecycle:** the service runs **iff** pinned-notes ≥ 1 OR quick-add enabled. One decision point drives start/stop.
- **`NotificationController` is the sole `NotificationManager` toucher.** Route all notification changes through it.
- **`PendingIntent`s use `FLAG_IMMUTABLE`** (API 31+).
- **Notification ID is derived deterministically from `note.id`** so a note always maps to the same notification.
- **Pinned-note body tap opens the editor** for that note (same as the **Edit** action); **Remove** unpins it without deleting. (The quick-add notification's body tap opens a new note and pins it on save.)
- **v1 ships a single default silhouette icon** — no icon picker. The `iconId` column exists (defaulted) only to keep the v2 icon set migration-free.

## Persistence reality

Modern Android cannot guarantee truly undismissable notifications or unkillable
processes. The honest promise: pinned notes **self-heal** (re-post if cleared),
**survive reboot** (via `BootReceiver`), and **resist background kill** (foreground
service). Onboarding guides battery-optimization exemption + OEM autostart.

## Building & testing

Android Studio's bundled JDK is used; `java` is not on PATH. From PowerShell at the
repo root (set `$ProgressPreference = 'SilentlyContinue'` first to avoid slow downloads):

    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    .\gradlew.bat assembleDebug              # build the debug APK
    .\gradlew.bat testDebugUnitTest          # JVM unit tests
    .\gradlew.bat connectedDebugAndroidTest  # instrumented tests (needs the emulator)

- Emulator AVD is **Pixel_8** (API 36, `emulator-5554`). If it has shut down,
  relaunch with `emulator.exe -avd Pixel_8` (under `%LOCALAPPDATA%\Android\Sdk\emulator`)
  and wait until `getprop sys.boot_completed` returns `1`.
- `connectedDebugAndroidTest` uninstalls the app afterward; `adb install -r` the debug
  APK to drive it manually. Grant notifications with `adb shell pm grant
  dev.davidfdev.stela android.permission.POST_NOTIFICATIONS`.
- The Gradle distribution is cached locally (the in-wrapper download was slow).

## Conventions

- Follow TDD where practical: Room/DAO instrumented tests, `NoteRepository` unit
  tests over a fake DAO, pure-function unit test for the service start/stop rule.
- Keep the schema flat (forward-compatible with a future JSON export/import).
- Build phases are sequenced in §12 of the design doc — implement in order.

## Git

- Do not run git actions (commit, push, branch, reset, etc.) unless explicitly
  directed to. Staging, history, and remotes are managed by the user.

## Comments

- Write conservatively. Default to no comment; add one only when the WHY is non-obvious (a hidden constraint, a subtle invariant, a workaround for a specific bug).
- When a comment is warranted, keep `//` comments to a single concise line.
- Class and method `///` summaries may be multi-line.
- Describe what the target *is* / *does*; broader context belongs in external docs, not in code comments.
- Keep them self-contained and stable.
- **Comments shouldn't have dependencies. The acid test: a comment should not need to be edited unless the code immediately below it changes.**
- A reader who has never seen the rest of the codebase should be able to verify the comment against the local code alone.
- Do not refer to file paths or file names.