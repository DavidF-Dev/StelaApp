# Google Play release preparation

**Status:** in progress (2026-09-18) — app-side hygiene done; packaging and the Console track not started
**Date:** 2026-09-18
**Related:** [2026-06-09-v1-release.md](2026-06-09-v1-release.md), [2026-06-14-fdroid-submission.md](2026-06-14-fdroid-submission.md),
[2026-06-11-support-purchase.md](2026-06-11-support-purchase.md)

Goal: publish Stela on Google Play, starting with an **Internal Testing** release for the handful of
people already running the GitHub build.

## Readiness assessment (verified, not assumed)

The app is in a releasable state. Nothing in the code blocks an internal test; the work is packaging,
Console paperwork, and two decisions. Checked on 2026-09-18:

- **`bundleRelease` works out of the box.** A signed 11.3 MB AAB builds at
  `app/build/outputs/bundle/release/stela-release.aab`, with `lintVitalRelease` clean. (Bundle size is
  every density, ABI and language; Play delivers ~2–3 MB per device.) Neither CI nor `scripts/release.ps1`
  produces one today — that is a script change, not an app change.
- **16 KB page size compliance** (required by Play for `targetSdk` 35+ since Nov 2025): the app does ship
  two native libraries via dependencies — `libandroidx.graphics.path.so` (Compose) and
  `libdatastore_shared_counter.so` (DataStore). The arm64 ELF program headers show `p_align 0x4000` on
  every `PT_LOAD`. **Compliant.**
- **Permissions.** See the decisions below. No `INTERNET`, no advertising ID.
- **`targetSdk = 36`**, comfortably inside Play's window.

## Decisions locked

### Signing: upload the existing key as the app signing key

An AAB means Play App Signing, and the choice is between letting Google generate the app signing key —
which gives Play installs a *different* signature from the GitHub APKs, so existing testers must
uninstall (losing their notes unless they export first) and can never cross-update — or uploading
`stela-release.jks` via the PEPK tool so one signature covers both channels.

**Decided: upload the existing key.** After months of real use by real people, in-place updates matter
more than keeping the key solely on one machine. Verified eligible: 2048-bit RSA, SHA384withRSA, valid
until Oct 2053 (Play requires RSA ≥2048 and validity past 22 Oct 2033).

Caveats: it must be done **at app creation** — changing the app signing key later is a key-upgrade
process best not learned under pressure; the key then also lives in Google's custody, so the local backup
remains the author's responsibility; and F-Droid remains a third signature regardless. A separate *upload*
key can be registered later if rotation ever matters.

### Permissions: drop `ACCESS_NETWORK_STATE`, keep `WAKE_LOCK` — **done**

The merged manifest carried two permissions the app never declared: `WAKE_LOCK` and
`ACCESS_NETWORK_STATE`, both from `androidx.work:work-runtime:2.7.1`, a hard runtime dependency of
`androidx.glance:glance:1.1.1` (still present in Glance 1.2.0, so upgrading does not shed it). WorkManager
is genuinely used: `androidx.glance.session.SessionManagerImpl` enqueues a `OneTimeWorkRequest` running
`SessionWorker` for every widget session.

But that request sets **no `Constraints`** and is neither expedited nor foreground (read from the class
constant pools). `ACCESS_NETWORK_STATE` exists in WorkManager purely to service network constraints, and
nothing here asks for one — so it was removed with `tools:node="remove"`, rather than appear on the store
listing as "view network connections" next to an app that does no networking.

`WAKE_LOCK` stays. WorkManager takes one during its own start-up and rescheduling, which run on boot —
where a missing permission would break re-pinning, the app's core promise. The trade is a permission that
reads "prevent phone from sleeping", which contradicts nothing.

Verified: merged manifest (debug and release) carries only the four declared permissions plus
`WAKE_LOCK`; the device grants only `WAKE_LOCK` of the pair; WorkManager initialises cleanly at app
start; after a reboot both the pinned note and the quick-add foreground-service notification return with
no permission denial; and a real bound widget renders with Glance's session work running in Stela's own
process. Still worth confirming with a widget on a physical home screen before release — the emulator
will not accept a synthetic widget drag.

### Auto Backup off, device-to-device transfer left on — **done**

`android:allowBackup="true"` with no rules meant Android's automatic backup could copy the notes database
to the user's Google Drive, which sits awkwardly beside "your notes never leave your device". It is now
`false`.

**Correction to an earlier assumption:** `allowBackup="false"` does *not* reliably disable
device-to-device transfer on Android 12+. Google's wording: *"On devices from some device manufacturers,
specifying `android:allowBackup="false"` does disable backups to Google Drive, but doesn't disable D2D
transfers for the app."* From Android 12 the attribute governs cloud backups alone.

| | Android 8–11 | Android 12+ |
|---|---|---|
| Cloud backup to Drive | off | off |
| Device-to-device transfer | off | **still on** |
| adb backup | off | off |

**Decided: leave D2D enabled.** The concern was the database landing in cloud storage, and that is shut
off on every version. A D2D transfer is device→device and user-initiated, never touching a cloud, and it
means notes survive a phone upgrade. Closing it too would take a `dataExtractionRules` resource excluding
every domain under both `<cloud-backup>` and `<device-transfer>` — which would also clear the one lint
warning this leaves behind (`DataExtractionRules`, severity Warning: `allowBackup` is deprecated from
Android 12 and may eventually be removed). Accepted knowingly, alongside the project's other intentional
warnings.

## Remaining app-side work (not started)

- Add `bundleRelease` to the release tooling — a `-Play` switch on `scripts/release.ps1` that builds the
  AAB and reports its path and `versionCode`, uploading nothing. GitHub stays the APK channel; Play
  starts as a manual Console upload, with service-account automation only once the flow is boring.
- Fix `scripts/release.ps1`'s `APK SHA-256:` label — it publishes the *signing certificate* fingerprint,
  so a user who runs `sha256sum` on the APK gets a mismatch and reasonably concludes tampering.
- Fix the stale "Minification is disabled for now" comment in `app/proguard-rules.pro`.
- Add `bundleRelease` to `.github/workflows/build.yml`. It debug-signs on CI, which is fine — the value
  is running `lintVitalRelease` and R8 on every push so packaging breakage surfaces before release day.

## The Console track

Before any track can publish: privacy policy URL (publicly hosted — **the repo is still private**, so
this needs GitHub Pages, a personal site, or a public gist), Data safety ("no data collected, no data
shared" — genuinely true), content rating questionnaire, target audience, ads = none, app access = fully
open, plus the foreground-service declaration below. Store listing: ≤30-character title, ≤80 short, ≤4000
full, 512 icon, 1024×500 feature graphic, ≥2 phone screenshots — the same asset set the F-Droid plan
needs, so one capture pass feeds both. That is the bulk of the manual effort.

Suggested order: decide the signing key → create the app and complete App content → add `bundleRelease`
to the tooling → capture screenshots and graphics → upload to internal testing and add testers.

## Risks

- **`FOREGROUND_SERVICE_SPECIAL_USE` is the main one.** Play requires a per-type foreground-service
  declaration with a justification and a demo video, and `specialUse` is the type Google reviews by hand,
  biased toward "use an existing type, or no FGS at all". The manifest property is already drafted, which
  is what they ask for, but budget for a rejection round rather than assuming first-pass approval.
  Escape hatch, should they push hard: posted notifications are owned by the system and survive process
  death, `BootReceiver` re-pins after reboot, and alarms fire without the app running — so the service's
  real jobs are hosting quick-add and resisting kill. "No foreground service" is a genuine design option,
  not a dead end. Do not pre-emptively change it.
- **Account type.** If the developer account is a *personal* one created after Nov 2023, production
  access requires a **closed** test with ≥12 testers opted in for 14 continuous days. Internal testing
  does **not** count toward it. Plan internal → closed (12 testers / 14 days) → production. Confirm the
  current rule in Console; it has moved.
- **Version friction.** Play refuses a repeated `versionCode`, and ours derives from `versionName`, so
  every re-upload during testing needs a patch bump. Expect 1.8.1, 1.8.2 during the shake-out.

## Supporter badge

Unchanged in shape — see [2026-06-11-support-purchase.md](2026-06-11-support-purchase.md), whose
channel-split conclusion still holds. Adopting Play was that plan's gating prerequisite, so it moves from
"blocked" to "a later slice". Notes added on 2026-09-18 are appended to that document.
