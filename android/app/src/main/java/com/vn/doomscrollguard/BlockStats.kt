package com.vn.doomscrollguard

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Local-only intervention counters + clean-streak tracker.
 *
 * Original design, informed by the general pattern (counters per day,
 * streaks, widget-visible stats) common to screen-time apps. Nothing
 * leaves the device; everything lives in one SharedPreferences file.
 *
 * - [recordBlock] is called by the accessibility service on every
 *   intervention. It bumps the per-day counter for the target and marks
 *   today as a "Guard-active" day.
 * - [markActiveDay] is also called from the main screen whenever the
 *   Guard is on, so days with zero blocks (a good day!) still count.
 * - [currentStreakDays] counts consecutive Guard-active days ending
 *   today — or yesterday, so a streak isn't "lost" before the user has
 *   even opened their phone in the morning.
 */
object BlockStats {

    private const val PREFS = "doomscroll_guard_stats"
    private const val PREFIX_BLOCKS = "blocks_"
    private const val PREFIX_ACTIVE = "active_"
    private const val KEY_TOTAL = "total_blocks"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today(): String =
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun recordBlock(context: Context, targetId: String) {
        val day = today()
        prefs(context).edit()
            .putLong(PREFIX_BLOCKS + day, todayBlocks(context) + 1)
            .putLong(KEY_TOTAL, totalBlocks(context) + 1)
            .putBoolean(PREFIX_ACTIVE + day, true)
            .apply()
        // Per-target counters are cheap and make a future per-app stats
        // screen possible without a migration.
        prefs(context).edit()
            .putLong(PREFIX_BLOCKS + day + "_" + targetId, todayBlocksFor(context, targetId) + 1)
            .apply()
    }

    fun markActiveDay(context: Context) {
        prefs(context).edit().putBoolean(PREFIX_ACTIVE + today(), true).apply()
    }

    fun todayBlocks(context: Context): Long =
        prefs(context).getLong(PREFIX_BLOCKS + today(), 0L)

    fun todayBlocksFor(context: Context, targetId: String): Long =
        prefs(context).getLong(PREFIX_BLOCKS + today() + "_" + targetId, 0L)

    fun totalBlocks(context: Context): Long =
        prefs(context).getLong(KEY_TOTAL, 0L)

    fun currentStreakDays(context: Context): Int {
        val p = prefs(context)
        var date = LocalDate.now()
        // Forgive "today not yet marked": a streak ending yesterday is
        // still the user's current streak until today is over.
        if (!p.getBoolean(PREFIX_ACTIVE + date.format(DateTimeFormatter.ISO_LOCAL_DATE), false)) {
            date = date.minusDays(1)
        }
        var streak = 0
        while (p.getBoolean(PREFIX_ACTIVE + date.format(DateTimeFormatter.ISO_LOCAL_DATE), false)) {
            streak++
            date = date.minusDays(1)
            if (streak > 3650) break // sanity cap: ten years
        }
        return streak
    }
}
