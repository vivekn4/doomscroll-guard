"""Mock-test harness for Doomscroll Guard's detection engine.

Ports android/.../RuleEngine.kt logic exactly (same layering, same debounce,
same layout-corroboration rule) and runs it against synthetic accessibility
trees across a wide parameter matrix + error cases.

Run:  python3 mock_detection_tests.py
Exit 0 = all green.
"""
import json, sys, time

BUNDLE = json.load(open('shared/rules/rules.json'))
ENGINE = BUNDLE['android']['engine']
TARGETS = BUNDLE['android']['targets']
DEBOUNCE_MS = ENGINE['debounce_ms']

# ---------------------------------------------------------------- mock nodes
class Node:
    def __init__(self, view_id=None, text=None, desc=None, bounds=None, children=None, parent=None):
        self.view_id = view_id          # e.g. "com.google.android.youtube:id/shorts_container"
        self.text = text
        self.desc = desc
        self.bounds = bounds or (0, 0, 0, 0)   # (l, t, r, b)
        self.children = children or []
        self.parent = parent
        for c in self.children:
            c.parent = self

def chain(*nodes):
    """Build a root with the given children."""
    return Node(children=list(nodes))

# ------------------------------------------------------- ported RuleEngine
class RuleEngine:
    def __init__(self, targets, debounce_ms, enabled):
        self.targets = targets
        self.debounce_ms = debounce_ms
        self.enabled = enabled          # fn(target_id) -> bool
        self.last_fire = {}
        self._now = 0                   # mock clock (ms)

    def tick(self, ms):
        self._now += ms

    def _debounced(self, tid, pkg):
        key = f"{tid}|{pkg}"
        last = self.last_fire.get(key, -10**12)
        if self._now - last < self.debounce_ms:
            return False
        self.last_fire[key] = self._now
        return True

    def detect(self, root, package):
        if root is None:
            return None
        for t in self.targets:
            if not self.enabled(t['id']):
                continue
            if package not in t['packages']:
                continue
            if t.get('whole_app_block'):
                if self._debounced(t['id'], package):
                    return (t['id'], 'whole_app')
                continue
            via = self._match_surface(root, t)
            if via and self._debounced(t['id'], package):
                return (t['id'], via)
        return None

    def _match_surface(self, root, t):
        queue = [root]
        visited = 0
        text_hits = []
        view_ids = [v.lower() for v in t.get('view_ids', [])]
        markers = t.get('text_markers', [])
        while queue and visited < 400:
            node = queue.pop(0)
            visited += 1
            cursor, depth = node, 0
            while cursor is not None and depth < 6:
                vid = cursor.view_id
                if vid:
                    short = vid.split('/')[-1].lower()
                    if short in view_ids:
                        return f'view_id:{short}'
                cursor = cursor.parent
                depth += 1
            texts = [x for x in (node.text, node.desc) if x]
            if any(m.lower() in txt.lower() for m in markers for txt in texts):
                text_hits.append(node)
            queue.extend(node.children)
        if text_hits:
            if not t.get('requires_layout_corroboration'):
                return 'text_marker'
            if any(self._vertical_player(n) for n in text_hits):
                return 'text_marker+layout'
        return None

    @staticmethod
    def _vertical_player(node, sw=1080, sh=1920):
        l, t, r, b = node.bounds
        w, h = r - l, b - t
        return h > 0 and h > sh * 0.7 and h > w and sw > 0

# ---------------------------------------------------------------- helpers
FULLSCREEN = (0, 0, 1080, 1920)   # tall vertical player bounds
SMALL = (100, 1600, 300, 1700)    # small label bounds (NOT a player)

def nid(pkg, vid):
    return f"{pkg}:id/{vid}"   # real Android format: com.pkg:id/view_name

passed = failed = 0
def check(name, got, want):
    global passed, failed
    ok = (got == want) if not callable(want) else want(got)
    if ok:
        passed += 1
    else:
        failed += 1
        print(f"  FAIL {name}: got {got}, want {want}")

def fresh(enabled_map=None):
    em = enabled_map or {}
    return RuleEngine(TARGETS, DEBOUNCE_MS, lambda tid: em.get(tid, True))

print("== 1. Positive: every view ID per app must BLOCK ==")
for t in TARGETS:
    if t.get('whole_app_block'):
        continue
    for pkg in t['packages']:
        for vid in t['view_ids']:
            e = fresh()
            root = chain(Node(view_id=nid(pkg, vid), bounds=FULLSCREEN))
            hit = e.detect(root, pkg)
            check(f"{t['id']}/{pkg}/{vid}", hit[0] if hit else None, t['id'])

print("== 2. Positive: text markers must BLOCK (with layout where required) ==")
for t in TARGETS:
    if t.get('whole_app_block') or not t.get('text_markers'):
        continue
    pkg = t['packages'][0]
    marker = t['text_markers'][0]
    e = fresh()
    root = chain(Node(text=marker, bounds=FULLSCREEN))
    hit = e.detect(root, pkg)
    check(f"{t['id']}/text-marker+layout", hit[0] if hit else None, t['id'])
    # text marker WITHOUT player layout must NOT fire (corroboration gate)
    e2 = fresh()
    root2 = chain(Node(text=marker, bounds=SMALL))
    check(f"{t['id']}/text-marker-no-layout", e2.detect(root2, pkg), None)

print("== 3. Positive: whole-app blocks ==")
for t in TARGETS:
    if not t.get('whole_app_block'):
        continue
    pkg = t['packages'][0]
    e = fresh()
    hit = e.detect(chain(Node(text='For You')), pkg)
    check(f"{t['id']}/whole-app", hit, (t['id'], 'whole_app'))

print("== 4. Negative: benign screens must NOT block ==")
e = fresh()
check("yt watch page", e.detect(chain(Node(view_id=nid('com.google.android.youtube', 'player_view'), text='Some video title', bounds=FULLSCREEN)), 'com.google.android.youtube'), None)
e = fresh()
check("ig home feed", e.detect(chain(Node(view_id=nid('com.instagram.android', 'feed_recycler'), text='Suggested for you', bounds=FULLSCREEN)), 'com.instagram.android'), None)
e = fresh()
# video *titled* "Shorts" but not a vertical player -> corroboration must save it
check("video titled Shorts (no player layout)", e.detect(chain(Node(text='My Shorts compilation 2026', bounds=SMALL)), 'com.google.android.youtube'), None)
e = fresh()
check("unknown package", e.detect(chain(Node(view_id='x/y', bounds=FULLSCREEN)), 'com.example.random'), None)
e = fresh()
check("null root", e.detect(None, 'com.google.android.youtube'), None)
e = fresh()
check("empty tree", e.detect(chain(), 'com.google.android.youtube'), None)

print("== 5. Switch semantics: guard off / per-app off ==")
e = fresh({'youtube_shorts': False})
check("target disabled", e.detect(chain(Node(view_id=nid('com.google.android.youtube', 'shorts_container'), bounds=FULLSCREEN)), 'com.google.android.youtube'), None)
e = fresh({'youtube_shorts': False})
check("other target still fires when one disabled",
      (lambda h: h[0] if h else None)(e.detect(chain(Node(view_id=nid('com.instagram.android', 'clips_viewer_container'), bounds=FULLSCREEN)), 'com.instagram.android')), 'instagram_reels')

print("== 6. Debounce: rapid bursts fire once ==")
e = fresh()
pkg = 'com.google.android.youtube'
root = chain(Node(view_id=nid(pkg, 'shorts_container'), bounds=FULLSCREEN))
h1 = e.detect(root, pkg)
e.tick(100)
h2 = e.detect(root, pkg)
e.tick(600)
h3 = e.detect(root, pkg)
check("first fires", h1[0] if h1 else None, 'youtube_shorts')
check("burst suppressed", h2, None)
check("fires again after debounce", h3[0] if h3 else None, 'youtube_shorts')

print("== 7. Ancestor walk: ID on container 3 levels up ==")
e = fresh()
leaf = Node(text='...', bounds=SMALL)
mid2 = Node(children=[leaf])
mid1 = Node(view_id=nid('com.google.android.youtube', 'shorts_container'), children=[mid2])
root = chain(mid1)
check("ancestor id found", (lambda h: h[0] if h else None)(e.detect(root, 'com.google.android.youtube')), 'youtube_shorts')

print("== 8. Case-insensitivity ==")
e = fresh()
root = chain(Node(view_id=nid('com.google.android.youtube', 'SHORTS_CONTAINER'), bounds=FULLSCREEN))
check("upper-case view id", (lambda h: h[0] if h else None)(e.detect(root, 'com.google.android.youtube')), 'youtube_shorts')
e = fresh()
root = chain(Node(text='SHORTS', bounds=FULLSCREEN))
check("upper-case marker", (lambda h: h[0] if h else None)(e.detect(root, 'com.google.android.youtube')), 'youtube_shorts')

print("== 9. Rule JSON robustness ==")
bad_cases = [
    ("missing targets", {"bundle_version": "1.0.0"}),
    ("target missing packages", {"android": {"targets": [{"id": "x"}]}}),
]
for name, doc in bad_cases:
    try:
        tgts = doc['android']['targets']
        assert all('packages' in t and 'id' in t for t in tgts)
        check(name, 'no-raise', 'raise')
    except (KeyError, AssertionError):
        check(name, 'raise', 'raise')

print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
