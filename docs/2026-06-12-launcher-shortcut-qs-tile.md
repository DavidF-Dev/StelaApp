# Launcher app shortcuts + Quick Settings tile — implementation plan

> Status: **Implemented** · 2026-06-12. Built as the plan below; see the "As-built notes" at the end for
> the few places reality differed. Extends the glanceable, no-app-open entry (notification · widget) onto
> the launcher icon and the Quick Settings panel. Reuses `QuickNoteActivity.newNoteIntent`.

## Goal

Two more low-friction entry points to jot-and-pin a note without opening the app:

1. **Static launcher shortcuts** (long-press the app icon): **"New quick note"** → the quick-note popup,
   and **"View notes"** → the note list.
2. **Quick Settings tile**: **"New quick note"** → the quick-note popup, tappable from the QS panel
   (and offered via an in-app *add-tile* prompt on API 33+).

Neither adds editor logic: both are thin launchers over existing machinery.

## Scope — locked decisions

- **Both surfaces** ship in this slice.
- **Two static shortcuts:** "New quick note" (new-note popup) and "View notes" (list). Listed so that
  **"New quick note" sits nearest the icon** (launchers render the *last* `<shortcut>` closest to the
  app icon, so "View notes" is declared first).
- **"View notes" resets to the list** via the existing `…/list` deep link through `MainActivity` (so it
  lands on the list even if the app was left on Settings or the editor) — not a bare `MAIN` launch.
- **Option B — a thin exported trampoline** for the new-note shortcut. `QuickNoteActivity` **stays
  `exported="false"`**; the trampoline only ever opens a *fresh* note, so the edit-by-id path is never
  externally reachable. (Why not export `QuickNoteActivity` directly: a caller can't read note contents,
  but exporting would also make the guessable-id `editNoteIntent` path externally triggerable. The
  trampoline avoids that for ~10 lines.)
- **Add-tile prompt** included (`requestAddTileService`, API 33+), as a Settings row hidden below 33.
- **Icons reused:** the notification silhouette `ic_stela_pin` for the tile and shortcuts.
- All labels **string-externalised** (i18n convention).

## Why a trampoline is the *only* place export is needed

The other surfaces already reach the non-exported `QuickNoteActivity` without export:

| Surface | How it launches | Needs export? |
|---------|-----------------|---------------|
| Notification Edit / body tap | `PendingIntent` (runs as **us**) | No |
| Widget ＋ | `actionStartActivity` → `PendingIntent` (runs as us) | No |
| **QS tile** | `TileService` runs **in our process** | No |
| **Launcher shortcut** | dispatched by the **launcher** (another app) | **Yes** |

So export is a concession solely for the launcher shortcut, and that shortcut only needs the benign
new-note path. The trampoline isolates exactly that.

## Surfaces — design

### 1. Static shortcuts (`res/xml/shortcuts.xml`)

- Referenced from the **launcher** activity (`MainActivity`, which holds the `LAUNCHER` intent-filter):
  `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />`.
- **"View notes"** (declared first): `<intent android:action="android.intent.action.VIEW"
  android:targetPackage="dev.davidfdev.stela" android:targetClass="…MainActivity"
  android:data="stela://stela/list" />` — matches the existing list `navDeepLink`.
  - **Sync note:** `shortcuts.xml` is static, so the `stela://stela/list` URI is a literal that must track
    `AndroidNotificationController.DEEP_LINK_BASE`. Lift the base into a string resource shared by both,
    or accept a one-line literal with a comment. (Low risk — the scheme/host are stable.)
- **"New quick note"** (declared last → nearest the icon): explicit component to the new
  **`NewNoteShortcutActivity`** (the trampoline), `action=VIEW`, no data.
- Each `<shortcut>` carries `android:shortcutId`, `android:icon="@drawable/ic_stela_pin"`,
  `android:shortcutShortLabel`, `android:shortcutLongLabel` (string resources).

### 2. Trampoline — `NewNoteShortcutActivity` (exported, no UI)

```
exported=true · taskAffinity="" · excludeFromRecents · noHistory · Theme.Stela.Transparent
onCreate: startActivity(QuickNoteActivity.newNoteIntent(this)); finish()
```

- No `<intent-filter>` (the shortcut targets it by explicit component), so it never appears as a launcher
  icon. Exported only so the launcher can start it.
- It forwards to `QuickNoteActivity` (non-exported, same process → fine). `QuickNoteActivity` is
  `singleTask` with `taskAffinity=""`, so the popup lands in its own task and floats over the launcher —
  same as the widget/notification path. The transparent theme + immediate `finish()` mean no visible flash.
- Only ever calls `newNoteIntent` — no note-id surface.

### 3. QS tile — `QuickNoteTileService : TileService`

- Manifest `<service>`: `exported="true"`, `permission="android.permission.BIND_QUICK_SETTINGS_TILE"`,
  `icon="@drawable/ic_stela_pin"`, `label="@string/qs_tile_label"`, intent-filter
  `android.service.quicksettings.action.QS_TILE`.
- `onClick()` launches the popup. Runs in our process, so it starts the **non-exported**
  `QuickNoteActivity` directly:
  - **API 34+:** `startActivityAndCollapse(PendingIntent)` — the `Intent` overload is deprecated at 34.
    The `PendingIntent` uses **`FLAG_IMMUTABLE`** (project invariant).
  - **API < 34:** `startActivityAndCollapse(Intent)`.
  - Intent = `QuickNoteActivity.newNoteIntent(this).addFlags(FLAG_ACTIVITY_NEW_TASK)`.
- `onStartListening` / `onTileAdded`: set `Tile.STATE_INACTIVE` + label (a stateless action tile).
- **Secure lock screen:** the system unlocks first, then launches; `QuickNoteActivity`'s existing keyguard
  check falls back to the full editor. No tile-specific handling needed.
- Extract the intent build into a small pure helper so the version branch is the only framework-bound part.

### 4. Add-tile prompt (Settings, API 33+)

- A row in the **Notifications** section of `SettingsScreen` (alongside the quick-add toggle — both are
  quick-entry surfaces), **rendered only when `Build.VERSION.SDK_INT >= 33`**.
- On click: `getSystemService(StatusBarManager).requestAddTileService(component, label, icon, executor,
  resultCallback)` via a small `requestAddQuickNoteTile(context)` helper. The result callback can drive a
  snackbar ("Tile added" / "Already added" / dismissed) using the documented result codes.
- Pure-UI plumbing; no ViewModel/DataStore change (it's a one-shot system call, not persisted state).

## Files

**New**
- `app/src/main/res/xml/shortcuts.xml`
- `…/ui/shortcut/NewNoteShortcutActivity.kt` (trampoline)
- `…/ui/tile/QuickNoteTileService.kt`
- `…/ui/tile/AddTile.kt` (the `requestAddQuickNoteTile` helper) — or fold into the Settings route.

**Edited**
- `AndroidManifest.xml` — register the trampoline activity + the tile service; add the `shortcuts`
  meta-data under `MainActivity`.
- `…/ui/settings/SettingsScreen.kt` — the API-33+ add-tile row.
- `res/values/strings.xml` — shortcut short/long labels (×2), tile label, add-tile row title/summary,
  add-tile result snackbars.
- (optional) lift the deep-link base into a string resource if we share it with `shortcuts.xml`.

**Reused as-is**
- `QuickNoteActivity.newNoteIntent` · `ic_stela_pin` · the `…/list` `navDeepLink` · `NotePinner` pin-on-save.

## Build order (phased)

1. **Strings + (optional) shared deep-link base resource.**
2. **Shortcuts** — trampoline activity + `shortcuts.xml` + `MainActivity` meta-data + manifest entry.
   (Long-press icon → both shortcuts work.)
3. **Tile** — `QuickNoteTileService` + manifest entry. (Manually add the tile in QS → tap → popup.)
4. **Add-tile prompt** — the API-33+ Settings row + helper.
5. **Verify** — manual matrix below.

## Invariants honoured

- **No `INTERNET`.** Neither surface touches the network.
- **`QuickNoteActivity` stays `exported="false"`** — only the dumb new-note trampoline is exported.
- **`PendingIntent`s use `FLAG_IMMUTABLE`** (the tile's API-34 path).
- **Reuse `newNoteIntent`** — no duplicated pin/create logic; pin-on-save still routes through `NotePinner`
  gated on `POST_NOTIFICATIONS`.
- **No new background work**; the service-lifecycle rule is untouched (these are launchers, not services).
- **Secure-lock fallback** reused from `QuickNoteActivity` for every popup trigger.

## Testing

Automated coverage is genuinely thin here (flagged up front, given the project's TDD lean):

- **Trampoline** — it's exported, so an instrumented test can `startActivity` it and assert it forwards
  (popup/`QuickNoteActivity` comes up) and finishes.
- **Tile intent build** — unit-test the pure intent-builder helper (component + `FLAG_IMMUTABLE`); the
  `TileService` lifecycle itself isn't readily instrumented.
- **Shortcuts** — pure XML; nothing to unit-test beyond "it launches."
- **Manual matrix:** long-press icon → both shortcuts (order, labels, icons); "View notes" resets to list
  from a non-list screen; add the tile from QS → tap → popup (and pin-on-save); tile tap behind a **secure
  lock** → full-editor fallback; the **API-34** `startActivityAndCollapse` branch on the Pixel_8 (API 36)
  emulator; the add-tile prompt on API 33+ (added / already-present / dismissed).

## Risk / effort

**Small–medium.** Mostly manifest + XML + two tiny classes. The only fiddly bits are the tile's
**API-34 `startActivityAndCollapse` branch** and the **`requestAddTileService`** callback. Contained by
reusing `newNoteIntent` and the existing deep links.

## Open questions

**Resolved (2026-06-12):**
- Scope — **both** surfaces.
- Shortcuts — **two**: "New quick note" + "View notes".
- Export — **option B** (exported trampoline; `QuickNoteActivity` stays non-exported).
- Order — **"New quick note" nearest the icon**; "View notes" target = `…/list` deep link.
- Add-tile prompt — **yes**, in the Settings **Notifications** section, API 33+ only.
- Icons — **reuse** `ic_stela_pin`.

**Minor, deferrable to implementation:**
- Shortcut icons — resolved to per-shortcut adaptive icons (＋ / bulleted-list on the brand indigo; a flat
  `ic_stela_pin` renders blank — see As-built).
- Whether to lift the deep-link base into a shared string resource vs a commented literal in `shortcuts.xml`.

## As-built notes (2026-06-12)

The structure and decisions held; the few specifics:

- **Deep-link sync** resolved as a **commented literal** in `shortcuts.xml` (`stela://stela/list`) rather
  than a shared resource — lifting the base out of `DEEP_LINK_BASE` (a Kotlin const used by string
  concatenation in several places) would have been more churn than the one-line coupling warrants. The
  comment flags that it must track `DEEP_LINK_BASE`.
- **Trampoline is a plain `Activity`**, not AppCompat — it draws nothing and `finish()`es in `onCreate`,
  so it needs no Compose/AppCompat host. The transparent theme just avoids a flash. It forwards
  `newNoteIntent` with `FLAG_ACTIVITY_NEW_TASK` so the popup lands in its own task, not atop the trampoline.
- **No `applicationId` suffix** across build types, so the `targetPackage="dev.davidfdev.stela"` literal in
  `shortcuts.xml` is correct for debug and release alike.
- **Tile `onClick`** carries `@SuppressLint("StartActivityAndCollapseDeprecated")`: the deprecated
  `Intent` overload is reached only on the pre-34 branch (the `PendingIntent` overload doesn't exist
  below 34), but lint flags the call site regardless. The 34+ path uses `FLAG_IMMUTABLE` (project invariant).
- **Add-tile prompt** lives in the Settings **Notifications** section, just below the quick-add toggle,
  rendered only on API ≥ 33. A small `requestAddQuickNoteTile` helper + an `AddTileResult` enum keep the
  `StatusBarManager` result codes out of the composable; the row shows an "added" / "already added" snackbar.
- **Shortcut icons — not the tint-mask silhouette** *(fixed 2026-06-12)*: `ic_stela_pin` is a white-on-
  transparent alpha mask meant to be *tinted* (notification small icon). Launchers don't tint shortcut
  icons — they drop the drawable onto a white circle, so the white pin rendered invisible (blank white
  circles). Fixed by giving each shortcut its own **adaptive icon** (`mipmap-anydpi-v26/ic_shortcut_*` =
  the `ic_launcher_background` indigo + a white foreground glyph, framed in the safe zone exactly like the
  launcher pin): a **＋** for "New quick note" and a **bulleted list** for "View notes". Adaptive icons
  carry their own background, so they render correctly, and the distinct glyphs read at a glance. (A first
  pass reused `@mipmap/ic_launcher` for both — visible but identical; the per-shortcut glyphs followed.)
- **Widget ＋ reuses the new-note glyph** *(2026-06-12)*: the home-screen widget's add button previously
  drew the ＋ as a text character; it now renders a shared `ic_add` plus vector (24dp, tinted
  `GlanceTheme.colors.primary`) — the same glyph as the "New quick note" shortcut — so the quick-add entry
  points look consistent. The `widget_add` string was removed.
- **Tests:** an instrumented `NewNoteShortcutActivityTest` launches the exported trampoline and asserts the
  popup's Title field appears (proving the forward), finishing the leftover popup in `@After`. The tile
  service and shortcuts XML are otherwise covered by the manual matrix.
- **Verified (2026-06-12):** `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest`, `lintDebug`
  (0 errors), and the trampoline instrumented test all green. On the Pixel_8 (API 36) emulator: both static
  shortcuts register with the right labels; the new-note shortcut's trampoline resumes `QuickNoteActivity`;
  the view-notes shortcut's deep link resumes `MainActivity`; and a `cmd statusbar click-tile` launches the
  popup via the **34+ `startActivityAndCollapse(PendingIntent)`** branch.
