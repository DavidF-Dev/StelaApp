# Stela — 6g Verification (API levels & OEM matrix)

> Companion to [2026-06-08-phase6-polish.md](2026-06-08-phase6-polish.md) §"Slice 6g".
> Records the in-environment audit + API-36 emulator verification, and provides the
> real-device OEM checklist for the maintainer to run later on physical hardware.

**Environment:** only the Pixel_8 (API 36) emulator is available here; no command-line
tools or other system images are installed. Real-device testing is **deferred** to the
maintainer's later personal-device pass (Part C).

---

## Part A — API-behavior audit (in-environment)

Reviewed every `Build.VERSION.SDK_INT` branch and the known foreground-service /
notification / permission pitfalls. Behaviour claims were checked against the Android
developer docs, not memory.

| Area | API | Concern | Verdict |
|------|-----|---------|---------|
| Boot restore + `specialUse` FGS | 34/35 | Android 14+ blocks some FGS types from `BOOT_COMPLETED` | ✅ OK — blocked types are `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone`; `specialUse` is **not** blocked. Verified live on API 36. |
| Background FGS start | 31+ | `startForegroundService` from background throws unless exempt | ✅ Both callers are exempt — `BOOT_COMPLETED` and a notification-action tap are documented exemptions. Hardened anyway (below). |
| FGS start crash safety | 31+ | `ForegroundServiceStartNotAllowedException` (a subtype of `IllegalStateException`) would crash the caller | 🔧 **Fixed** — `PinServiceController.start()` now catches it; `BootReceiver` routes through that single guarded seam. |
| Boot permission guard | 33+ | `BootReceiver` bypassed the `canPostNotifications` guard that `PinServiceController` has | 🔧 **Fixed** — boot now goes through `serviceController.start()`, which skips when notifications can't post. |
| Notification trampolines | 31+ | Actions may not launch an Activity via a receiver/service trampoline | ✅ OK — Edit / body tap use `getActivity`; Remove / Reassert use `getBroadcast` and the receiver never launches an Activity. |
| Ongoing-notification swipe | 34 | Ongoing notifications became user-dismissible | ✅ OK — `setDeleteIntent` → Reassert re-posts (covered by `ReassertOnClearTest`). |
| POST_NOTIFICATIONS | 33 | Runtime permission + denial handling | ✅ OK — onboarding requests it; the gate handles denial; the list shows a blocked-channel banner. |
| Per-app language | 33 | `localeConfig` per-app language | ✅ Declared (6a); single language ships, so the system picker is latent until a 2nd locale lands. |
| Adaptive / themed icon | 26 / 33 | Adaptive icon + monochrome layer | ✅ Added in 6f; verified in the launcher drawer. |

**Fix applied.** The two `startForegroundService` call sites were unguarded. Exposed the
single `serviceController` seam on the container, routed `BootReceiver` through it (gaining
the `canPostNotifications` guard), and wrapped the start in a `try/catch` for
`IllegalStateException` (the supertype of `ForegroundServiceStartNotAllowedException`, so
the catch is valid on all supported API levels). No happy-path behaviour change; crash-safe
and DRY. Existing unit + instrumented suites stay green; boot restore re-verified live.

**Follow-up implemented (2026-06-09).** `BootReceiver` now also handles
`ACTION_MY_PACKAGE_REPLACED`, so pinned notes re-post after an app update — not just a
reboot — without waiting for the next launch. Verified live: with a note pinned,
`adb install -r` (app not launched afterward) re-posted the pinned notification, with the
FGS started via the `MY_PACKAGE_REPLACED` allow-list (`reasonCode:PACKAGE_REPLACED`).
`ACTION_LOCKED_BOOT_COMPLETED` is deliberately **not** handled: it fires before the device
is unlocked, when the credential-encrypted Room database isn't readable.

---

## Part B — API 36 emulator verification

All on the Pixel_8 (API 36) emulator:

- ✅ Full suite: **54 JVM unit + 21 instrumented**, green.
- ✅ **Boot restore (live):** pinned a note ("BootCheck"), `adb reboot`; after boot the
  pinned notification re-posted (id=1, `pinned_notes`) and `PinService` ran as a
  `specialUse` foreground service, started via the `BOOT_COMPLETED` temp-allow-list.
- ✅ Pinned notification: brand accent (`color=0xff4a57b5`), ongoing, Edit/Remove actions;
  "Tap to edit or remove" hint when the description is empty; **body tap opens the editor**.
- ✅ Quick-add notification: brand accent; **pin-on-create** via `/new?pin=true`.
- ✅ Swipe-dismiss self-heal: `ReassertOnClearTest` (instrumented).
- ✅ Quick-add / service lifecycle: `ServiceLifecycleTest` (unit) + live service foreground.

**Not verifiable here** (AOSP emulator, single API level): OEM autostart intents (the
Settings row hides when the intent doesn't resolve), battery-saver kill-aggression,
multi-day persistence, and pre-34 API levels. These move to Part C.

---

## Part C — Real-device OEM checklist (deferred — run later on personal device)

Run per device. Target families: **Samsung (One UI)**, **Pixel/AOSP**, **OnePlus/Oppo/Realme
(ColorOS/OxygenOS)**. Record model, OS version, and API level for each.

### Setup
- [ ] Install; grant the notification permission when prompted (Android 13+).
- [ ] Settings → the **Battery optimisation** row opens a guidance dialog; use it (or the
  steps) to set Stela to Unrestricted / ignore battery optimisation.
- [ ] Settings → if an **Auto-start** row is shown, open its dialog and enable Stela in the
  device's auto-launch settings.

### Core persistence
- [ ] Pin a note → it appears as an ongoing notification.
- [ ] Swipe the pinned notification away → it **self-heals** (re-posts).
- [ ] Swipe Stela from Recents → pinned notes persist / re-post.
- [ ] Reboot → pinned notes re-post (may require autostart allowed first).
- [ ] Leave overnight under battery saver → pinned notes still present next morning.

### Per-API behaviours
- [ ] Android 13+: per-app language picker appears in system settings (once a 2nd locale ships).
- [ ] Android 14+: the ongoing pinned notification can be swiped but self-heals.
- [ ] Deny the notification permission → pinning shows the "notifications off" snackbar and the list banner appears.

### Lock screen
- [ ] Settings → Hide on lock screen ON → pinned notes hidden on a secure lock screen.

### Results
| Device | OS / API | Battery exempt | Autostart | Swipe self-heal | Recents-swipe | Reboot restore | Overnight | Notes |
|--------|----------|----------------|-----------|-----------------|---------------|----------------|-----------|-------|
| OnePlus (OxygenOS) | | see below | see below | ✅ | ✅ | ✅ | | core functions good |
| _(Samsung …)_ | | | | | | | | |
| _(Pixel …)_ | | | | | | | | |

### First device pass — OnePlus (OxygenOS), 2026-06-09
Core flows (pin, self-heal, recents-swipe, reboot restore) all worked. Four issues found
and addressed:

- **Settings/Editor not scrollable** — content cut off at the bottom. *Fixed:* added
  `verticalScroll` to both (NoteList is a `LazyColumn`, About already scrolled).
- **Quick-add showed on the lock screen** — it should never. *Fixed:* quick-add and
  "running" notifications now use `VISIBILITY_SECRET`; only pinned notes appear on the lock
  screen, governed by the "Hide on lock screen" setting.
- **Battery-optimisation button crashed OxygenOS Settings** — confirmed an OEM Settings
  bug (other apps crash the same way on that screen); not detectable from our process.
  *Mitigated:* the row now opens a guidance dialog (manual steps + best-effort "open
  settings" + a "may not work" note) instead of launching the intent blindly.
- **Auto-start button was a no-op** — the OnePlus auto-launch component is stale on newer
  OxygenOS (launch blocked, silently swallowed). *Fixed:* the row now shows for any known
  aggressive-OEM and opens the same guidance dialog; manual steps are the reliable path.

Also: the default English strings were converted to **British English** (optimisation,
licence/licences).
