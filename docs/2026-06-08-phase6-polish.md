# Stela — Phase 6 (Polish) Implementation Plan

> Companion to [2026-06-08-stela-design.md](2026-06-08-stela-design.md) §12.
> The "everything before v1 ships" phase. Sequenced so localisation comes first
> (it's an invasive refactor that every later slice would otherwise redo).

## Slices & recommended order

- **6a — Localisation foundation** *(done)* — externalize all UI + notification
  strings to resources; `locales_config.xml` + `android:localeConfig` for the
  Android 13+ per-app language override; system locale otherwise. No in-app picker.
- **6b — Timestamps** — created/modified: relative on list rows, absolute in editor;
  locale-aware formatting util (injectable clock, unit-tested).
- **6d — Share** — Editor `ACTION_SEND` text/plain action (offline-safe).
- **6e — About** — version, author (David F Dev), privacy promise, "how Stela works"
  honest-persistence note, static open-source licenses list.
- **6c — Multi-select + batch** — NoteList selection mode (long-press → contextual
  bar), batch delete + batch pin/unpin, confirm dialog with `<plurals>`. Largest
  piece; overlaps the Phase 7 list pipeline, so last of the feature slices.
- **6f — Visual polish** — adaptive launcher icon, refined notification silhouette
  (+ optional colored large icon), brand color scheme.
- **6g — API 33/34 + OEM matrix** — behavior verification across API levels and
  aggressive OEMs (real devices).

Also: the **`LICENSE` file** (GPL-3.0; repo housekeeping) and the deferred
**notification-text refinements** (pinned-note "Tap to edit or remove"; quick-add
pin-on-create) slot in where they fit.

## Decisions to settle when planning a slice
- Multi-select: include batch pin/unpin now (pinning exists) or delete-only?
- Visual: dynamic-color (Material You) toggle and/or custom brand palette, or keep
  Material defaults?
- About: include a "view source" link (opens a browser; no `INTERNET` for Stela)?
- Notification refinements: confirm the pinned-note content should become "Tap to
  edit or remove" (replaces showing the description).

---

## Slice 6a — Localisation foundation

**Status (2026-06-08) — complete.** Tests: 35 JVM unit + 19 instrumented, all green;
no `INTERNET`. ~45 strings externalized to `res/values/strings.xml` (English values
identical to the prior literals, so text-matching UI tests pass unchanged) across the
note list, editor, settings, and all notification text. `res/xml/locales_config.xml`
+ `android:localeConfig` give Android 13+ a free per-app language picker once a second
language ships; the system locale governs otherwise. No in-app picker (deferred). The
`specialUse` justification string stays English (for Play reviewers, not users).

**Adding a language later:** drop a `values-<lang>` strings file and add one
`<locale>` entry — no code changes.
