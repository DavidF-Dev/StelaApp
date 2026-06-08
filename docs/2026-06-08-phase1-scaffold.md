# Stela — Phase 1 (Scaffold) Implementation Plan

> Companion to [2026-06-08-stela-design.md](2026-06-08-stela-design.md) §12.
> Establishes a buildable Compose + Room project with the core invariants baked
> in, empty screens, and the test harness. **No notifications** (Phase 3+).

**Goal:** A buildable, navigable Compose app over Room with notes CRUD persistence
and a passing test harness — no notification/service code yet.

**Tech stack:** Kotlin 2.0.x · Jetpack Compose (Material 3, dark) · Room (KSP) ·
Compose Navigation · JDK 17 · Gradle 8.x.

---

## Locked configuration

- `applicationId = "dev.davidfdev.stela"` (overrides the design doc's `io.stela`)
- `minSdk = 26`, `compileSdk` / `targetSdk = 35` (Android 15, "latest stable")
- Room via **KSP** (not kapt)
- **Manifest declares no permissions in Phase 1.** `INTERNET` never; the
  FGS/notification/boot permissions arrive in Phases 3–4 when first used.
- Single-activity host + Compose Navigation. No DI framework in v1 — manual
  factory off the `Application`.

## Project shape

```
StelaApp/
├── settings.gradle.kts
├── build.gradle.kts                 # root: plugins via version catalog
├── gradle/libs.versions.toml        # version catalog (Compose BOM, Room, …)
├── gradle.properties                # AndroidX, JVM args
├── gradle/wrapper/…                 # wrapper 8.x
└── app/
    ├── build.gradle.kts             # applicationId dev.davidfdev.stela, minSdk 26
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml  # NO INTERNET permission
        │   ├── java/dev/davidfdev/stela/
        │   │   ├── StelaApp.kt              # Application
        │   │   ├── MainActivity.kt          # single-activity host
        │   │   ├── data/
        │   │   │   ├── Note.kt              # @Entity
        │   │   │   ├── NoteDao.kt           # @Dao, Flow CRUD
        │   │   │   ├── StelaDatabase.kt     # @Database
        │   │   │   └── NoteRepository.kt    # SSOT over DAO
        │   │   └── ui/
        │   │       ├── StelaTheme.kt        # Material3 dark
        │   │       ├── StelaNavHost.kt      # nav: list/editor/settings
        │   │       ├── notelist/NoteListScreen.kt
        │   │       ├── editor/EditorScreen.kt
        │   │       └── settings/SettingsScreen.kt
        │   └── res/                 # themes, strings, app icon
        ├── test/java/dev/davidfdev/stela/
        │   └── data/NoteRepositoryTest.kt   # unit, fake DAO
        └── androidTest/java/dev/davidfdev/stela/
            └── data/NoteDaoTest.kt          # instrumented, in-memory Room
```

## Tasks

### 1. Gradle wrapper + build files
Create wrapper (8.x), version catalog, root + app `build.gradle.kts`,
`gradle.properties`, `settings.gradle.kts`. Empty `MainActivity` placeholder.
**Gate:** `./gradlew assembleDebug` succeeds.

### 2. Manifest + theme + app skeleton
`AndroidManifest.xml` (no permissions), `StelaApp` Application, Material3 dark
theme, `MainActivity` hosting `StelaNavHost`.
**Gate:** app launches to an empty NoteList scaffold.

### 3. Room layer (TDD)
- `Note` entity (exact schema from design §4, incl. defaulted `iconId`).
- `NoteDao` — `observeAll(): Flow<List<Note>>` ordered by `updatedAt DESC`,
  `getById`, `upsert`, `delete`.
- `StelaDatabase`.
- Write `NoteDaoTest` (instrumented, in-memory DB) **first**: insert→observe,
  ordering, update bumps order, delete. Then implement until green.

**Gate:** instrumented DAO tests pass.

### 4. NoteRepository (TDD)
- `NoteRepository` wrapping the DAO: exposes `notes: Flow`, `create/update/delete`,
  stamps `createdAt` / `updatedAt`.
- Unit test over a hand-written fake DAO (no Android deps) — verifies timestamp
  stamping and delegation.

**Gate:** JVM unit tests pass.

### 5. Empty screens + navigation wiring
NoteList (FAB, empty state), Editor (title/description fields, save→repository),
Settings (placeholder toggles, no behavior). Wire nav routes. Repository provided
via a manual factory off `StelaApp`.
**Gate:** navigate list ↔ editor ↔ settings; creating a note persists and shows
in the list.

### 6. Commit & verify clean build
`./gradlew assembleDebug testDebugUnitTest` green; one commit per task.

## Explicitly NOT in Phase 1
NotificationController, PinService, BootReceiver, channels, runtime permissions,
silhouette icon set, OEM/battery helpers — Phases 3–6.
