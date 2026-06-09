# Stela — Notes as Notifications

Stela is a simple, **fully offline** Android note-taking app. Write plain notes and
**pin** them as persistent notifications in your status bar, so the things you need to
remember stay in front of you.

No ads. No analytics. **No internet permission** — your notes never leave your device.

## Features

- **Notes as notifications** — pin any note as an ongoing status-bar notification.
- **Quick-add** — an optional persistent notification to jot a new note from the tray.
- **Self-healing pins** — a swiped-away pinned note re-posts itself.
- **Survives reboot & app updates** — pinned notes return after a restart or an update.
- **Resists background kill** — a foreground service keeps pinned notes alive; onboarding
  guides battery-optimisation and OEM autostart exemptions.
- **Multi-select** — batch pin/unpin and delete.
- **Share** — send a note as plain text via the system share sheet.
- **Timestamps** — relative times in the list, absolute in the editor.
- **Theming** — Light / Dark / Follow System, with a brand colour scheme.
- **Localisation-ready** — all strings externalised; per-app language on Android 13+.

## Honest persistence

Modern Android cannot guarantee truly undismissable notifications or unkillable
processes. Stela's honest promise: pinned notes **self-heal** (re-post if cleared),
**survive reboot**, and **resist background kill**. The onboarding flow helps you grant
the battery-optimisation and autostart exemptions that make this reliable on aggressive
OEM builds.

## Requirements

- Android 8.0 (API 26) or newer.

## Building

Requires JDK 17 (Android Studio's bundled JDK works). From the repo root:

```
./gradlew assembleDebug              # debug APK
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests (needs a device/emulator)
./gradlew assembleRelease            # release APK (see Signing)
```

### Signing a release

Release builds are signed from a git-ignored `keystore.properties`. Copy
`keystore.properties.template` to `keystore.properties` and fill it in; generate the
keystore with:

```
keytool -genkeypair -v -keystore stela-release.jks -alias stela \
  -keyalg RSA -keysize 2048 -validity 10000
```

Without `keystore.properties`, `assembleRelease` falls back to debug signing so the build
stays runnable for testing. Never commit the keystore or its passwords.

## License

Stela is free software licensed under the **GNU General Public License v3.0**. See
[LICENSE](LICENSE).

App id: `dev.davidfdev.stela`
