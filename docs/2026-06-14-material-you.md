# Material You / dynamic colour — implementation plan

> Status: **Implemented** · v1.6.0 · 2026-06-14. Built as the plan below. Part of the
> [post-v1.5 improvements queue](2026-06-14-post-v1.5-improvements.md). As-built notes at the end.

## Goal

Offer an optional "Use system colours" mode that themes the in-app UI from the device's Material You
(wallpaper-derived) palette on Android 12+, falling back to Stela's brand indigo otherwise.

## Scope — locked decisions

(Confirmed with the user 2026-06-14.)

- **Opt-in, off by default.** The brand indigo stays the default in-app; the user turns on "Use system
  colours" to adopt the wallpaper palette. No surprise colour change for existing users on update.
- **In-app UI only.** The toggle themes the Compose UI in `MainActivity` and the quick-note popup
  (`QuickNoteActivity`). The splash, notification colour, and launcher icon stay brand indigo (brand in
  the chrome, dynamic in the content).
- **Widget left as-is.** The Glance widget already uses `GlanceTheme`'s default, which is dynamic on
  Android 12+ (brand-free fallback below) — it is *not* wired to this toggle. Known, accepted minor
  inconsistency: with the toggle off, the in-app UI is brand while the widget still tracks the wallpaper
  on 12+. (Widgets conventionally blend with the home screen; wiring the setting into Glance would need
  brand `ColorProviders` + a refresh, out of scope here.)
- **Hidden below Android 12.** `dynamicLightColorScheme`/`dynamicDarkColorScheme` need API 31, so the
  toggle is shown only on `SDK_INT >= S`; `StelaTheme` also guards, so a stray persisted `true` on an
  older device safely falls back to brand.

## Orthogonal to light/dark

`themeMode` (Light / Dark / Follow System) still decides light vs dark exactly as today; `dynamicColor`
only changes the *source* of the palette (brand vs wallpaper). The two combine cleanly.

## Theme

```kotlin
@Composable
fun StelaTheme(darkTheme: Boolean, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

`dynamicColor` defaults to false, so existing component tests calling `StelaTheme(darkTheme = …)` compile
unchanged. Dynamic schemes provide every M3 role the app uses (primary, secondaryContainer, error,
onSurfaceVariant, …), so nothing is left unresolved.

## Setting

- `Settings.dynamicColor: Boolean = false`; `SettingsKeys.DYNAMIC_COLOR =
  booleanPreferencesKey("dynamic_color")`; mapped in `settingsFromPreferences`; `setDynamicColor` on the
  repository (+ DataStore impl + `FakeSettingsRepository`); `SettingsViewModel.setDynamicColor`.
- Not part of backup (backup is notes only), so there's no cross-device-version concern.

## UI wiring

- **`MainActivity`** / **`QuickNoteActivity`**: pass `dynamicColor = settings.dynamicColor` into
  `StelaTheme` (both already collect `settings` and resolve `darkTheme`).
- **`SettingsScreen`** (Theme section, after the Light/Dark/System radios), shown only on
  `SDK_INT >= S`:

  ```kotlin
  ListItem(
      headlineContent = { Text(stringResource(R.string.settings_dynamic_color_title)) },
      supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
      trailingContent = { Switch(checked = state.dynamicColor, onCheckedChange = onDynamicColorChange) },
  )
  ```

  `SettingsRoute` wires `onDynamicColorChange = viewModel::setDynamicColor`.

## Strings

- `settings_dynamic_color_title` = "Use system colours"
- `settings_dynamic_color_summary` = "Match your wallpaper's colours (Android 12+)."

## Files touched

- `settings/Settings.kt`, `settings/SettingsRepository.kt` — the flag (field, key, mapping, setter).
- `test/.../settings/FakeSettingsRepository.kt` — the setter.
- `ui/StelaTheme.kt` — `dynamicColor` param + dynamic schemes.
- `MainActivity.kt`, `ui/quicknote/QuickNoteActivity.kt` — pass the setting.
- `ui/settings/SettingsScreen.kt` — the toggle row (gated on API 31); `SettingsViewModel.kt` —
  `setDynamicColor`.
- `res/values/strings.xml` — the two strings.

## Testing

- **Unit (`SettingsMappingTest`):** `dynamic_color` maps (default false; stored true read back).
- **Unit (`SettingsViewModelTest`):** `setDynamicColor` updates the exposed state.
- **Component (Compose, render `SettingsScreen`):** the "Use system colours" row is present (the test
  device is API ≥ 31) and toggling it invokes `onDynamicColorChange`.
- **Manual:** on a Pixel (API 12+), toggle on → in-app colours follow the wallpaper in both light and
  dark; toggle off → brand indigo returns; splash/notification stay indigo throughout. (The actual
  palette swap isn't asserted in tests — colour values aren't readily inspectable — so it's verified by
  eye.)

## Edge cases

- **API < 31:** toggle hidden; `StelaTheme` guard keeps brand even if the flag is somehow true.
- **No new roles needed:** the app already styles from standard M3 roles, all present in dynamic schemes.

## As-built notes

Built exactly as planned. `dynamicColor` flag added to `Settings`/`SettingsRepository` (+ fake +
`SettingsViewModel.setDynamicColor`); `StelaTheme` gained a defaulted `dynamicColor` param choosing
`dynamicLight/DarkColorScheme(LocalContext)` on `SDK_INT >= S`, else brand; `MainActivity` and
`QuickNoteActivity` pass `settings.dynamicColor`; the Theme section shows a "Use system colours" `Switch`
only on API 31+. The defaulted param meant the existing component tests (`NoteListBannerTest`,
`DescriptionFieldTest`, `AdvancedSectionTest`, `ScheduledIndicatorTest`) compiled unchanged. **Tests:**
`SettingsMappingTest` (flag round-trips), `SettingsViewModelTest` (`setDynamicColor` updates state),
`DynamicColorToggleTest` (the row renders on the API-36 test device); the colour swap itself is verified
by eye. **Verified:** unit tests, full instrumented suite (59 tests, 0 failed), lint clean.
