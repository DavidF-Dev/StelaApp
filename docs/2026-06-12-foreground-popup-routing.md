# Foreground popup routing — implementation plan

> Status: **Implemented** · 2026-06-12 · v1.5.0. Built as planned.
> A fix for a concurrency hazard: a notification/widget trigger can float the quick-note popup over the
> app's own editor, leaving two live editors for the same note (last save wins, clobbering the other).

## Goal

When **Stela's `MainActivity` is already on-screen**, a notification / widget / tile trigger that would open
the quick-note popup instead routes into the open app (deep-links to the full editor / new note). The popup
is reserved for when Stela is in the **background** — its actual purpose: floating over *another* app.

This eliminates the double-editor clobber and gives one clean rule: **popup only when Stela is backgrounded.**

## Chosen rule (Option A / Rule 3)

Route into `MainActivity` whenever it is **visible at all** (list or editor) — not only when the editor is
open, and not scoped to the same note. This needs the least new state (just a visibility flag, no nav-
destination plumbing) and the cleanest predicate. Side effects are improvements, not regressions:
tapping quick-add / the widget ＋ / the QS tile *while already in the app* opens the full new-note editor
rather than a popup over the app — which is the more sensible behaviour.

## Why this is cheap

The popup already makes exactly this kind of runtime hand-off: the **keyguard branch** in
`QuickNoteActivity.onCreate` forwards to `MainActivity` via `fullEditorIntent(...)` and `finish()`s instead
of drawing the popup. The warm delivery path (`MainActivity` `singleTop` → `onNewIntent` → `handleDeepLink`)
is the same one Expand and the keyguard fallback already use and is exercised by tests. We add a second
branch beside the keyguard one; the only missing piece is a *"is MainActivity visible"* signal.

## Design

### The visibility signal

- `AppContainer` gains `@Volatile var isMainActivityVisible: Boolean = false` (a process-wide flag, beside
  the existing `pendingDraft` hand-off).
- `MainActivity` sets it `true` in `onStart` and `false` in `onStop`. **Started**, not resumed: the popup is
  transparent, so when it appears over `MainActivity` the latter is *paused but still started* — at
  `QuickNoteActivity.onCreate` time `MainActivity` is started, which is the signal we want. (`onResume`/
  `onPause` would read `false` and mis-route.)
- `singleTop` means a single `MainActivity` instance in steady state; a config-change recreation has a brief
  old-`onStop` → new-`onStart` window where the flag is `false`, which is self-correcting and unlikely to
  coincide with a notification tap.

### The routing branch

In `QuickNoteActivity.onCreate`, immediately after the keyguard fallback:

```kotlin
// App already on-screen: route into it rather than floating a popup over our own UI (which would open a
// second editor for the same note). Mirrors the keyguard fallback's hand-off to MainActivity.
if (container.isMainActivityVisible) {
    startActivity(fullEditorIntent(currentNoteId(), currentPinOnSave()))
    finish()
    return
}
```

`fullEditorIntent` already builds `/editor/$noteId` (existing note) or `/new?pin=…` (fresh note) and adds
`FLAG_ACTIVITY_NEW_TASK`, so `MainActivity`'s existing task is brought forward and its `singleTop` instance
reused via `onNewIntent`. No `EXTRA_FROM_POPUP_EXPAND`, so the editor pops normally on done (the app was
already open — not a cold notification entry).

**Same-note re-entry dedup** *(2026-06-12, follow-up)*: routing into `MainActivity` while the editor for
that *exact* note is already on top would otherwise have `handleDeepLink` stack a duplicate editor copy.
`MainActivity.onNewIntent` now skips `handleDeepLink` when the current back-stack entry is the edit editor
(`Routes.EDITOR_EDIT`) whose `noteId` argument equals the intent's deep-link id (`intent.data.lastPathSegment`).
A *different* note still navigates (and can be backed out of); a cold start is unaffected (no `onNewIntent`).

## Files

**Edited:**
- `di/AppContainer.kt` — add `isMainActivityVisible`.
- `MainActivity.kt` — set the flag in `onStart` / `onStop`.
- `ui/quicknote/QuickNoteActivity.kt` — the routing branch.

No new permissions, no manifest change, no new task/affinity.

## Invariants honoured

- `QuickNoteActivity` stays `exported="false"`; the popup still floats over *other* apps unchanged.
- No `INTERNET`, no new permission.
- Reuses the existing popup → `MainActivity` deep-link hand-off (keyguard / Expand path).

## Testing

- **Manual / emulator:** (1) open the editor for a pinned note, tap its notification → no popup; the open
  editor navigates to that note (one editor, no second task). (2) On the list, tap a pinned note's
  notification → full editor opens in-app. (3) Quick-add / widget ＋ while in the app → full new-note editor.
  (4) From another app (Stela backgrounded), every trigger still floats the popup as before. (5) Locked
  secure screen still falls back to the full editor.
- Cross-task activity routing is impractical to assert reliably in Espresso (see the warm deep-link
  verification gotcha); coverage here is the emulator walkthrough, consistent with how the other
  notification/popup entry points are verified.

## Risk / effort

**Small.** Three short edits, all reusing existing infrastructure. The only subtlety is started-vs-resumed,
called out above.
