# Teardown notes — doomscroll-guard (2026-10-13)

Artifacts (all from official sources; SHA-256 in report):
- Curbox Detox 4-fdroid — F-Droid APK == GitHub release asset byte-identical
- DetoxDroid 2.9.1 — official GitHub release APK
- Zenwell 1.6 — official GitHub release APK

All three are GPL open source. Mechanisms below are paraphrased from
manifests, class inventories, and decompiled method structure. No code,
strings, art, or brand material was copied; implementations in this repo
are original code written from these mechanism descriptions.

## Curbox Detox (neth.iecal.curbox) — closest comparator
- One AccessibilityService fans events out to small single-purpose blocker
  components (app blocker, grayscale filter, focus-mode blocker, anti-
  uninstall check) plus usage trackers; async consumers get events through
  a coroutine channel. Lesson: keep the service a thin dispatcher; put
  each behavior in its own testable unit.
- Short-form blocking is split into: a view-based checker (counts/limits,
  cooldowns), a script runner that evaluates declarative per-app rules
  against the event, and a UI-hider that compiles per-app hide scripts and
  paints overlays over matched nodes. Lesson: declarative rules +
  compiled scripts scale better than hardcoded view IDs; cooldowns belong
  in the blocker, not the UI.
- Cooldowns: after a block, a per-app cooldown window suppresses repeat
  triggers. Lesson: cooldown prevents the back-action from fighting the
  user in a loop and gives the shield a reason to exist.
- Settings-change delay: weakening a rule does not apply instantly; the
  change sits pending and is reviewed/applied later (dedicated job +
  review screen). Lesson: friction on weakening is a first-class feature.
- Temporary disable: a scheduled job re-enables a disabled group after a
  timer. Lesson: "pause with auto-resume" beats "off until I remember".
- Watchdog: a JobService watches the blocker service and restores it.
  Lesson: a periodic health check + user-visible warning beats silence.
- Tamper protection: device-admin receiver + anti-uninstall checks in the
  event path. Lesson: real tamper resistance needs device admin; without
  it, honesty ("friction tool, not parental control") is the right claim.
- Boot receiver re-arms everything (boot, quickboot, package-replaced).
  Lesson: every timed/pending state must survive reboot.
- Widgets show live counters (reels blocked, screen time) with their own
  refresh broadcasts. Lesson: counters turn an invisible service into a
  visible win — retention hook.
- Unlock friction via NFC tag intents and QR/barcode scan activities.
  Lesson: physical-world friction is the strongest bypass resistance.
- No INTERNET permission at all. Lesson: the privacy posture itself is a
  trust feature worth stating in the listing.

## DetoxDroid (com.flx_apps.digitaldetox) — scroll-break + pause design
- "Break doom scrolling": a scroll-session monitor + scroll-distance
  estimator watches scrolling; after a long session it interrupts with
  a break screen and locks that feed for a cooldown. A "finish grace"
  window after the break lets the user finish what they were doing
  without instantly re-triggering. Typing in a text field does NOT
  count as scrolling (anti-false-positive rule). Lesson: scroll
  counting needs an input-mode guard and a grace window.
- "Wait before opening": a distracting app can make the user wait a few
  seconds behind a waiting screen before it opens. Lesson: a short wait
  is the gentlest effective friction.
- "Pause button": pause from Quick Settings, voice assistant, or a long
  hardware-button hold; auto-resumes after a timer; a minimum gap between
  pauses stops "just one more". Lesson: auto-resume + min-gap is the
  humane form of a pause.
- Commitment password: selected settings locked behind a passphrase,
  with tamper-warning overlays. Lesson: a passphrase gate is the
  middle ground between nothing and device admin.
- Device admin / device owner for hiding apps outright; a dedicated
  activity handles the admin-revoked case. Lesson: if you depend on
  admin, you must handle revocation as a first-class screen.
- Emergency-brake QS tile is a separate toggleable tile from the pause
  tile. Lesson: one tile per action; don't overload a tile.
- Also ships Shizuku provider for privileged operations.

## Zenwell (com.sarangem.zenwell) — friction menu + schedules
- Blocking runs through an AccessibilityService (`AppBlockerService`
  with `onAccessibilityEvent`), and the app additionally requests
  PACKAGE_USAGE_STATS — for usage statistics, not for the blocking loop
  itself. (Early manifest skim suggested UsageStats-only blocking; the
  class inventory corrects that: blocking is accessibility-based.)
- Friction unlocks: strict block, countdown timer, guided breathing
  timer, math problems, intentional typing. Lesson: a menu of friction
  types lets users pick their own difficulty — the "choose your
  friction" pattern.
- Schedules (active windows + weekdays) and Pomodoro. Lesson: time-boxed
  rules beat always-on rules for retention.
- Privacy posture: no INTERNET permission.

## What we implement (original code, this repo)
1. Pause with auto-resume + minimum gap (PauseManager + AlarmManager +
   BootReceiver). From DetoxDroid pause + Curbox temporary-disable.
2. Turn-off delay: weakening the master switch takes effect after a
   short countdown with a cancel path (OffDelayManager + alarm finalize).
   From Curbox settings-change delay, simplified to one switch.
3. Block counter + clean-streak stats on the main screen and widget
   (BlockStats). From Curbox trackers/widgets + Zenwell streaks.
4. Watchdog: periodic JobScheduler check; if master is on but the
   service is disabled, post a tap-to-fix notification
   (GuardWatchdogJob). From Curbox watchdog, simplified.
5. Rapid-retry friction: repeated blocks in a short window show the
   shield with a longer hold + a "Pause 15 min instead" action.
   From Curbox cooldowns + DetoxDroid wait screen.

## Deliberately NOT implemented
- Granular UI hiding (overlay cards over in-app elements): needs live
  node-bounds tracking; false-positive + overlay-abuse risk, and it
  cannot be verified without a device. Observed, not implemented.
- Device-admin / device-owner tamper resistance: heavy permission +
  Play scrutiny; conflicts with the honest "friction tool, not parental
  control" claim. Observed, not implemented.
- UsageStats polling loop: duplicates the accessibility engine and adds
  a permission; kept as a documented fallback design only. (Note: the
  Zenwell analysis initially suggested UsageStats-only blocking; the
  class inventory corrected that — all three teardown apps block via
  AccessibilityService.)
- NFC / QR unlock friction: needs hardware + camera permission for a
  marginal gain over pause/delay. Observed, not implemented.
- Passphrase lock on settings: adds account-recovery risk (forgotten
  passphrase = user locked out of their own switch); delay + pause
  give most of the benefit. Observed, not implemented.
