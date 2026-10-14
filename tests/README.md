# Tests

Everything here runs with the stock Python 3 on any machine — no Android SDK needed.

## Mock detection tests (50 cases, all green ✅)

`mock_detection_tests.py` ports `RuleEngine.kt`'s detection logic **exactly**
(same layering: view-ID → text-marker → layout corroboration, same 600 ms
debounce) and runs it against synthetic accessibility trees:

| Suite | What it checks |
|---|---|
| 1. Positive — every view ID per app | each known view ID on YouTube/IG/Snapchat (+ ReVanced/Vanced variants) must BLOCK |
| 2. Text markers | markers BLOCK with player layout; must NOT fire on small labels (corroboration gate) |
| 3. Whole-app blocks | TikTok fires on any screen |
| 4. Negative screens | regular watch page, IG feed, unknown package, null/empty trees must NOT block |
| 5. Switch semantics | per-app toggle off; other targets unaffected |
| 6. Debounce | rapid bursts fire once, re-fire after 600 ms |
| 7. Ancestor walk | view ID found on a container 3 levels up |
| 8. Case-insensitivity | upper-case IDs/markers still match |
| 9. Rule JSON robustness | malformed bundles raise instead of crashing |

Run: `python3 tests/mock_detection_tests.py` (exit 0 = all green).

## Friction-logic tests (new: pause, turn-off delay, streak, rapid-retry)

`friction_tests.py` ports the *pure* logic of the new Kotlin managers
(`PauseManager`, `OffDelayManager`, `BlockStats` streak counting, rapid-retry
window) with a fake clock and in-memory prefs, and asserts the exact state
machine: pause auto-resume at expiry, 30-min gap between pauses, cancel
paths, 10-second delay with cancel, streak forgiveness for "today not yet
marked", gap breaking a streak, 3-in-60s rapid-retry trigger + window expiry.

Run: `python3 tests/friction_tests.py` (exit 0 = all green, 40 cases).

## Rule-bundle validator

`../tools/validate_rules.py` checks `shared/rules/rules.json` against
`shared/rules/schema.json` (required fields, version format, no duplicate IDs).

## What these tests do NOT cover

Native compilation (needs Android SDK / Xcode) and live accessibility-tree
dumps (`adb shell uiautomator dump`) — see the "Needs on-device testing"
section in the README.
