# Changelog

All notable changes to Stela are documented here. This project adheres to
[Semantic Versioning](https://semver.org/).

## [1.1.0] - unreleased

### Added
- Per-note emoji, shown before the title in the note list and the notification; chosen with
  an emoji picker in the editor.
- "Swipe to unpin" setting (off by default): when on, swiping a pinned notification unpins
  it instead of self-healing.

### Changed
- The note Title and Description fields now auto-capitalise the first letter.
- The pinned-notification "Remove" action is now labelled "Unpin".

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
