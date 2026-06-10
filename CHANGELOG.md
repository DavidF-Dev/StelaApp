# Changelog

All notable changes to Stela are documented here. This project adheres to
[Semantic Versioning](https://semver.org/).

## [1.2.0] - unreleased

### Fixed
- The emoji picker now scrolls and its category labels are legible. It is hosted in a
  Material bottom sheet themed to match the app's light/dark setting, fixing both the
  unreadable headers and the grid that wouldn't scroll.
- The quick-add notification now self-heals if swiped away (on Android 14+, where ongoing
  notifications became dismissible), matching pinned notes. To remove it, turn off quick-add
  in Settings.

## [1.1.0] - 2026/06/10

### Added
- Per-note emoji, shown before the title in the note list and the notification; chosen with
  an emoji picker in the editor.
- "Swipe to unpin" setting (off by default): when on, swiping a pinned notification unpins
  it instead of self-healing.
- Search, sort, and filter on the note list: search by title or description, sort by last
  modified / date created / title (with a direction toggle — newest/oldest first, or A–Z /
  Z–A), and filter to all / pinned / unpinned notes. An active filter is shown as a chip you
  can tap to clear, and the sort and filter choices are remembered.
- "Select all" in multi-select mode, which selects every note currently shown (respecting an
  active search or filter).

### Changed
- The note Title and Description fields now auto-capitalise the first letter.
- The pinned-notification "Remove" action is now labelled "Unpin".
- Opening the editor from a notification when the app was closed now returns you to your
  home screen on save or back, instead of dropping you on a note list you never opened.

## [1.0.0] — 2026/06/09

First public release.

### Added
- Plain-text notes with offline Room storage.
- Pin notes as ongoing status-bar notifications, with Edit and Remove actions.
- Quick-add notification for jotting a note from the tray; pins on save.
- Self-healing pins (re-post when swiped away) and re-pin on reboot or app update.
- Foreground service keeping pinned notes alive, with battery-optimisation and OEM
  autostart guidance.
- Multi-select with batch pin/unpin and delete.
- Share a note as plain text via the system share sheet.
- Created/modified timestamps — relative in the list, absolute in the editor.
- Light / Dark / Follow-System theming and an indigo brand colour scheme.
- Adaptive launcher icon (with a themed-icon layer).
- About screen (version, author, privacy promise, licenses, view source).
- Fully localisation-ready strings; per-app language picker on Android 13+.

### Privacy
- No `INTERNET` permission, no ads, no analytics — notes never leave the device.
