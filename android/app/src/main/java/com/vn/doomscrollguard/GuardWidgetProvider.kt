package com.vn.doomscrollguard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

/**
 * Home-screen widget: one big tap toggles the Guard. RemoteViews only
 * (max compatibility, no custom views). Live state comes from the same
 * SharedPreferences the app UI uses; every toggle — from widget, app or
 * Quick Settings tile — re-broadcasts ACTION_STATE_CHANGED and immediately
 * redraws all widget instances.
 *
 * Layout: the big button toggles; tapping the status line opens the app.
 */
class GuardWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.vn.doomscrollguard.WIDGET_TOGGLE"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) updateOne(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> GuardState.toggle(context) // toggle() already re-broadcasts + redraws
            GuardAccessibilityService.ACTION_STATE_CHANGED ->
                redrawAll(context)
        }
    }

    private fun redrawAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(
            android.content.ComponentName(context, GuardWidgetProvider::class.java)
        )
        for (id in ids) updateOne(context, mgr, id)
    }

    private fun updateOne(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val on = GuardState.isOn(context)
        val views = RemoteViews(context.packageName, R.layout.widget_guard)

        views.setTextViewText(
            R.id.widget_toggle,
            if (on) "✓ GUARD ON" else "○ GUARD OFF",
        )
        views.setTextViewText(
            R.id.widget_status,
            if (on) "Shorts & Reels blocked" else "Tap to block Shorts & Reels",
        )
        // RemoteViews can't tint at runtime pre-31 cleanly; swap text color instead.
        val accent = if (on) 0xFF34D399.toInt() else 0xFFFFFFFF.toInt()
        views.setTextColor(R.id.widget_toggle, accent)

        val toggleIntent = Intent(context, GuardWidgetProvider::class.java)
            .setAction(ACTION_TOGGLE)
        val togglePi = PendingIntent.getBroadcast(
            context, appWidgetId, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_toggle, togglePi)

        val openIntent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_status, openPi)
        views.setOnClickPendingIntent(R.id.widget_root, openPi)

        // Re-route the root tap through the toggle area too? No — root opens the
        // app (secondary tap = deep link, per spec). Toggle button is the switch.
        mgr.updateAppWidget(appWidgetId, views)
    }
}
