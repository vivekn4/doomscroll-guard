"""Friction-logic tests for Doomscroll Guard's teardown upgrades.

Ports the *pure* logic of the new Kotlin managers exactly, with a fake
clock and an in-memory prefs dict standing in for SharedPreferences:

  - PauseManager     (android/.../PauseManager.kt)
  - OffDelayManager  (android/.../OffDelayManager.kt)
  - BlockStats       (android/.../BlockStats.kt) — streak counting
  - Rapid-retry      (GuardAccessibilityService.isRapidRetry)

The Android wiring (AlarmManager, JobScheduler, broadcasts) is not
simulated; what is tested here is the state machine that wiring drives.
Each test drives the fake clock forward and asserts the exact outcomes.

Run:  python3 tests/friction_tests.py
Exit 0 = all green.
"""
import datetime as dt

PASS = FAIL = 0


def check(name, cond):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print(f"FAIL: {name}")


# ------------------------------------------------------------ fake clock
class Clock:
    def __init__(self):
        self.now_ms = 0

    def advance(self, ms):
        self.now_ms += ms

    def advance_days(self, n):
        self.now_ms += n * 86_400_000


# ------------------------------------------------- PauseManager (ported)
class PauseManager:
    DEFAULT_PAUSE_MINUTES = 15
    MIN_GAP_MS = 30 * 60 * 1000

    def __init__(self, prefs, clock, master):
        self.prefs = prefs          # dict
        self.clock = clock
        self.master = master        # dict with 'on' bool

    def is_paused(self):
        return self.prefs.get("pause_until", 0) > self.clock.now_ms

    def can_pause(self):
        if self.is_paused():
            return False
        return self.clock.now_ms - self.prefs.get("last_pause_at", -10**18) >= self.MIN_GAP_MS

    def start_pause(self, minutes=DEFAULT_PAUSE_MINUTES):
        if not self.can_pause():
            return False
        self.prefs["pause_until"] = self.clock.now_ms + minutes * 60 * 1000
        self.prefs["last_pause_at"] = self.clock.now_ms
        self.master["on"] = False
        return True

    def cancel_pause(self):
        self.prefs.pop("pause_until", None)

    def resume_if_due(self):
        until = self.prefs.get("pause_until", 0)
        if until == 0 or until > self.clock.now_ms:
            return
        self.prefs.pop("pause_until", None)
        self.master["on"] = True


# ---------------------------------------------- OffDelayManager (ported)
class OffDelayManager:
    OFF_DELAY_MS = 10 * 1000

    def __init__(self, prefs, clock, master):
        self.prefs = prefs
        self.clock = clock
        self.master = master

    def is_pending(self):
        return self.prefs.get("pending_off_until", 0) > self.clock.now_ms

    def start_delay(self):
        self.prefs["pending_off_until"] = self.clock.now_ms + self.OFF_DELAY_MS

    def cancel(self):
        self.prefs.pop("pending_off_until", None)

    def resolve(self):
        until = self.prefs.get("pending_off_until", 0)
        if until == 0 or until > self.clock.now_ms:
            return
        self.prefs.pop("pending_off_until", None)
        self.master["on"] = False

    def is_effectively_on(self):
        self.resolve()
        return self.master["on"]


# --------------------------------------------------- BlockStats (ported)
class BlockStats:
    def __init__(self, prefs):
        self.prefs = prefs

    def _day(self, offset=0):
        return (dt.date(2026, 10, 14) + dt.timedelta(days=offset)).isoformat()

    def record_block(self, day):
        self.prefs[f"blocks_{day}"] = self.prefs.get(f"blocks_{day}", 0) + 1
        self.prefs["total"] = self.prefs.get("total", 0) + 1
        self.prefs[f"active_{day}"] = True

    def mark_active(self, day):
        self.prefs[f"active_{day}"] = True

    def streak(self, today_offset=0):
        # mirrors BlockStats.currentStreakDays: forgive "today not marked"
        date = dt.date(2026, 10, 14) + dt.timedelta(days=today_offset)
        if not self.prefs.get(f"active_{date.isoformat()}", False):
            date -= dt.timedelta(days=1)
        n = 0
        while self.prefs.get(f"active_{date.isoformat()}", False):
            n += 1
            date -= dt.timedelta(days=1)
            if n > 3650:
                break
        return n


# --------------------------------------------------- rapid-retry (ported)
class RapidRetry:
    WINDOW_MS = 60 * 1000
    THRESHOLD = 3

    def __init__(self, clock):
        self.clock = clock
        self.queues = {}

    def hit(self, target):
        q = self.queues.setdefault(target, [])
        now = self.clock.now_ms
        q[:] = [t for t in q if now - t <= self.WINDOW_MS]
        q.append(now)
        return len(q) >= self.THRESHOLD


# ------------------------------------------------------------------ tests
def test_pause_happy_path():
    clock, prefs, master = Clock(), {}, {"on": True}
    pm = PauseManager(prefs, clock, master)
    check("pause: can pause initially", pm.can_pause())
    check("pause: start returns True", pm.start_pause())
    check("pause: master off during pause", master["on"] is False)
    check("pause: is_paused", pm.is_paused())
    check("pause: cannot pause while paused", not pm.can_pause())
    clock.advance(15 * 60 * 1000 - 1)
    check("pause: not due 1ms early", pm.is_paused())
    pm.resume_if_due()
    check("pause: still off before expiry", master["on"] is False)
    clock.advance(1)
    pm.resume_if_due()
    check("pause: auto-resumed at expiry", master["on"] is True)
    check("pause: not paused after resume", not pm.is_paused())


def test_pause_min_gap():
    clock, prefs, master = Clock(), {}, {"on": True}
    pm = PauseManager(prefs, clock, master)
    pm.start_pause()
    clock.advance(15 * 60 * 1000)
    pm.resume_if_due()
    check("gap: resumed", master["on"] is True)
    check("gap: cannot pause inside 30-min gap", not pm.can_pause())
    clock.advance(14 * 60 * 1000)
    check("gap: still blocked at 29 min", not pm.can_pause())
    clock.advance(60 * 1000)
    check("gap: allowed at exactly 30 min", pm.can_pause())
    check("gap: second pause works", pm.start_pause())


def test_pause_cancel():
    clock, prefs, master = Clock(), {}, {"on": True}
    pm = PauseManager(prefs, clock, master)
    pm.start_pause()
    pm.cancel_pause()
    check("cancel: not paused after cancel", not pm.is_paused())
    clock.advance(60 * 60 * 1000)
    pm.resume_if_due()
    check("cancel: no phantom resume flips master", master["on"] is False)


def test_off_delay():
    clock, prefs, master = Clock(), {}, {"on": True}
    od = OffDelayManager(prefs, clock, master)
    od.start_delay()
    check("delay: pending", od.is_pending())
    check("delay: still effectively on during countdown", od.is_effectively_on())
    clock.advance(9_999)
    od.resolve()
    check("delay: not applied 1ms early", master["on"] is True)
    clock.advance(1)
    check("delay: effectively off at expiry", not od.is_effectively_on())
    check("delay: master off after expiry", master["on"] is False)
    check("delay: pending cleared", not od.is_pending())


def test_off_delay_cancel():
    clock, prefs, master = Clock(), {}, {"on": True}
    od = OffDelayManager(prefs, clock, master)
    od.start_delay()
    od.cancel()
    check("delay-cancel: not pending", not od.is_pending())
    clock.advance(60_000)
    od.resolve()
    check("delay-cancel: master still on long after", master["on"] is True)


def test_streak():
    stats = BlockStats({})
    stats.mark_active("2026-10-12")
    stats.mark_active("2026-10-13")
    check("streak: 2 days ending yesterday", stats.streak() == 2)
    stats.mark_active("2026-10-14")
    check("streak: 3 with today marked", stats.streak() == 3)
    stats2 = BlockStats({})
    stats2.mark_active("2026-10-12")
    stats2.mark_active("2026-10-14")  # gap on the 13th
    check("streak: gap breaks the streak (1)", stats2.streak() == 1)
    stats3 = BlockStats({})
    check("streak: zero with no activity", stats3.streak() == 0)
    stats4 = BlockStats({})
    stats4.record_block("2026-10-14")
    check("streak: a block marks the day active", stats4.streak() == 1)
    check("streak: block counter increments", stats4.prefs["blocks_2026-10-14"] == 1)


def test_rapid_retry():
    clock, rr = Clock(), None
    rr = RapidRetry(clock)
    check("retry: 1st hit no shield", not rr.hit("youtube_shorts"))
    check("retry: 2nd hit no shield", not rr.hit("youtube_shorts"))
    check("retry: 3rd hit in window -> shield", rr.hit("youtube_shorts"))
    check("retry: 4th hit still shield", rr.hit("youtube_shorts"))
    check("retry: other target independent", not rr.hit("instagram_reels"))
    clock.advance(61_000)
    check("retry: window expiry resets", not rr.hit("youtube_shorts"))


def test_pause_and_delay_interaction():
    # Pause + delay must not fight: a pause is a master-off, so a pending
    # turn-off delay is meaningless and must be cancelled on manual ON.
    clock, prefs, master = Clock(), {}, {"on": True}
    od = OffDelayManager(prefs, clock, master)
    pm = PauseManager(prefs, clock, master)
    od.start_delay()
    clock.advance(5_000)
    # User taps ON-equivalent (cancel delay, stay on), then pauses.
    od.cancel()
    check("interact: delay cancelled, still on", master["on"] is True)
    check("interact: pause starts cleanly", pm.start_pause())
    clock.advance(15 * 60 * 1000)
    pm.resume_if_due()
    check("interact: resumed after pause", master["on"] is True)
    check("interact: no stale delay", not od.is_pending())


if __name__ == "__main__":
    for fn in (test_pause_happy_path, test_pause_min_gap, test_pause_cancel,
               test_off_delay, test_off_delay_cancel, test_streak,
               test_rapid_retry, test_pause_and_delay_interaction):
        fn()
    print(f"\n{PASS} passed, {FAIL} failed")
    raise SystemExit(1 if FAIL else 0)
