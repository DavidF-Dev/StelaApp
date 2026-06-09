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
2. **Editor** — title, description, icon picker (silhouette set), pin toggle.
   Reachable from: app list, FAB, the quick-add notification, and a pinned note's
   **Edit** action. Save persists via repository (and refreshes the notification
   if pinned). Shows **created and modified** timestamps (absolute) — *Phase 6*.
   A **Share** action sends the note's title + description as plain text via the
   system share sheet — *Phase 6* (no new permission; Stela stays offline).
3. **Settings** —
   - **Theme** — Light / Dark / Follow System, persisted via DataStore — *Phase 5*
     (the first consumer of the preferences store; default Follow System once
     shipped, dark until then).
   - **Hide on lock screen** — when on, pinned-note notifications are hidden on a
     secure lock screen (notification visibility SECRET) — *Phase 5* (default off).
   - Toggle the persistent **quick-add** notification.
   - **Battery optimization** helper (request exemption).
   - **OEM autostart** helper (deep-link / guidance where detectable).
   - **About** — version, author (**David F Dev**), the privacy promise ("nothing
     leaves your device"), a short "how Stela works" honest-persistence note, and an
     open-source licenses list (static) — *Phase 6*.
   - (v2 placeholder) Export / import.

### Pinned-note notification anatomy

- Small icon: the note's **silhouette** icon (monochrome; Android tints it).
- Large icon (optional): a colored version for in-tray richness.
- Title = the note title; **content line = the note description when present**, falling
  back to the **"Tap to edit or remove"** hint only when the description is empty (so a
  title-only note still has a useful, action-pointing line). *(Implemented 2026-06-09.)*
- Actions: **Edit** (opens editor) · **Remove** (unpins; note is *not* deleted).
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
- **Notification permission denied:** app still works as a plain note list;
  pinning is gated behind a clear explanation.
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
7. **List querying** — **search** (fuzzy, over title + description), **sort**
   (creation / modified / title / icon), **filter** (all / pinned / unpinned),
   all derived in one in-memory pipeline over the notes flow in the list
   ViewModel. Sort/filter selections persist via the Phase 5 preferences store.
   (Sort-by-icon stays inert until the v2 icon set makes icons distinguishable.)

**Scope additions (2026-06-08):** theme selection (→ Phase 5), timestamp display
and multi-select / batch actions (→ Phase 6), plain-text **share** (→ Phase 6),
**About screen** (→ Phase 6), **localisation** via string externalisation (→ early
Phase 6), and **list querying** — search / sort / filter (→ Phase 7) — were added
after Phase 1. The repo is licensed **GPL-3.0** (`LICENSE` file added in Phase 6). All
features are deferred to their natural phases rather than implemented eagerly. Notes:
search, sort, and filter share **one in-memory derivation** over the notes flow
(fine at personal scale; move to SQL `WHERE`/`ORDER BY` and Room FTS only if a
library ever grows to thousands of notes); persisted sort/filter selections depend
on the Phase 5 preferences store; **sort-by-icon** stays inert until the v2 icon set
makes icons distinguishable; **share** keeps the app offline — Stela only hands
plain text to the OS share sheet and declares no `INTERNET`.

**v2 (deferred):** JSON export/import, optional "tap = edit", widget.

---

## 13. Open questions for review

- Package / application id (e.g. `io.stela`, `dev.<you>.stela`)?
- Curated icon set — how many, and any specific symbols you want?
- Confirm "tap does nothing" vs. the friendlier "tap = edit".
