# Changelog

All notable changes to Stela are documented here. This project adheres to
[Semantic Versioning](https://semver.org/).

## [1.4.0] - unreleased

### Added
- Quick-note popup: a lightweight editor that floats over whatever is on screen — emoji, title,
  description, and Save — for jotting a note without bringing the whole app forward. It opens from
  the quick-add notification (the New note action or tapping its body) and the home-screen widget's
  ＋ (a new note, pinned on save), and from a pinned note's Edit action or tapping its body (that
  note). An Expand button carries whatever you've typed into the full editor. Behind a secure lock
  screen the popup is skipped and the full editor opens as before.

### Changed
- The note editor's title auto-focus is now driven by whether the title is empty: a blank title (a
  new note, or an expanded popup left empty) focuses the title and opens the keyboard; a note that
  already has a title does not. (Previously only brand-new notes auto-focused.)

## [1.3.0] - 2026/06/11

### Added
- Archive notes: a reversible way to set a note aside without deleting it. Archive from the
  pinned notification, the editor, or by selecting notes in the list (with an undo). Archived
  notes are hidden from the main list, can't be pinned, and live in their own "Archived notes"
  screen (reachable from the list's overflow menu) where you can restore or delete them. They're
  kept in backups.
- Clear notes: a "Clear notes" option in Settings → Backup permanently deletes all notes, including
  archived, behind a confirmation dialog and with an undo. Your settings are kept.

### Changed
- The note editor's title heading is now hidden while editing a note, to give its action row more room
  (a new note keeps its "New note" heading). The Share action is hidden while creating a new note
  (shown only for existing notes). An archived note shows an "Archived" banner at the top of the editor.
- Opening an unpinned note briefly "pops" the pin button to draw attention to it (so it's easy to pin).
  Respects the system's reduce-animations setting.
- Delete confirmation buttons are now shown in the destructive (red) colour.
- Creating a new note now focuses the title and opens the keyboard automatically, so you can start
  typing straight away. (Opening an existing note is unchanged — it won't pop the keyboard.)

### Fixed
- The keyboard now closes promptly when you leave the note editor, instead of lingering for a moment
  over the next screen.
- On a full note list, the last note's pin button is no longer hidden behind the New-note button —
  there's now room to scroll it into view.

## [1.2.0] - 2026/06/11

### Added
- Undo delete: after deleting notes from the list, a snackbar offers to undo and restore
  them, re-pinning any that were pinned.
- Export and import notes as a JSON file, for backup and transfer. Both use the system file
  picker and stay fully offline. Importing adds the notes to your existing ones.
- Search the emoji picker: tap the search tab in the editor's emoji picker and type to find an
  emoji by name. Stays fully offline.
- New notes can now be pinned straight from the editor: the pin toggle is shown while creating a
  note (defaulting to pinned) and the note is pinned as soon as you save it.
- Home-screen widget: a quick-add ＋ plus a glanceable list of your pinned notes; tap a note to open
  it. Add it from your launcher's widget picker. Fully offline.
- A brief branded splash screen on launch: the Stela icon on the app's indigo, shown consistently
  across Android versions.

### Fixed
- The emoji picker now scrolls and its category labels are legible. It is hosted in a
  Material bottom sheet themed to match the app's light/dark setting, fixing both the
  unreadable headers and the grid that wouldn't scroll.
- Searching the emoji picker no longer shifts the emoji grid up when the keyboard appears; only
  the search box moves.
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
