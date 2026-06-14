# Share to Stela (receive shared text) — implementation plan

> Status: **Implemented** · v1.6.0 (unreleased) · 2026-06-14. Built as the plan below. Part of the
> [post-v1.5 improvements queue](2026-06-14-post-v1.5-improvements.md). As-built notes at the end.

## Goal

Make Stela appear in the system **share sheet** for plain text. Sharing text from any app (a browser
selection, a message, a clipboard manager) hands that text to Stela, which opens the **full editor** on
a new note prefilled with the shared content, ready to adjust (emoji / title / pin) and save.

This is the natural counterpart to the existing **share-out** (`shareNote` → `ACTION_SEND`): Stela can
now both emit and receive plain text. It keeps the app fully offline — receiving an `ACTION_SEND`
intent needs no permission and no `INTERNET`.

## Scope — locked decisions

- **Plain text only.** Declare `ACTION_SEND` with `mimeType="text/plain"`. No `ACTION_SEND_MULTIPLE`,
  no images / files / other MIME types (out of scope; the app stores plain notes).
- **Lands in the full editor, in-app — not the quick-note popup.** A share always foregrounds Stela
  (the source app goes to the background and our task comes forward), so the popup — whose whole
  purpose is to float over *other* apps — is the wrong surface. This matches the existing rule that a
  trigger routes in-app when Stela is on screen. The editor opens on top of the list.
- **Content mapping:** `EXTRA_SUBJECT` → **title**, `EXTRA_TEXT` → **description**. When there is no
  subject (the common plain-text case), the **title is left empty** — the editor already auto-focuses
  an empty title, so the user lands ready to type/confirm a title with the body already filled.
- **New notes default to pinned**, consistent with every other new-note entry point
  (`EditorViewModel` seeds `isPinned = true` for new notes). The user can flip the pin toggle before
  saving; pinning on save is still gated by `canPostNotifications()`.
- **Exit behaviour:** finishing the editor (save / back) **pops to the list**, because the editor sits
  on top of the list start destination. A shared note is an in-app session, not a cold notification
  entry, so it does **not** `finish()` the task.
- **Empty share is harmless:** if both subject and text are blank, just open a normal blank new note.

## Entry point — why `MainActivity`

`MainActivity` is the only exported, nav-host-bearing activity, and it already handles incoming
`ACTION_VIEW` deep links and warm re-delivery via `onNewIntent`. `QuickNoteActivity` must stay
`exported="false"` (invariant) and is the wrong surface anyway. So the share filter goes on
`MainActivity`.

Add a second `<intent-filter>` to the existing `MainActivity` declaration:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

(The launcher `MAIN`/`LAUNCHER` filter stays as its own filter; intent-filters do not merge.)

## Seeding the editor — reuse the `NoteDraft` hand-off

The shared text can be arbitrarily long, so it must **not** ride in nav-route arguments (Binder/parcel
limits, encoding). Reuse the exact mechanism the popup's **Expand** already uses: the process-scoped
**`NoteDraft`** on `AppContainer.pendingDraft`, consumed once by `EditorViewModel.Factory`.

Flow:

1. `MainActivity` receives the `ACTION_SEND` intent (cold in `onCreate`, warm in `onNewIntent`).
2. It builds a `NoteDraft(noteId = null, title = subject, description = text, emoji = "",
   pinOnSave = true)` and writes it to `container.pendingDraft`.
3. It navigates to the **new-note** route (`Routes.EDITOR_NEW`) on top of the list.
4. `EditorViewModel.Factory` consumes `pendingDraft` (already wired) and seeds the new note's fields
   from it — the existing "new, unsaved note expanded from the popup" branch in `EditorViewModel.init`
   already handles `draft != null && noteId == null`.

No `EditorViewModel` changes are required — the draft path already supports a new note. This is the
key reuse win.

## Mapping the intent — a pure, testable seam

Add a pure function alongside `EditorEntry.kt` (mirroring `isEditorDeepLink`), so the mapping is unit
testable without the framework:

```kotlin
// ShareEntry.kt
fun sharedNoteDraft(subject: String?, text: String?): NoteDraft =
    NoteDraft(
        noteId = null,
        title = subject?.trim().orEmpty(),
        description = text?.trim().orEmpty(),
        emoji = "",
        pinOnSave = true,
    )

fun isSendTextIntent(action: String?, type: String?): Boolean =
    action == Intent.ACTION_SEND && type == "text/plain"
```

`MainActivity` reads `intent.getStringExtra(Intent.EXTRA_SUBJECT)` and
`intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()` and passes them through `sharedNoteDraft`.
(Use `getCharSequenceExtra` for `EXTRA_TEXT` — some apps share styled text; we flatten with
`toString()`.)

## `MainActivity` wiring

- **Cold start (`onCreate`):** the nav controller is created inside `setContent`, so a share can't
  navigate synchronously. Capture a one-shot "pending share" flag in `onCreate` (after detecting the
  SEND intent and setting `pendingDraft`), then a `LaunchedEffect` keyed on that flag — once
  `navController` is set — navigates to `Routes.EDITOR_NEW` exactly once and clears the flag. The
  start destination (`LIST`) stays under it, so back/save pop to the list.
- **Warm start (`onNewIntent`):** `navController` already exists. Set `pendingDraft`, then
  `navController?.navigate(Routes.EDITOR_NEW)`. Reset the editor-exit flags as the existing
  `onNewIntent` already does (a share is not a popup Expand, so `goToListOnEditorDone = false`,
  `finishOnEditorDone = false` — a plain pop returns to the list).
- The existing `isEditorDeepLink` keys off `ACTION_VIEW`, so a SEND intent leaves `finishOnEditorDone`
  false — exactly what we want (pop to list, don't finish the task).

## Edge cases

- **Both extras blank** → open a normal blank new note (the draft has empty title/description; editor
  auto-focuses the title). No special-casing needed.
- **Very long text** → fine; held process-side in `NoteDraft`, never in nav args.
- **`ACTION_SEND_MULTIPLE` / non-text MIME** → not declared, so Stela won't be offered. No handling.
- **Re-share while a shared-note editor is already open (warm)** → `onNewIntent` writes a fresh draft
  and navigates a new editor on top; acceptable (same as opening another new note).
- **Notification permission denied** → pin-on-save is already gated by `canPostNotifications()` in
  `EditorViewModel.save`; the note is created unpinned, consistent with other entry points.

## Files touched

- `AndroidManifest.xml` — add the `ACTION_SEND` / `text/plain` intent-filter to `MainActivity`.
- `ShareEntry.kt` *(new)* — `sharedNoteDraft(...)` + `isSendTextIntent(...)` pure helpers.
- `MainActivity.kt` — detect the SEND intent in `onCreate` + `onNewIntent`; set `pendingDraft`;
  navigate to the new-note route (one-shot deferred nav for the cold case).
- No `EditorViewModel` / nav-graph changes (the `NoteDraft` new-note path already exists).
- Tests (below).

## Testing

- **Unit (JVM):** `sharedNoteDraft` mapping — subject→title, text→description, trimming, blank
  handling, `pinOnSave = true`; `isSendTextIntent` true only for `ACTION_SEND` + `text/plain`.
- **Instrumented:** launch `MainActivity` with an `ACTION_SEND` `text/plain` intent carrying
  `EXTRA_TEXT` (and a case with `EXTRA_SUBJECT`); assert the editor opens with the description (and
  title) prefilled, then save and assert the note was created. (Grant `POST_NOTIFICATIONS` in
  `@BeforeClass` per the standing instrumented-test gotcha; reset any mutated DataStore setting in
  `@After`.)
- **Manual:** share a text selection from Chrome and from a messaging app; confirm Stela appears in
  the share sheet, the editor opens prefilled, and saving lands on the list with the note present.

## Open questions

- **Title-from-first-line?** When there's no subject, should the first line of the text become the
  title and the rest the description, instead of leaving the title empty? Locked to *empty title* for
  v1 (simpler, predictable; editor focuses it). Revisit if it feels clunky on-device.
- **Share-target label / icon** in the sheet: defaults to the app label + launcher icon. A dedicated
  `android:label` on the filter is optional polish, not needed for v1.

## As-built notes

Built exactly as planned, with these specifics worth recording:

- **Cold-share recreation guard.** `onCreate` only handles the share when `savedInstanceState == null`,
  so a rotation/recreation doesn't re-seed the draft and re-navigate to a second editor (the original
  SEND intent is still attached to the recreated activity).
- **Cold navigation** rides a `pendingShareNavigation` state set in `onCreate`; a `LaunchedEffect(Unit)`
  inside the content navigates to `Routes.EDITOR_NEW` once the nav host is composed, then clears it.
- **Warm share** (`onNewIntent`) returns early before the deep-link path: it seeds the draft, forces the
  in-app exit flags (`finishOnEditorDone`/`goToListOnEditorDone` false → plain pop on done), and
  navigates directly.
- **Files:** `ShareEntry.kt` (new — `isSendTextIntent` + `sharedNoteDraft`), the `MainActivity` wiring,
  and the manifest filter. No `EditorViewModel` / nav-graph changes, as planned.
- **Tests:** `ShareEntryTest` (JVM — mapping + recognition) and `ShareToStelaTest` (instrumented —
  share intent → prefilled editor → save → note in list).
