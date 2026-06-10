# Emoji search via vanniktech/Emoji — Implementation Plan

> Status: **Implemented** · 2026-06-10 (released in v1.2.0).
> Replaces the AndroidX `EmojiPickerView` (no search) with vanniktech `EmojiView` (built-in search).
>
> **Version:** now on **0.24.1**. Originally pinned to **0.23.0** because 0.24.x is built with Kotlin 2.3
> and was unreadable by the then-current Kotlin 2.1.0 compiler (`incompatible version of Kotlin … metadata
> is 2.3.0, expected 2.1.0`); 0.23.0 was the newest Kotlin-2.1.x release with identical APIs. After the
> Kotlin 2.3.21 toolchain upgrade (2026-06-10) it was bumped to 0.24.1.
>
> **Verified on the Pixel_8 emulator:** app launches on the new `AppCompatActivity`/Material3 theme;
> the picker shows a search tab; tapping it opens the search dialog (the `FragmentActivity` path) with
> no crash; typing "swim" filters to swimming emojis; picking one sets the note's emoji and closes the
> sheet.
>
> ### Post-implementation revisions (2026-06-10)
>
> Two bugs surfaced in the first cut and were fixed (verified on the emulator in both light and dark):
>
> 1. **Grey/invisible emojis → switched provider to the bundled sprite set.** The original
>    `emoji-androidx-emoji2` provider renders via `EmojiCompat`; its `AndroidxEmoji2Drawable` falls back
>    to `drawText(..., textPaint)` with a hardcoded **white** paint (`color = -0x1`) for any emoji
>    `EmojiCompat` doesn't produce a span for (newer than the loaded font, or font not loaded). Those
>    drew invisible on the light picker. Replaced the provider with **`emoji-google`**
>    (`GoogleEmojiProvider`), which draws every emoji from a bundled colour sprite sheet as a
>    `BitmapDrawable` — no font dependency, no white fallback, every emoji coloured and background-
>    independent, and it drops the runtime downloadable-font reliance (more offline-pure). Cost: the
>    sprite sheet adds ~2.65 MB (the earlier "net-flat APK" claim no longer holds — an accepted trade).
>    `EmojiCompat`/`emoji2` are no longer used directly by the picker.
> 2. **Picker + search ignored dark mode → explicit theming.** `EmojiTheming.from(context)` resolves
>    four of its six colours from vanniktech's own theme attributes (`emojiBackgroundColor`,
>    `emojiTextColor`, …), which our `Theme.Material3.*` wrapper doesn't define, so they fell back to
>    **fixed light** defaults regardless of dark mode (only `colorPrimary`/`colorAccent` flipped). Now
>    an explicit `EmojiTheming` is built from the Compose `MaterialTheme.colorScheme` (`.toArgb()`) and
>    passed to `setUp(theming = …)`; the `EmojiView` and the search dialog (which inherits it) both
>    follow the app's light/dark choice exactly.
> 3. **Search keyboard pushed the grid up → sheet ignores the IME.** Opening search shows the keyboard
>    for vanniktech's separate search `DialogFragment`, but Material's `BottomSheetDialog` follows the
>    IME insets and lifted the background grid with it. The sheet has no text field of its own, so its
>    window is set to `SOFT_INPUT_ADJUST_NOTHING` — the grid stays fixed while the search dialog (its own
>    window) keeps its box above the keyboard. *(v1.2.0.)*

**Goal:** Let users find an emoji by name when setting a note's emoji, by replacing the
search-less AndroidX emoji picker with vanniktech's `EmojiView`, whose search is on by default.

**Approach:** Swap the picker inside the existing themed `BottomSheetDialog` host (the View-interop
seam already used in the editor). Add an app-level provider install. Promote `MainActivity` from
`ComponentActivity` to `AppCompatActivity` (with a Material3 window theme) because vanniktech's search
dialog is a `DialogFragment` that needs `supportFragmentManager` + an AppCompat-styled dialog.

**Invariant guard:** no `INTERNET` permission is added. Emoji render from a **bundled colour sprite
sheet** (`emoji-google`; see the post-implementation revisions above — the first cut used the
`emoji-androidx-emoji2`/`EmojiCompat` backing but switched after a rendering bug), so there is no
network behaviour at all.

> ⚠️ **The Findings and Task sections below capture the *original* plan** (the `emoji-androidx-emoji2`
> provider, no explicit theming). They are kept for decision history. **The as-built result differs** —
> bundled `emoji-google` sprites + an explicit `EmojiTheming` from the Compose colour scheme — per the
> *Post-implementation revisions* above, which are authoritative. Where the two disagree, trust the
> revisions and the current code.

---

## Findings that shaped this plan

- **AndroidX `EmojiPickerView` has no search** and exposes no way to filter or to reach its
  emoji-name data. Public API is only `emojiGridColumns`, `setOnEmojiPickedListener`,
  `setRecentEmojiProvider`, styling. A search bar is not addable to it — replacement is required.
- **vanniktech `EmojiView` (standalone) has search on by default.** In `setUp(...)`,
  `searchEmoji: SearchEmoji = SearchEmojiManager()`; search is disabled only by passing
  `NoSearchEmoji`. It renders as a magnifying-glass **tab**; tapping opens an `EmojiSearchDialog`
  (debounced `EditText` matching emoji **shortcodes**, e.g. "swim" → 🏊).
- **Use `EmojiView`, not `EmojiPopup`.** `EmojiPopup` overlays the soft keyboard for inline text
  entry into an `EmojiEditText`; we pick a single emoji prefix, so the standalone `EmojiView` fits
  our existing bottom-sheet host.
- **The search dialog requires a `FragmentActivity`.** `EmojiView` calls
  `(Utils.asActivity(context) as FragmentActivity).supportFragmentManager`. Our
  `MainActivity : ComponentActivity()` is **not** a `FragmentActivity`, so tapping search would throw
  `ClassCastException`. → promote to `AppCompatActivity` (a `FragmentActivity`) with a Material3 theme
  (the dialog builds an `androidx.appcompat.app.AlertDialog`, which needs an AppCompat-compatible
  activity theme).
- **License: Apache-2.0**, one-way compatible with the app's GPL-3.0; stays F-Droid-friendly.
- **APK size (Maven Central, v0.24.1 AARs; 0.23.0 is comparable):** core `emoji` 0.16 MB + `emoji-androidx-emoji2` 0.14 MB
  ≈ **+0.30 MB before shrinking**, and we **remove** `emoji2-emojipicker`, so net impact is roughly
  flat. The sprite providers are the heavy ones and are **not** used: `emoji-google` 2.65 MB,
  `emoji-twitter` 2.53 MB, `emoji-ios` 3.59 MB, `emoji-facebook` 3.85 MB. `emoji-google-compat`
  (0.14 MB) is **rejected** — it downloads its font at runtime via Google Play Services (network),
  against the offline ethos.
- **Version:** **0.23.0** (released 2025-11-28, built with Kotlin 2.1.21) — the search feature is
  present from 0.21.0 onward. The newer 0.24.1 is **rejected** for now: it is built with Kotlin 2.3
  and its metadata can't be read by this project's Kotlin 2.1.0 compiler. 0.23.0's
  `EmojiView.setUp(...)` and `AndroidxEmoji2Provider(EmojiCompat)` APIs are identical to 0.24.1's.

### Alternative considered (documented fallback)

Keep the AndroidX picker and add a button to switch to the soft keyboard so the user searches with
their keyboard's own emoji panel. Rejected as the primary path because (a) there is **no API to open
the keyboard directly in emoji mode** — the user lands on the text keyboard and must reach the emoji
key themselves; and (b) the keyboard needs a text field to type into, so we'd have to host an
`EditText` in the sheet and capture an emoji grapheme cluster (skin-tone/flag/ZWJ handling). Lower
risk (no `AppCompatActivity` migration) but clunkier UX. Kept as the fallback if the activity
migration proves troublesome.

---

## File map

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add `vanniktech-emoji` + `vanniktech-emoji-androidx-emoji2` + `androidx-appcompat`; the `emojipicker` entry becomes unused. |
| `app/build.gradle.kts` | Replace `androidx.emoji2.emojipicker` with the two vanniktech deps; add `appcompat`; keep `material`. |
| `app/src/main/res/values/themes.xml` | Re-parent `Theme.Stela` to `Theme.Material3.DayNight.NoActionBar`. |
| `app/src/main/res/values-night/themes.xml` | Remove (DayNight handles night) — or re-parent to the same Material3 theme. |
| `app/src/main/java/dev/davidfdev/stela/MainActivity.kt` | `ComponentActivity` → `AppCompatActivity`. |
| `app/src/main/java/dev/davidfdev/stela/StelaApp.kt` | `EmojiManager.install(AndroidxEmoji2Provider(EmojiCompat.get()))` in `onCreate`. |
| `app/src/main/res/layout/emoji_picker_sheet.xml` | Replace `EmojiPickerView` with `com.vanniktech.emoji.EmojiView`. |
| `app/src/main/java/dev/davidfdev/stela/ui/editor/EditorScreen.kt` | Drive `EmojiView.setUp(...)`/`tearDown()` in the existing `DisposableEffect`. |

---

## Task 1 — Dependencies

**`gradle/libs.versions.toml`** — add under `[versions]`:

```toml
vanniktechEmoji = "0.23.0"   # newest built with Kotlin 2.1.x; 0.24.x needs Kotlin 2.3
appcompat = "1.7.0"
```

Under `[libraries]`:

```toml
vanniktech-emoji = { group = "com.vanniktech", name = "emoji", version.ref = "vanniktechEmoji" }
vanniktech-emoji-androidx-emoji2 = { group = "com.vanniktech", name = "emoji-androidx-emoji2", version.ref = "vanniktechEmoji" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
```

The existing `androidx-emoji2-emojipicker` line (and the `emojipicker = "1.4.0"` version) become unused
once Task 6 lands — delete them in that task.

**`app/build.gradle.kts`** — in the dependencies block, replace

```kotlin
    implementation(libs.androidx.emoji2.emojipicker)
    // Material Components (View system): hosts the emoji picker in a themed, scrollable BottomSheetDialog.
    implementation(libs.material)
```

with

```kotlin
    implementation(libs.vanniktech.emoji)
    implementation(libs.vanniktech.emoji.androidx.emoji2)
    implementation(libs.androidx.appcompat)
    // Material Components (View system): hosts the emoji picker in a themed, scrollable BottomSheetDialog.
    implementation(libs.material)
```

**Verify:** `./gradlew.bat help` (or a sync) resolves the new coordinates. `emoji-androidx-emoji2`
pulls `androidx.emoji2:emoji2`, which the app already had transitively — no rendering path changes.

---

## Task 2 — Window theme → Material3 (AppCompat-compatible)

`AppCompatActivity` requires an AppCompat-descended theme; the current parent is the **framework**
`android:Theme.Material.*`, which is not. Material3 themes extend AppCompat.

**`app/src/main/res/values/themes.xml`:**

```xml
<resources>
    <!--
      Window theme applied before Compose draws. Material3 DayNight is AppCompat-compatible
      (required once MainActivity is an AppCompatActivity); in-app colors come from the Compose theme.
    -->
    <style name="Theme.Stela" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
```

**`app/src/main/res/values-night/themes.xml`:** delete the file — `DayNight` resolves night
automatically. (If you prefer to keep an explicit night file, set its parent to the same
`Theme.Material3.DayNight.NoActionBar`.)

**Verify:** app builds and launches; status-bar / navigation-bar appearance and edge-to-edge still
behave in both light and dark (the `WindowCompat` insets controller in `MainActivity` is unchanged).

---

## Task 3 — Promote MainActivity to AppCompatActivity

**`app/src/main/java/dev/davidfdev/stela/MainActivity.kt`:**

Replace the import

```kotlin
import androidx.activity.ComponentActivity
```

with

```kotlin
import androidx.appcompat.app.AppCompatActivity
```

and the class declaration

```kotlin
class MainActivity : ComponentActivity() {
```

with

```kotlin
class MainActivity : AppCompatActivity() {
```

Nothing else in the file changes: `enableEdgeToEdge()`, `setContent {}`, `onSaveInstanceState`,
`onNewIntent`, and the ActivityResult/Compose APIs are all inherited unchanged
(`AppCompatActivity` extends `ComponentActivity`).

**Verify:** app builds, launches, and the editor/list/settings render exactly as before; rotate the
device to confirm `onSaveInstanceState`/recreation still works.

---

## Task 4 — Install the emoji provider at startup

`EmojiView` requires a provider to be installed once before use. `emoji2`'s startup initializer
runs before `Application.onCreate`, so `EmojiCompat.get()` is available here.

**`app/src/main/java/dev/davidfdev/stela/StelaApp.kt`:** add imports

```kotlin
import androidx.emoji2.text.EmojiCompat
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.androidxemoji2.AndroidxEmoji2Provider
```

and in `onCreate()`, after `super.onCreate()` and before building the container:

```kotlin
    override fun onCreate() {
        super.onCreate()
        // Emoji rendering reuses the app's existing emoji2/EmojiCompat backing (no sprite sheets, no INTERNET).
        EmojiManager.install(AndroidxEmoji2Provider(EmojiCompat.get()))
        container = AppContainer(this)
        observePinnedNotificationPreferences()
        observeQuickAddPreference()
    }
```

**Verify:** launch the app and open the editor's emoji sheet — it renders without throwing
`"Please install an EmojiProvider through the EmojiManager.install()"`.

---

## Task 5 — Swap the view in the sheet layout

**`app/src/main/res/layout/emoji_picker_sheet.xml`:** replace the `EmojiPickerView` element with
vanniktech's `EmojiView`, keeping the same id and height:

```xml
    <com.vanniktech.emoji.EmojiView
        android:id="@+id/emoji_picker"
        android:layout_width="match_parent"
        android:layout_height="400dp" />
```

(The `clear_emoji` `MaterialButton` above it is unchanged.)

---

## Task 6 — Drive EmojiView from the editor; remove the old picker

**`app/src/main/java/dev/davidfdev/stela/ui/editor/EditorScreen.kt`:**

Replace the import

```kotlin
import androidx.emoji2.emojipicker.EmojiPickerView
```

with

```kotlin
import com.vanniktech.emoji.EmojiView
```

In `EmojiPickerBottomSheet`'s `DisposableEffect`, replace the picker wiring

```kotlin
        content.findViewById<EmojiPickerView>(R.id.emoji_picker)
            .setOnEmojiPickedListener { onPickCurrent(it.emoji) }
```

with `setUp(...)` (search defaults on; `editText = null` since we only collect a single emoji):

```kotlin
        val emojiView = content.findViewById<EmojiView>(R.id.emoji_picker)
        emojiView.setUp(
            rootView = content,
            onEmojiClickListener = { onPickCurrent(it.unicode) },
            onEmojiBackspaceClickListener = null,
            editText = null,
        )
```

Then call `tearDown()` on dispose (it persists recent/variant choices) — change the dispose block from

```kotlin
        onDispose { dialog.dismiss() }
```

to

```kotlin
        onDispose {
            emojiView.tearDown()
            dialog.dismiss()
        }
```

The themed `ContextThemeWrapper`, `behavior.isDraggable = false`, and `STATE_EXPANDED` are kept —
`EmojiView` hosts a `ViewPager` with vertical `RecyclerView` pages, so the same drag-disable that let
the AndroidX picker scroll still applies, and `EmojiTheming.from(...)` (the `setUp` default) reads the
Material3 colors from that wrapper.

**Finally**, delete the now-unused dependency: remove `implementation(libs.androidx.emoji2.emojipicker)`
(already replaced in Task 1) and drop the `androidx-emoji2-emojipicker` library + `emojipicker` version
from `libs.versions.toml`.

**Verify (manual, on the emulator):**
1. Open a note → tap the emoji (Mood) button → the sheet shows category tabs **and a search tab**.
2. Tap search → type "swim" → results filter to swimming emojis.
3. Pick one → it inserts as the note's emoji and the sheet closes.
4. Pick from a category directly (no search) → still works.
5. Long-press a skin-tone-capable emoji → variant popup still works.
6. The "Clear" button still clears the emoji.

---

## Task 7 — Tests & docs

- **Instrumented test:** update any existing emoji-picker test to find
  `com.vanniktech.emoji.EmojiView` instead of `EmojiPickerView`. Honor the recorded gotchas: launch
  via `MainActivity` with `POST_NOTIFICATIONS` granted in `@BeforeClass`; the picker needs a
  Material3-themed host and the sheet's drag disabled to render/scroll. Add a smoke assertion that the
  sheet appears and a category emoji can be picked. (The search dialog itself is a `DialogFragment`;
  asserting its full flow in Compose tests is optional — a manual check per Task 6 is sufficient.)
- **Run:**
  ```
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  .\gradlew.bat assembleDebug
  .\gradlew.bat testDebugUnitTest
  .\gradlew.bat connectedDebugAndroidTest   # emulator running
  ```
- **Docs:** update `CHANGELOG.md` (v1.2.0 unreleased) with "Emoji picker now supports search (switched
  to vanniktech/Emoji)"; the design doc's emoji-picker note is updated alongside this plan.

---

## Risks & rollback

- **Activity/theme migration** is the only non-trivial change. Risk: a visual regression in the
  pre-Compose window theme (status bar, edge-to-edge). Mitigation: verify light + dark after Task 2/3.
- **`EmojiCompat.get()` timing:** relies on `emoji2`'s startup initializer (runs before
  `Application.onCreate`). If the initializer is ever disabled, call `EmojiCompat.init(this)` first.
- **Glyph coverage:** rendering is via the device emoji font (unchanged from today). If
  device-independent glyphs are ever required, switch the provider to a sprite one
  (`emoji-google`, +~2.6 MB) — code change is only the `EmojiManager.install(...)` argument.
- **Rollback:** revert the dependency swap, `MainActivity`, the theme parent, and the layout/editor
  edits — fully self-contained, no schema or data changes.
