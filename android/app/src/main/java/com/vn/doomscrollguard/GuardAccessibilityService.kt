package com.vn.doomscrollguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The blocking engine. Package-filtered, event-throttled, fully deterministic:
 * every intervention is a declared rule from the bundle firing — no autonomous
 * decisions, per Play policy (see docs/play-policy.md).
 *
 * Master + per-target state lives in SharedPreferences (RuleRepository) and is
 * re-read on every event, so the home-screen widget, Quick Settings tile and the
 * app UI all toggle the same live state with no restart needed.
 */
class GuardAccessibilityService : AccessibilityService() {

    private lateinit var repo: RuleRepository
    private lateinit var engine: RuleEngine
    private var bundle: RuleBundle? = null

    /** Timestamps (uptime ms) of recent interventions, per target. */
    private val recentHits = mutableMapOf<String, ArrayDeque<Long>>()

    override fun onServiceConnected() {
        repo = RuleRepository(this)
        reloadBundle()
        applyDynamicPackageFilter()
    }

    /** Re-read rules + retarget package filter (called on connect and on toggle). */
    fun reloadBundle() {
        bundle = repo.load()
        val b = bundle ?: return
        engine = RuleEngine(
            bundle = b,
            debounceMs = b.engine.debounceMs,
            isTargetEnabled = { id -> repo.isMasterEnabled() && repo.isTargetEnabled(id) },
            screenSizePx = {
                val dm = resources.displayMetrics
                dm.widthPixels to dm.heightPixels
            },
        )
        applyDynamicPackageFilter()
    }

    /**
     * Narrow the event stream to packages of currently-enabled targets only.
     * This is the main battery saver: the OS drops everything else before it
     * ever reaches us.
     */
    private fun applyDynamicPackageFilter() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        val pkgs = bundle?.targets
            ?.filter { repo.isTargetEnabled(it.id) }
            ?.flatMap { it.packages }
            ?.toSet()
            ?.toTypedArray()
            ?: emptyArray()
        // Never set an empty array — that would mean "all packages". If the user
        // disabled everything, keep the last known filter; the master check below
        // short-circuits anyway.
        if (pkgs.isNotEmpty()) {
            info.packageNames = pkgs
            setServiceInfo(info)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Timed states resolve here too, so a late alarm can only delay a
        // resume/turn-off, never lose it.
        PauseManager.resumeIfDue(this)
        if (!GuardState.isEffectivelyOn(this)) return
        val b = bundle ?: return
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> Unit
            else -> return
        }
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val hit = try {
            engine.detect(root, pkg)
        } catch (_: Exception) {
            null
        } ?: return

        BlockStats.recordBlock(this, hit.target.id)

        when (hit.target.intervention) {
            Intervention.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            Intervention.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            Intervention.OVERLAY -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                ShieldOverlayService.show(this, hit.target.label)
            }
        }

        // Rapid-retry friction: if the user reopens the same blocked
        // surface 3+ times within a minute, the plain bounce-out isn't
        // landing — show the longer shield with a "Pause 15 min instead"
        // escape valve. The bounce-out above still applies.
        if (isRapidRetry(hit.target.id)) {
            ShieldOverlayService.showExtended(this, hit.target.label)
        }
    }

    /**
     * True when this is the 3rd (or later) intervention for the target
     * within [RAPID_RETRY_WINDOW_MS]. Rolling window, per target.
     */
    private fun isRapidRetry(targetId: String): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        val queue = recentHits.getOrPut(targetId) { ArrayDeque() }
        while (queue.isNotEmpty() && now - queue.first() > RAPID_RETRY_WINDOW_MS) {
            queue.removeFirst()
        }
        queue.addLast(now)
        return queue.size >= RAPID_RETRY_THRESHOLD
    }

    override fun onInterrupt() { /* nothing latched; safe to no-op */ }

    override fun onUnbind(intent: Intent?): Boolean {
        // Keep the widget honest: when the service dies, toggles still work,
        // they just take effect next time the service is enabled.
        sendBroadcast(Intent(ACTION_STATE_CHANGED))
        return super.onUnbind(intent)
    }

    companion object {
        const val ACTION_STATE_CHANGED = "com.vn.doomscrollguard.STATE_CHANGED"
        const val ACTION_RELOAD = "com.vn.doomscrollguard.RELOAD_RULES"

        /** Rapid-retry window + threshold for the extended shield. */
        const val RAPID_RETRY_WINDOW_MS = 60 * 1000L
        const val RAPID_RETRY_THRESHOLD = 3

        /** True when the user has flipped the switch in Android Settings. */
        fun isServiceEnabled(context: android.content.Context): Boolean {
            val expected = android.content.ComponentName(context, GuardAccessibilityService::class.java)
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any {
                android.content.ComponentName.unflattenFromString(it) == expected
            }
        }
    }
}
