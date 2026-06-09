# Stela — Phase 6 (Polish) Implementation Plan

> Companion to [2026-06-08-stela-design.md](2026-06-08-stela-design.md) §12.
> The "everything before v1 ships" phase. Sequenced so localisation comes first
> (it's an invasive refactor that every later slice would otherwise redo).

## Slices & recommended order

- **6a — Localisation foundation** *(done)* — externalize all UI + notification
  strings to resources; `locales_config.xml` + `android:localeConfig` for the
  Android 13+ per-app language override; system locale otherwise. No in-app picker.
- **6b — Timestamps** *(done)* — created/modified: relative on list rows, absolute in
  editor; locale-aware formatting util (injectable clock, unit-tested).
- **6d — Share** *(done)* — Editor `ACTION_SEND` text/plain action (offline-safe).
- **6e — About** — version, author (David F Dev), privacy promise, "how Stela works"
  honest-persistence note, static open-source licenses list.
- **6c — Multi-select + batch** *(done)* — NoteList selection mode (long-press →
  contextual bar), batch delete + batch pin/unpin, confirm dialog with `<plurals>`.
  Largest piece; overlaps the Phase 7 list pipeline, so last of the feature slices.
- **6f — Visual polish** — adaptive launcher icon, refined notification silhouette
  (+ optional colored large icon), brand color scheme.
- **6g — API 33/34 + OEM matrix** — behavior verification across API levels and
  aggressive OEMs (real devices).

Also: the **`LICENSE` file** (GPL-3.0; repo housekeeping) and the deferred
**notification-text refinements** (pinned-note "Tap to edit or remove"; quick-add
pin-on-create) slot in where they fit.

## Decisions to settle when planning a slice
- ~~Multi-select: include batch pin/unpin now (pinning exists) or delete-only?~~
  **Resolved (6c):** include batch pin/unpin via a single smart toggle.
- Visual: dynamic-color (Material You) toggle and/or custom brand palette, or keep
  Material defaults?
- ~~About: include a "view source" link (opens a browser; no `INTERNET` for Stela)?~~
  **Resolved (6e):** yes — `ACTION_VIEW` to the OS browser, Stela stays `INTERNET`-free.
- Notification refinements: confirm the pinned-note content should become "Tap to
  edit or remove" (replaces showing the description).

---

## Slice 6a — Localisation foundation

**Status (2026-06-08) — complete.** Tests: 35 JVM unit + 19 instrumented, all green;
no `INTERNET`. ~45 strings externalized to `res/values/strings.xml` (English values
identical to the prior literals, so text-matching UI tests pass unchanged) across the
note list, editor, settings, and all notification text. `res/xml/locales_config.xml`
+ `android:localeConfig` give Android 13+ a free per-app language picker once a second
language ships; the system locale governs otherwise. No in-app picker (deferred). The
`specialUse` justification string stays English (for Play reviewers, not users).

**Adding a language later:** drop a `values-<lang>` strings file and add one
`<locale>` entry — no code changes.

---

## Slice 6b — Timestamps

**Status (2026-06-08) — complete.** Tests: 38 JVM unit + 19 instrumented, all green.
`TimeFormatter` (absolute via `java.time`, JVM-tested; relative via `DateUtils`); list
row shows a relative-time overline above the title; editor shows an absolute
"Created … · Updated …" caption when editing. Verified live. (Sub-minute notes render
"0 minutes ago" — standard `DateUtils` output; could be polished to "Just now" later.)

Pure presentation — the data (`createdAt`/`updatedAt`, epoch millis) already exists,
so no schema or DAO changes.

**Confirmed decisions:** relative time via `DateUtils`; list shows the modified time
as an **overline above the title**; the editor caption shows **created + modified**.

**What & where**
- **List row** — modified time, **relative** (`DateUtils.getRelativeTimeSpanString`,
  localized for free), as `ListItem` overlineContent above the title. Formatted
  against "now" at composition (no live ticking — fine for a list).
- **Editor** — **created and modified**, **absolute** + locale-formatted, as a small
  caption shown only when editing an existing note.

**Units**
- **`TimeFormatter`** util:
  - `absolute(epochMillis, locale, zone)` — `java.time` +
    `DateTimeFormatter.ofLocalizedDateTime(MEDIUM)`; pure, JVM-tested with a fixed
    `Instant`/`Locale`/`ZoneId`.
  - `relative(epochMillis, now)` — thin `DateUtils` call; verified by a light
    instrumented test.
- **`EditorUiState`** gains nullable `createdAt`/`updatedAt` (null for a new note);
  the editor formats them. `NoteRow` formats `note.updatedAt`.

**Tasks**
1. `TimeFormatter` (absolute TDD on JVM; relative via DateUtils). 2. List overline.
3. Editor caption + `EditorUiState` timestamps. 4. Verify (build + tests; manual:
list shows relative time, editor shows created/modified).

**Testing**: JVM for the absolute formatter; instrumented/manual for the relative
list text and the editor caption.

---

## Slice 6d — Share

**Status (2026-06-08) — complete.** Build + 38 JVM unit tests green. `shareNote`
helper fires `ACTION_SEND` text/plain via a chooser; Editor app-bar Share action shown
when the title or description is non-blank. Verified live (Share → system sheet with
the note text). No `INTERNET`.

Send the note's title + description as plain text via the system share sheet.

**Confirmed decisions:** Share is available **whenever the title or description is
non-blank** (including a new, unsaved note) and shares the **current on-screen text**;
**manual testing** (no espresso-intents dependency).

**Behaviour**
- A **Share icon in the Editor app bar** fires `Intent.ACTION_SEND` (`text/plain`)
  via `Intent.createChooser`, launched with `startActivity`.
- `EXTRA_SUBJECT` = title; `EXTRA_TEXT` = title + blank line + description (so apps
  that ignore the subject still get the title).
- **Offline invariant intact** — Stela only hands text to the OS; no `INTERNET`.

**Units**
- `shareNote(context, title, description)` helper builds + launches the chooser;
  `EditorRoute` passes `onShare` (reading current `state`) to `EditorScreen`. Share
  is shown when `state.title` or `state.description` is non-blank.
- Strings: `action_share`, `share_chooser_title`.

**Tasks**: 1. `shareNote` helper + strings. 2. Editor app-bar Share action (shown on
content). 3. Verify (build; manual: tap Share → system sheet with title + text).

**Testing**: manual (Intent launch isn't JVM-testable; espresso-intents declined).

---

## Slice 6c — Multi-select + batch

**Status (2026-06-09) — complete.** Tests: 51 JVM unit + 20 instrumented, all green; no
`INTERNET`. NoteList selection mode: long-press a row to enter, then batch pin/unpin
(one smart toggle) and batch delete, with a `<plurals>` delete confirm. The pin seam
grew `pinAll`/`unpinAll`/`delete`/`deleteAll` (each reconciling the service once), and
the editor's delete was retrofitted onto `NotePinner.delete`, closing the
orphaned-notification gap. Selection lives in the ViewModel (combined with the repo flow,
stale ids pruned). New instrumented `SelectionFlowTest` grants POST_NOTIFICATIONS in
`@BeforeClass` so first-run onboarding's permission dialog can't pause the Activity.

**Confirmed decisions:**
- **Full scope** — batch delete *and* batch pin/unpin (pinning already exists).
- **Single smart toggle** for pin/unpin: if any selected note is unpinned the action is
  **Pin** (pins every selected-unpinned; already-pinned stay) and runs through the
  notification-permission gate; if every selected note is already pinned the action is
  **Unpin**. The bar icon/label reflects which it will do.
- **Delete routes through `NotePinner`** — a new pin-seam delete unpins a pinned note's
  notification and reconciles the service; the editor's delete is retrofitted onto it,
  closing an orphaned-notification gap.
- A completed batch action (pin/unpin/delete) **exits selection mode**.
- **No "select all"** in this slice — deferred (see below).

**Selection model (ViewModel-owned)**
- `NoteListUiState` gains `selectedIds: Set<Long>`; `inSelectionMode` is derived
  (`selectedIds` non-empty). Selecting the first note enters the mode; deselecting the
  last note, the close action, or a completed batch action exits it.
- `uiState` combines the repo notes flow with a selection `StateFlow` and **prunes ids
  whose note no longer exists**, so selection can never reference a deleted note.
- VM methods: `toggleSelection(id)`, `clearSelection()`, `batchDelete()`,
  `batchTogglePin()` (computes Pin-vs-Unpin from the current selection).

**Pin seam (`NotePinner`)** — batch ops reconcile the service **once** at the end:
- `pinAll(notes)` / `unpinAll(ids)` — loop persist + notification, then one reconcile.
- `delete(note)` — unpin-if-pinned, delete, reconcile (single).
- `deleteAll(notes)` — looped delete, reconcile once.
- `EditorViewModel.delete()` switches to `pinner.delete(...)`.

**UI (`NoteListScreen`)**
- Long-press enters selection; tap toggles while selecting (else opens the note).
- `TopAppBar` swaps to a contextual bar: close (exit) · selected count · `[pin/unpin]`
  `[delete]`. Selected rows get a tonal highlight; the per-row pin icon is hidden in
  selection mode. The Add FAB is hidden, and system back exits selection (not the
  screen).
- Delete opens a `<plurals>` confirm dialog; pin/unpin apply with no confirm.

**Strings**: contextual-bar close cd, `notelist_selected_count` (plural),
`notelist_delete_confirm` (plural) + dialog title/confirm/cancel; reuse `action_pin`,
`action_unpin`, `action_delete`.

**Tasks**
1. `NotePinner` batch + delete seam (TDD; reconcile-once) + editor retrofit.
2. ViewModel selection state + batch actions (TDD: toggle, stale-id pruning,
   smart-toggle choice, batch delete).
3. NoteList selection UI (contextual bar, row highlight, FAB/back handling, confirm
   dialog) + strings/plurals.
4. Verify (build + tests; manual: long-press → batch pin/unpin/delete).

**Testing**: JVM for the ViewModel and pinner (assert reconcile-once via the fakes);
instrumented for long-press → contextual bar → delete; manual for the permission-gated
batch pin.

**Deferred — "Select all":** a select-all toggle in the contextual bar (overflow or a
title affordance) is out of scope here to keep the slice bounded; add it in a later
polish pass (or fold into this slice if it proves trivial).

---

## Slice 6e — About

**Status (2026-06-09) — planned.** An About screen reached from Settings: app version,
author, the privacy promise, an honest "how Stela works" persistence note, the app's
license, a static (grouped) open-source licenses list, and a "View source" link.

**Confirmed decisions:**
- **Entry from Settings** — a new "About" section/row navigates to a dedicated About
  screen (`Routes.ABOUT`).
- **Version via `PackageManager`** (`getPackageInfo(packageName, 0).versionName`), so the
  one string we need doesn't require enabling the `buildConfig` build feature.
- **View source: included** — a row fires `ACTION_VIEW` to the OS browser for
  `https://github.com/DavidF-Dev/StellaApp`. Offline-safe: Stela only hands a URL to the
  OS (same shape as Share), so still no `INTERNET`.
- **Licenses: inline, grouped by license** — one About screen; "Jetpack Compose,
  AndroidX, Kotlin — Apache License 2.0", and the app itself under GPL-3.0.

**Screen content (top → bottom)**
1. Header — "Stela" + version name.
2. Author — David F Dev.
3. Privacy — fully offline; no ads, no analytics, no `INTERNET` permission.
4. How Stela works — pinned notes self-heal (re-post if cleared), survive reboot, and
   resist background kill; modern Android can't guarantee truly undismissable
   notifications or unkillable processes.
5. License — Stela is GPL-3.0.
6. Open-source licenses — grouped static list (Apache-2.0).
7. View source — opens the repo in a browser.

**Units**
- `Routes.ABOUT` + nav composable → `AboutRoute(onBack)`; Settings gains an `onOpenAbout`
  callback, wired in the nav host.
- `AboutScreen` (Scaffold + back `TopAppBar`, reusing the Settings `SectionHeader` /
  `ListItem` idiom).
- `appVersionName(context)` helper (thin `PackageManager` read) so the screen carries no
  packaging logic inline.
- `openUrl(context, url)` helper (`ACTION_VIEW`) — mirrors `shareNote`.
- Strings: an About section/row label, title, `about_version` ("Version %1$s"), author,
  privacy, how-it-works, the app license line, the grouped licenses line, and
  `action_view_source`.

**Tasks**
1. Strings + `appVersionName` / `openUrl` helpers.
2. `AboutScreen` + `AboutRoute`; `Routes.ABOUT` and nav wiring.
3. Settings "About" section/row + `onOpenAbout`.
4. Verify (build + tests; instrumented: Settings → About shows version; manual: View
   source opens browser).

**Testing**: instrumented for Settings → About (version + key text visible); manual for
the View-source intent (`ACTION_VIEW` isn't JVM-testable — same call shape as Share).

Note: the GitHub repo is named **StellaApp** while the product is **Stela**; the
View-source link uses the actual remote URL above.
