# Editor entry-context & keyboard insets — implementation plan

> Status: **Implemented** · 2026-06-13 · v1.5.1. Issue 1 built as planned; Issue 2's keyboard fix grew
> during implementation — `imePadding` alone did not suffice (see Issue 2, point 3: page scroll + a
> keyboard-aware adaptive cap on the description).
> Two independent fixes surfaced during testing:
> 1. Entering the app via **"View Notes"** (shortcut / notification action), then opening and leaving the
>    editor, wrongly **finished the task** (closed the app) — the old cold-notification behaviour leaking
>    onto a list entry point.
> 2. In the editor, a long multi-line description **slid behind the keyboard**, and re-opening the keyboard
>    **panned the Top Bar off the top**. The top bar should stay pinned on every screen.

## Issue 1 — "View Notes" should not finish the task after editing

### Root cause

`MainActivity` decides "finish on editor done" with a `finishOnEditorDone` flag set in `onCreate`, classified
by `isNotificationDeepLink(action, scheme)`. That classifier matched **any** `ACTION_VIEW` + the app's `stela`
scheme — it never inspected the path. The three list deep links all use that scheme:

- the **"View Notes" launcher shortcut** (`stela://stela/list`),
- the quick-add notification's **"View notes" action**,
- the **service-running notification body tap**.

All three landed on the list but set `finishOnEditorDone = true`, so pushing the editor and leaving it
(`back` / `save` / `delete`) took the `finish()` branch and closed the app instead of popping back to the
list. The flag is meant only for a cold entry that lands **directly on the editor** (now just the secure
lock-screen fallback), where returning home is intended.

### Fix

1. **Path-aware classifier.** Rename `isNotificationDeepLink` → `isEditorDeepLink(action, scheme, path)`,
   returning true only for the **editor** deep links — `path == "/new"` or `path` starting with `/editor`.
   The `/list` path is no longer classified as a finish-on-done entry. (Allowlist the editor paths rather
   than blocklist `/list`, so a future list-like deep link can't regress.)
2. **Thread the path through** at the `MainActivity.onCreate` call site.

### Sub-fix — reset entry-context when the app is re-entered from background

`MainActivity` is `singleTop`. Pressing HOME stops but does not destroy it; relaunching via the launcher
resumes the same instance through `onRestart`/`onStart`/`onResume` — **neither `onCreate` nor `onNewIntent`
runs**, so `finishOnEditorDone` keeps its value. A user who cold-entered the editor, pressed HOME, then
re-opened the app from the launcher would still have the editor finish the task — even though they re-entered
"normally."

Fix: override `onRestart`. If `finishOnEditorDone` is set, downgrade the editor to in-app behaviour —
clear it and set `goToListOnEditorDone = true` so the re-entered editor lands on the **list** on done. (We
reuse the go-to-list path rather than plain `popBackStack`, because the editor deep link's synthesised back
stack can be just `[Editor]` with no list under it — see the popup plan — so a bare pop could pop into
nothing.) `onRestart` fires only on return-from-background, never on first launch, so the genuine cold
session keeps its finish behaviour.

### Units

- `isEditorDeepLink(action, scheme, path)` — pure classifier (JVM-unit-tested).
- `MainActivity` — sets `finishOnEditorDone` in `onCreate` via the classifier; resets in `onNewIntent`;
  downgrades to go-to-list in `onRestart`; persists in `onSaveInstanceState`.

### Testing

JVM for the classifier (`/list` → false; `/editor/…`, `/new` → true; non-deep-link → false). The three
`/list` entries → editor → exit → **list stays open**, and the lock-screen `/editor` entry → HOME → relaunch
→ exit → **list** are device/manually verified (launch-intent + back-stack behaviour is not reliably
instrumentable).

## Issue 2 — keyboard occludes editor text; Top Bar pans up

### Root cause

The app is edge-to-edge (`enableEdgeToEdge()` sets `decorFitsSystemWindows = false`), so the framework
neither resizes nor pads content for the IME — the app must consume the IME inset itself. The editor's
content body did not, so its lower lines sat behind the keyboard, and with the inset unconsumed the system
**panned** the whole window to reveal the caret, dragging the `TopAppBar` off the top. (The list Top Bar
looked fine only incidentally: its sole text field, search, lives in the bar itself, so panning had nothing
to push it past.)

### Fix — the standard Compose edge-to-edge keyboard recipe

1. **`android:windowSoftInputMode="adjustResize"`** on `MainActivity` and `QuickNoteActivity`. Required
   because **minSdk is 26**: on API < 30, Compose only receives IME insets (so `imePadding()` works) under
   `adjustResize`, and it forbids the window pan that moves the bar. (Also makes the popup's existing
   `imePadding()` reliable below API 30.)
2. **`Modifier.imePadding()` on the editor (and list) content body** (`Scaffold` content slot, not
   `topBar`). The bar is a separate slot, so it stays pinned while the body shrinks to sit above the
   keyboard. On the list this is future-proofing (its only field, search, lives in the bar).
3. **Page scroll + a description capped to the space above the keyboard, so the caret follows.**
   `imePadding` alone was *not* enough. The original description had a fixed cap (`heightIn(max = 400.dp)`)
   and scrolled *internally*, nested inside the page's `verticalScroll`. A multiline field's caret
   bring-into-view is satisfied by the field's **own** scroll and does not propagate to a parent scroll —
   so as new lines were added the caret stayed pinned to the box's bottom edge, which on shorter phones sat
   at/below the keyboard. Removing the cap didn't help either (then *nothing* followed the caret), and a
   single fixed cap is fragile across screen sizes (too tall occludes on short phones, too short wastes
   space). The fix keeps the page `verticalScroll` (so Advanced and a bottom spacer are reachable, like
   Settings) and caps the description **adaptively**: `BoxWithConstraints` reads the height left above the
   keyboard and the description max becomes `that − 140.dp` (reserving the title + paddings + a margin). The
   box therefore always fits above the keyboard and scrolls **within** itself, and its internal scroll keeps
   the caret on screen. A bottom `Spacer(48.dp)` gives breathing room. The shared `NoteFields` is unchanged
   from its original signature; the popup is unchanged (compact, content-sized sheet; never occluded).

### Testing

Verified on the emulator with a **docked** keyboard (a floating keyboard reports no IME inset, so it can't
be insetted for and isn't a valid test). With 25 lines typed, the focused description box measured
`[42,542][1038,1297]` while the keyboard top sat at y=1517 (883px IME inset of 2400) — the box bottom (and
thus the caret, which its internal scroll holds at the bottom) clears the keyboard by ~220px. Separately,
with the keyboard down, the page scrolls so the expanded Advanced section and the bottom spacer are
reachable, Top Bar pinned throughout. Still worth a pass on an API ≤ 29 device, where `adjustResize` is what
makes `imePadding()` receive IME insets at all.
