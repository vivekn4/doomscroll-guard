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
 */
object GuardState {

    fun isOn(context: Context): Boolean =
        RuleRepository(context).isMasterEnabled()

    /** Flip the master switch. Returns the new state. */
    fun toggle(context: Context): Boolean {
        val repo = RuleRepository(context)
        val next = !repo.isMasterEnabled()
        repo.setMasterEnabled(next)
        buzz(context)
        broadcast(context)
        return next
    }

    fun setOn(context: Context, on: Boolean) {
        RuleRepository(context).setMasterEnabled(on)
        buzz(context)
        broadcast(context)
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
