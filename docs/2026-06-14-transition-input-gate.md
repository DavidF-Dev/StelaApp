# Screen-transition input gate — implementation plan

> Status: **Implemented** · 2026-06-14 · 1.5.0 (unreleased).

## Symptom

A tap landing on a screen *while that screen is animating out* is still delivered to that screen's
widgets. Two reproductions:

1. On **Settings**, tap Back, then immediately tap the "Light mode" radio → the theme changes even though
   the Note List is already shown.
2. On the **Editor**, tap Back, then immediately tap "Set" for a Pin At time → the time-picker pops up over
   the Note List for a moment before being discarded.

Both are the same bug: the outgoing destination remains interactive throughout its exit animation.

## Root cause

`StelaNavHost` uses the **default** navigation-compose transitions — no `enterTransition`/`exitTransition`
is supplied, so navigation-compose applies its default ~700 ms cross-fade. During that window the outgoing
and incoming destinations are **both composed, both laid out, and stacked in the same `AnimatedContent`**.
The exit is only a *visual* fade.

The decisive fact: **Compose gates drawing by alpha, but does not gate hit-testing by alpha or by animation
state.** A composable at near-zero opacity that is sliding away is still a full participant in pointer
dispatch. So for the duration of the fade, the leaving screen's click handlers (radio, "Set", list rows…)
remain live and fire on a tap.

## Why "all apps handle this" but this one doesn't

On the View/Fragment system the framework disables touch on the exiting view during a transition
(FragmentManager marks the exiting view non-interactive). **Compose Navigation does not do this
automatically** — it ships the transitions enabled but never blocks input on the transitioning
destinations. It is a long-standing, widely-hit gap, not a misconfiguration.

## Why the existing guards don't catch it

`guardedPop` / `entry.lifecycle.isResumed()` (the back-navigation blank fix) guard only the **navigation
actions themselves** — system Back and the editor's `onDone` — so a second Back during a transition can't
pop a further destination. They do **nothing** for arbitrary widgets on the outgoing screen (radios,
"Set", switches, list rows): those are ordinary click handlers with no lifecycle check and keep firing.
The systemic cause — input is never gated during transitions — was never addressed, which is why these
one-off guards keep accreting.

## Fix

A single central gate, applied uniformly to every route, that **consumes all pointer input on a
destination whenever it is not settled** (i.e. whenever it is entering or leaving). The
`composable { }` content lambda's receiver is already `AnimatedContentScope` (an
`AnimatedVisibilityScope`), which exposes `transition: Transition<EnterExitState>`. A destination is
settled iff `transition.currentState == transition.targetState` (both `Visible`); during enter
(`PreEnter → Visible`) or exit (`Visible → PostExit`) the two differ.

The gate consumes **pointer events only**; system Back is a key/gesture handled by `BackHandler`, not
pointer dispatch — so the existing `guardedPop`/`isResumed` back guards stay and remain necessary. The two
mechanisms are complementary.

### Step 1 — Add the `ScreenGate` helper

In `ui/StelaNavHost.kt`, a private `@Composable` extension on `AnimatedVisibilityScope`:

- Reads `transition.currentState == transition.targetState` → `settled`.
- **Settled:** render `content()` with no extra modifier (zero steady-state overhead — a destination is
  settled for ~99 % of its life).
- **Not settled:** wrap `content()` in a `Box` whose modifier consumes every pointer event on the
  **`Initial`** pass — `awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }` in a
  loop — so the gate wins before any child sees the event.

New imports: `androidx.compose.animation.AnimatedVisibilityScope`,
`androidx.compose.foundation.layout.Box`, `androidx.compose.ui.Modifier`,
`androidx.compose.ui.input.pointer.pointerInput`, `androidx.compose.ui.input.pointer.PointerEventPass`.

### Step 2 — Wrap every route body

In `ui/StelaNavHost.kt`, wrap the `…Route(...)` call inside each of the six `composable { }` blocks
(LIST, ARCHIVED, EDITOR_NEW, EDITOR_EDIT, SETTINGS, ABOUT) in `ScreenGate { … }`. The lambda receiver is
already an `AnimatedVisibilityScope`, so the helper resolves on `this` with no extra plumbing. The
`guardedPop` / `BackHandler` / `onEditorDone` wiring is unchanged and stays outside the gate.

Apply to **all six**, not just Settings/Editor — every screen has the same exposure, and uniformity stops
a future screen from silently regressing.

### Step 3 — Tighten the cross-fade to 350 ms

The default ~700 ms fade is sluggish for an app the user pops in and out of constantly. Reduce it to
**350 ms** — snappy without reading as a flicker (a pure cross-fade tolerates less shortening than a
directional slide; ~250 ms is the practical floor for a fade). 350 ms sits in Material's full-screen
motion range.

This is **independent of correctness**: with the gate in place, taps during the transition are consumed at
any duration. Implement by defining one shared `fadeIn`/`fadeOut` spec at `tween(350)` and passing it to
the `NavHost`'s `enterTransition`, `exitTransition`, `popEnterTransition`, and `popExitTransition` so all
four directions stay symmetric. Keep it a plain fade — no slide / shared-element (scope creep).

## Files

- `ui/StelaNavHost.kt` — add the `ScreenGate` helper; wrap all six route bodies in it; supply the four
  `tween(350)` fade transitions to `NavHost`. No other file changes expected.

## Testing

- **Build:** `assembleDebug` with the Android Studio JBR `JAVA_HOME`.
- **Manual reproduction of both reported cases (Pixel_8 emulator):**
  1. Settings → Back, then immediately tap "Light mode" → theme must **not** change; list is shown.
  2. Editor → Back, then immediately tap "Set" on a Pin At time → time-picker must **not** appear over the
     list.
- **Regression sweep:** settled taps still work everywhere — Settings radios, editor "Set", list-row taps,
  multi-select — confirming the gate bites only mid-transition.
- **Back guards intact:** rapid double/triple Back still lands cleanly on the list (no blank host),
  confirming the gate didn't disturb the existing lifecycle guards.
- Static screen-capture can't catch a sub-second animation, so verification here is behavioral/manual, not
  screenshot-based. No automated test added by default — the failure is timing/animation-dependent and the
  existing suite doesn't exercise mid-transition input.

## Risks / watch-items

- **Steady-state cost is nil** — the blocking modifier exists only while `!settled`.
- **Scope limit:** the gate sits at the screen root, so it does not cover a `Dialog`/picker that is
  *already open in its own window* during a transition. The reported cases are the in-screen tap that
  *opens* the picker, which is covered. Revisit if a dialog-already-open variant surfaces.
- Reducing the fade to 350 ms changes exit/enter feel app-wide; sanity-check the editor and popup-expand
  hand-off still read smoothly at the shorter duration.
