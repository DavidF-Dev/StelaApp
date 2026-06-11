# Stela: Notes as Notifications

**Design document** · 2026-06-08 · Status: Draft for review

> A simple, offline note-taking app. Pin your notes to the notification tray so
> they're always one glance away — while using any app, with no ads, no tracking,
> and no Internet permission.

---

## 1. Overview

Stela lets a user create plain notes and **pin** them as persistent notifications
in the Android status bar / notification tray. A pinned note stays visible until
the user unpins it. The app is fully offline and requests no Internet permission.

**Name:** *Stela* — after the upright inscribed stone slabs of antiquity: a
message posted to remain. Tagline: *Notes as Notifications*.

**Distribution:** Sideload / personal initially, but designed to meet Google Play
requirements from the start (notification permission, foreground-service typing,
target SDK currency).

**License:** GPL-3.0 (copyleft; keeps derivatives open, F-Droid-friendly).
Copyright © David F Dev. Add a `LICENSE` file at the repo root.

### Goals

- Create, edit, and delete notes offline.
- Pin a note as a persistent, self-healing notification.
- A persistent "quick add" entry point for creating a note without opening the app.
- Survive reboot and aggressive background management as well as Android allows.
- No ads, no analytics, no Internet permission.

### Non-goals (v1)

- Cloud sync / accounts.
- Rich text, attachments, checklists.
- Reminders / scheduling / alarms.
- Widgets.

---

## 2. The persistence constraint (read first)

The product asks for notifications that are "unclearable" and an app that Android
"should not clear or kill." Modern Android resists all three. What we can
honestly deliver:

| Desired                          | Reality on modern Android                                                                 | Our approach |
|----------------------------------|-------------------------------------------------------------------------------------------|--------------|
| Notification can't be dismissed  | `setOngoing(true)` blocks swipe on older Android, but **API 34 lets users dismiss** ongoing notifications. | Make notes **self-healing**: re-post if removed. Not "impossible to dismiss." |
| App not killed in background     | The OS may kill any process. Only a **foreground service** reliably keeps it alive.        | Run a foreground service whenever there is something to keep alive. |
| Survive reboot                   | Notifications do not persist across reboot.                                                | `BOOT_COMPLETED` receiver re-pins notes and restarts the service. |
| Survive OEM battery management   | Samsung/Xiaomi/Oppo/Vivo aggressively kill apps; some need explicit autostart.            | Onboarding flow guides battery-optimization exemption **and** OEM autostart. |

**The honest promise:** pinned notes re-pin themselves if cleared, survive reboot,
and resist background kill via a foreground service — strong persistence, but not
literally undismissable.

---

## 3. Architecture

Kotlin + Jetpack Compose + Room. The system breaks into small, single-purpose
units communicating through well-defined interfaces.

```
            ┌──────────────────────────────┐
            │            UI (Compose)       │
            │  NoteList · Editor · Settings │
            └───────────────┬──────────────┘
                            │ reads / writes
                            ▼
            ┌──────────────────────────────┐
            │        NoteRepository         │  ← single source of truth
            │     (Room DAO + Flow)         │
            └───────┬───────────────┬───────┘
                    │               │
        observes    │               │ pin / unpin / refresh
                    ▼               ▼
   ┌────────────────────┐   ┌────────────────────────┐
   │     PinService     │   │  NotificationController │
   │ (foreground svc)   │──▶│  (only NotificationMgr  │
   │  keeps app alive   │   │   toucher)              │
   └────────┬───────────┘   └────────────────────────┘
            ▲                          ▲
            │ start on boot            │ action PendingIntents
   ┌────────┴───────────┐             (Edit / Remove)
   │    BootReceiver    │
   └────────────────────┘
```

### Units

- **`NoteRepository`** — single source of truth over Room. Exposes notes as a
  `Flow`, plus CRUD. Both UI and service read through it. *Depends on:* Room DAO.
- **`NotificationController`** — the only class that touches `NotificationManager`.
  Builds an ongoing notification per pinned note (title, description, silhouette
  icon, Edit + Remove actions). Pin / unpin / refresh / re-assert-all. *Depends
  on:* `NotificationManager`, app context.
- **`PinService`** — foreground service. Keeps the process alive whenever there is
  anything to keep alive (≥1 pinned note **or** quick-add enabled). Hosts the
  baseline notification (the quick-add entry — see §6). Re-asserts pinned notes
  on start. *Depends on:* `NoteRepository`, `NotificationController`.
- **`BootReceiver`** — on `BOOT_COMPLETED` or `MY_PACKAGE_REPLACED` (reboot or app
  update), starts `PinService`, which re-pins every note flagged pinned.
  *Depends on:* `PinService`.
- **UI (Compose)** — NoteList, Editor, Settings. Talks only to `NoteRepository`
  and asks `NotificationController` (via repository/service intents) to pin/unpin.

### Data flow

- **Edit a note:** UI → `NoteRepository` writes to Room → Flow emits →
  `NotificationController` refreshes that note's live notification (if pinned).
- **Pin toggle:** UI sets `isPinned` → repository persists → service ensures it is
  running → `NotificationController` posts/cancels the notification.
- **Notification action:** the Edit/Remove buttons fire a `PendingIntent` into a
  small `BroadcastReceiver`/service entry — **not** the UI — which calls the
  repository / controller.

---

## 4. Data model

Single Room entity:

```kotlin
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val iconId: String,      // stable key into the bundled icon set
    val isPinned: Boolean = false,
    val createdAt: Long,     // epoch millis
    val updatedAt: Long,     // epoch millis
)
```

- Notification ID is derived deterministically from `note.id` so a note always
  maps to the same notification.
- Schema is intentionally flat so a future **JSON export/import** (v2) is trivial.

---

## 5. Screens

1. **Note list** — all notes (pinned and unpinned), most-recently-updated first.
   Per-row pin toggle. "New note" FAB. Empty state explaining the concept.
   Each row shows the note's **modified time** (relative) — *Phase 6*.
   Long-press enters **multi-select** with a contextual bar for **batch delete**
   (and batch pin/unpin once pinning exists) — *Phase 6*.
   **Search** (fuzzy, over title + description), **sort** (creation / modified /
   title / icon), and **filter** (all / pinned / unpinned) refine the list —
   *Phase 7*. The chosen sort + filter persist in the DataStore preferences store
   (set from the list, not the Settings screen; search is transient). Filtering by
   **Pinned** replaces the earlier "pinned notes at top" toggle.
2. **Editor** — title, description, emoji picker, pin toggle.
   Reachable from: app list, FAB, the quick-add notification, and a pinned note's
   **Edit** action. Save persists via repository (and refreshes the notification
   if pinned). Shows **created and modified** timestamps (absolute) — *Phase 6*.
   A **Share** action sends the note's title + description as plain text via the
   system share sheet — *Phase 6* (no new permission; Stela stays offline).
   The **pin toggle is shown for new notes too** *(v1.2.0)* — for an unsaved note it sets the
   *intended* pin state (no live notification until save), **defaulting to pinned**, and the note is
   pinned on save if notifications are permitted; for an existing note it pins/unpins live. (Delete
   stays edit-only.)
3. **Settings** —
   - **Theme** — Light / Dark / Follow System, persisted via DataStore — *Phase 5*
     (the first consumer of the preferences store; default Follow System once
     shipped, dark until then).
   - **Hide on lock screen** — when on, pinned-note notifications are hidden on a
     secure lock screen (notification visibility SECRET) — *Phase 5* (default off).
   - Toggle the persistent **quick-add** notification.
   - **Swipe to unpin** — when on, swiping a pinned notification unpins it instead of
     self-healing — *v1.1* (default off).
   - **Battery optimisation** helper — a guidance dialog (manual steps + a best-effort
     "open settings" shortcut), since the system screen is unreliable on some OEMs.
   - **OEM autostart** helper — same guidance dialog; shown for any known aggressive OEM.
   - **About** — version, author (**David F Dev**), the privacy promise ("nothing
     leaves your device"), a short "how Stela works" honest-persistence note, and an
     open-source licences list (static) — *Phase 6*.
   - (v2 placeholder) Export / import.

### Pinned-note notification anatomy

- Small icon: the note's **silhouette** icon (monochrome; Android tints it).
- Large icon (optional): a colored version for in-tray richness.
- Title = the note title; **content line = the note description when present**, falling
  back to the **"Tap to edit or unpin"** hint only when the description is empty (so a
  title-only note still has a useful, action-pointing line). *(Implemented 2026-06-09.)*
- Actions: **Edit** (opens editor) · **Unpin** (the note is kept, not deleted). Swiping the
  notification also unpins it when **"Swipe to unpin"** is enabled; otherwise it self-heals.
  *(Unpin rename + swipe-to-unpin: 2026-06-10.)*
- **Tapping the body opens the editor** for that note. *(Implemented 2026-06-09; was a
  no-op in the original spec.)*

---

## 6. Quick-add notification (reuse strategy)

Android forces a foreground service to display its own ongoing notification, so we
**reuse** it rather than create a second permanent entry:

- The foreground service's mandatory notification **is** the quick-add entry.
  Requested presentation (2026-06-08; addressed later if needed):
  - **Title:** "New Stela note"
  - **Content:** "Tap to create a new note"  *(request read "Tap to create a new
    notification note").*
  - **Body tap opens a fresh editor that pins on save** *(pin-on-save implemented
    2026-06-09).* Two actions:
    - **New note** — opens the editor on a fresh note; the note is pinned once saved
      (via a `pin` flag on the new-note route). Same behaviour as the body tap.
    - **View notes** — opens the note list.
- If quick-add is **disabled** in settings but notes are still pinned, the service
  must keep a notification to stay alive → downgrade to a **minimal, silent,
  low-importance** "Stela is running" line on its own channel.
- If quick-add is **disabled** *and* nothing is pinned → **stop the service
  entirely** (nothing to keep alive, no notification shown).
- The quick-add and "running" notifications are **never shown on the lock screen**
  (`VISIBILITY_SECRET`) — they carry no note content. Only pinned notes appear there, and
  only when the user has not enabled "Hide on lock screen". *(2026-06-09.)*

One service, one baseline notification, no redundancy.

---

## 7. Notifications, channels & permissions

- **Channels** (API 26+):
  - `pinned_notes` — default importance, silent, no badge; hosts pinned notes.
  - `quick_add` — low importance; hosts the quick-add / service notification.
  - A user can *disable a channel*, silently breaking pinning. Detect this state
    (`areNotificationsEnabled` / channel importance == NONE) and prompt to
    re-enable rather than failing invisibly.
- **Runtime permission (API 33+):** request `POST_NOTIFICATIONS` at the first pin
  or during onboarding. Handle denial gracefully (explain, offer settings).
- **`PendingIntent` immutability (API 31+):** all action intents use
  `FLAG_IMMUTABLE`.
- **Icons are silhouettes:** the bundled small-icon set is designed as alpha masks
  (Android flattens/tints them); color is conveyed via the large icon only.

---

## 8. Foreground service & Play readiness

- **`foregroundServiceType="specialUse"`** (API 34+). Add the manifest
  `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>`
  with a written justification (drafted now, ready for eventual Play review).
- **Permissions declared:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
  `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. **No `INTERNET` permission.**
  *`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is intentionally NOT declared* — the
  battery helper opens the battery-optimisation settings screen rather than the direct
  request dialog, avoiding Google Play's allowlist restriction. Because that screen is
  unreliable on some OEMs (e.g. it crashes OxygenOS Settings), the battery and auto-start
  rows open a **guidance dialog** — manual steps plus a best-effort "open settings"
  shortcut and a "may not work on every device" note — rather than launching the intent
  blindly. *(2026-06-09.)*
- **Min SDK 26** (notification channels baseline; ~99% device reach).
  **Target SDK:** latest stable (Play currency requirement).
- **Service lifecycle invariant:** the service runs **iff** (pinned notes ≥ 1) OR
  (quick-add enabled). The repository's pin-state changes and the settings toggle
  both drive start/stop through one decision point.

---

## 9. Edge cases & risks

- **API 34 dismissal:** users can swipe ongoing notes away → re-assert on service
  start / next repository emit; document the behavior honestly.
- **OEM autostart:** `BOOT_COMPLETED` may not fire without it → treat boot-restore as
  best-effort. The auto-start row shows for any known aggressive-OEM manufacturer (the
  `autostartTarget` map) and opens the guidance dialog; the deep-link to the OEM's
  auto-launch screen is best-effort and often stale, so the dialog's manual steps are the
  reliable path. *(2026-06-09.)*
- **OEM active-notification caps (~24–50):** "unlimited notes" is fine, but
  pinning hundreds may hit an OEM ceiling → documented expectation, not a blocker.
- **Channel disabled by user:** detect and prompt (see §7).
- **Notification → editor return (implemented, v1.1.x):** opening the editor from a
  notification now returns the user to **where they were** rather than always to the Note
  List. A cold/external entry (`onCreate` with an `ACTION_VIEW` deep link and no saved
  state) `finish()`es the task on completion — back to home / the previous app; a warm entry
  (`onNewIntent`) and in-app navigation `popBackStack` instead. The decision is a
  `finishOnEditorDone` flag set in `onCreate` (classified by `isNotificationDeepLink`),
  reset on `onNewIntent`, and persisted across recreation in `onSaveInstanceState`; it is
  threaded into the editor deep links, and a `BackHandler` routes system back through the
  same exit. *Warm fidelity:* a warm entry pops to the **list** (the deep link rebuilds the
  `[List → Editor]` back stack), not an arbitrary prior screen such as Settings — accepted,
  since the list is the usual warm screen. Verified: cold→finish (device), in-app→list
  (instrumented); warm→list shares the in-app pop mechanism.
- **Play `specialUse` review:** the biggest publishing risk; mitigated by the
  justification string and honest functionality.

---

## 10. Testing strategy

- **Room / DAO:** instrumented tests for CRUD and ordering.
- **`NoteRepository`:** unit tests over a fake/in-memory DAO.
- **Service lifecycle decision** (run iff pinned≥1 OR quick-add): pure-function
  unit tests for the start/stop rule.
- **`NotificationController` mapping logic:** unit-test note→notification-content
  building (where separable from the framework), instrumented smoke test for
  posting.
- **Manual matrix:** a stock Android emulator + at least one aggressive OEM device
  (Samsung/Xiaomi) for reboot, dismissal, and battery-kill behavior.

---

## 11. Tech stack summary

| Concern        | Choice                                   |
|----------------|------------------------------------------|
| Language       | Kotlin                                   |
| UI             | Jetpack Compose (Material 3; dark v1, theme choice in Phase 5) |
| Storage        | Room (SQLite), offline                   |
| Preferences    | Jetpack DataStore (theme + settings) — Phase 5 |
| Background     | Foreground Service                       |
| Boot restore   | `BroadcastReceiver` on `BOOT_COMPLETED`  |
| Min SDK        | 26 (Android 8)                           |
| Target SDK     | latest stable                            |
| Icons          | Bundled silhouette vector drawables      |
| Internet       | **none** (permission not declared)       |

---

## 12. Phased build plan

1. **Scaffold** — Compose project, Room, navigation, empty screens, package
   `io.stela` (or chosen id).
2. **Notes CRUD** — list + editor + icon picker, fully working offline. No
   notifications yet.
3. **Pinning** — `NotificationController`; ongoing notifications with Edit/Remove
   actions; channels; runtime permission.
4. **Persistence** — `PinService` foreground service + reused quick-add
   notification + `BootReceiver`; service lifecycle invariant.
5. **Resilience & settings** — battery-optimization + OEM-autostart helpers,
   re-assert-on-clear, settings toggle, channel-disabled detection. Introduces the
   **DataStore preferences store**; its first consumer is **theme selection**
   (Light / Dark / Follow System).
6. **Polish** — silhouette icon set, full theming, **created/modified timestamps**
   (relative on the list, absolute in the editor), **multi-select + batch delete**
   (extends to batch pin/unpin), **share note** (title + description as plain text
   via the system share sheet), an **About screen** (version, author, privacy note,
   open-source licenses), **string externalisation for localisation** (move all UI
   and notification strings to resources, with `<plurals>` and locale-aware dates —
   the i18n enabler, done early in the phase), API 33/34 behavior, manual OEM matrix.
7. **List querying** *(implemented — see [2026-06-10-phase7-list-querying.md](2026-06-10-phase7-list-querying.md))* —
   **search** (case-insensitive substring over title + description), **sort**
   (modified / created / title), **filter** (all / pinned / unpinned), all derived in one
   in-memory `applyQuery` pass over the notes flow in the list ViewModel. Sort/filter persist
   via the Phase 5 preferences store; search is transient. (The planned *sort-by-icon* was
   dropped — the per-note emoji superseded the v2 icon set. A sort-direction toggle is a
   queued follow-up.)

**Scope additions (2026-06-08):** theme selection (→ Phase 5), timestamp display
and multi-select / batch actions (→ Phase 6), plain-text **share** (→ Phase 6),
**About screen** (→ Phase 6), **localisation** via string externalisation (→ early
Phase 6), and **list querying** — search / sort / filter (→ Phase 7) — were added
after Phase 1. The repo is licensed **GPL-3.0** (`LICENSE` file added in Phase 6). All
features are deferred to their natural phases rather than implemented eagerly. Notes:
search, sort, and filter share **one in-memory derivation** over the notes flow
(fine at personal scale; move to SQL `WHERE`/`ORDER BY` and Room FTS only if a
library ever grows to thousands of notes); persisted sort/filter selections depend
on the Phase 5 preferences store; the planned **sort-by-icon** was dropped (the per-note
emoji superseded the v2 icon set); **share** keeps the app offline — Stela only hands
plain text to the OS share sheet and declares no `INTERNET`.

**v1.1.0 (released 2026/06/10):** auto-capitalise the editor fields, a **"swipe to unpin"**
setting, a per-note **emoji** shown in the list + notification title (via a derived
`displayTitle`; `Note.emoji` column, DB v2 — the emoji supersedes the deferred v2 icon-set
picker), **notification-→-editor return-to-context** (§9), and the **list-querying** work —
search / sort / filter with an active-filter chip and a **sort-direction toggle**, plus
**select all** in multi-select. See [2026-06-10-v1.1-features.md](2026-06-10-v1.1-features.md)
and [2026-06-10-phase7-list-querying.md](2026-06-10-phase7-list-querying.md). The mooted
**"Tap to edit" setting was dropped** — tapping already opens the editor everywhere, so there
is nothing for it to toggle to.

**v1.2.0 (released 2026/06/11):** bug fixes — the emoji picker re-hosted in a themed, scrollable
Material `BottomSheetDialog`, the emoji-search grid no longer pushed up by the keyboard, and the
quick-add notification self-healing when swiped away (Android 14+) — plus **Planned features** 1–3
and 5 below (**undo-delete**, **JSON export/import**, the **home-screen widget**, and
**emoji-picker search**), the **new-note pin toggle**, and a **branded splash screen**. Only item 4
(scheduled/timed pins) remains, deferred to a later version. See [CHANGELOG.md](../CHANGELOG.md).

**Planned features (prioritized, 2026-06-10):** each is its own slice (spec → plan →
implement); all keep the no-`INTERNET` invariant.
1. **Undo-delete** *(done — v1.2.0)* — deleting from the list shows an "Undo" snackbar that
   restores the notes (re-pinning any that were pinned), via a `NotePinner.restore` seam that
   re-inserts each note as it was (preserving id/timestamps, so the same notification
   re-posts). The confirm dialogs were kept in all cases; the editor's single delete keeps its
   confirm dialog without an undo (cross-screen — a possible follow-up).
2. **JSON export/import** *(done — v1.2.0)* — back up and restore all notes to a file via the
   Storage Access Framework (offline, no `INTERNET`). A versioned `NotesBackup` DTO + a pure
   `BackupCodec` keep the file format decoupled from the Room entity; the `BackupIo` seam
   wraps the `ContentResolver`. Import **appends** (each note gets a fresh id, so it never
   overwrites existing notes) and brings notes in **unpinned**. Settings → Backup → Export /
   Import. See CHANGELOG.
3. **Home-screen widget** *(done — v1.2.0; see [2026-06-10-home-screen-widget.md](2026-06-10-home-screen-widget.md))* —
   a Jetpack Glance widget extending the glanceable, no-app-open spirit onto the home screen: a
   quick-add **＋** plus a scrollable list of **pinned notes** (tap → editor), one combined widget,
   theme follows system. Reads `NoteRepository` and reuses the existing deep links (`/new?pin=true`,
   `/list`, `/editor/{id}`) — no duplicated logic; refresh is event-driven via a `StelaApp` observer →
   `updateAll`. Medium effort (a separate render surface).
4. **Advanced note settings (scheduled/timed pins)** — pin a note as a notification at a
   chosen time (`AlarmManager` + exact-alarm permission). A natural extension of "pin as
   notification" and the largest; a deliberate step toward reminder territory, so confirm
   scope before building.
5. **Emoji-picker search** *(done — v1.2.0; see [2026-06-10-emoji-search-vanniktech.md](2026-06-10-emoji-search-vanniktech.md))* —
   the AndroidX `EmojiPickerView` had no search and exposed no way to add one, so it was replaced with
   **vanniktech/Emoji**'s standalone `EmojiView` (search on by default, matches emoji by shortcode),
   kept in the existing themed `BottomSheetDialog` host. Apache-2.0 (GPL-3 compatible). Renders from the
   bundled-sprite **`emoji-google`** provider — every emoji is a colour bitmap, fully offline, no
   `INTERNET` (the first cut used the `emoji-androidx-emoji2`/`EmojiCompat` provider but its
   white-paint fallback drew uncovered emojis invisibly, so it was swapped; sprites add ~2.65 MB). The
   `EmojiView` + search dialog are themed from an explicit `EmojiTheming` built off the Compose colour
   scheme (its built-in `EmojiTheming.from` defaults to fixed light colours that ignore dark mode). The
   one non-trivial structural cost: vanniktech's search dialog is a `DialogFragment`, so `MainActivity`
   moved from `ComponentActivity` to `AppCompatActivity` (and the window theme to Material3 DayNight).
   First shipped on **0.23.0** (the newest release built with Kotlin 2.1.x); bumped to **0.24.1** once
   the project moved to Kotlin 2.3 (see the toolchain upgrade below). A considered fallback — a toggle
   to the soft keyboard so the user searches via their keyboard's own emoji panel — was kept in reserve
   (clunkier: there is no API to open the keyboard directly in emoji mode).

**Branded splash screen *(done — v1.2.0; see [2026-06-11-splash-screen.md](2026-06-11-splash-screen.md))*:**
a small polish slice using AndroidX `core-splashscreen` to show the Stela icon on the indigo brand
colour at cold start — consistently across API 26+ (the platform draws one only from API 31), static,
with no artificial delay. Reuses the brand colour and the launcher foreground; no `INTERNET`.

Lower priority, kept deferred: an in-app language picker.

**Support purchase ("Supporter" gesture) *(deferred — findings recorded 2026-06-11; see
[2026-06-11-support-purchase.md](2026-06-11-support-purchase.md))*:** a purely support-based, one-time
non-consumable purchase that locks no features. Gated on adopting Google Play as a channel (itself a
later goal) and on confirming it doesn't compromise the `no INTERNET permission` invariant. Likely
channel-split: an external donation link on the FOSS/GitHub builds, Play Billing on a future `play`
flavour. Not scheduled.

**Kotlin toolchain upgrade *(done — 2026-06-10)*:** bumped Kotlin **2.1.0 → 2.3.21**. It cascaded
through a matched set: **KSP 2.1.0-1.0.29 → 2.3.9** (KSP dropped the `<kotlin>-<ksp>` scheme at 2.3),
which required **AGP 8.9.2 → 8.10.1** (KSP 2.3 needs AGP ≥ 8.10), **Room 2.6.1 → 2.8.4** (2.6.1's KSP
processor fails under KSP2 with "unexpected jvm signature V"), and **kotlinx-serialization 1.7.3 →
1.9.0** (the old runtime threw `AbstractMethodError`
against the Kotlin 2.3 serialization plugin / Room 2.8.4's bundle serializers). The build-script
`kotlinOptions { jvmTarget }` (removed in Kotlin 2.3) was migrated to the `compilerOptions` DSL. One
brittle instrumented test (`AboutFlowTest`) surfaced — it clicked an off-screen Settings row without
scrolling; fixed with `performScrollTo()` (not an app regression). Verified green: `assembleDebug`,
`testDebugUnitTest`, and all 31 instrumented tests. As immediate follow-ups (same day), two currency
bumps the upgrade unblocked: the **Compose BOM 2024.12.01 → 2026.05.01** and **vanniktech/Emoji
0.23.0 → 0.24.1** (built with Kotlin 2.3) — both verified green (build, all 31 instrumented tests, and a
manual emoji-picker smoke check). A later follow-up bumped **AGP 8.10.1 → 8.13.2** and **Gradle 8.11.1
→ 8.14.5** to clear an R8 release-build warning (`An error occurred when parsing kotlin metadata` — R8
in AGP 8.10 only parses Kotlin 2.2 metadata; 2.3 needs AGP 8.13.2 / R8 8.13.19). AGP 8.13's stricter
consistent resolution then surfaced a transitive version skew (`androidx.concurrent:concurrent-futures`
1.1.0 from `profileinstaller` vs 1.2.0 from the androidx.test libs), resolved with a dependency
constraint pinning it to 1.2.0. (Two benign warnings remain and are unrelated: the native-lib strip
notice for `graphics-path`/`datastore`, and a kotlinx-serialization R8 keep-rule note.)

---

## 13. Open questions for review

- Curated icon set — how many, and any specific symbols you want?
- Tap behaviour: **resolved** — shipped as "tap = edit" (list rows and notification bodies
  open the editor). A configurable setting was considered and **dropped**: tap already edits
  everywhere, so there is nothing for it to toggle to.
