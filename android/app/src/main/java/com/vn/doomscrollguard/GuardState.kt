package com.vn.doomscrollguard

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Single choke point for the master switch. App UI, home-screen widget and the
 * Quick Settings tile all go through here, so state can never disagree:
 * SharedPreferences is the source of truth, and every toggle re-broadcasts
 * [GuardAccessibilityService.ACTION_STATE_CHANGED] so the widget redraws
 * immediately no matter which surface flipped it.
 *
 * Weakening the Guard goes through friction, never a bare flip:
 * - Turning OFF starts a short countdown ([OffDelayManager]) — the Guard
 *   keeps blocking until it expires, and tapping again cancels it.
 * - Turning ON cancels any pending turn-off and any active pause.
 */
object GuardState {

    fun isOn(context: Context): Boolean =
        RuleRepository(context).isMasterEnabled()

    /** The service's view: on, and not inside a pending turn-off. */
    fun isEffectivelyOn(context: Context): Boolean =
        OffDelayManager.isEffectivelyOn(context)

    /**
     * Flip the master switch. Returns the new *effective* state.
     * - ON -> starts the turn-off countdown (still blocking until it expires).
     * - ON + countdown already running -> cancels the countdown (stays on).
     * - OFF -> turns on immediately, cancelling any pause/delay.
     */
    fun toggle(context: Context): Boolean {
        val repo = RuleRepository(context)
        if (OffDelayManager.isPending(context)) {
            OffDelayManager.cancel(context)
            buzz(context)
            return true
        }
        return if (repo.isMasterEnabled()) {
            OffDelayManager.startDelay(context)
            buzz(context)
            true // still effectively on until the countdown expires
        } else {
            PauseManager.cancelPause(context)
            repo.setMasterEnabled(true)
            buzz(context)
            broadcast(context)
            true
        }
    }

    fun setOn(context: Context, on: Boolean) {
        if (on) {
            PauseManager.cancelPause(context)
            OffDelayManager.cancel(context)
            RuleRepository(context).setMasterEnabled(true)
        } else {
            // Never double-start a countdown that's already running.
            if (!OffDelayManager.isPending(context) &&
                RuleRepository(context).isMasterEnabled()
            ) {
                OffDelayManager.startDelay(context)
            }
        }
        buzz(context)
        broadcast(context)
    }

    /** The delay receiver / boot path applies the turn-off for real. */
    fun applyPendingOffNow(context: Context) {
        OffDelayManager.resolve(context)
    }

    fun broadcast(context: Context) {
        context.sendBroadcast(Intent(GuardAccessibilityService.ACTION_STATE_CHANGED))
        // Nudge the a11y service to re-read rules + package filter.
        context.sendBroadcast(Intent(GuardAccessibilityService.ACTION_RELOAD))
        // Redraw every widget instance right now.
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, GuardWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            val update = Intent(context, GuardWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(update)
        }
    }

    private fun buzz(context: Context) {
        try {
            val vib = if (android.os.Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            vib?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { }
    }
}
