# Editor "Advanced" collapsible section (scaffold) — implementation plan

> Status: **Planned** · 2026-06-12 · targets v1.5.0. The first slice of the **Advanced note features**
> (design doc §12 planned-feature #4, scheduled/timed pins). This slice builds **only the collapsible
> container, with no contents** — the home that later advanced controls drop into.

## Goal

A collapsible **"Advanced"** area in the **full Editor screen** (not the quick-note popup), **collapsed by
default**, that expands/collapses on tap. It is empty for now; content lands in subsequent slices.

## Scope — locked decisions

- **Editor-only, enforced by placement.** The section is rendered in `EditorScreen`'s scrollable body
  `Column`, **not** in the shared `NoteFields`. Because the popup reuses `NoteFields` (and not
  `EditorScreen`), this single placement choice guarantees the popup never shows Advanced — no flag, no
  popup changes.
- **Empty body, shown genuinely empty** *(decision: option c)*. Expanding reveals an empty container; the
  section is user-visible from this slice. Content is coming soon, so a visibly-empty expand is accepted.
- **Both new and existing notes.** The header shows regardless of `isEditing`. Per-control gating (e.g. a
  future scheduling control that needs a saved note) is deferred to the slice that adds that control.
- **Collapse state is transient, collapsed by default.** Local `rememberSaveable { mutableStateOf(false) }`
  in `EditorScreen` — survives rotation/process death, resets to collapsed on each fresh open. **Not**
  persisted (no DataStore), **not** in the ViewModel (it's pure UI state).
- **Placed below the timestamps**, at the end of the editor body.

## Design

A small private composable in `EditorScreen.kt`:

```
AdvancedSection(expanded, onToggle) {
    HorizontalDivider above            // separates it from the note fields
    Row (toggleable header)            // "Advanced" label + trailing chevron
        - Modifier.toggleable(value = expanded, role = Role.Button, onValueChange = onToggle)
        - semantics { stateDescription = expanded ? "Expanded" : "Collapsed" }
        - chevron rotates 0°→180° via animateFloatAsState
    AnimatedVisibility(expanded, expandVertically()/shrinkVertically()) {
        Box(Modifier.testTag(ADVANCED_CONTENT_TAG))   // empty for now
    }
}
```

- **Header affordance:** the whole row toggles (not just the chevron), with a `stateDescription` so
  TalkBack announces expanded/collapsed — a `toggleable` row, not a bare `clickable`.
- **Animation:** `AnimatedVisibility` with vertical expand/shrink for the body; `animateFloatAsState` for
  the chevron rotation. Consistent with the Material expandable-section idiom.
- **Visual:** match the editor's existing typography; a `HorizontalDivider` above for separation. The
  divider/header sit inside the body column's 16 dp padding (inset) — fine for a scaffold; a full-bleed
  divider is a later nicety if wanted.
- **State hoisting:** `expanded` + `onToggle` are hoisted to `EditorScreen` (held in `rememberSaveable`),
  so `AdvancedSection` stays stateless and trivially testable.

## Strings (externalised)

- `editor_advanced` — "Advanced" (the header label).
- `state_expanded` / `state_collapsed` — "Expanded" / "Collapsed" (reusable `stateDescription` values).

(Exact names finalised in implementation; all via resources per the i18n convention.)

## Files

**Edited**
- `app/src/main/java/dev/davidfdev/stela/ui/editor/EditorScreen.kt` — add the `AdvancedSection` composable
  and the `rememberSaveable` toggle state; render it after the timestamps block.
- `app/src/main/res/values/strings.xml` — the new strings.

**New**
- `app/src/androidTest/.../ui/editor/AdvancedSectionTest.kt` (or fold into an existing editor UI test) —
  the collapse/expand behaviour test.

No ViewModel, DataStore, schema, or manifest changes. The popup, `NoteFields`, and `EditorViewModel` are
untouched.

## Build order

1. **Strings.**
2. **`AdvancedSection` composable** + the `rememberSaveable` state in `EditorScreen`, rendered below the
   timestamps. Collapsed by default, empty body.
3. **Test** — collapsed-by-default; tap header → body (`testTag`) visible; tap again → hidden.

## Invariants honoured

- **Editor-only**: the section lives in `EditorScreen`, never in the popup (which uses `NoteFields`).
- No `INTERNET`, no new permissions, no background work, no schema change — purely a UI container.
- The popup remains a strict subset of the editor (Advanced is one of the editor-only extras).

## Testing

`EditorScreen` is a **stateless composable**, so the cleanest test drives it directly with a fake
`EditorUiState` via `createComposeRule` + `setContent { EditorScreen(...) }` (no activity/nav needed):

- **Collapsed by default** — `onNodeWithTag(ADVANCED_CONTENT_TAG)` is not displayed; the "Advanced" header
  is displayed.
- **Expands on tap** — tap the header → the content node is displayed.
- **Collapses on tap** — tap again → not displayed.

(If the shared compose-test host makes a direct `EditorScreen` test awkward, fall back to launching the
editor via the existing editor instrumented-test harness.) The empty body still toggles its presence, so
the `testTag` is the assertion anchor until real content arrives.

## Risk / effort

**Small.** One self-contained composable + local state + three string resources + one UI test. No
data-layer or cross-surface work. The only judgement calls (placement, transient state, empty body) are
already settled above.

## Open questions

**Resolved (2026-06-12):**
- Empty body — **shown genuinely empty** (content coming soon).
- New *and* existing notes — **both**.
- Persist expanded state — **no**, collapsed by default each open (transient `rememberSaveable`).
- Editor-only — enforced by **placement** in `EditorScreen` (outside the popup-shared `NoteFields`).

**Deferred to later slices (when content lands):**
- Per-control gating for new/unsaved notes (e.g. scheduling that needs a saved note).
- Whether a full-bleed divider / richer section styling is wanted once there's content.
