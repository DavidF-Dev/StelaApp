# Action-row overflow · Removal Preference · Tooltips — implementation plan

> Status: 2026-06-12 · three independent slices. **All three (A, B, C) implemented.**
>
> Motivation: on a real device the existing-note popup's action row packs **seven** hit targets
> (Back · Expand · Share · Pin/Unpin · Archive · Delete · Save) into a fixed-width row, which squeezes
> the text "Save" button until it reads malformed. The canonical fix is an **overflow menu** plus a
> width-stable (icon) Save. Slices B and C were queued alongside.

## Slices at a glance

- **Slice A — action-row overflow + icon Save** *(the fix for the cramped popup row)*.
- **Slice B — Removal Preference setting** (Unpin / Archive / Delete for the notification's remove action
  and swipe).
- **Slice C — tooltips** on icon-only buttons (depends on A — the button set changes).

**Sequencing:** A first (it reshapes the action row); C after A (its tooltip list depends on which
buttons survive as icons); B is independent and can land any time. Each ships on its own.

---

## Slice A — action-row overflow + icon Save

> **Implemented 2026-06-12.** As-built matches the plan: `NoteEditorActions` now renders Pin/Unpin ·
> Delete · `⋮` · Save(`FilledIconButton` check), with a private `NoteOverflowMenu` holding Open-in-full-
> editor (popup) · Share · Archive/Restore; the `⋮` is hidden when empty (e.g. a new note in the editor).
> The popup's action row hugs the edges (4 dp) while its fields stay at 16 dp. Six instrumented tests
> that clicked Save *by text* were switched to `onNodeWithContentDescription("Save")`. Verified on the
> emulator: existing-note popup, new-note popup, and the full editor all show the compact row with the
> filled check; the overflow holds the right items per surface/state. No new strings.

### Locked decisions
1. **Expand → overflow menu item** ("Open in full editor"), popup only. (Not a FAB — a FAB for a
   secondary action is non-standard and would re-diverge popup from editor.)
2. **Share and Archive/Restore → overflow ("kebab" `⋮`) menu** in **both** the editor and the popup,
   reusing the List screen's `OverflowMenu`/`DropdownMenu` pattern. Menu items carry text + a leading
   icon (self-labelling).
3. **Tighten the popup action-row margins** to the standard app-bar inset (~4 dp) so Back hugs the left
   and Save hugs the right; keep the **text fields** at 16 dp. (Editor is unaffected — its real
   `TopAppBar` already uses standard insets.)
4. **Save: text → icon.** A Material 3 **`FilledIconButton`** with `Icons.Filled.Check` — i.e. a
   checkmark on a filled (coloured) circle — marking it as the **primary** action so it stands out
   against the plain `IconButton`s, and making its width locale-independent. Applied in both surfaces.

### Resulting layout
- **Visible row (existing note):** `Back · ⟨flex space⟩ · Pin/Unpin · Delete · ⋮ · ✓(Save)`
- **Overflow (`⋮`):** `Open in full editor` *(popup only)* · `Share` *(greyed when empty)* ·
  `Archive`/`Restore`
- **New note:** `Back · Pin/Unpin · [⋮ → Open in full editor (popup only)] · ✓(Save)` — Share/Archive/
  Delete are hidden (not editing), so the editor's new-note row has no overflow at all.
- The `⋮` button is shown only when the overflow would be non-empty.
- *(Open option: Delete could also move into the overflow — destructive-in-overflow is a mild Material
  convention and it already has a confirm — leaving just Pin/Unpin + Save visible. Kept visible for now
  per the agreed scope; revisit if still tight.)*

### Approach
Restructure the shared `RowScope.NoteEditorActions` (in `NoteFields.kt`):
- Render `Pin/Unpin` (keeps `pinModifier` for the editor's pin-pop) and `Delete` as visible icons.
- Add an internal overflow: a `MoreVert` `IconButton` anchored to a `DropdownMenu` (a small private
  composable), populated with `Open in full editor` (when `onExpand != null`), `Share` (enabled by the
  has-content rule), and `Archive`/`Restore` (by `isArchived`). Shown only when non-empty.
- Replace the trailing Save `TextButton` with a `FilledIconButton { Icon(Check, "Save") }`.
- `onExpand` stays a nullable param: popup passes it (→ overflow item), editor passes null.
- The popup's inline Expand handling moves into this overflow, so `QuickNotePopup` drops its own Expand
  wiring and just keeps the `onExpand` callback it already passes.

### Files
- `.../ui/editor/NoteFields.kt` — `NoteEditorActions` restructure (overflow + icon Save).
- `.../ui/editor/EditorScreen.kt` — unchanged call shape (passes `onExpand = null`); confirm the Save
  `dismissKeyboard(); onSave()` wrapper still applies.
- `.../ui/quicknote/QuickNotePopup.kt` — action-row horizontal padding → ~4 dp (fields stay 16 dp); Back
  stays at row start; Expand now flows through the shared overflow.
- Strings: **none new** — reuse `quick_note_expand_description` ("Open in full editor"), `action_share`,
  `action_archive`/`action_restore`, `action_more`, `editor_save` (as the Save icon's contentDescription).

### Build order
1. Restructure `NoteEditorActions` (overflow + `FilledIconButton` Save); editor + popup compile against it.
2. Tighten popup row insets.
3. Verify on device — specifically reproduce the cramped case: an **existing** note, ideally on a
   narrower width / a long locale, and confirm Save no longer deforms and the overflow holds Share/
   Archive/Expand.

### Risk
**Low–medium.** Mostly a refactor of a shared composable. Watch: the `⋮`-empty case (hide it), the
new-note vs existing-note action sets, and that the editor's pin-pop still animates the Pin icon.

### Considered: move "About" to the List overflow — declined *(2026-06-12)*
"About" (version, author, privacy promise, licences) stays a **Settings** row. It is low-frequency,
informational, and conventionally lives under Settings; the List overflow currently holds **Archived**,
a *content* destination, so adding an app-info screen there would make the overflow an incoherent
grab-bag for no discoverability gain. Not part of this slice.

---

## Slice B — Removal Preference setting

> **Implemented 2026-06-12.** As-built per the plan. `RemovalPreference { UNPIN, ARCHIVE, DELETE }`
> (default UNPIN) in `Settings`; the `swipeToUnpin` field/method/UI renamed to `swipeToRemove` (DataStore
> key string kept as `"swipe_to_unpin"` for back-compat). `AndroidNotificationController` gained a
> volatile `removalPreference`; `post()` picks the remove action's label (Unpin/Archive/Delete) and
> intent, and the swipe `deleteIntent`, by mode. `NotificationActionReceiver` added `ACTION_DELETE`.
> `StelaApp` observes the preference and re-asserts pinned notes on change. Settings shows the
> radio choice + a delete warning (shown when DELETE is selected). The stale `notification_pinned_hint`
> "…or unpin" became "…or remove". Verified on the emulator end-to-end (set Archive → the action read
> "Archive" → tapping it archived the note). Tests: settings round-trip/fallback (unit) + two new
> `NotificationControllerTest` cases (action label reflects mode; swipe-to-remove makes it dismissable).

### Goal
Generalise the notification's single "remove" gesture so the user picks what it does: **Unpin**,
**Archive**, or **Delete** — applied to both the **Remove action button** and the **swipe** (when
"Swipe to remove" is on).

### Locked decisions
- New preference **`RemovalPreference` { UNPIN, ARCHIVE, DELETE }**, persisted via DataStore. **Default
  `UNPIN`** (preserves today's behaviour; no surprise for existing users).
- **Rename the toggle "Swipe to unpin" → "Swipe to remove"** (same semantics: on → swipe performs the
  removal; off → the notification self-heals/re-posts). Keep the internal DataStore key to avoid a
  migration; relabel in the UI (internal code may rename the field for clarity — a pure rename).
- **The action label reflects the chosen mode:** the second notification action reads "Unpin" /
  "Archive" / "Delete" per `RemovalPreference` (not a generic "Remove"), so the tray is predictable.
- **Delete is allowed for both the action and the swipe** — the user opted in explicitly. **Add a clear
  warning in Settings** next to the Delete option: deleting from the notification is permanent and has
  no undo (the in-app undo snackbar can't reach a tray gesture).

### Behaviour matrix
| Trigger | Swipe-to-remove | Result |
|---|---|---|
| **Remove action button** | n/a | Always performs `RemovalPreference` (unpin / archive / delete) |
| **Swipe** | **on** | Performs `RemovalPreference` |
| **Swipe** | **off** | Self-heals (re-posts), as today — `RemovalPreference` does not apply |

- `UNPIN` → `NotePinner.unpin` (today's behaviour).
- `ARCHIVE` → `NotePinner.archive` (unpins + archives; reuses the existing seam, honours "never both
  archived and pinned").
- `DELETE` → `NotePinner.delete`.

### Approach
- **Settings:** add `removalPreference` to the settings model + `DataStoreSettingsRepository`;
  `SettingsScreen` gets a single-choice control (Unpin / Archive / Delete) + the warning text, and the
  swipe toggle is relabelled.
- **Controller:** `NotificationController` gains a `removalPreference` (volatile, synced from settings
  like `hideOnLockScreen`/`swipeToRemove`). In `post()`, the second action's **label + intent** are
  chosen by the mode; the swipe `deleteIntent` is the mode's intent when swipe-to-remove is on, else the
  self-heal `reassertIntent`.
- **Receiver:** `NotificationActionReceiver` reuses `ACTION_UNPIN`/`ACTION_ARCHIVE` and adds
  `ACTION_DELETE` → `getById(id)?.let { notePinner.delete(it) }`.
- **App wiring:** `StelaApp.observePinnedNotificationPreferences` adds `removalPreference` to the observed
  set, so changing it re-asserts pinned notifications (updating the action label/intent and the swipe
  intent).

### Files
- `.../settings/Settings.kt` (+ `SettingsRepository` / `DataStoreSettingsRepository`) — add the pref.
- `.../ui/settings/SettingsScreen.kt` — choice control + warning; relabel swipe toggle.
- `.../notifications/NotificationController.kt` + `AndroidNotificationController.kt` — mode-driven action
  label/intent + swipe intent.
- `.../notifications/NotificationActionReceiver.kt` — `ACTION_DELETE`.
- `.../StelaApp.kt` — observe `removalPreference`; set the controller field.
- Strings — relabel `settings_swipe_to_unpin` → "Swipe to remove"; add Removal Preference title / option
  labels / warning; reuse `notification_action_unpin`/`_archive`, add `notification_action_delete`.
- Tests — unit-test the mode → (label, intent) + swipe-state mapping where separable from the framework;
  manual matrix on device.

### Risk
**Medium.** Touches the notification action/swipe wiring and adds a destructive path. Verify the full
matrix (3 modes × swipe on/off × action vs swipe), the re-assert-on-change, and that delete-by-swipe
actually removes the note (and is clearly warned in Settings).

### Docs to update when it ships
`CLAUDE.md` (the "Unpin removes it…" invariant and the swipe-to-unpin line), the design doc §5/§9 swipe
notes, and `CHANGELOG`.

---

## Slice C — tooltips (adjusted for Slice A)

> **Implemented 2026-06-12.** Shared `ui/TooltipIconButton.kt` exposes `TooltipIconButton(icon, label,
> onClick, …)` (plain icon buttons; `label` feeds both the tooltip and `contentDescription`) plus
> `ButtonTooltip(label) { … }` for the non-`IconButton` cases (the Save `FilledIconButton`, the FAB).
> Applied across `NoteEditorActions` (Pin/Unpin, Delete, the `⋮`, Save), `NoteListScreen` (Search, Sort
> and filter, Settings, the `⋮`, the New-note FAB, and the selection bar's Select-all/Pin/Archive/Delete),
> and `ArchivedScreen` (selection bar's Select-all/Restore/Delete). Skipped per plan: Back, Close-selection,
> per-row Pin, and the redundant search-clear / clear-filter / sort-direction affordances. Tooltips use
> `TooltipAnchorPosition.Above` and `focusable = false`. Verified on the emulator (long-press shows the
> label above the button).
>
> **Gotcha:** a `TooltipBox` adds a transient popup window after a programmatic `performClick()`, which
> confuses Espresso's `RootViewPicker` and breaks a following `Espresso.pressBack()` (it waits on the
> wrong, unfocused root) — even with `focusable = false`. Real OS back is unaffected. Fixed three tests
> by pressing back at the OS level (`UiAutomation.performGlobalAction(GLOBAL_ACTION_BACK)` via the new
> `pressSystemBack()` test helper) instead of `Espresso.pressBack()`.

### Goal
Long-pressing an **icon-only** button briefly shows its label in a tooltip above it, via Material 3
`TooltipBox` + `PlainTooltip`, through a shared **`TooltipIconButton(label, onClick) { icon }`** helper
that feeds `label` to both the tooltip and the icon's `contentDescription` (one source of truth).
State-dependent buttons pass the current label.

### Adjustment from the earlier survey
Slice A turns Expand / Share / Archive/Restore into **overflow menu items** (text labels — no tooltip
needed) and Save into an **icon** (now needs one). Net change to the editor/popup tooltip set:
- **Drop** (now overflow items): Expand, Share, Archive/Restore.
- **Add** (now an icon): Save.

### Add tooltips
- **Editor + popup (`NoteEditorActions`):** Pin/Unpin (state), Delete, Save (the new check icon). *(`⋮`
  "More" optional — low value; it opens a labelled menu.)*
- **List — top bar:** Search, Sort and filter, Settings.
- **List — FAB:** New note.
- **List + Archived — selection bar:** Select all / Deselect all (state); List also Pin/Unpin (state) +
  Archive; Archived also Restore; both Delete.

### Skip (with reasons)
- **Back** (everywhere) and **Save text** — excluded by request (Save is now an icon → tooltipped above).
- **Close-selection (`✕`)** in the multi-select bars — it's a cancel/dismiss (akin to Back). *Lean skip.*
- **Per-row Pin toggle** (List rows) — a row long-press enters multi-select; a tooltip on the row's pin
  would swallow that long-press and create a dead zone in the select gesture. *Skip.*
- **Search clear (`✕`)**, **clear-filter chip (`✕`)**, **sort-direction (`⇅`)** — redundant (field/chip
  affordances, or an adjacent visible label).
- **Row checkbox / About's OpenInNew** — decorative, not buttons.

*(Open: Close-selection, per-row Pin, and the `⋮` "More" are judgement calls — recorded as "skip / skip /
optional"; confirm before implementing C.)*

### Files
- `.../ui/` — a shared `TooltipIconButton` helper (e.g. alongside the other small `ui` helpers).
- `NoteFields.kt`, `NoteListScreen.kt`, `ArchivedScreen.kt` — swap the relevant `IconButton`s for it.

### Risk
**Low.** Purely additive polish; `contentDescription` already covers screen readers, so this is for
sighted discoverability of the icon-only actions.
