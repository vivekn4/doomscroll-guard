package com.vn.doomscrollguard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * "Pause 15 minutes" — the humane off-switch.
 *
 * Turning the Guard off forever is an impulse decision; pausing it is a
 * plan. A pause turns the master switch off now and turns it back on
 * automatically when the timer ends. A minimum gap between pauses
 * ([MIN_GAP_MS] from the last pause *start*) keeps "just one more pause"
 * from becoming a habit.
 *
 * Robustness: the resume is scheduled with an inexact alarm (no exact-
 * alarm permission needed; a few minutes of drift is harmless here) AND
 * re-checked on every entry point — [resumeIfDue] is called from the
 * boot receiver, the alarm receiver, the main screen, and the
 * accessibility service — so a missed or late alarm can only delay the
 * resume, never lose it.
 */
object PauseManager {

    private const val PREFS = "doomscroll_guard_pause"
    private const val KEY_PAUSE_UNTIL = "pause_until_elapsed"
    private const val KEY_LAST_PAUSE_AT = "last_pause_at_elapsed"

    /** Default pause length offered by the UI. */
    const val DEFAULT_PAUSE_MINUTES = 15

    /** Minimum gap between the start of one pause and the next. */
    const val MIN_GAP_MS = 30 * 60 * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun now(): Long = SystemClock.elapsedRealtime()

    fun isPaused(context: Context): Boolean =
        prefs(context).getLong(KEY_PAUSE_UNTIL, 0L) > now()

    fun pauseUntilMs(context: Context): Long =
        prefs(context).getLong(KEY_PAUSE_UNTIL, 0L)

    fun pauseRemainingMs(context: Context): Long =
        (pauseUntilMs(context) - now()).coerceAtLeast(0L)

    fun canPause(context: Context): Boolean {
        if (isPaused(context)) return false
        val last = prefs(context).getLong(KEY_LAST_PAUSE_AT, 0L)
        return now() - last >= MIN_GAP_MS
    }

    /** Time until the user may pause again (0 = now). */
    fun millisUntilCanPause(context: Context): Long {
        if (isPaused(context)) return pauseRemainingMs(context)
        val last = prefs(context).getLong(KEY_LAST_PAUSE_AT, 0L)
        return (MIN_GAP_MS - (now() - last)).coerceAtLeast(0L)
    }

    /**
     * Start a pause: master off now, auto-resume at [minutes] from now.
     * Returns false if a pause isn't allowed yet (still paused, or inside
     * the minimum gap) — the caller should show the wait time instead.
     */
    fun startPause(context: Context, minutes: Int = DEFAULT_PAUSE_MINUTES): Boolean {
        if (!canPause(context)) return false
        val until = now() + minutes * 60 * 1000L
        prefs(context).edit()
            .putLong(KEY_PAUSE_UNTIL, until)
            .putLong(KEY_LAST_PAUSE_AT, now())
            .apply()
        RuleRepository(context).setMasterEnabled(false)
        scheduleResumeAlarm(context)
        GuardState.broadcast(context)
        return true
    }

    /** User changed their mind: cancel the pause and turn the Guard on. */
    fun cancelPause(context: Context) {
        prefs(context).edit().remove(KEY_PAUSE_UNTIL).apply()
        cancelResumeAlarm(context)
    }

    /**
     * If a pause has expired, flip the master switch back on. Safe to
     * call from anywhere, any number of times — it no-ops unless a pause
     * is actually due.
     */
    fun resumeIfDue(context: Context) {
        val until = prefs(context).getLong(KEY_PAUSE_UNTIL, 0L)
        if (until == 0L || until > now()) return
        prefs(context).edit().remove(KEY_PAUSE_UNTIL).apply()
        RuleRepository(context).setMasterEnabled(true)
        GuardState.broadcast(context)
    }

    /** Re-arm the resume alarm after a reboot, or resume if already due. */
    fun restoreAfterBoot(context: Context) {
        val until = prefs(context).getLong(KEY_PAUSE_UNTIL, 0L)
        if (until == 0L) return
        if (until <= now()) resumeIfDue(context) else scheduleResumeAlarm(context)
    }

    fun resumeIntent(context: Context): PendingIntent {
        val intent = Intent(context, PauseResumeReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleResumeAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Inexact is fine: every entry point also calls resumeIfDue().
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            pauseUntilMs(context),
            resumeIntent(context),
        )
    }

    private fun cancelResumeAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(resumeIntent(context))
    }
}
