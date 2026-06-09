# Changelog

All notable changes to Stela are documented here. This project adheres to
[Semantic Versioning](https://semver.org/).

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
