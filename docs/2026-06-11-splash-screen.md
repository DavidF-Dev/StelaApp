# Branded splash screen (AndroidX core-splashscreen) — Plan

> Status: **Planned** · 2026-06-11 · a small polish slice (the deferred "branded splash screen").
>
> Not yet implemented. Spec only.

**Goal:** Replace the incidental cold-start surface with a deliberate, on-brand one — the Stela
icon on the **indigo brand colour** — consistently across every supported API level, with **no
artificial delay**.

## Why (the honest framing)

Since Android 12 (API 31) the splash screen is **system-drawn and mandatory** — there is no opting
out, only customising. Today Stela customises nothing, so the experience is incidental and
inconsistent:

- **API 31+:** the system shows the adaptive launcher icon on the theme's *default* window
  background (≈white / near-black), a background we never chose.
- **API 26–30:** **no splash** — a brief flash of the bare window background until Compose's first
  frame.

`androidx.core:core-splashscreen` backports the API 31 splash down to API 23, so one definition gives
**every** user (min SDK 26) the same brief indigo-and-icon moment instead of "nice on 12+, blank flash
below."

## Scope (locked)

- **Static, not animated.** No animated vector, no exit animation beyond the system default, and —
  critically — **no `setKeepOnScreenCondition` gate**. Stela has no slow startup (offline, Room, no
  network), so the splash dismisses the instant the first frame is ready. An artificial dwell would be
  user-hostile and is explicitly avoided.
- **Brand surface, reused assets.** Indigo background + the existing adaptive-icon **foreground**;
  no new artwork.
- Keeps the no-`INTERNET` invariant (purely declarative; no runtime logic).

## Design

- **Background:** `@color/brand_indigo` (full-bleed), the **same in light and dark**. Indigo is the
  brand and reads well on both; a single value keeps it consistently branded and avoids a `values-night`
  variant. The post-splash app theme (`Theme.Stela`, Material3 DayNight) still resolves day/night for
  the app itself.
- **Icon:** the adaptive-icon foreground `@drawable/ic_launcher_foreground` as the splash icon
  (static). The system masks it to a circle; the foreground already respects the adaptive safe zone, so
  it scales correctly. Because the launcher background and the splash background are both indigo, the
  glyph keeps exactly the contrast it has on the launcher — no `windowSplashScreenIconBackgroundColor`
  needed.
- **Hand-off:** a dedicated launch theme `Theme.Stela.Splash` whose `postSplashScreenTheme` is
  `Theme.Stela`, so the window swaps to the real app theme the moment the splash clears.

## Components

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` + `app/build.gradle.kts` | add `androidx.core:core-splashscreen` (1.0.1, stable; backports to API 23). |
| `res/values/themes.xml` | add `Theme.Stela.Splash` (parent `Theme.SplashScreen`): `windowSplashScreenBackground=@color/brand_indigo`, `windowSplashScreenAnimatedIcon=@drawable/ic_launcher_foreground`, `postSplashScreenTheme=@style/Theme.Stela`. |
| `AndroidManifest.xml` | point `MainActivity`'s `android:theme` at `@style/Theme.Stela.Splash` (the launch theme). |
| `MainActivity.kt` | call `installSplashScreen()` **before** `super.onCreate()` / `setContent`. No keep-on-screen condition. |

No new colours (reuses `brand_indigo`); no new drawables (reuses the launcher foreground).

## Build order

1. **Dependency** — add `core-splashscreen`; sync.
2. **Theme** — `Theme.Stela.Splash` with the three `windowSplashScreen*` / `postSplashScreenTheme`
   attributes; switch `MainActivity` to it in the manifest.
3. **Install** — `installSplashScreen()` at the top of `MainActivity.onCreate`, before `super`.
4. **Verify** — cold start on the API 36 emulator (indigo + icon, instant hand-off); light & dark;
   confirm no perceptible dwell (no keep-on-screen gate).

## Invariants

- No `INTERNET`. No artificial delay (no `setKeepOnScreenCondition`). minSdk 26 is fine
  (core-splashscreen supports 23+).
- `MainActivity` stays an `AppCompatActivity` (unchanged); the splash theme parent is the library's
  `Theme.SplashScreen`, and `postSplashScreenTheme` restores the Material3 DayNight app theme.

## Verification limit (known)

The Pixel_8 AVD is API 36, so the **native** API 31+ path is what gets exercised on-device. The
**API 26–30 backport path** is provided by core-splashscreen (a well-trodden library) but is not
covered by the local emulator; it would need a second AVD to observe directly.

## Risk

Low. The only subtlety is icon contrast on the indigo background — mitigated by reusing the launcher
foreground, which is already designed to sit on indigo. The dependency is small, stable, and
declarative.

## Docs / release note

Add a one-line CHANGELOG entry under the current unreleased version (v1.2.0) — e.g. *"A brief branded
splash screen on launch."* — unless that release is cut first, in which case it moves to the next.
