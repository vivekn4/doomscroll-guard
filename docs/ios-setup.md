# iOS setup & build

Requires: a Mac with Xcode 16+, an Apple Developer account (for the entitlement).

## Build

Follow `ios/README.md` to create the Xcode project and wire the targets
(app + 3 Screen Time extensions + Safari Content Blocker). There is no
`.xcodeproj` in this repo — generating one by hand is error-prone; the README's
5-step setup is the supported path.

## Day-one checklist

1. **File the FamilyControls entitlement FIRST** (`docs/entitlements.md`).
   It takes days-to-weeks and can be denied. Everything shield-related is
   entitlement-gated; the wrapper works regardless.
2. Build & run on a real device (Screen Time APIs don't work in Simulator).
3. In the app: flip the master switch → open the stripped browser tabs.
4. Settings → Screen Time → authorize → pick YouTube/Instagram/TikTok in Apple's
   picker → toggle the Guard → confirm the native apps grey out.

## What works without the entitlement

- The stripped WKWebView browser (Shorts shelves/nav hidden, `/shorts/<id>`
  rewritten to `/watch`, `/reels/` bounced to feed).
- The Safari Content Blocker (enable in Settings → Safari → Extensions).

## What needs the entitlement

- Whole-app shields on the native apps, DeviceActivity schedules.

## Known limits (do not file these as bugs)

- The app **cannot** remove the Reels tab from inside the native Instagram app or
  the Shorts tab from inside the native YouTube app. No iOS API allows it.
- The wrapper is a second login session: log into the mobile sites inside the app.
- Instagram's web app is a heavy SPA — selectors in `shorts-stripper.js` need
  periodic maintenance; keep them in sync with `shared/rules/rules.json`.
