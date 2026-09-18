# Privacy Policy for Stela

**Last updated:** 18 September 2026

Stela does not collect, transmit, or share any personal information. There is no account to
create, no analytics, no advertising, and no crash reporting. The app has no ability to send
anything anywhere: it does not request Android's `INTERNET` permission, so it cannot make a
network connection even if it wanted to.

This policy covers the Stela Android app (`dev.davidfdev.stela`), however you obtained it.

## What Stela stores, and where

Everything Stela holds is created by you and stays in the app's private storage on your
device:

- **Your notes** — the title, description, emoji, and whether each note is pinned, archived,
  or scheduled.
- **Your preferences** — theme, notification options, and how the note list is sorted and
  filtered.

Both are readable only by Stela. Uninstalling the app removes them.

## What leaves your device

Nothing, unless you deliberately send it somewhere. There are exactly three ways note content
can move, and you initiate all of them:

- **Export** — Settings → Export notes writes a JSON file to a location you choose. Where that
  file then goes is up to you.
- **Share** — the Share action passes a note's text to whichever app you pick. That app's own
  privacy policy then applies.
- **Transferring to a new phone** — Stela opts out of Android's automatic cloud backup, so your
  notes are never uploaded to Google Drive or any other cloud service. Android's direct
  device-to-device transfer, which copies data straight from your old phone to your new one
  during setup, is left enabled so your notes come with you.

## Permissions, and why each one exists

- **Notifications** (`POST_NOTIFICATIONS`) — pinned notes *are* notifications; without this the
  app has nothing to show.
- **Foreground service** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) — keeps Stela
  running so pinned notes stay posted, re-post if they are cleared, and the quick-add entry
  stays available.
- **Run at startup** (`RECEIVE_BOOT_COMPLETED`) — re-pins your notes after the device reboots.
- **Prevent sleeping** (`WAKE_LOCK`) — required by an Android library Stela uses for the
  home-screen widget. Stela does not keep your device awake.

Stela requests no other permissions. It cannot access your contacts, location, camera,
microphone, files, or the internet.

## Children

Stela is a general-purpose notes app and is not directed at children. It collects no data from
anyone, including children.

## Changes to this policy

If this policy ever changes, the updated version will be published in this repository and the
date above will change with it. Because the app collects nothing, any change is likely to be a
clarification rather than a new practice.

## Contact

Questions about this policy, or about Stela generally:

- Email: contact@davidfdev.com
- Issues: https://github.com/DavidF-Dev/StelaApp/issues
