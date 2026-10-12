# iOS — Xcode setup

The iOS app can't be built here (no Xcode/SDK on this machine); everything below
is written to compile in Xcode 16+ against the iOS 17+ SDK. Follow these steps on a Mac.

## 1. Create the project

1. Xcode → New Project → **iOS → App**. Name: `DoomscrollGuard`, bundle ID:
   `com.vn.doomscrollguard` (keep this exact — the entitlement is per bundle ID).
   Interface: SwiftUI, Language: Swift. **Uncheck** "Include Tests" (add later).
2. Drag the contents of `ios/DoomscrollGuard/` into the project (Copy items if needed).
   In Build Phases → Copy Bundle Resources, make sure `Resources/shorts-stripper.js`
   is included.

## 2. Add the three Screen Time extensions

Each is **File → New → Target → App Extension** with the matching template.
Use these bundle IDs (the entitlement must cover each one):

| Target | Template | Bundle ID |
|---|---|---|
| ShieldConfiguration | Shield Configuration | `com.vn.doomscrollguard.ShieldConfiguration` |
| ShieldAction | Shield Action | `com.vn.doomscrollguard.ShieldAction` |
| DeviceActivityMonitor | Device Activity Monitor | `com.vn.doomscrollguard.DeviceActivityMonitor` |

Replace each template's stub with the matching file under `ios/Extensions/`.

## 3. Add the Safari Content Blocker

**File → New → Target → Content Blocker Extension**, bundle ID
`com.vn.doomscrollguard.SafariBlocker`. Replace the stub handler with
`ios/SafariBlocker/ContentBlockerRequestHandler.swift` and add the generated
`ios/SafariBlocker/blockerList.json` to the extension target.
Regenerate it any time the rules change:
`python3 -c` (see the generator snippet in `ios/SafariBlocker/` header) —
source of truth is always `shared/rules/rules.json`.

## 4. Capabilities & groups

- Select the **app target** → Signing & Capabilities → **+ Capability → Family Controls**
  (this is the gated `com.apple.developer.family-controls` entitlement — see
  `docs/entitlements.md`; file the request **before** writing shield code).
- Add **App Groups** capability to the app + all three extensions:
  `group.com.vn.doomscrollguard`.

## 5. Run

The wrapper (stripped browser) works with no entitlement. The shield UI shows
"not available" until the entitlement is approved and Screen Time is authorized —
by design, never a crash.

## What to verify on-device

- Wrapper: Shorts shelves/nav hidden on m.youtube.com; `/shorts/<id>` rewrites to
  `/watch`; `/reels/` bounces to the IG feed; MutationObserver re-cleans on scroll.
- Shields: authorize → pick apps in Apple's picker → toggle Guard → native apps grey out.
- Content Blocker: enable in Settings → Safari → Extensions; check m.youtube.com in Safari.
