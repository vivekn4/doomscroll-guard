package com.vn.doomscrollguard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Turn-off delay: weakening the Guard takes effect after a short
 * countdown, with a cancel path.
 *
 * The failure mode of every blocker is the impulse toggle — the user
 * opens the app to turn the Guard off *because* they want to scroll.
 * A 10-second delay with a visible countdown and a one-tap Cancel turns
 * "off" from a reflex into a decision, without ever trapping the user:
 * the delay is short, the cancel is always one tap away, and the Guard
 * keeps blocking for the whole countdown.
 *
 * State lives in its own SharedPreferences file so a crash or reboot
 * can't leave a half-applied toggle: [resolve] is called from every
 * entry point (service, main screen, alarm, boot) and either finalizes
 * an expired delay or re-arms its alarm.
 */
object OffDelayManager {

    private const val PREFS = "doomscroll_guard_offdelay"
    private const val KEY_PENDING_UNTIL = "pending_off_until_elapsed"

    /** How long "turn off" waits before it takes effect. */
    const val OFF_DELAY_MS = 10 * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun now(): Long = SystemClock.elapsedRealtime()

    fun isPending(context: Context): Boolean =
        prefs(context).getLong(KEY_PENDING_UNTIL, 0L) > now()

    fun pendingRemainingMs(context: Context): Long =
        (prefs(context).getLong(KEY_PENDING_UNTIL, 0L) - now()).coerceAtLeast(0L)

    /** Start the countdown. The master switch stays ON until it expires. */
    fun startDelay(context: Context) {
        prefs(context).edit().putLong(KEY_PENDING_UNTIL, now() + OFF_DELAY_MS).apply()
        scheduleFinalizeAlarm(context)
        GuardState.broadcast(context)
    }

    /** User changed their mind: cancel the pending turn-off. */
    fun cancel(context: Context) {
        prefs(context).edit().remove(KEY_PENDING_UNTIL).apply()
        cancelFinalizeAlarm(context)
        GuardState.broadcast(context)
    }

    /**
     * Single choke point for "is the Guard effectively on?".
     * Finalizes an expired delay (flips master off) as a side effect, so
     * callers never need to remember to check twice.
     */
    fun isEffectivelyOn(context: Context): Boolean {
        resolve(context)
        return RuleRepository(context).isMasterEnabled()
    }

    /**
     * If a pending turn-off has expired, apply it. No-op otherwise.
     * Safe to call from anywhere, any number of times.
     */
    fun resolve(context: Context) {
        val until = prefs(context).getLong(KEY_PENDING_UNTIL, 0L)
        if (until == 0L || until > now()) return
        prefs(context).edit().remove(KEY_PENDING_UNTIL).apply()
        RuleRepository(context).setMasterEnabled(false)
        GuardState.broadcast(context)
    }

    /** Re-arm the finalize alarm after a reboot, or finalize if due. */
    fun restoreAfterBoot(context: Context) {
        val until = prefs(context).getLong(KEY_PENDING_UNTIL, 0L)
        if (until == 0L) return
        if (until <= now()) resolve(context) else scheduleFinalizeAlarm(context)
    }

    fun finalizeIntent(context: Context): PendingIntent {
        val intent = Intent(context, OffDelayReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleFinalizeAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            prefs(context).getLong(KEY_PENDING_UNTIL, 0L),
            finalizeIntent(context),
        )
    }

    private fun cancelFinalizeAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(finalizeIntent(context))
    }
}
