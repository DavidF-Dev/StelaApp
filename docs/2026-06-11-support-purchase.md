# Support purchase ("Supporter" gesture) — findings & deferred plan

> Status: **Deferred** · 2026-06-11 · a findings/decision record from an early discussion, kept for a
> later slice. **Not scheduled, not implemented.**
>
> Context: Stela currently has a single user (the author), so there is no immediate need. This captures
> the design space and the decisions already locked so the work can be picked up cold later.

## Intent

A **purely support-based** in-app purchase: a way for users to show appreciation. It **locks no
features** — the app stays fully functional whether or not anyone ever buys it. A small, optional
"thank you," nothing more.

## Decisions locked in this discussion

- **One-time "Supporter" gesture, not repeatable.** A single purchase that is "owned" forever — not a
  re-buyable tip jar. In Play terms this is a **non-consumable** product (see model below).
- **No feature gating, ever.** At most a cosmetic acknowledgement (e.g. a small "Supporter" heart in
  About). Never a paywall.
- **Google Play is a later goal.** Adopting Play as a distribution channel is a prerequisite (see below)
  and is itself deferred. This slice cannot land before that.

## The core tension — the `no INTERNET permission` invariant

This is the first thing to resolve, because it is existential to Stela's identity (stated in CLAUDE.md,
the manifest comment, and the About privacy copy).

- Google Play Billing talks to the **Play Store app over IPC**; the Play Store (not Stela) does the
  networking. So in principle Stela does **not** need `android.permission.INTERNET` — the Billing
  Library adds `com.android.vending.BILLING`, not `INTERNET`.
- **Verify, do not assume.** Before committing: add the dependency, build, and inspect the **merged**
  manifest. If `INTERNET` is absent, the literal "no internet permission" promise survives; if a
  transitive dependency drags it in, that is a hard conflict with the app's identity and a stop-and-
  reconsider point.
- Regardless of the above, **"your notes never leave the device" stays 100% true** — billing never
  touches note data. The About copy would still want a precise tweak (e.g. "the optional Supporter
  purchase is handled by Google Play, not the app").

## Product model — one-time = non-consumable

A one-time Supporter gesture is the *simpler* end of billing in some ways and the *harder* end in
others, versus a repeatable tip:

- **Non-consumable product**: bought once, owned forever; Play prevents re-purchase. Correct for a
  one-time gesture.
- Unlike a consumable tip jar, the app **must reflect ownership**: on launch, query existing purchases
  (`queryPurchasesAsync`) to detect the Supporter state and show the cosmetic acknowledgement. This is a
  lightweight "restore," inherent to non-consumables.
- On purchase, **acknowledge** (not consume) within Play's **3-day** window or it auto-refunds.
- No server-side verification or backend needed — the acknowledgement is cosmetic, so local trust is
  fine. (If the badge ever mattered, that calculus would change; it does not here.)

## Google Play requirements (the gating prerequisite)

- **Distribution via Play is mandatory** — IAP only works for an app *installed from Play*. The current
  sideloaded GitHub APK and any future F-Droid build **cannot** use it. So this slice is really a
  *distribution* decision: it commits to Play as a channel.
- Play Console: a **merchant/payments profile** (bank, tax), the **in-app product** defined, a
  **privacy-policy URL**, and a **Data Safety** form (still "no data collected" — Google handles
  payment).
- **Google's cut:** 15% up to $1M/yr (small-business program), 30% above.
- The Billing Library must stay reasonably current — Play periodically enforces a minimum version.

## Architecture sketch (when built)

Slots into the existing "one class owns each external system" pattern:

- A single **`SupportManager`** (or `BillingRepository`) seam — the *only* class touching the Billing
  Library, mirroring how `NotificationController` is the sole `NotificationManager` toucher. UI observes
  a simple state: product available? · purchase in flight? · already a Supporter?
- **Graceful degradation** is mandatory given offline-first: no Play Store, no connection, or no payment
  method → the support entry shows "unavailable," and everything else stays fully functional offline.
- **Flavor split** to protect the pure build:
  - **`play` flavor** — includes the Billing dependency + Supporter UI.
  - **`foss` flavor** (GitHub / F-Droid) — no billing (F-Droid forbids proprietary Google libs), stays
    `INTERNET`-free.
- **Testing** needs Play license-test accounts / static test SKUs and an internal testing track; it
  cannot be exercised from a sideloaded or CI build.

## The cleaner alternative for non-Play builds

An **external donation link** (GitHub Sponsors / Ko-fi) opened via an `ACTION_VIEW` intent: the browser
handles the URL, so Stela needs **no billing library and no `INTERNET` permission**, keeps the app pure,
and avoids Google's cut. Natural fit for the GitHub/F-Droid builds (F-Droid permits donation links).

Trade-off: an external link **cannot reflect an in-app "Supporter" state** (no entitlement to query) —
it is fire-and-forget. And on the *Play* build it may be non-compliant: Play generally requires Play
Billing for in-app digital purchases, and the donation-vs-digital-good classification of a "give nothing
in return" gesture is genuinely fuzzy and has shifted over time. **Re-read the current Play Payments
policy before deciding.**

## Likely shape when picked up

Given the locked decisions, the probable end state is **channel-split**:

- **GitHub / F-Droid (`foss`):** an external "Support Stela" link — zero new permissions, app stays
  pure, no fees, but no in-app Supporter badge.
- **Play (`play`, once adopted):** a non-consumable Supporter purchase behind a `SupportManager` seam,
  with the cosmetic acknowledgement and precise About copy.

If Play is never adopted, the external link alone satisfies the intent at a fraction of the effort and
keeps the app's identity perfectly intact.

## Before building — open items to settle

1. Decide to publish on Google Play at all (the real fork; currently deferred).
2. Verify whether the Billing Library forces `INTERNET` into the merged manifest.
3. Confirm the **current** Play Payments policy on donations / supporter purchases.
4. Choose the acknowledgement UX (a cosmetic Supporter badge in About vs. nothing).
5. Decide whether the `foss` build also gets an external-link button, or stays purchase-free.

## Effort estimate

Bounded but real. The billing code for a single non-consumable is small; the weight is in **adopting
Play** (merchant account, review, Data Safety, privacy policy), the **flavor split**, the **testing
setup**, and keeping the `foss` build clean. Not a quick slice — gated on the Play decision.
