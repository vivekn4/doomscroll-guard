# Google Play policy — accessibility API

> Source: https://support.google.com/googleplay/android-developer/answer/13986130?hl=en
> (Accessibility API section, read live 2026-10-12). This doc summarizes what it
> means for Doomscroll Guard. Policy changes — re-read the source before any
> Play submission.

## The rules that matter

1. **Only disability-aid apps may set `isAccessibilityTool=true`.**
   Doomscroll Guard is not one. We do NOT set it — setting it falsely is itself
   a violation.
2. **Everything else needs all four:**
   - an **accessibility declaration** completed in Play Console,
   - a clear **in-app disclosure** explaining what accessibility data is accessed
     and how it's used (implemented: `OnboardingActivity` step 1, strings in
     `res/values/strings.xml`),
   - **affirmative user consent** before the service is used (implemented: the
     *I understand — continue* button; the app does nothing until it's tapped),
   - documentation of the use in the **Play listing**, plus a **demo video** for review.
3. **Prohibited:** changing user settings without permission; preventing the user
   from disabling/uninstalling the app or service (parental-control and enterprise
   exceptions don't apply to a self-control app — our service must stay
   user-disableable, and it is); working around platform security/privacy controls;
   deceptive UI changes.
4. **The Oct-2025 autonomous-agent clause.** Banned: "an app that autonomously
   initiates, plans, and executes actions or decisions." Our triggers are
   **deterministic rules** ("short-form view ID visible → back action") declared in
   `shared/rules/rules.json` — the explicitly permitted side of that line. No ML,
   no planning, no autonomous decisions anywhere in the codebase.
5. **Data minimization:** the service reads window content only from the target
   packages, only to detect short-form surfaces. Nothing is transmitted, logged
   remotely, or used for any other purpose. Say exactly this in the listing.

## Distribution strategy (risk-aware)

1. **GitHub Releases APK + F-Droid first.** No policy gate; precedent: Curbox Detox
   (F-Droid), MindShield / mood (GitHub Releases). Off-Play, the user still enables
   the service manually in Android Settings — it cannot be enabled programmatically.
2. **Google Play second**, only with the full declaration + disclosure + consent +
   listing docs + demo video package, accepting that review is discretionary.
   Category precedent (ScreenZen, Opal ship accessibility-based blocking on Play in
   2026) suggests a well-declared honest blocker passes — but plan for F-Droid-first
   regardless.

## Checklist before any Play submission

- [ ] Accessibility declaration completed in Play Console
- [ ] In-app disclosure + affirmative consent (in the app — verify the wording)
- [ ] Data safety form matches: no data collected/shared
- [ ] Listing documents the accessibility use in plain language
- [ ] Demo video showing: onboarding disclosure → enabling the service →
      Shorts being closed → disabling the service
- [ ] `RELEASE_ENFORCE_SIGNATURE = true` and ed25519 verification wired
      (see `RuleRepository.kt` TODO)
