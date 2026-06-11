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
| Storage      | Room (SQLite, schema v3), offline                   |
| Preferences  | Jetpack DataStore (theme, quick-add, lock-screen, swipe-to-unpin, list sort/filter) |
| Backup       | JSON export/import via Storage Access Framework (kotlinx.serialization), offline |
| Background   | Foreground Service (`specialUse` type, API 34+)     |
| Boot restore | `BroadcastReceiver` on `BOOT_COMPLETED`             |
| Min SDK      | 26 (Android 8) · Target SDK: latest stable          |
| App id       | `dev.davidfdev.stela`                               |

## Architecture (one-line each)

- **`NoteRepository`** — single source of truth over Room; exposes notes as a `Flow` + CRUD. UI and service both read through it.
- **`SettingsRepository`** — single source of truth for preferences over DataStore (theme, quick-add, lock-screen, swipe-to-unpin, list sort/filter). UI, theme, controller, and service read through it.
- **Backup** — `BackupCodec` (pure JSON encode/decode over a versioned `NotesBackup` DTO, decoupled from the Room entity) + `BackupIo` (the `ContentResolver` seam). Export/import is driven from the Settings screen via the Storage Access Framework; import appends notes (fresh ids, unpinned, archived state preserved).
- **`NotificationController`** — the *only* class that touches `NotificationManager`. Builds ongoing pinned notifications (Edit/Unpin/Archive actions) plus the quick-add and minimal "running" service notifications. Pin/unpin/refresh/re-assert.
- **`NotePinner`** — the single seam for pin/unpin and archive/unarchive: persists the flag(s), posts/cancels the notification, and reconciles the service (start/stop/swap). UI and the notification actions both route through it.
- **`PinService`** — foreground service. Runs **iff** (≥1 pinned note) **OR** (quick-add enabled). Shows the quick-add notification, or a minimal "running" line when quick-add is off but notes are pinned; re-asserts pins on start.
- **`BootReceiver`** — on `BOOT_COMPLETED` or `MY_PACKAGE_REPLACED` (reboot or app update), starts `PinService` to re-pin flagged notes.
- **UI (Compose)** — NoteList (search · sort/filter · multi-select with select-all · undo-delete · archive) · Archived (restore/delete archived notes; reached from the list overflow) · Editor (emoji picker; pin and archive/restore) · Settings (theme, notification prefs, keep-alive guidance, backup export/import, clear all notes). Talks to the repositories; pin/unpin, archive, and clear via `NotePinner`.

## Invariants — do not break

- **No `INTERNET` permission.** Ever. The app is offline by design.
- **Service lifecycle:** the service runs **iff** pinned-notes ≥ 1 OR quick-add enabled. One decision point drives start/stop.
- **A note is never both archived and pinned.** Archiving unpins (and cancels the notification); pinning an archived note unarchives it. `NotePinner` enforces this, so archived notes never count toward the service-lifecycle rule, never appear in the list or widget, and are reachable only via the Archived screen and the editor.
- **`NotificationController` is the sole `NotificationManager` toucher.** Route all notification changes through it.
- **`PendingIntent`s use `FLAG_IMMUTABLE`** (API 31+).
- **Notification ID is derived deterministically from `note.id`** so a note always maps to the same notification.
- **Pinned-note body tap opens the editor** for that note (same as the **Edit** action); **Unpin** removes it from the status bar without deleting; **Archive** unpins and moves it to the Archived screen. (The quick-add notification's body tap opens a new note and pins it on save.)
- **The notification small icon is the single default silhouette** — no icon picker. A per-note **emoji** (v1.1) prefixes the display title in the list and notification (see `displayTitle`); the `iconId` column is now vestigial (the emoji superseded the planned v2 icon set).

## Persistence reality

Modern Android cannot guarantee truly undismissable notifications or unkillable
processes. The honest promise: pinned notes (and the foreground-service notification)
**self-heal** (re-post if cleared — on Android 14+, where ongoing notifications became
swipeable), **survive reboot** (via `BootReceiver`), and **resist background kill**
(foreground service). Onboarding guides battery-optimization exemption + OEM autostart.

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
- To build and install on a connected **physical device** (release by default; emulators are
  ignored), use `powershell -File scripts/install-device.ps1` — it confirms before installing
  and then launches the app. Pass `-DebugBuild`, `-NoBuild` (install the existing APK),
  `-Reinstall`, `-NoLaunch`, or `-Force`; see its `-?` help.
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