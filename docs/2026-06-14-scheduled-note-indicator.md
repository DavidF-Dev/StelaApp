# Scheduled-note list indicator — implementation plan

> Status: **Implemented** · v1.6.0 (unreleased) · 2026-06-14. Built as the plan below. Part of the
> [post-v1.5 improvements queue](2026-06-14-post-v1.5-improvements.md). As-built notes at the end.

## Goal

A note with an auto-pin (`pinAt`) or auto-unpin (`unpinAt`) schedule looks identical to any other in the
list. Add a small, glanceable indicator on those rows so scheduled notes are discoverable and reviewable
without opening the editor.

## Scope — locked decisions

(Confirmed with the user 2026-06-14.)

- **Icon + relative time:** a clock icon plus a short label — "Pins in 3h" / "Unpins tomorrow".
- **Inline in the overline:** appended to the existing modified-time line (e.g. `2 hours ago · 🕒 Pins
  in 3h`), single line, ellipsized if cramped. No extra row height.
- **Main list only.** The Archived screen is excluded: archived notes keep their schedule *dormant*, so
  a "Pins in 3h" label there would be misleading.
- **Pure UI change.** `NoteRow` already receives the full `Note` (incl. `isPinned`/`pinAt`/`unpinAt`), so
  no ViewModel or data-layer change is needed.

## Which event to show

The editor's `ScheduleControls` constrains the data so the "next" event is unambiguous:
- A **pinned** note can only carry `unpinAt` (the "Pin at" row is disabled while pinned).
- An **unpinned** note carries `pinAt` (optionally plus a later `unpinAt`); its next event is the pin.

So:

```kotlin
data class ScheduledEvent(val atMillis: Long, val isUnpin: Boolean)

fun Note.scheduledEvent(): ScheduledEvent? = when {
    isPinned && unpinAt != null -> ScheduledEvent(unpinAt, isUnpin = true)   // temporary pin ending
    !isPinned && pinAt != null  -> ScheduledEvent(pinAt, isUnpin = false)    // future / snoozed pin
    else -> null
}
```

Exactly one branch applies for a normally-scheduled note; anomalous combinations (which the editor
prevents) fall through to `null` and simply show nothing. Pure and unit-testable.

**Snooze caveat:** a snooze *is* a `pinAt` (the design records "a snooze is just a pinAt"), so it's
indistinguishable from a scheduled pin at the data level — the label is the generic "Pins …", never
"Snoozed …".

## Display

In `NoteRow`, the overline becomes a `Row` instead of a bare `Text`:

```kotlin
overlineContent = {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(relativeTime)
        note.scheduledEvent()?.let { event ->
            Text(" · ")
            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            val whenText = remember(event.atMillis) { TimeFormatter.relative(event.atMillis).toString() }
            Text(
                stringResource(
                    if (event.isUnpin) R.string.notelist_unpins_at else R.string.notelist_pins_at,
                    whenText,
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- **Tint:** subtle (`onSurfaceVariant`), so it reads as secondary metadata, not an alert.
- **Strings:** `notelist_pins_at` = "Pins %1$s", `notelist_unpins_at` = "Unpins %1$s" (the `%1$s` is the
  relative time, which `TimeFormatter` already localises — no extra strings for the time itself).
- **Accessibility:** the icon's `contentDescription` is null because the adjacent label conveys the
  meaning.

## Files touched

- `ui/notelist/ScheduledEvent.kt` *(new)* — `ScheduledEvent` + the pure `Note.scheduledEvent()` helper.
- `ui/notelist/NoteListScreen.kt` — overline `Row` with the indicator in `NoteRow`.
- `res/values/strings.xml` — `notelist_pins_at` / `notelist_unpins_at`.

## Testing

- **Unit (`ScheduledEventTest`):** `scheduledEvent()` returns a pin event for an unpinned note with
  `pinAt`; an unpin event for a pinned note with `unpinAt`; the pin event for an unpinned note with both
  set (the next one); and null for an unscheduled note (and the anomalous pinned-with-`pinAt` /
  unpinned-with-only-`unpinAt` cases).
- **Component (Compose, `createComposeRule`):** render `NoteListScreen` with a fabricated state — a note
  carrying a future `pinAt` shows the "Pins …" label; a plain note shows no schedule label. This renders
  the public screen directly, so it needs no `MainActivity` / DB / onboarding plumbing.

## Edge cases

- **Past-due before reconcile:** a `pinAt` that just elapsed shows roughly "Pins now" until the alarm /
  reconcile fires (imminently). Acceptable; no special-casing.
- **Both `pinAt` and `unpinAt` on an unpinned note:** shows the pin (the sooner, next event); once it
  auto-pins, the row would then show the `unpinAt`.
- **Long relative strings + narrow screens:** the overline is single-line and ellipsizes; the modified
  time leads, the schedule trails.

## As-built notes

Built exactly as planned. `ScheduledEvent` + `Note.scheduledEvent()` live in
`ui/notelist/ScheduledEvent.kt`; `NoteRow`'s overline became a `Row` that appends a private
`ScheduleIndicator` (a ` · ` separator, a 14dp `Icons.Filled.Schedule`, and the relative label), all
tinted `onSurfaceVariant`. Strings `notelist_pins_at` / `notelist_unpins_at` take the relative time as
`%1$s`. **Tests:** `ScheduledEventTest` (6 unit cases for the helper) and `ScheduledIndicatorTest` (3
Compose component cases rendering `NoteListScreen` — pins label, unpins label, none when unscheduled).
**Verified:** unit tests, full instrumented suite (57 tests, 0 failed), lint clean.
