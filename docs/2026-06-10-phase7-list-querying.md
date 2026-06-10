# Stela — Phase 7: List querying (search · sort · filter)

> The last pre-v2 roadmap item (§12 of [2026-06-08-stela-design.md](2026-06-08-stela-design.md)):
> search, sort, and filter over the note list, derived in one in-memory pass.

**Status (2026-06-10) — complete.** Tests: 79 JVM unit (adds `NoteQueryTest` + sort/filter
mapping and ViewModel cases) + 25 instrumented (adds `ListQueryFlowTest`), all green; no
`INTERNET`. Verified on the emulator: search narrows the list; the sort/filter sheet opens
and applies; sort/filter persist across restart.

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
  closes on back), a `Tune` icon opening a sort/filter **bottom sheet** (radio rows), and a
  second empty state ("No matching notes") distinct from "No notes yet" via
  `NoteListUiState.isSourceEmpty`.

## Testing

JVM: `NoteQueryTest` (each filter, substring/case-insensitivity, trimming, each sort, and a
combined filter→search→sort case); extended `SettingsMappingTest` (read-back + unparsable
fallback for both enums); `NoteListViewModelTest` (search narrows, filter persists, sort
reorders). Instrumented: `ListQueryFlowTest` (search narrows the list; the Pinned filter
hides an unpinned note and restores). Bottom-sheet taps are gated on the sheet being fully
open/closed so they don't race the animation.

## Queued follow-up

- **Sort-direction toggle** (asc/desc) — fixed direction ships now; a per-order direction
  control can follow.
