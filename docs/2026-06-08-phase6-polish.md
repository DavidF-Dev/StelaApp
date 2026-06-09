# Stela — Phase 6 (Polish) Implementation Plan

> Companion to [2026-06-08-stela-design.md](2026-06-08-stela-design.md) §12.
> The "everything before v1 ships" phase. Sequenced so localisation comes first
> (it's an invasive refactor that every later slice would otherwise redo).

**Status (2026-06-09): Phase 6 complete.** All slices 6a–6g plus the `LICENSE` file and the
notification-text refinements are done and verified (54 JVM unit + 21 instrumented tests
green; no `INTERNET`). The only outstanding item is the **real-device OEM matrix** (6g
Part C), deferred to the maintainer's later personal-device pass — see
[2026-06-09-6g-verification.md](2026-06-09-6g-verification.md).

## Slices & recommended order

- **6a — Localisation foundation** *(done)* — externalize all UI + notification
  strings to resources; `locales_config.xml` + `android:localeConfig` for the
  Android 13+ per-app language override; system locale otherwise. No in-app picker.
- **6b — Timestamps** *(done)* — created/modified: relative on list rows, absolute in
  editor; locale-aware formatting util (injectable clock, unit-tested).
- **6d — Share** *(done)* — Editor `ACTION_SEND` text/plain action (offline-safe).
- **6e — About** *(done)* — version, author (David F Dev), privacy promise, "how Stela
  works" honest-persistence note, static open-source licenses list.
- **6c — Multi-select + batch** *(done)* — NoteList selection mode (long-press →
  contextual bar), batch delete + batch pin/unpin, confirm dialog with `<plurals>`.
  Largest piece; overlaps the Phase 7 list pipeline, so last of the feature slices.
- **6f — Visual polish** *(done)* — adaptive launcher icon, refined notification
  silhouette (+ optional colored large icon), brand color scheme.
- **6g — API 33/34 + OEM matrix** *(audit + API-36 done; real-device matrix deferred)* —
  behavior verification across API levels and aggressive OEMs (real devices).

Also: the **`LICENSE` file** (GPL-3.0; repo housekeeping) and the deferred
**notification-text refinements** (pinned-note "Tap to edit or remove"; quick-add
pin-on-create) — **done** (see the housekeeping section below).

## Decisions to settle when planning a slice
- ~~Multi-select: include batch pin/unpin now (pinning exists) or delete-only?~~
  **Resolved (6c):** include batch pin/unpin via a single smart toggle.
- ~~Visual: dynamic-color (Material You) toggle and/or custom brand palette, or keep
  Material defaults?~~ **Resolved (6f):** custom brand palette seeded from indigo
  #4A57B5; no dynamic color, no toggle.
- ~~About: include a "view source" link (opens a browser; no `INTERNET` for Stela)?~~
  **Resolved (6e):** yes — `ACTION_VIEW` to the OS browser, Stela stays `INTERNET`-free.
- ~~Notification refinements: confirm the pinned-note content should become "Tap to
  edit or remove" (replaces showing the description).~~ **Resolved:** show the
  description when present, fall back to the hint only when it's empty; body tap now
  opens the editor; quick-add pins on create.

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

**Status (2026-06-09) — complete.** Tests: 51 JVM unit + 21 instrumented, all green; no
`INTERNET`. An About screen reached from Settings: app version, author, the privacy
promise, an honest "how Stela works" persistence note, the app's license, a static
(grouped) open-source licenses list, and a "View source" link. Version is read via
`appVersionName` (`PackageManager`); the link uses `openUrl` (`ACTION_VIEW`, mirroring
`shareNote`). New instrumented `AboutFlowTest` (Settings → About shows version/author/
View-source) grants POST_NOTIFICATIONS in `@BeforeClass` like the selection test.

**Confirmed decisions:**
- **Entry from Settings** — a new "About" section/row navigates to a dedicated About
  screen (`Routes.ABOUT`).
- **Version via `PackageManager`** (`getPackageInfo(packageName, 0).versionName`), so the
  one string we need doesn't require enabling the `buildConfig` build feature.
- **View source: included** — a row fires `ACTION_VIEW` to the OS browser for
  `https://github.com/DavidF-Dev/StelaApp`. Offline-safe: Stela only hands a URL to the
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

---

## Slice 6f — Visual polish

**Status (2026-06-09) — complete.** Tests: 51 JVM unit + 21 instrumented, all green; no
`INTERNET`. A brand-identity pass: an adaptive launcher icon (the app previously shipped
none — it fell back to the system default), a custom Material 3 brand palette seeded from
indigo, and a notification silhouette unified with the launcher glyph plus a branded
accent. Verified live on the emulator: the launcher drawer shows the indigo+pin icon, the
in-app FAB/accents are indigo, and the quick-add notification carries the brand accent.
(The notification's app-icon badge still showed the cached default robot from earlier
icon-less test installs — a SystemUI cache that clears on reboot, not a resource issue.)

**Confirmed decisions:**
- **Custom brand palette** — light + dark `ColorScheme`s seeded from **indigo #4A57B5**;
  no dynamic color, no Material You toggle (keeps the app simple). Honors the existing
  theme-mode setting.
- **No colored large icon** on notifications — the tinted small silhouette stays the only
  notification mark.
- **minSdk 26** ⇒ adaptive icons cover every device; **no legacy PNG fallbacks** needed.

**Launcher icon (adaptive, vector)**
- `drawable/ic_launcher_foreground.xml` — the pin glyph, white, centred in the adaptive
  safe zone (108dp canvas).
- `drawable/ic_launcher_monochrome.xml` — same glyph for Android 13 themed icons.
- `values/colors.xml` — `ic_launcher_background` = the indigo brand tone.
- `mipmap-anydpi-v26/ic_launcher.xml` (+ `ic_launcher_round.xml`) — `<adaptive-icon>`
  with background / foreground / monochrome layers.
- Manifest gains `android:icon` + `android:roundIcon`.

**Brand color scheme (Compose)**
- New `ui/Color.kt` — brand tones derived from the indigo seed; `StelaTheme` builds the
  light/dark `ColorScheme`s from them, overriding the key roles: primary + its containers,
  secondary / secondaryContainer (the latter drives the selection UI), and tertiary.
- The brand color is mirrored in `colors.xml` so the notification accent (`setColor`) and
  the icon background can reference it.

**Notification mark**
- `ic_stela_pin` is refined to share the launcher foreground glyph, so the launcher icon
  and the status-bar silhouette read as the same mark (still a white-on-transparent alpha
  mask that the system tints).
- Pinned / quick-add notifications gain `setColor(brand)` for a branded accent.

**Tasks**
1. Brand palette: `ui/Color.kt` + `StelaTheme` light/dark schemes; brand color in
   `colors.xml`.
2. Adaptive launcher icon (foreground/monochrome vectors, background color, mipmaps) +
   manifest wiring.
3. Unify `ic_stela_pin` with the launcher glyph; add `setColor(brand)` to the
   notification builders.
4. Verify (build; manual: launcher icon incl. themed-icon mode, in-app brand colors, a
   pinned-notification screenshot).

**Testing**: build + manual on the emulator with screenshots (icon, themed icon, app UI,
notification). Almost pure resources — no unit-testable logic.

**Deferred:** a branded splash screen (SplashScreen API) is outside the doc's 6f bullet;
revisit in a later pass if wanted.

---

## Notification refinements + LICENSE (housekeeping)

**Status (2026-06-09) — complete.** Tests: 54 JVM unit + 21 instrumented, all green; no
`INTERNET`. The two deferred notification-text refinements plus the repo's GPL-3.0 LICENSE
file. Verified live on the emulator: the quick-add deep link (`/new?pin=true`) creates a
note that is **pinned on save** (status-bar silhouette + filled row icon); the pinned
notification shows the **"Tap to edit or remove"** hint when the description is empty (the
description otherwise); and **tapping the notification body opens the editor**. CLAUDE.md
and the design doc were updated for the body-tap invariant change.

**Confirmed decisions:**
- **Pinned-note content line** — show the note **description** when non-blank; fall back
  to the **"Tap to edit or remove"** hint only when the description is empty (so a
  title-only note still has a useful, action-pointing line). `BigTextStyle` applies only
  when showing a description.
- **Pinned-note body tap now opens the editor** (was a no-op). This changes a documented
  invariant — CLAUDE.md and the design doc are updated to match; the hint wording is then
  accurate (body tap, or Edit, opens the editor).
- **Quick-add pin-on-create** — both the **New note** action *and* the notification body
  tap open a fresh editor that **pins the note when it is saved**. Pinning is
  permission-gated at the platform level: if `POST_NOTIFICATIONS` is denied the note still
  saves (unpinned); in practice quick-add already implies the permission.
- **LICENSE** — verbatim GPL-3.0 text at the repo root (fetched from gnu.org).

**Pinned notification (`AndroidNotificationController.post`)**
- Content line = `description` if non-blank, else the hint string; `BigTextStyle` only
  with a description.
- `setContentIntent(editIntent(note.id))` so the body tap opens the editor.

**Quick-add pin-on-create flow**
- The editor's new-note route gains an optional `pin` argument; the quick-add `New note`
  action and the body-tap deep links set `pin=true`.
- `EditorViewModel` reads the flag; on saving a *new* note with it set, it routes the
  created note through `NotePinner.pin`.

**Strings**: `notification_pinned_hint` = "Tap to edit or remove".

**Tasks**
1. `LICENSE` (GPL-3.0, fetched from gnu.org).
2. Pinned-note: description-or-hint content line + body-tap-opens-editor; hint string.
3. Pin-on-create: `pin` nav arg + deep links; `EditorViewModel` pins the new note on
   save (TDD).
4. Invariant docs: update CLAUDE.md + design doc (body tap, content line).
5. Verify (build + unit tests; manual/instrumented: pinned body tap opens editor;
   quick-add New note → the saved note is pinned).

**Testing**: JVM for the pin-on-create save logic (`EditorViewModelTest`); manual/
instrumented for the body-tap deep links and the notification text.

---

## Slice 6g — API 33/34 + OEM matrix

**Status (2026-06-09) — in-environment work complete; real-device matrix deferred.**
Parts A–B done; Part C written as a ready checklist for the maintainer's later
personal-device pass. Full results in
[2026-06-09-6g-verification.md](2026-06-09-6g-verification.md). Tests: 53 JVM unit + 21
instrumented, all green; no `INTERNET`. **Audit fix:** the two unguarded
`startForegroundService` call sites were hardened — `BootReceiver` now routes through the
single `serviceController.start()` seam (gaining the `canPostNotifications` guard), and
that seam catches `ForegroundServiceStartNotAllowedException`. Boot restore verified live
(`adb reboot` → pinned note re-posted; `specialUse` FGS started via the `BOOT_COMPLETED`
allow-list, confirming `specialUse` is not among the boot-blocked types on API 34+).

Behaviour verification across API levels and aggressive OEMs. Inherently a real-device
slice: this environment runs only the Pixel_8 (API 36) emulator (no cmdline-tools or other
system images installed). The maintainer has opted to **defer all real-device testing** to
a later session on their personal device.

**Confirmed decisions:**
- **Emulator scope:** deep-verify on the existing API 36 emulator + a full code-level API
  audit; no extra image downloads.
- **Bugs:** fix + verify unambiguous findings (tests where possible); report ambiguous
  ones for triage.
- **Real-device matrix: deferred** — written now as a checklist for the maintainer to run
  later on their personal device; not executed in-session.
- **OEM targets** the checklist covers: Samsung (One UI), Pixel/AOSP, and the
  OnePlus/Oppo/Realme (ColorOS/OxygenOS) family.

**Part A — API-behavior code audit (in-environment)**
Review every `Build.VERSION.SDK_INT` branch and the known API-level pitfalls:
- **FGS background-start (API 31+):** `PinServiceController.start()` calls
  `startForegroundService` without catching `ForegroundServiceStartNotAllowedException`;
  confirm every caller is foreground-exempt, else guard it.
- **FGS from boot + `specialUse` (API 34):** confirm `BootReceiver`'s start is permitted
  for the `specialUse` type, and that it tolerates a missing POST_NOTIFICATIONS grant (it
  currently bypasses the `canPostNotifications` guard that `PinServiceController` has).
- **Notification trampolines (API 31+):** confirm no action launches an Activity via a
  receiver/service trampoline (Edit/body = `getActivity`; Remove/reassert = `getBroadcast`
  with no Activity launch).
- **Ongoing-notification dismissibility (API 34):** confirm the `setDeleteIntent` self-heal
  covers the Android 14 swipe-away of ongoing notifications (`ReassertOnClearTest`).
- **POST_NOTIFICATIONS (API 33):** onboarding request + denial handling + the
  blocked-channel banner.
- **Per-app language / themed icon / adaptive icon (API 33/26):** resources resolve.

**Part B — API 36 emulator verification**
- Full JVM + instrumented suite.
- Manual scenarios: boot restore (`adb reboot` with a pinned note → re-pinned);
  swipe-dismiss self-heal; permission grant + deny paths; battery & autostart settings
  intents resolve; quick-add lifecycle (enable/disable, last-unpin → service stops).

**Part C — Real-device OEM checklist (deferred — written now, run later)**
A matrix the maintainer runs per device (Samsung / Pixel / OnePlus-Oppo-Realme) at a later
point on their personal device — produced now as a ready artifact, not executed in-session:
- Battery-optimization exemption flow; OEM autostart toggle present + honored.
- Kill-resistance: swipe from recents, then verify pinned notes persist / self-heal.
- Boot restore: reboot → pinned notes re-posted (after autostart allowed).
- Long-duration persistence (hours/overnight) under battery saver.
- Per-app language picker (Android 13+); lock-screen visibility toggle.

**Deliverable:** `docs/2026-06-09-6g-verification.md` — audit findings (+ any fixes),
API-36 emulator results, and the real-device checklist (left for the maintainer to fill in
per device).

**Tasks**
1. API-behavior audit; fix unambiguous bugs (+ tests); list ambiguous findings.
2. API 36 emulator verification (suite + manual scenarios incl. boot restore).
3. Write the real-device OEM checklist + verification doc (execution deferred to the
   maintainer's later personal-device pass).
4. Update CLAUDE.md / design doc if any behavior changes from fixes.

**Testing**: existing suite stays green; new tests for any audit fixes; the OEM matrix is
manual on real hardware.
