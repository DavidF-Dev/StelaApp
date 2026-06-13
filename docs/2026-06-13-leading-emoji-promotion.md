# Leading-emoji promotion on save — implementation plan

> Status: **Implemented** · 2026-06-13 · 1.5.0 (unreleased).

## Goal

A quality-of-life step when **saving** a note (new or existing): if the **emoji slot is empty** and the
**title starts with a single emoji** that is **immediately followed by a non-emoji**, promote that emoji into
the emoji slot, strip it from the title, and tidy whitespace — provided the **leftover title is not blank**.

Example: saving title `👑 Foo` with no emoji selected → emoji becomes `👑`, title becomes `Foo`.

**Display-neutral.** The list, widget, and notification already render via `displayTitle(emoji, title)`, so
`emoji="", title="👑 Foo"` and `emoji="👑", title="Foo"` look identical ("👑 Foo"). This feature only
*promotes* an inline emoji into the structured emoji field (so it shows in the emoji-picker button and is used
consistently as the note's emoji) — it never changes how a note looks. That makes it a safe, silent step.

## Detection mechanism

Reuse the app's existing emoji source of truth — vanniktech — not a Unicode-property regex:

- Public extension `CharSequence.emojiInformation()` (`com.vanniktech.emoji`) returns
  `emojiRanges: List<EmojiRange>`, each `EmojiRange(emoji, range: IntRange)`. Ranges are **ascending,
  inclusive UTF-16 offsets**, and each range spans a *whole* emoji grapheme — ZWJ sequences, skin-tone
  modifiers, flags, keycaps, and variation selectors all collapse into one range.
- `EmojiManager.install(GoogleEmojiProvider())` already runs in `StelaApp.onCreate`, so detection works at
  save time without the picker ever being opened.

Why not `\p{Emoji}` regex: bare digits, `#`, `*`, `©`, `™` carry the Unicode *Emoji* property, so a regex
would wrongly strip the "1" from `1. Buy milk`. vanniktech's curated set treats only real emoji as emoji (the
keycap `1️⃣` is an emoji; the digit `1` is not) and stays **consistent with what the picker recognizes**.

## Algorithm (pure)

```
promoteLeadingEmoji(title, currentEmoji, emojiRanges):     // emojiRanges: ascending, inclusive IntRanges
    if currentEmoji is not blank             -> null        # never overwrite a chosen emoji
    lead = emojiRanges.firstOrNull { it.first == 0 }
    if lead == null                          -> null        # title must BEGIN with an emoji (index 0)
    after = lead.last + 1
    if emojiRanges.any { it.first == after } -> null        # next char is an emoji
    rest = title.substring(after).trim()
    if rest.isEmpty()                        -> null        # remainder must not be only whitespace
    return EmojiPromotion(emoji = title.substring(0, after), title = rest)
```

Walk-throughs:

| Title (no emoji selected) | Result |
|---|---|
| `👑 Foo` | emoji `👑`, title `Foo` |
| `👑Foo` (no space) | emoji `👑`, title `Foo` |
| `👑 👸 Foo` | emoji `👑`, title `👸 Foo` (next char is a space, not an emoji) |
| `👑👸 Foo` | unchanged — next char is an emoji |
| ` 👑 Foo` (leading space) | unchanged — emoji not at index 0 (the **opt-out**) |
| `👑` / `👑   ` | unchanged — remainder blank |
| `1. Buy milk` | unchanged — `1` is not an emoji |
| `1️⃣ Tasks` | emoji `1️⃣`, title `Tasks` |
| `👨‍👩‍👧 Trip` | emoji `👨‍👩‍👧`, title `Trip` (one ZWJ grapheme) |
| `Foo 👑` | unchanged — emoji not at index 0 |

## Decisions (locked)

1. **No space after emoji** (`👑Foo`) → extract. Matches the "next char is not an emoji" rule literally.
2. **Leading whitespace** (` 👑 Foo`) → **do not extract**; the emoji must sit at index 0. This is a
   deliberate **opt-out**: a user who wants to keep a leading emoji *in the title* prefixes it with a space.
   (Simplifies the algorithm — no `trimStart`.)
3. **Internal whitespace** (`👑  Foo   Bar`) → trim ends only; leave internal spacing as typed (`Foo   Bar`).
4. **Spaced multi-emoji** (`👑 👸 Foo`) → extract `👑`, leaving title `👸 Foo` (literal rule).
5. **Silent** — no toast/confirmation. It is display-neutral and deterministic.
6. **Re-saving older notes** that have an inline leading emoji promotes them too — intended gentle migration.
7. **Emoji string** = the exact substring the user typed (`title.substring(0, after)`), not vanniktech's
   canonical `emoji.unicode`, so we never silently swap their glyph/variation.
8. **Ordinary (non-promoted) titles** are saved exactly as typed — no new trimming of normal titles.

## Where it lives & wiring

- New `data/EmojiTitle.kt`, beside `displayTitle` in `Note.kt`:
  - `data class EmojiPromotion(val emoji: String, val title: String)`.
  - `promoteLeadingEmoji(title, currentEmoji, emojiRanges: List<IntRange>): EmojiPromotion?` — **pure**,
    JVM-testable, no vanniktech dependency.
  - `emojiRangesOf(text): List<IntRange>` — the lone vanniktech touch
    (`text.emojiInformation().emojiRanges.map { it.range }`).
- `EditorViewModel` takes an **injected** `detectEmojiRanges: (String) -> List<IntRange> = ::emojiRangesOf`
  (defaulted via the Factory). This keeps `save()` JVM-testable: the real detector needs `EmojiManager`
  (only installed in the app process), so unit tests inject a fake/empty detector. `save()` computes the
  promotion once and feeds `(title, emoji)` into **both** the create and update branches:

  ```kotlin
  val state = _uiState.value
  val promotion = promoteLeadingEmoji(state.title, state.emoji, detectEmojiRanges(state.title))
  val title = promotion?.title ?: state.title
  val emoji = promotion?.emoji ?: state.emoji
  // create: repository.create(title, state.description, emoji = emoji)
  // update: existing.copy(title = title, description = state.description, emoji = emoji)
  ```

  This covers both the full editor and the quick-note popup, since both route through `save()`. Pin/refresh
  already read the persisted note, so the notification picks up the promoted emoji automatically.

No schema, settings, or `displayTitle` changes.

## Testing

- **JVM unit** `EmojiTitleTest` over the pure `promoteLeadingEmoji(title, emoji, ranges)` — ranges supplied by
  hand — covering every row in the walk-through table plus: emoji-already-set (unchanged), emoji-not-at-start
  (`Foo 👑`), blank remainder, and each locked decision.
- **VM wiring** (`EditorViewModelTest`, JVM): new tests inject a fake crown detector and assert `save()`
  promotes for a new note and an existing note, and does **not** promote when an emoji is already chosen.
- **Instrumented** `LeadingEmojiPromotionTest` composing `promoteLeadingEmoji(title, "", emojiRangesOf(title))`
  with real emoji strings (`👑 Foo`, `👑👸 Foo`, ` 👑 Foo`, `1. Milk`, `1️⃣ Tasks`, `👨‍👩‍👧 Trip`).
  Instrumented because it exercises the real `emojiInformation()` (EmojiManager is installed by
  `StelaApp.onCreate` in the app process). This validates the vanniktech integration — the digit guard and
  grapheme handling — not just the algorithm.

## Docs & changelog

- This doc (flip Status to Implemented when done).
- `CHANGELOG.md`, under `[1.5.0] - unreleased` → **Added** (public-facing): "When you save a note whose title
  starts with an emoji and you haven't chosen one, that emoji automatically becomes the note's emoji. Tip:
  start the title with a space to keep the emoji as part of the text instead."
- A bullet in the emoji section of `2026-06-08-stela-design.md` noting promotion-on-save, its four conditions
  (no emoji selected · emoji at index 0 · next char not an emoji · non-blank remainder), and the space opt-out.

## Out of scope

- Live promotion while typing (only on save).
- Trimming or normalizing ordinary titles.
- Promoting a trailing or mid-title emoji.
- Promoting when the title leads with whitespace (the deliberate opt-out).
