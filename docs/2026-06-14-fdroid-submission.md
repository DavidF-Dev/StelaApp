# F-Droid submission — plan

> Status: **Planned** · 2026-06-14 · gated on the repo going **public**. Not implemented.
>
> Context: Stela is currently distributed as a signed APK on GitHub Releases. This plan adds F-Droid
> as a distribution channel. Eligibility was assessed first (see "Eligibility" below) and is clean; the
> work is packaging and process, not app changes. The source repo is **still private** and will be made
> public later, after further self-testing — every outward step here is blocked on that.

## Decisions locked

- **Signing: F-Droid-signed now, reproducible builds later.** The first submission lets F-Droid sign with
  its own key (simplest path to listing). Consequence: the F-Droid build has a **different signature** from
  the GitHub-released APK, so a user cannot in-place update between the two channels — they pick one per
  install. Reproducible builds (same signature everywhere) are a deferred enhancement, sketched at the end.
- **License id: `GPL-3.0-or-later`.** The repo currently ships the bare GPLv3 text, which by itself reads as
  `GPL-3.0-only`. To legitimately declare *or-later*, the "or (at your option) any later version" grant must
  be stated authoritatively (see Phase 1). Apache-2.0 dependencies remain one-way compatible into GPLv3.

## Eligibility (already confirmed)

- **License:** GPL-3.0 — a recognised free license.
- **No proprietary dependencies:** every dependency is Apache-2.0/FOSS. `com.google.android.material` is
  Material Components (not Play Services); `com.vanniktech:emoji-google` bundles Noto emoji **sprites as
  drawables**, not a proprietary library. No Firebase/GMS/analytics.
- **No network / tracking:** the app declares **no `INTERNET` permission**. Expected anti-features: **none**.
- **Buildable from source:** standard Gradle wrapper, no NDK.
- **Tag-based releases:** `vX.Y.Z` tags with an auto-derived `versionCode` — a natural fit for F-Droid's
  tag-driven update checking.

## Phase 0 — Gate: make the repo public

Everything below is blocked on this. The pre-public secret scan is already done and clean (no keystore,
`keystore.properties`, or credentials in history; author identity is a project email). When testing is
finished, flipping visibility is the only action. Re-run a quick secrets check first if significant new
history has accumulated.

## Phase 1 — Repo-side prep (most can be done *while still private*)

1. **Declare GPL-3.0-or-later.** **Done in the LICENSE file** — a copyright + "version 3 or (at your option)
   any later version" grant notice now precedes the GPLv3 text, making the `GPL-3.0-or-later` metadata
   accurate. Remaining (optional polish): mirror the wording in the README **License** section and, if
   desired, add the standard short header to source files.
2. **Add the fastlane metadata tree** under `fastlane/metadata/android/en-US/`:
   - `title.txt` — `Stela`
   - `short_description.txt` — one line, **≤80 chars** (e.g. "Pin your notes as persistent notifications.
     Fully offline.")
   - `full_description.txt` — longer listing copy (≤4000 chars); adapt the README intro + curated features.
   - `changelogs/<versionCode>.txt` — per-version notes, filename is the **versionCode** (e.g. `10500.txt`
     for 1.5.0). Lift from `CHANGELOG.md`.
   - `images/icon.png` (512×512), optional `images/featureGraphic.png` (1024×500), and
     `images/phoneScreenshots/1.png …` — **screenshots need a device/emulator capture pass** (small
     logistics task; Pixel_8 AVD is available).
3. **Public-repo polish (optional but recommended):** a short `CONTRIBUTING` note and confirm the GitHub
   issue tracker is enabled (the metadata points users there).

## Phase 2 — Signing

No build change required for the F-Droid-signed path. On F-Droid's build server `keystore.properties` is
absent, so `assembleRelease` debug-signs; F-Droid strips that and applies its own signature. (Reproducible
builds — see end — would change this.)

## Phase 3 — Submit to `fdroiddata`

F-Droid is not push-based. Open a Merge Request against `gitlab.com/fdroid/fdroiddata` adding
`metadata/dev.davidfdev.stela.yml`. Skeleton recipe (fill `commit`/version to the submission tag):

```yaml
Categories:
  - Writing
License: GPL-3.0-or-later
AuthorName: David F Dev
AuthorEmail: contact@davidfdev.com
SourceCode: https://github.com/DavidF-Dev/StelaApp
IssueTracker: https://github.com/DavidF-Dev/StelaApp/issues
Changelog: https://github.com/DavidF-Dev/StelaApp/blob/HEAD/CHANGELOG.md
# No WebSite/AuthorWebSite: the repo is already declared via SourceCode. Add a personal
# homepage here only if one exists (it should not duplicate the SourceCode link).

RepoType: git
Repo: https://github.com/DavidF-Dev/StelaApp.git

Builds:
  - versionName: 1.5.0
    versionCode: 10500
    commit: v1.5.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 1.5.0
CurrentVersionCode: 10500
```

- **Submit at a stable tag, not 1.6.0-in-progress.** F-Droid builds the `commit:` tag. Use whatever stable
  tag exists at submission time (v1.5.0 today, or a later release) and set the matching `versionCode`.
- F-Droid CI (`fdroid lint` / a test build) plus a maintainer review run on the MR.

## Phase 4 — Review iteration & steady state

- Respond to review feedback on the MR until merged.
- Once merged, `UpdateCheckMode: Tags` + `AutoUpdateMode` means F-Droid auto-detects each new `vX.Y.Z` tag
  and rebuilds — the existing tag-based release flow drives F-Droid with no extra per-release work.

## Risks / open items

- **Build-server SDK lag.** `compileSdk = 36` / AGP 8.13.2 must be available on F-Droid's build server.
  They track current SDKs but can lag the newest by a few weeks; if so, wait or temporarily pin lower.
- **Screenshots** are the main manual effort in Phase 1.
- **Donate link (out of scope).** If the deferred Supporter gesture ever ships as an external link (see
  [support-purchase.md](2026-06-11-support-purchase.md)), F-Droid permits a `Donate:` metadata field; the
  `foss` flavor stays `INTERNET`-free and F-Droid-safe.

## Later: reproducible builds (deferred enhancement)

Goal: F-Droid verifies its build is byte-for-byte identical to the APK you sign and publishes **your** signed
APK — giving the **same signature** across GitHub and F-Droid, so users can update across channels. Requires:

- A deterministic build (no embedded timestamps/paths that vary per build).
- Registering `AllowedAPKSigningKeys` (your cert fingerprint) and a `Binaries:` URL pointing at your
  published, version-stamped APK so F-Droid can compare.
- Verifying reproducibility locally before claiming it in the recipe.

This is the only potentially deep task and is intentionally out of the first submission.

## Related docs

- [2026-06-09-v1-release.md](2026-06-09-v1-release.md) — signing, the cross-channel signature caveat, the
  GitHub release flow F-Droid will track.
- [2026-06-11-support-purchase.md](2026-06-11-support-purchase.md) — the deferred Supporter gesture and why
  it stays F-Droid-safe.
- [2026-06-08-stela-design.md](2026-06-08-stela-design.md) — authoritative design; notes GPL-3.0 /
  F-Droid-friendliness.
