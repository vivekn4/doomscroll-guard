# PLAN.md — DOOMSCROLL-GUARD

**Status:** build phase · **Date:** 2026-10-12
**Concept:** Free, open-source mobile app — one on/off switch that removes short-form video
(YouTube Shorts, Instagram Reels, TikTok, Snapchat Spotlight, Facebook Reels). Android + iOS.
**Feasibility brief:** `~/workspace/research_notes/doomscroll-guard-feasibility-20261012-0609/report.md`
(a deep-research pass; its architecture recommendation and honest-limitation statements are
followed decisively below).

---

## 1. Architecture

One repo, one shared "switch" concept, two fundamentally different native implementations —
there is no shared "reach inside the native app" mechanism across platforms.

```
doomscroll-guard/
├── shared/rules/        # versioned JSON rule bundle (packages, view IDs, text
│                        # markers, URL patterns, CSS selectors) + JSON schema
├── tools/               # validate_rules.py — schema + sanity validator (runs in CI/human QC)
├── android/             # Kotlin app — AccessibilityService detector (PRIMARY platform)
├── ios/                 # Swift app — WKWebView wrapper + FamilyControls shields (honest limits)
├── docs/                # android-setup, ios-setup, entitlements, play-policy
├── index.html           # VN landing page (GitHub Pages)
└── assets/screenshot.png# concept mockup (honestly labeled — no device testing possible here)
```

**The switch.** One master toggle + per-service toggles (`youtube_shorts`, `instagram_reels`,
`tiktok`, `snapchat_spotlight`, `facebook_reels`). The same toggle state maps to
platform actions:
- Android: selects which detector rules the AccessibilityService enforces.
- iOS: selects which app tokens sit in `ManagedSettingsStore` and which wrapper tabs are enabled.

**Rule bundle.** `shared/rules/rules.json` carries a `bundle_version` (semver) and an ed25519
`signature` block (value `null` until release signing is wired; the field and verification
flow are designed now). Both apps ship a bundled copy and can fetch updates without an app
release. `tools/validate_rules.py` enforces the schema before any bundle ships.

---

## 2. Android approach (primary — true in-app blocking)

**Engine:** `GuardAccessibilityService` (Kotlin), package-filtered.
- Subscribes to `TYPE_WINDOW_STATE_CHANGED | TYPE_WINDOW_CONTENT_CHANGED | TYPE_VIEW_SCROLLED`,
  filtered by `android:packageNames` = packages of *enabled* services only.
- Config: `flagReportViewIds | flagIncludeNotImportantViews`, `canRetrieveWindowContent=true`,
  `notificationTimeout` 50–100ms.
- **Detection (layered):** view/resource IDs on the scrolled node + ancestors →
  text/content-desc markers ("Shorts", "Reels", "Spotlight") → layout-pattern fallback.
  Starting ID sets (to be re-verified on-device against current app builds):
  - YouTube: `shorts_container`, `reel_player_page`, `reel_player_page_container`,
    `reel_recycler_view`, `shorts_control_panel`, `shorts_video_player`
  - Instagram: `clips_viewer_container`, `reel_viewer_fragment`, `clips_recycler_view`, `clips_viewer_pager`
  - Snapchat: `spotlight_container`, `stories_feed_recycler_view`, `snap_view`
- **Interventions:** `GLOBAL_ACTION_BACK` bounce-out of the Shorts/Reels player (default);
  optional full-screen shield overlay (`SYSTEM_ALERT_WINDOW`) with a calm "you turned this off"
  screen; `GLOBAL_ACTION_HOME` for whole-app blocks.
- **TikTok = whole-app block.** The entire feed is short-form; there is no meaningful
  "TikTok without short videos". Enabling the TikTok switch blocks the app itself
  (`com.zhiliaoapp.musically`).
- **Deterministic rule-based triggers only.** No ML, no heuristics that "decide" —
  this keeps the app on the explicitly permitted side of Play's Oct-2025 anti-autonomous-agent
  clause ("an app that autonomously initiates, plans, and executes actions or decisions" is banned;
  "if Shorts view ID visible → back action" is a deterministic rule).
- **Performance:** package filtering + event-type restriction + `notificationTimeout` +
  ~600ms debounce on detections. Battery delta to be measured with Battery Historian on-device.

**Onboarding (Play-policy driven):**
1. In-app disclosure screen: plain-language statement of what accessibility data is accessed
   (window content of the listed apps), why (detecting short-form surfaces), that nothing
   leaves the device. Affirmative consent button (required — this app is NOT an
   `isAccessibilityTool=true` accessibility aid).
2. System Settings deep-link: the service **cannot** be enabled programmatically; the user
   flips it in Android Settings → Accessibility. The app detects the state and shows
   setup-complete only when active.
3. Overlay permission (`SYSTEM_ALERT_WINDOW`) requested only if the user enables shield mode.

**Distribution:** GitHub Releases APK + F-Droid **first** (no policy gate; Curbox Detox precedent).
Google Play **second**, with full accessibility declaration in Play Console, in-app disclosure,
affirmative consent, listing documentation, and a demo video — accepting review risk.

---

## 3. iOS approach (honest limits — cannot touch native apps)

Apple provides **no API** to inspect or modify another app's UI. There is no iOS equivalent
of AccessibilityService. Tokens are opaque; `ManagedSettingsStore` works at whole-app /
web-domain granularity only. The README and landing page state this plainly.

Three layers, degrading gracefully:

1. **Wrapper (works today, no entitlement):** SwiftUI app with WKWebView tabs loading
   `m.youtube.com` / `instagram.com`, injecting `shorts-stripper.js` at `document_start`:
   route-rewrite `/shorts/<id>` → `/watch?v=<id>`; route-block `/reels/`; DOM-remove Shorts
   shelves / nav icons / Reels tab via CSS selectors + `MutationObserver` re-cleanup.
   Session cookies stay on-device; no account needed by the app itself.
2. **Shield (entitlement-gated):** FamilyControls `.individual` auth → `FamilyActivityPicker`
   → `ManagedSettingsStore` whole-app shields for native Instagram/YouTube/TikTok +
   `DeviceActivity` schedule for timed on/off. Three extensions (ShieldConfiguration,
   ShieldAction, DeviceActivityMonitor), one App Group. Requires the gated
   `com.apple.developer.family-controls` entitlement (per bundle ID, days-to-weeks, can be
   denied). **File the entitlement request on day one;** the app must be fully useful
   (wrapper layer) even if denied — `FamilyControlsManager` degrades to a "not available"
   state with an explanatory screen.
3. **Safari Content Blocker companion:** declarative WebKit JSON rules (cosmetic CSS hiding)
   removing Shorts shelves / nav icons on `m.youtube.com` and Reels UI on `instagram.com`
   **in Safari only**. User enables it in Settings → Safari → Content Blockers.

**iOS honest capability line (also in README):** this app cannot remove the Reels tab from
inside the native Instagram app or the Shorts tab from inside the native YouTube app. It
gives you a stripped in-app browser and can lock the native apps entirely via Screen Time.

---

## 4. Likely bugs / pitfalls + mitigations

| # | Pitfall | Mitigation |
|---|---------|------------|
| 1 | **View-ID rot.** YouTube/Instagram rename undocumented view IDs without notice; detection silently stops working. | Layered detection (IDs → text markers → layout patterns); rule bundle updates without app release; `validate_rules.py` in the loop; document the `adb uiautomator dump` re-verification flow in `docs/android-setup.md`. |
| 2 | **False positives.** Text-marker fallback ("Shorts") could match a video *titled* "Shorts". | IDs first, markers second; require fullscreen-vertical-video layout corroboration before intervening; debounce 600ms; prefer back-action (recoverable) over overlays. |
| 3 | **Battery drain (Android).** High-frequency window/scroll events. | Package + event-type filtering, `notificationTimeout`, debounce; measure with Battery Historian on-device; document expected overhead honestly. |
| 4 | **iOS entitlement denial / delay.** Shield layer dead without `com.apple.developer.family-controls`. | File day one; wrapper-first architecture so the app is useful regardless; graceful "not available" UI, never a crash. |
| 5 | **Play review rejection.** Accessibility-API review tightened 2024–2026. | GitHub/F-Droid-first distribution; Play only with full declaration + disclosure + consent + demo video; deterministic rules only (no autonomous-agent behavior). |
| 6 | **APM / OEM quirks (Android).** Android 17 Advanced Protection revokes non-tool a11y access; OEMs (Xiaomi etc.) add extra auto-start/overlay gates. | Detect and explain in-app ("Advanced Protection is on — the service can't run"); docs cover OEM-specific steps. |
| 7 | **Wrapper login friction (iOS).** Users must log into mobile sites inside the wrapper; some features (IG DMs) may behave oddly in WKWebView. | Honest docs; wrapper is opt-in per service; keep native-app shield as the alternative. |
| 8 | **Rule-bundle trust.** A malicious bundle update could widen detection. | ed25519 signature field designed in; releases signed, app verifies before applying (implementation stubbed with clear TODO for release engineering). |

---

## 5. UI/UX

**One-switch philosophy.** The entire product is a single master switch plus five per-service
toggles. No dashboards, no streaks, no gamification — the anti-doomscroll app must not itself
become a slot machine.

- **Android:** big master switch (haptic on toggle), per-service rows with status dots
  (YouTube Shorts / Instagram Reels / TikTok / Spotlight / FB Reels), a "test it" button that
  opens YouTube to verify detection, setup checklist (Accessibility ✓ / Overlay ✓), and a
  quiet "rules v1.2.0 · updated" line. Dark, calm palette — the opposite of an attention app.
- **iOS:** same switch UI; tabs for the stripped wrapper (YouTube / Instagram); shield setup
  flow (Screen Time auth → pick apps → schedule); clear "wrapper vs native app" explainer so
  users understand *why* the iPhone experience differs.
- **Landing page (`index.html`):** VN-maximalist (neon, particles, SFX on toggle demo) but the
  *copy* stays honest — per-platform capability cards with ✅/⚠️/❌, never implying iOS can do
  what it can't.

## 6. Distribution plan

1. GitHub repo `vivekn4/doomscroll-guard` (public, MIT), GitHub Pages landing page.
2. Android: signed APK on GitHub Releases; submit to F-Droid (metadata + fastlane); Play
   Console submission only after the disclosure/consent/demo-video package is complete.
3. iOS: source + Xcode project docs (no sideloading without a dev account); TestFlight when the
   entitlement lands; App Store only after.
4. Rule bundles: released as versioned JSON assets on GitHub Releases; in-app updater verifies
   signature before applying.

## 7. What "done" means for this build task (no SDKs here)

- [x] PLAN.md (this file)
- [ ] `shared/rules/rules.json` + `schema.json`, validated by `tools/validate_rules.py`
- [ ] Android: complete, human-compilable Kotlin sources + manifest + resources + gradle files
- [ ] iOS: complete, human-compilable Swift sources + JS engine + Safari blocker JSON + extensions
- [ ] docs: android-setup, ios-setup, entitlements, play-policy
- [ ] README with honest capabilities/limitations + validated-vs-untested section
- [ ] LICENSE (MIT), description.txt, VN landing page, concept-mockup screenshot (honestly labeled)
- [ ] Explicit "needs on-device testing" list in the final report
