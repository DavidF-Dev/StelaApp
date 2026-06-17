# Relative-time refresh on the list — fix

> Status: **Implemented** · v1.6.0 · 2026-06-14. A device-testing fix, not a queued feature.

## Problem

The note list (and the Archived screen) show relative times — "2 hours ago", and the scheduled-note
indicator's "Pins in 30 minutes" / "Unpins tomorrow". These were computed once and cached
(`remember(note.updatedAt) { TimeFormatter.relative(...) }`), keyed on a **fixed** timestamp, so they
never recomputed as wall-clock time advanced — only when the row composable was recreated (which is what
*re-entering* the screen does). Returning from the background resumes the same composition, so the times
stayed frozen (a note still read "Pins in 30 minutes" 10 minutes later); they also didn't tick while the
list sat open.

## Fix

A lifecycle-aware ticker, `rememberCurrentTimeMillis()` (`ui/CurrentTime.kt`):

```kotlin
@Composable
fun rememberCurrentTimeMillis(periodMillis: Long = 60_000L): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { now = System.currentTimeMillis(); delay(periodMillis) }
        }
    }
    return now
}
```

- `repeatOnLifecycle(RESUMED)` fires immediately on resume (fixing the reported case) and re-runs every
  minute while the screen is resumed — and **only** while resumed, so nothing ticks in the background.
- 60s matches the minute granularity the spans already use (`DateUtils … MINUTE_IN_MILLIS`).

`NoteListScreen` and `ArchivedScreen` each read `now` once and thread it into their rows; the rows pass it
to `TimeFormatter.relative(timestamp, now)` and key their `remember` on `(timestamp, now)` so the string
recomputes when either changes. (`TimeFormatter.relative` already accepted a `now` parameter.)

## Files touched

- `ui/CurrentTime.kt` *(new)* — the ticker.
- `ui/notelist/NoteListScreen.kt` — `now` into `NoteRow` (modified-time overline) and `ScheduleIndicator`.
- `ui/archived/ArchivedScreen.kt` — `now` into `ArchivedRow`.

## Testing

The ticker is lifecycle/time-driven, so it isn't cleanly unit-testable; the underlying formatting is
already covered by `TimeFormatterTest` (which exercises `relative` with an injected `now`). Existing
component tests that render these screens still pass (they don't assert exact times). The live refresh is
verified manually on the emulator: open the list with a scheduled note, leave the app, return after a few
minutes, and confirm the time has advanced without re-entering the screen.
