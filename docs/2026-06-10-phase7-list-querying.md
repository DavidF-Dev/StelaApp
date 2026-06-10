# Stela — Phase 7: List querying (search · sort · filter)

> The last pre-v2 roadmap item (§12 of [2026-06-08-stela-design.md](2026-06-08-stela-design.md)):
> search, sort, and filter over the note list, derived in one in-memory pass.

**Status (2026-06-10) — complete** (incl. the sort-direction follow-up below). Tests: 85 JVM
unit (adds `NoteQueryTest` + sort/filter/direction mapping and ViewModel cases) + 28
instrumented (adds `ListQueryFlowTest`), all green; no `INTERNET`. Verified on the emulator:
search narrows the list; the sort/filter sheet opens and applies; an active filter shows as a
tap-to-clear chip; the sort-direction toggle flips the order; sort/filter/direction persist
across restart.

## Confirmed decisions

- **Search:** case-insensitive **substring** (`contains`, trimmed) over a note's `title` and
  `description` — not fuzzy/edit-distance (surprising at personal scale). **Transient**: it
  resets each session and is not persisted.
- **Sort:** `MODIFIED` (default, = the prior `updatedAt DESC` order) / `CREATED` / `TITLE`.
  Fixed direction per order (timestamps newest-first, title A–Z). A user-facing **asc/desc
  toggle is a queued follow-up.**
- **Filter:** `ALL` (default) / `PINNED` / `UNPINNED`, by pin state.
- **Sort applies uniformly** — pin state does *not* float notes to the top; the `PINNED`
  filter covers "show me my pins."
- **Sort-by-icon dropped:** the planned v2 icon-set sort is obsolete — the per-note **emoji**
  superseded the icon set. (`TITLE` sorts the raw `title`, not the emoji-prefixed
  `displayTitle`, so ordering is alphabetical by the text, not by emoji codepoint.)
- **Persistence:** sort + filter live in `Settings`/DataStore (stored by enum `.name` with a
  parse-fallback, like `themeMode`); search stays in the ViewModel.

## Units

- **`applyQuery(notes, search, sort, filter)`** — a pure function (one pass: filter → search
  → sort). The testable core; the bulk of the value lives here.
- **`Settings`** gains `sortOrder: SortOrder` + `noteFilter: NoteFilter`; `SettingsKeys`,
  `settingsFromPreferences`, the repository interface/impl, and the fake gain the two.
- **`NoteListViewModel`** combines `notes`, `selectedIds`, a transient `searchQuery`, and
  `settings` into the UI state via `applyQuery`; exposes `onSearchChange` /
  `onSortChange` / `onFilterChange` (the latter two persist). Selection can't change while a
  query is active (the bar swaps to the selection bar), so selection always stays a subset of
  the visible notes.
- **`NoteListScreen`** — a search field that expands in the top bar (auto-focused, clearable,
  closes on back), a `Tune` icon opening a sort/filter **bottom sheet** (radio rows), a
  second empty state ("No matching notes") distinct from "No notes yet" via
  `NoteListUiState.isSourceEmpty`, and — when a non-default filter is active — an
  **active-filter chip** below the app bar that names the filter and clears it on tap (so a
  shortened list reads as "filtered", not "empty"). The chip is hidden during selection.

## Testing

JVM: `NoteQueryTest` (each filter, substring/case-insensitivity, trimming, each sort, and a
combined filter→search→sort case); extended `SettingsMappingTest` (read-back + unparsable
fallback for both enums); `NoteListViewModelTest` (search narrows, filter persists, sort
reorders). Instrumented: `ListQueryFlowTest` (search narrows the list; the Pinned filter
hides an unpinned note and restores; the active-filter chip appears and clears the filter on
tap). Bottom-sheet taps are gated on the sheet being fully open/closed so they don't race the
animation, and an `@After` resets the persisted filter so a mutated filter can't poison other
tests on the device.

## Sort-direction toggle (follow-up — done 2026-06-10)

A `sortReversed: Boolean` preference (default `false` = the natural order, so no change to
existing behaviour) inverts the active sort when set. `applyQuery` gains a `reversed`
parameter (`ordered.reversed()` when set); a `SwapVert` row in the sort sheet toggles it,
labelled **order-aware** ("Newest first / Oldest first" for the timestamps, "A–Z / Z–A" for
title) so the control reads naturally for whichever sort is active. Persisted alongside
`sortOrder`. Unit-tested (reversed for each order; mapping; ViewModel toggle) + instrumented
(the label flips on tap).
