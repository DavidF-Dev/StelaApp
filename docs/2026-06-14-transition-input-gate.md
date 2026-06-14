# Screen-transition input gate — implementation plan

> Status: **v1 (per-screen gate) superseded · v2 (container gate) implemented** ·
> 2026-06-14 · 1.5.0 (unreleased).

## Symptom

A tap landing on a screen *while that screen is animating out* is still delivered to that screen's
widgets. Two reproductions:

1. On **Settings**, tap Back, then immediately tap the "Light mode" radio → the theme changes even though
   the Note List is already shown.
2. On the **Editor**, tap Back, then immediately tap "Set" for a Pin At time → the time-picker pops up over
   the Note List for a moment before being discarded.

Both are the same bug: the outgoing destination remains interactive throughout its exit animation.

## Root cause

`StelaNavHost` wraps every destination in a single `AnimatedContent`. During a transition the outgoing and
incoming destinations are **both composed, both laid out, and stacked**; the exit is only a *visual* fade.

The decisive fact: **Compose gates drawing by alpha, but does not gate hit-testing by alpha or by animation
state.** A composable at near-zero opacity that is fading away is still a full participant in pointer
dispatch. So for the duration of the fade, the leaving screen's click handlers (radio, "Set", list rows…)
remain live and fire on a tap. On the View/Fragment system the framework disabled touch on the exiting view
for you; **Compose Navigation does not** — a long-standing, widely-hit gap, not a misconfiguration.

The pre-existing `guardedPop` / `entry.lifecycle.isResumed()` guards (the back-navigation blank fix) guard
only the **navigation actions themselves** — system Back and the editor's `onDone`. They do nothing for
arbitrary widgets on the outgoing screen, which keep firing.

## v1 — per-screen `ScreenGate` (shipped, then found insufficient)

The first fix added a `ScreenGate` that wrapped each route and, while the destination's own
`AnimatedVisibilityScope.transition` was unsettled (`currentState != targetState`), consumed all pointer
input on the `Initial` pass. It also tightened the cross-fade to 350 ms.

**The consume mechanism is sound** — consuming the down on the `Initial` pass (ancestor-first) does block a
descendant `clickable`/`selectable`, whose `awaitFirstDown(requireUnconsumed = true)` then skips the
consumed event. **The flaw is *when the gate engages.*** Reading the navigation-compose 2.8.5 source, the
per-screen `AnimatedVisibilityScope.transition` is the child `EnterExitState` transition derived from the
host's `AnimatedContent`, which sits several hops *downstream* of the Back tap:

```
popBackStack()
  → ComposeNavigator.backStack       (StateFlow)
  → visibleEntries                   (derivedStateOf)
  → LaunchedEffect(backStackEntry) → transitionState.animateTo()
  → AnimatedContent sets the child exit target to PostExit
  → ScreenGate recomposes → settled = false → pointer-consuming node added
```

So there is a **leading-edge window**: from the instant Back is tapped until that chain propagates, the
outgoing screen is still drawn *and still interactive* because the gate has not engaged yet. A quick second
tap that lands in that window goes straight through. The per-screen gate reliably covers only the *middle*
of the animation. (Shortening the fade to 350 ms didn't shrink that leading window, only the total on-screen
time — hence the bug became "harder to trigger" but did not go away. The source confirms there is no early
*trailing* drop: `onTransitionComplete` fires only once `currentState == targetState`.)

The 350 ms cross-fade from v1 is kept — it is an independent, harmless improvement.

## v2 — container-level gate driven from the navigation trigger (the fix)

The per-screen approach can only ask "is *this composable* mid-transition?", and that answer arrives *after*
navigation has already started. To block from the very first frame, arm the block from the **navigation
trigger itself** — which we own (`guardedPop`, `onEditorDone`, and every `navigate(...)` call). The block is
then armed by the **first** tap (Back / the nav action); by the time the user's **second** tap reaches a
control, the block is already up. The remaining unguarded instant is the same frame as the nav tap itself —
which is the intended action, not a stray tap.

Keep the 350 ms cross-fade (overlap preserved, as desired). Remove `ScreenGate` — the container gate
supersedes it. Keep the `guardedPop`/`isResumed` back guards (they cover Back-key over-pop, a different
concern from pointer passthrough).

### Step 1 — Block state, armed at the trigger, released on settle (frame-driven)

In `StelaNavHost`:

- `var blockNavInput by remember { mutableStateOf(false) }` and a monotonically increasing
  `navToken` (`mutableIntStateOf`).
- `beginNavigation()` sets `blockNavInput = true` and increments `navToken` — called **synchronously**
  inside every nav trigger, in the same call that starts the transition. This is what closes the leading
  edge: the overlay is up from the tap instant, before the transition's own state has propagated.
- `LaunchedEffect(navToken)` (skipping the initial `0`) releases it by tracking the *actual* transition,
  not a timer: it polls `navController.visibleEntries.value.size` once per frame (`withFrameNanos`) —
  first waiting for the transition to **start** (size > 1, i.e. the leaving and entering entries both
  visible), then for it to **settle** (size back to 1) — and only then sets `blockNavInput = false`. Keying
  on `navToken` restarts coverage for a navigation that begins mid-window.

  **Why frame-driven, not `delay(TRANSITION_MS + buffer)`:** a fixed timer keeps the overlay up for a guessed
  duration, which overshoots the real animation end and leaves a *trailing* dead-input window — taps that
  land just after the screen settles get swallowed. (This is exactly what broke six list/selection
  instrumented tests on the first cut, which tap controls immediately after a transition.) Releasing on the
  observed settle removes that window and self-corrects to the real animation length. Two frame caps
  (`NAV_START_FRAME_CAP`, `NAV_SETTLE_FRAME_CAP`) are backstops so the overlay can never stick if a trigger
  somehow produces no transition.

### Step 2 — Route every navigation trigger through `beginNavigation()`

Set the flag at the source of every navigation. A small `goTo(route)` seam (`beginNavigation()` then
`navController.navigate(route)`) covers the plain forward navigations so the arming isn't repeated by hand:

- List: `onAddNote`, `onOpenNote`, `onOpenSettings`, `onOpenArchived` → `goTo(...)`.
- Archived: `onOpenNote` → `goTo(...)`. Settings: `onOpenAbout` → `goTo(...)`.
- Archived / Settings / About back: `guardedPop`, which now takes `beginNavigation` and invokes it only
  when the pop actually proceeds (inside the resumed-guard), so a guarded-away rapid second Back doesn't
  arm the overlay needlessly.
- Editor: `onEditorDone` calls `beginNavigation()` inline in both the `goToListOnEditorDone`
  navigate-to-list branch (it uses a `popUpTo` builder, so it can't go through `goTo`) and the plain
  `popBackStack` branch. The `finishOnEditorDone` branch finishes the Activity — no in-app cross-fade — so
  it does not arm the overlay.

The `LaunchedEffect` safety-net re-assertion of the list need not arm the overlay (not a user-facing
transition).

### Step 3 — Full-screen input-consuming overlay

Wrap the `NavHost` in a `Box`. After the `NavHost` (so it draws on top / higher z-order), render — only
while `blockNavInput` — a `Modifier.fillMaxSize()` `Box` whose `pointerInput` consumes every event on the
`Initial` pass (the same consume loop the superseded `ScreenGate` used). While up, it intercepts taps to
**both** the outgoing and incoming destinations for the whole window.

### Step 4 — Remove `ScreenGate`

Delete the `ScreenGate` helper and unwrap the six route bodies back to their bare `…Route(...)` calls.
Retain `TRANSITION_MS` (drives the fade) and the `Initial`-pass / `pointerInput` imports (reused by the
overlay); drop `AnimatedVisibilityScope`.

## Files

- `ui/StelaNavHost.kt` — added `blockNavInput`/`navToken` state, `beginNavigation` + the `goTo` seam, the
  frame-driven release `LaunchedEffect`, the `NAV_START_FRAME_CAP`/`NAV_SETTLE_FRAME_CAP` backstop
  constants, and the `Box` + overlay around `NavHost`; routed all nav triggers through `beginNavigation`;
  gave `guardedPop` a `beginNavigation` parameter; removed the `ScreenGate` helper and its per-route
  wrapping. The 350 ms `tween` transitions and the `guardedPop`/`isResumed` back guards are retained. No
  other file changed.

## Testing

- **Build:** `assembleDebug` with the Android Studio JBR `JAVA_HOME`.
- **Manual reproduction of both reported cases — on a physical device** (the leading-edge race is easiest to
  hit on real hardware; the emulator under-reproduces it):
  1. Settings → Back, then as fast as possible tap "Light mode" → theme must **not** change.
  2. Editor → Back, then as fast as possible tap "Set" on a Pin At time → no time-picker over the list.
  Repeat several times, varying the gap, including near-simultaneous double taps.
- **Forward-navigation variant:** tap a list row / Settings and immediately tap a control on the arriving
  screen → must not register until the screen settles.
- **Regression sweep:** once settled, every screen's controls work normally — Settings radios, editor
  "Set", list-row taps, multi-select, overflow menus. Confirm there is no lingering dead-input period after
  a transition completes (the overlay fully clears).
- **Back guards intact:** rapid double/triple Back still lands cleanly on the list (no blank host).
- **Predictive back:** an edge-swipe back still works; check it isn't swallowed by the overlay window.
- **Automated:** `testDebugUnitTest` and `connectedDebugAndroidTest` both pass (50/50 instrumented). The
  first cut, which released the overlay on a fixed `delay()`, regressed six `ListQueryFlowTest` /
  `SelectionFlowTest` cases — those tap controls immediately after a transition and were swallowed by the
  trailing dead-input window; the frame-driven settle release fixed them. No new automated test is added:
  the leading-edge race is timing/animation-dependent and the suite doesn't exercise mid-transition input;
  static screen-capture can't catch a sub-second animation. Device verification is manual.

## Risks / watch-items

- **Trailing dead-input window — resolved.** Releasing the overlay on the observed transition settle (size
  back to one visible entry) rather than a fixed timer means it lifts when the animation actually ends, so
  there is no fixed-duration overshoot. The frame caps only bound a pathological transition.
- **Predictive back.** The overlay consumes pointer input for the window; verify an in-progress edge-swipe
  predictive-back gesture is not disrupted.
- **Steady-state cost is nil** — the overlay exists only while `blockNavInput`, and `blockNavInput` is only
  ever set by an actual navigation trigger.
- Keeping the 350 ms fade: re-confirm the editor and popup-expand hand-off still read smoothly.
