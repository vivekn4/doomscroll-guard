#!/usr/bin/env python3
"""Validate shared/rules/rules.json against shared/rules/schema.json plus sanity checks.

Usage: python3 tools/validate_rules.py [--rules PATH] [--schema PATH]
Exit 0 = valid. Any failure prints errors and exits 1.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def fail(errors, msg):
    errors.append(msg)


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--rules", default=str(ROOT / "shared" / "rules" / "rules.json"))
    ap.add_argument("--schema", default=str(ROOT / "shared" / "rules" / "schema.json"))
    args = ap.parse_args()
    errors: list[str] = []

    try:
        import jsonschema
    except ImportError:
        print("ERROR: the 'jsonschema' package is required (pip install jsonschema)")
        return 1

    try:
        rules = json.loads(Path(args.rules).read_text(encoding="utf-8"))
    except Exception as e:
        print(f"ERROR: cannot parse rules JSON: {e}")
        return 1
    try:
        schema = json.loads(Path(args.schema).read_text(encoding="utf-8"))
    except Exception as e:
        print(f"ERROR: cannot parse schema JSON: {e}")
        return 1

    try:
        jsonschema.validate(rules, schema)
    except jsonschema.ValidationError as e:
        fail(errors, f"schema: {e.message} (at {'/'.join(map(str, e.path))})")

    # ---- sanity checks beyond the schema ----
    def check_regex(pattern, where):
        try:
            re.compile(pattern)
        except re.error as e:
            fail(errors, f"{where}: invalid regex {pattern!r}: {e}")

    # unique IDs
    for section, items, key in (
        ("android.targets", rules["android"]["targets"], "id"),
        ("ios.wrapper.js_rules", rules["ios"]["wrapper"]["js_rules"], "id"),
        ("ios.wrapper.tabs", rules["ios"]["wrapper"]["tabs"], "id"),
    ):
        ids = [i[key] for i in items]
        dupes = {i for i in ids if ids.count(i) > 1}
        if dupes:
            fail(errors, f"{section}: duplicate ids {sorted(dupes)}")

    # whole-app-block targets must use the 'home' intervention
    for t in rules["android"]["targets"]:
        if t.get("whole_app_block") and t["intervention"] != "home":
            fail(errors, f"android target {t['id']}: whole_app_block=true requires intervention 'home'")
        if not t.get("whole_app_block") and not t.get("view_ids") and not t.get("text_markers"):
            fail(errors, f"android target {t['id']}: needs at least one detection signal (view_ids or text_markers)")

    # iOS js_rules: regexes compile; route-rewrite needs rewrite_to; dom-remove needs selectors
    for r in rules["ios"]["wrapper"]["js_rules"]:
        if r.get("match_url_pattern"):
            check_regex(r["match_url_pattern"], f"ios js_rule {r['id']}")
        if r["action"] == "route-rewrite" and not r.get("rewrite_to"):
            fail(errors, f"ios js_rule {r['id']}: route-rewrite requires rewrite_to")
        if r["action"] == "dom-remove" and not r.get("css_selectors"):
            fail(errors, f"ios js_rule {r['id']}: dom-remove requires css_selectors")

    # Safari content blocker rules: url-filter compiles; css-display-none needs selector
    for i, cb in enumerate(rules["ios"]["safari_content_blocker"]):
        check_regex(cb["trigger"]["url-filter"], f"safari_content_blocker[{i}]")
        if cb["action"]["type"] == "css-display-none" and not cb["action"].get("selector"):
            fail(errors, f"safari_content_blocker[{i}]: css-display-none requires selector")

    # signature block present (value may be null pre-release — warn, don't fail)
    sig = rules.get("signature", {})
    if not sig.get("key_id"):
        fail(errors, "signature: key_id missing")

    if errors:
        print(f"FAILED: {len(errors)} problem(s) in {args.rules}")
        for e in errors:
            print(f"  - {e}")
        return 1
    if rules["signature"]["value"] is None:
        print("OK: rules bundle valid (note: unsigned — signature.value is null, expected pre-release).")
    else:
        print("OK: rules bundle valid and signed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
