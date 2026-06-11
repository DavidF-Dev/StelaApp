# Home-screen widget (Jetpack Glance) — Plan

> Status: **Implemented** · 2026-06-10 (v1.2.0) · the §12 planned-features "home-screen widget" slice.
>
> **Verification:** `assembleDebug` + `testDebugUnitTest` green — the key risk, **Glance 1.1.1 against
> Kotlin 2.3.21 / Compose BOM 2026.05 / AGP 8.13, compiled cleanly** (two API-package import fixes:
> `actionStartActivity(Intent)` is in `glance.appwidget.action`, and `updateAll` needs its import). The
> widget is **registered and discoverable in the system widget picker** (correct "Stela notes" label +
> icon, confirmed on the emulator). The widget **rendered on the home screen** could not be captured via
> the harness — the Pixel launcher does not reliably accept a synthetic widget drag-and-drop through
> `adb input`. Follow-up option: a Glance unit test (`runGlanceAppWidgetUnitTest`, needs Robolectric) to
> assert the header / note rows / empty state deterministically.

**Goal:** A home-screen widget that extends Stela's glanceable, no-app-open spirit onto the launcher —
a **quick-add ＋** plus a scrollable list of **pinned notes**, tap-to-edit.

**Scope (locked):** one combined widget; rows **open the editor** (no in-widget unpin); theme **follows
system** light/dark. Reuses the existing seams — reads `NoteRepository`, acts via the existing deep
links — so no business logic is duplicated on the new render surface.

## Shape

- **Header:** "Stela" label (tap → note list) + a **＋ quick-add** button (→ new pinned note).
- **Body:** a `LazyColumn` of pinned notes, each showing `displayTitle(emoji, title)`; tap → that note's
  editor. **Empty state** when nothing is pinned ("No pinned notes", ＋ still available).
- **Theme:** `GlanceTheme` following the system day/night, seeded from the app's brand colour.

## Reuse points (already in the codebase)

- **Data:** `noteRepository.notes` (`Flow<List<Note>>`), via `(context.applicationContext as StelaApp).container`.
- **Deep links:** `AndroidNotificationController.DEEP_LINK_BASE` + `/new?pin=true`, `/list`, `/editor/{id}`
  — the same routes the quick-add notification uses. The widget builds `Intent(ACTION_VIEW, uri,
  MainActivity)` and fires it with Glance's `actionStartActivity`.
- **Display:** `Note.displayTitle` (emoji prefix), matching the list and notification.

## Components

| File | Responsibility |
|------|----------------|
| `gradle/libs.versions.toml` + `app/build.gradle.kts` | `androidx.glance:glance-appwidget` (1.1.1, latest stable). The default `GlanceTheme` follows system day/night, so `glance-material3` wasn't needed. |
| `ui/widget/StelaWidget.kt` | `GlanceAppWidget`: loads pinned notes in `provideGlance`, renders header + list/empty state, wires deep-link actions. |
| `ui/widget/StelaWidgetReceiver.kt` | `GlanceAppWidgetReceiver` — the manifest-registered `<receiver>`. |
| `res/xml/stela_widget_info.xml` | `appwidget-provider` metadata: resizable, `updatePeriodMillis=0` (event-driven), preview, min size. |
| `AndroidManifest.xml` | `<receiver>` for the receiver + `APPWIDGET_UPDATE` filter + widget-info meta-data. |
| `StelaApp.kt` | observe the pinned-notes subset (`distinctUntilChanged` on id + displayTitle) → `StelaWidget().updateAll(context)`. |

## Refresh model

Glance renders from a snapshot, so `provideGlance` loads `noteRepository.notes.first().filter { isPinned }`
and re-renders on `updateAll()`. The trigger is **event-driven** (not polled): a new observer in
`StelaApp` calls `updateAll` whenever the pinned set's displayed content changes (pin/unpin, a pinned
note's title/emoji edit, delete). The foreground `PinService` keeps the process alive whenever notes are
pinned, so the observer runs exactly when there is something to show.

The observer fires on its **first emission too**, not only on changes. An earlier version skipped the
first emission (assuming a bound widget already loaded its own up-to-date snapshot) — but that drops
the update when a note is created from the widget's **＋**: that path **cold-starts the process**, and
Room's first emission to the freshly-subscribed observer already contains the new note, so the
"skip first" guard swallowed it and the widget kept its stale pre-restart snapshot. Firing on the first
emission costs one idempotent redraw per launch and reconciles any drift from while the process was dead.

## Invariants

- No `INTERNET` (Glance is offline). `FLAG_IMMUTABLE` (Glance manages action `PendingIntent`s).
- Deterministic deep links, reused — no new routes.
- minSdk 26 is fine (Glance supports 23+).

## Build order

1. **Scaffold** — deps + `GlanceAppWidget` + receiver + widget XML + manifest; placeholder content. Also a
   **compatibility probe** for Glance 1.1.1 against Kotlin 2.3.21 / Compose BOM 2026.05 / AGP 8.13.
2. **Data + refresh** — load pinned notes, render rows + empty state; the `updateAll` observer in `StelaApp`.
3. **Actions** — ＋ → `new?pin=true`; header → `list`; row → `editor/{id}`.
4. **Theme + polish** — Glance Material3 (system day/night), sizing/resize, preview.
5. **Verify** — unit-test the notes→widget mapping; manual matrix (add, refresh on pin/edit/unpin/delete,
   tap → editor, ＋ → new pinned note, resize, light/dark, reboot).

## Risk

Glance 1.1.1 carries its own Compose-runtime usage and can lag the newest Compose compiler; Phase 1 stays
minimal to surface any incompatibility early. Fallback: pin a known-good Glance/Compose pairing.
