# F-Droid submission — plan

> Status: **Prepared, not submitted** · updated 2026-09-18 · deliberately held until the Play
> `specialUse` declaration is accepted.
>
> Context: Stela is distributed as a signed APK on GitHub Releases. This plan adds F-Droid as a
> distribution channel. Eligibility was assessed first (see "Eligibility" below) and is clean; the work is
> packaging and process, not app changes.
>
> **As of 2026-09-18:** the repo is public (Phase 0 done), the fastlane metadata tree and screenshots
> exist (Phase 1 done bar the licence-id decision), and the recipe is drafted at
> [`fdroid/dev.davidfdev.stela.yml`](../fdroid/dev.davidfdev.stela.yml). What remains is opening the MR.
>
> **Why it is being held:** the Play foreground-service review is the one outstanding thing that could
> still change the app's architecture. If `specialUse` is refused and the service is restructured,
> F-Droid should build the settled version rather than publish one that is immediately superseded — and
> F-Droid users update slowly, so a short-lived version lingers there. Submit once that declaration is
> accepted.

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

## Phase 0 — Gate: make the repo public — **done**

The repo is public. The pre-public secret scan was clean (no keystore, `keystore.properties`, or
credentials in history; author identity is a project email).

## Phase 1 — Repo-side prep (most can be done *while still private*)

1. **Declare GPL-3.0-or-later** — **done**, twice over. The copyright + "version 3 or (at your option) any
   later version" grant notice precedes the GPLv3 text in `LICENSE`, which is what makes the *or-later*
   metadata accurate rather than merely asserted. Note that this notice was once removed in passing by
   commit `6af78d7` ("Update LICENSE", 11 Jul 2026) and restored on 2026-09-18: the file reads as a
   complete licence either way, so its absence is easy to miss. If the recipe's `License:` field and this
   notice ever disagree, the notice is the thing that decides.
2. **The fastlane metadata tree** under `fastlane/metadata/android/en-US/` — **done**, and shared with the
   Play listing rather than written twice: `title.txt`, `short_description.txt`, `full_description.txt`,
   `changelogs/<versionCode>.txt` (the short user-facing note, which `scripts/release-aab.ps1` also reads),
   five `images/phoneScreenshots/`, and `images/icon.png` (512×512, rendered from the adaptive launcher
   icon — F-Droid would otherwise extract one from the APK, but Play requires the file). Still missing:
   `images/featureGraphic.png` (1024×500), which is optional here and required by Play.
3. **Public-repo polish** — **settled**. The GitHub issue tracker is enabled, which the recipe's
   `IssueTracker` field depends on. A `CONTRIBUTING` note was considered and deliberately skipped: F-Droid
   recommends but does not require one, and the README already states the refusals that matter to a
   would-be contributor (no internet permission, no ads, no analytics).

## Phase 2 — Signing

No build change required for the F-Droid-signed path. On F-Droid's build server `keystore.properties` is
absent, so `assembleRelease` debug-signs; F-Droid strips that and applies its own signature. (Reproducible
builds — see end — would change this.)

## Phase 3 — Submit to `fdroiddata`

F-Droid is not push-based. Open a Merge Request against `gitlab.com/fdroid/fdroiddata` adding
`metadata/dev.davidfdev.stela.yml`.

**The recipe is kept in this repo at [`fdroid/dev.davidfdev.stela.yml`](../fdroid/dev.davidfdev.stela.yml)**,
ready to copy into that MR. It is not read by anything here — the app's own build ignores it — so it exists
purely so the submission is a copy rather than a composition. It deliberately carries no `Summary` or
`Description`: F-Droid takes those from `fastlane/metadata/android/en-US/`, which is the same tree the Play
listing is written from, so there is one source for both. No `WebSite`/`AuthorWebSite` either, since
`SourceCode` already declares the repo.

- **Refresh the version fields at submission time.** The recipe pins `commit`, `versionName`,
  `versionCode`, `CurrentVersion` and `CurrentVersionCode` to a stable tag — v1.8.0 as drafted. F-Droid
  builds whatever `commit:` names, so point all five at whatever stable tag exists when the MR goes up.
- **Confirm the licence id still matches `LICENSE`** (see Phase 1), since that is the field most likely to
  have drifted between drafting and submitting.
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
