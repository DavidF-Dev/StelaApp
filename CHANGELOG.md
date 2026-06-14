# Changelog

All notable changes to Stela are documented here. This project adheres to
[Semantic Versioning](https://semver.org/).

## [1.6.0] - unreleased

### Added
- Share to Stela: Stela now appears in the system share sheet when you share plain text from another app
  (for example a selection in your browser). Sharing text opens a new note prefilled with it — the shared
  title becomes the note's title and the text becomes its description — ready to tweak and save. Like
  everything else in Stela, this stays offline.

## [1.5.0] - 2026/06/14

### Added
- Scheduled pins: a new **Advanced** section in the note editor lets you set a note to **pin at** a chosen
  time and/or **unpin at** a later time (a temporary pin). Timing is approximate (no exact-alarm permission
  is needed) and best-effort across reboots and battery saving. Editor only — not the quick-note popup.
- Snooze: a **Snooze** action (in the note's overflow menu) hides a pinned note now and re-pins it later —
  pick **30 minutes / 1 hour / 3 hours / 1 day** or a custom duration. The return time appears as the
  note's "Pin at", so you can change or cancel it there.
- The description field now shows a slim scroll indicator on its right edge when it holds more text than
  fits, making it clear you can scroll within the field.
- When you save a note whose title starts with an emoji and you haven't chosen one, that emoji automatically
  becomes the note's emoji. Tip: start the title with a space to keep the emoji as part of the text instead.

### Changed
- Opening a note from its notification (or the widget / Quick Settings tile) while Stela is already on
  screen now opens the full editor in the app, instead of floating the quick-note popup over it. This
  avoids two editors being open for the same note at once. The popup still appears when you trigger it
  from another app.
- The description field is now a compact, fixed height (about two to six lines) that scrolls within itself
  as you write more, rather than growing to fill the screen. This leaves more of the page free to scroll
  and keeps the field from dominating the editor.
- Opening the quick-note popup to edit an existing note now places the cursor in the title (keyboard ready,
  at the end of the text) so you can start typing straight away. The full editor is unchanged.

### Fixed
- A pinned note with a very long description no longer fills the whole notification shade when expanded;
  the description is now trimmed to a compact length with an ellipsis, and the full note is a tap away.
- Quickly pressing Back twice on a screen such as Settings, Archived, About, or the note editor no longer
  leaves the app on a blank screen that needed a restart.
- Tapping a control on a screen that is sliding away no longer takes effect. Previously, pressing Back and
  then quickly tapping (for example) a theme option in Settings or "Set" on a time picker could apply that
  action to the screen mid-exit; taps are now ignored while a screen is animating in or out. Screen
  transitions are also a little quicker.
- Opening Stela from the "View notes" shortcut, the quick-add notification, or the running-service
  notification — then opening a note and leaving it — no longer closes the app. It now returns you to your
  note list as expected.
- In the note editor, the on-screen keyboard no longer covers the description while you type a longer note,
  and the toolbar across the top stays in place instead of scrolling off-screen.
- Closing the quick-note popup now hides the keyboard at the same time as the popup, instead of letting it
  linger for a moment over the app behind.
- Opening the overflow (⋮) menu while editing a note no longer hides the keyboard — it stays up in both the
  full editor and the quick-note popup, so it doesn't flash away and back when you open and close the menu.
- Expanding the quick-note popup into the full editor for an existing note no longer sometimes opens it with
  the description focused and the keyboard popping up — the editor now opens quietly, as it does elsewhere.

## [1.4.0] - 2026/06/12

### Added
- Quick-note popup: a lightweight editor that floats over whatever is on screen for jotting or quickly
  editing a note without bringing the whole app forward. It opens from the quick-add notification (the
  New note action or tapping its body) and the home-screen widget's ＋ (a new note, pinned on save), and
  from a pinned note's Edit action or tapping its body (that note). For an existing note it offers the
  same actions as the full editor — Share, Pin/Unpin, Archive, Delete — with Archive and Delete asking
  for confirmation first. An Expand button carries whatever you've typed into the full editor and leaves
  you on your note list. Behind a secure lock screen the popup is skipped and the full editor opens as
  before. The popup floats over your current app (it doesn't pull Stela to the foreground), and a second
  trigger reuses the one popup rather than stacking another.
- Long-pressing an icon-only button (in the editor, quick-note popup, note list, and Archived screen)
  now shows a brief tooltip with its name.
- Launcher shortcuts: long-press the Stela app icon for **New quick note** (opens the quick-note popup)
  and **View notes** (jumps to your list).
- Quick Settings tile: a **New quick note** tile you can add to the Quick Settings panel to jot a note
  from anywhere. Add it from there, or use the new "Add Quick Settings tile" button in Settings (Android 13+).

### Changed
- New "Remove action" setting: choose what removing a pinned note does — **Unpin**, **Archive**, or
  **Delete** (default Unpin). It controls the notification's remove action (whose label now reads Unpin /
  Archive / Delete to match) and what a swipe does. "Swipe to unpin" is renamed **"Swipe to remove"**.
  Delete is permanent and shows a warning in Settings.
- The note editor and quick-note popup tidied their action row: Share and Archive/Restore (and the
  popup's Expand) moved into an overflow (⋮) menu, and Save is now a checkmark button — so the row stays
  uncrowded and Save no longer gets squeezed on narrower screens or in longer languages.
- The pinned-note notification's Archive action was removed — it now has just Edit and Remove. You can
  still archive a note from the editor, the quick-note popup, or the new Remove action setting.
- The note editor's title auto-focus is now driven by whether the title is empty: a blank title (a
  new note, or an expanded popup left empty) focuses the title and opens the keyboard; a note that
  already has a title does not. (Previously only brand-new notes auto-focused.)
- The editor's description field now grows with your text up to a larger height and then scrolls,
  instead of staying a fixed size.
- The home-screen widget's add button now uses the same ＋ icon as the new "New quick note" launcher
  shortcut, instead of a plain text character.

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
