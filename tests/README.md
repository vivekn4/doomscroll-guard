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

## Rule-bundle validator

`../tools/validate_rules.py` checks `shared/rules/rules.json` against
`shared/rules/schema.json` (required fields, version format, no duplicate IDs).

## What these tests do NOT cover

Native compilation (needs Android SDK / Xcode) and live accessibility-tree
dumps (`adb shell uiautomator dump`) — see the "Needs on-device testing"
section in the README.
