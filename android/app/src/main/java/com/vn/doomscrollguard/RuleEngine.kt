package com.vn.doomscrollguard

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Deterministic rule-based detector. Given an accessibility event's source node,
 * returns the matching [TargetRule] when a short-form surface is confidently visible.
 *
 * Detection layers (in order):
 *  1. view/resource IDs on the node + ancestors (highest confidence)
 *  2. text / content-description markers (medium — requires layout corroboration
 *     when the rule says so, to avoid matching e.g. a video *titled* "Shorts")
 *  3. layout pattern: fullscreen-ish vertical video + side controls (corroboration only)
 *
 * No ML, no heuristics that "decide" — every trigger is a declared rule firing.
 * Debounce: the same (target, package) pair can't re-trigger within [debounceMs].
 */
class RuleEngine(
    private val bundle: RuleBundle,
    private val debounceMs: Long,
    private val isTargetEnabled: (String) -> Boolean,
    private val screenSizePx: () -> Pair<Int, Int> = { 1080 to 1920 },
) {
    private val lastFire = mutableMapOf<String, Long>()

    data class Hit(val target: TargetRule, val via: String)

    fun detect(root: AccessibilityNodeInfo?, packageName: String): Hit? {
        if (root == null) return null
        val now = android.os.SystemClock.uptimeMillis()
        for (target in bundle.targets) {
            if (!isTargetEnabled(target.id)) continue
            if (!target.packages.contains(packageName)) continue
            if (target.wholeAppBlock) {
                if (debounced(target.id, packageName, now)) return Hit(target, "whole_app")
                continue
            }
            val via = matchSurface(root, target) ?: continue
            if (debounced(target.id, packageName, now)) return Hit(target, via)
        }
        return null
    }

    private fun debounced(targetId: String, pkg: String, now: Long): Boolean {
        val key = "$targetId|$pkg"
        val last = lastFire[key] ?: 0L
        if (now - last < debounceMs) return false
        lastFire[key] = now
        return true
    }

    /** Walk the node and its ancestors looking for a known view ID or text marker. */
    private fun matchSurface(root: AccessibilityNodeInfo, target: TargetRule): String? {
        // Collect the subtree breadth-first, but cap the walk: a11y trees can be huge.
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val textHits = mutableListOf<AccessibilityNodeInfo>()
        while (queue.isNotEmpty() && visited < 400) {
            val node = queue.removeFirst()
            visited++
            // 1) view IDs — check node + walk up ancestors (IDs often live on containers)
            var cursor: AccessibilityNodeInfo? = node
            var depth = 0
            while (cursor != null && depth < 6) {
                val id = cursor.viewIdResourceName?.substringAfterLast('/')?.lowercase()
                if (id != null && target.viewIds.any { it.lowercase() == id }) {
                    return "view_id:$id"
                }
                cursor = cursor.parent
                depth++
            }
            // 2) text markers (deferred: need layout corroboration check below)
            val texts = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            if (target.textMarkers.any { m -> texts.any { it.contains(m, ignoreCase = true) } }) {
                textHits += node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        if (textHits.isNotEmpty()) {
            if (!target.requiresLayoutCorroboration) return "text_marker"
            if (textHits.any { looksLikeVerticalPlayer(it) }) return "text_marker+layout"
        }
        return null
    }

    /** Corroboration: node occupies most of the screen in portrait aspect. */
    private fun looksLikeVerticalPlayer(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        val (w, h) = screenSizePx()
        val tallEnough = r.height() > h * 0.7
        val vertical = r.height() > r.width()
        return tallEnough && vertical && w > 0
    }
}
