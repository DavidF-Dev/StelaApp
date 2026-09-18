# Scheduling a quick note, and the deep link that never arrived

**Status:** implemented (2026-09-18) — shipped together toward v1.8.0
**Date:** 2026-09-18
**Related:** [2026-06-12-snooze.md](2026-06-12-snooze.md), [2026-06-12-scheduled-pins.md](2026-06-12-scheduled-pins.md),
[2026-06-11-quick-note-popup.md](2026-06-11-quick-note-popup.md), [2026-06-13-back-navigation-blank-fix.md](2026-06-13-back-navigation-blank-fix.md)

Two unrelated defects reported from several months of real use, fixed in one pass because both sit on
the path from the quick-note popup into the rest of the app.

---

## Part 1 — a new quick note could not be scheduled

### Problem

Creating a note from the tray's "New Stela note" gives a popup that can pin or not pin, but cannot say
*when*. Scheduling meant either saving the note and reopening it, or expanding into the full editor for
its Advanced section. Wanting to jot something now and have it surface later is a common case, and the
popup exists precisely so the full editor isn't needed.

### Root cause

Three gates, all in the shared action cluster:

1. `NoteOverflowMenu` wrapped Share, Duplicate, both Snooze items and Archive in `if (state.isEditing)`,
   and `isEditing` is simply `noteId != null`.
2. The Snooze items additionally required `state.isPinned`.
3. `EditorViewModel.snooze()` began `val note = loaded ?: return` — a silent no-op with no row to act on.

So a brand-new note's ⋮ held only "Expand", and in the full editor a new note had no ⋮ at all.

### Approach

The persistence model already expressed the desired outcome: an unpinned note with a future `pinAt` *is*
"pin me later", and `save()` on a new note already called `pinner.applySchedule(id, pinAt, unpinAt)`. The
only missing link was a way to put a `pinAt` into `EditorUiState` from the popup.

- **`snooze()` degrades for an unsaved note** — it records `isPinned = false, pinAt = until` in state
  instead of returning, mirroring how `pin()`/`unpin()` already record intent that `save()` realises.
- **The menu is gated on capability, not on `isEditing`** — Share and both Snooze items show for any
  note; only Delete, Duplicate and Archive/Restore, which act on a stored row, stay hidden until the note
  exists. The ⋮ itself is therefore always present.
- **A schedule summary line in the popup card** — a clock, the relative time ("Pins in 3 hours") and a
  clear ✕. Without it the sole visible effect of picking a snooze would be the pin icon turning hollow,
  indistinguishable from an unpin. It also fixes a pre-existing blind spot: a note carrying a pending
  auto-pin previously showed no sign of it in the popup at all.
- **The pair is always available, and says what it does** — see "Setting a pin time" below. "Snooze
  until… / Pin at…" opens on the time already set, so a pin that is waiting can be retimed in place.

### Invariant protected

"Pinned **and** carrying a future `pinAt`" is a combination the editor deliberately prevents:
`ScheduleControls` disables "Pin at" while pinned, and `pin()` clears `pinAt`. The unsaved branch of
`pin()` did not. Without that, Snooze → Pin → Save persists a note that is pinned *and* waiting to pin —
which `PinSchedule.resolve` happily preserves, which the list cannot display (`scheduledEvent()` reports
only `unpinAt` for a pinned note), and which later fires a redundant, possibly alerting, pin.

### Decisions

- **The ✕ is an unsaved edit applied on Save**, like the other fields — deliberately unlike snooze
  itself, which acts immediately. That asymmetry already exists in the editor (snooze acts, Advanced rows
  save) and was kept rather than invented here.
- **`unpinAt` and "Alert when pinned" stay Expand-only.** The absolute picker is already a date-and-time
  dialog writing `pinAt`, so the popup gains both relative and absolute first-pin scheduling without the
  Advanced section following it into a bottom sheet.

### Setting a pin time — corrected after device testing

The pair shipped gated on `isPinned || pinAt != null`, and device testing found the hole immediately: an
unpinned note offered no way to schedule one. The most natural way to say "not now" — turning the pin
toggle off — was the exact gesture that removed the ability to say "later".

The gate was **the "Unpin at" row's rule applied to a `pinAt` control**. The editor has both rules a few
lines apart, and the wrong one got copied:

| control | writes | enabled when |
|---|---|---|
| Advanced → Pin at | `pinAt` | `!isPinned` |
| Advanced → Unpin at | `unpinAt` | `isPinned \|\| pinAt != null` |

Neither rule is right for the menu pair, because they do both jobs: with a live pin they put it off, and
without one they set the first. The union is "always", so the gate is gone. `NotePinner.snooze` already
skipped the unpin step for an unpinned note, so nothing but the `enabled` expression stood in the way.

Removing it is what made the wording stretch — "Snooze for…" on a note that has never been pinned means
nothing — so the labels and icons now follow `isPinned`: **Snooze for… / Snooze until…** with the snooze
icon when a live pin is being put off, **Pin in… / Pin at…** with the schedule icon when a pin time is
merely being set. The duration dialog's title and confirm button follow the same flag, so the wording
stays consistent from menu item to dialog.

### Shape of the change

The rule deciding which schedule event is "next" moved from `ui/notelist/ScheduledEvent.kt` to
`ui/ScheduledEvent.kt` and gained an overload over loose fields, so the list (from a `Note`) and the
popup (from editor state) resolve it identically instead of keeping two copies. `notelist_pins_at` /
`notelist_unpins_at` became `schedule_pins_at` / `schedule_unpins_at` now that two surfaces use them.

### Tests

Six cases in `EditorViewModelTest`: snooze defers the first pin; snooze then save creates an unpinned
note with the schedule; pin after snooze clears the pending time (and the saved row carries none); a new
note's snooze marks it dirty; an unsaved snooze can be retimed; an existing scheduled note's snooze
retimes rather than adds. Three in `QuickNotePopupTest`: the overflow offers Share and both Snooze items
on a new note; snooze then save produces an unpinned note with a `pinAt`; snooze leaves both items
enabled so the time can be changed.

---

## Part 2 — "Open in full editor" landed on the wrong screen

### Problem

Expanding the popup into the full editor sometimes showed whatever screen the app was last left on
rather than the note. Reported as happening only when Stela had not been opened for a while.

### Diagnosis

Reproduced on device and logged, rather than reasoned about. Navigate to Settings → HOME → kill the
process (`adb shell run-as dev.davidfdev.stela kill -9 <pid>`; `am kill` refuses while the foreground
service runs, and `am force-stop` clears the task, which is a different scenario) → fire the editor deep
link. Temporary logging gave:

```
onCreate saved=true  data=null                  ← recreated from saved state, launch intent carries no deep link
onNewIntent nav=false data=stela://stela/new    ← the deep link arrives, navController is still null → dropped
compose: placeholder (settings not loaded)      ← composition begins ~0.9 s LATER
compose: settings loaded, onboarded=true
navController ready; dest=settings              ← restores the back stack to the last screen
```

Two mechanisms combine. Re-entering the app recreates the activity, so `rememberNavController` restores
its saved back stack and `NavHost` never consults the launch intent. The deep link arrives separately
through `onNewIntent` — which ran **~0.9 to 1.9 seconds before the first composition**, because the
activity holds a bare placeholder until the persisted settings arrive. `navController?.handleDeepLink()`
was therefore a no-op on null. The window is seconds wide, not a frame.

The blast radius was wider than Expand: the same drop hit widget note taps, the quick-add notification's
"View notes" action, the running notification's body tap, and the popup's lock-screen fallback.

### Approach

Make delivery survive the gap, reusing the pattern already present for cold shares:

- `pendingDeepLink` holds an intent that arrives before the host exists; a `LaunchedEffect` keyed on it
  replays it once `NavHost` is composed, re-checking `isEditorAlreadyOpenFor` so a restored back stack
  already showing that note does not get a duplicate editor stacked on it.
- `SideEffect { navController = controller }` became a `DisposableEffect` that also clears the field on
  disposal, so "is the host available?" is tracked accurately rather than leaving a stale controller
  behind when the host leaves composition.
- The warm-share path two lines above had the identical null-controller drop; it now falls back to the
  existing `pendingShareNavigation` instead of being lost.

### Verification

On device, before and after, same repro each time:

| path | before | after |
|---|---|---|
| popup ⋮ → Open in full editor, after a process kill | Settings | new-note editor |
| editor deep link, cold (task gone) | editor | editor |
| editor deep link, warm (app open) | editor | editor |
| list deep link, restored task | list | list |

### No automated coverage, deliberately

An instrumented test was written and deleted. Delivering the intent to the running instance spawns a
second `MainActivity`, after which `ActivityScenario` teardown fails with *"Activity never becomes
requested state [DESTROYED] (last lifecycle transition = PAUSED)"* — with and without `NEW_TASK`. The
null-controller window itself cannot be hit deterministically from a test, since it depends on
composition losing a race against intent delivery. The repro recipe above is the regression test; run it
by hand if this area is touched.

---

## Test-suite hardening (fallout from verifying the above)

Two real races surfaced while running the suite repeatedly, both pre-existing:

- **Transition race.** `waitUntil { onAllNodesWithText(title).isNotEmpty() }` after Save is satisfied by
  the *editor's own title field*, so a test could act while the editor was still up, or mid-cross-fade
  where `StelaNavHost`'s input gate swallows taps. Four classes shared it.
- **Startup race.** `MainActivity`'s pre-settings placeholder is an idle but empty composition, so a
  test's *first* tap could land on nothing — the same window the deep-link bug exploited. This was the
  dominant flake, hitting a different test each run.

Both are fixed by waiting on a transition's completion rather than on any node with the right text:
`awaitSavedNoteOnList` (the editor's Save button leaves the tree), `awaitEditorFromList` (the list's add
button leaves it) and `awaitNoteList` (the add button appears), in
`androidTest/.../ui/NavigationSettled.kt`. The readiness wait is a `@Before` in all eight
`createAndroidComposeRule<MainActivity>` classes.

`CreateNoteFlowTest` and `ShareToStelaTest` still use the weak post-save wait. They only assert
afterwards, so they are not flaky — but their assertion can currently be satisfied by the editor rather
than the list, which is worth tightening when that area is next touched.
