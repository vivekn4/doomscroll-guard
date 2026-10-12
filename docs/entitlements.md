# FamilyControls entitlement — how to file on day one

The iOS shield layer needs the gated entitlement `com.apple.developer.family-controls`.
Without it, `AuthorizationCenter.requestAuthorization` fails (typically an XPC
error, `NSCocoaErrorDomain` 4099, "Couldn't communicate with a helper application").
The app is built to degrade gracefully — but file this **before** writing shield code.

## What to request

In the Apple Developer portal → Identifiers, the entitlement must be approved
**per bundle ID** — one request for each of:

| Bundle ID | Why |
|---|---|
| `com.vn.doomscrollguard` | the app |
| `com.vn.doomscrollguard.ShieldConfiguration` | shield styling extension |
| `com.vn.doomscrollguard.ShieldAction` | shield button handling extension |
| `com.vn.doomscrollguard.DeviceActivityMonitor` | schedule enforcement extension |

All four share the App Group `group.com.vn.doomscrollguard`.

## The request itself

Apple asks for a written justification. Say, in your own words:

- **What the app does:** a free, open-source self-control tool that helps people
  stop doomscrolling short-form video.
- **Why FamilyControls:** to grey out (shield) the native YouTube/Instagram/TikTok
  apps while the user's Guard switch is on, so the stripped in-app browser becomes
  the way in. Individual (self) authorization mode — the user shields *themselves*.
- **Why it's safe:** open-source (link the repo), no data collection, tokens are
  opaque by design, no way to learn which app a token refers to.

## Timelines & denial

- Expect **days to weeks**; Apple publishes no SLA. It **can be denied**.
- If denied: the app remains fully useful — the wrapper + Safari Content Blocker
  need no entitlement. Do not resubmit the same justification verbatim; address
  whatever reason Apple gives.

## Checklist

- [ ] Apple Developer account active
- [ ] 4 bundle IDs registered
- [ ] Entitlement requests filed for all 4 (day one!)
- [ ] App Group `group.com.vn.doomscrollguard` created and attached to all 4
- [ ] Test on a real device (Screen Time APIs don't work in Simulator)
