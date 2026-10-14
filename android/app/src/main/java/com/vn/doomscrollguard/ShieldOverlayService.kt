package com.vn.doomscrollguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Optional calm shield screen: when a blocked surface opens and the user picked
 * "Shield" mode, we bounce out of the player and show this overlay for a moment
 * instead of just a bare back-navigation. Requires SYSTEM_ALERT_WINDOW, which the
 * onboarding flow requests only if the user enables shield mode.
 */
class ShieldOverlayService : Service() {

    private var wm: WindowManager? = null
    private var view: LinearLayout? = null

    companion object {
        private const val CHANNEL = "guard_shield"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_EXTENDED = "extended"
        private const val DISMISS_MS = 4000L
        private const val EXTENDED_DISMISS_MS = 10000L

        fun show(context: Context, label: String) {
            if (!Settings.canDrawOverlays(context)) return
            val i = Intent(context, ShieldOverlayService::class.java)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_EXTENDED, false)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        /**
         * The rapid-retry shield: longer hold + a "Pause 15 min" escape
         * valve, for when the plain bounce-out isn't landing. Shown after
         * the bounce-out, never instead of it.
         */
        fun showExtended(context: Context, label: String) {
            if (!Settings.canDrawOverlays(context)) return
            val i = Intent(context, ShieldOverlayService::class.java)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_EXTENDED, true)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, ShieldOverlayService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Guard shield", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Doomscroll Guard")
            .setContentText("Shield screen active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
        startForeground(2, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "short-form video"
        val extended = intent?.getBooleanExtra(EXTRA_EXTENDED, false) ?: false
        showOverlay(label, extended)
        return START_NOT_STICKY
    }

    private fun showOverlay(label: String, extended: Boolean = false) {
        dismissOverlay()
        wm = getSystemService(WindowManager::class.java)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF2101218.toInt())
            setPadding(64, 64, 64, 64)
        }
        val title = TextView(this).apply {
            text = "🛡️ Guarded"
            textSize = 30f
            setTextColor(0xFFF5C518.toInt())
            gravity = Gravity.CENTER
        }
        val body = TextView(this).apply {
            text = if (extended)
                "You turned $label off — and came back a few times in a row.\n" +
                "Take a breath. If you really need it, pause the Guard\n" +
                "for 15 minutes — it turns itself back on."
            else
                "You turned $label off.\nTake a breath — the feed will wait."
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 32)
        }
        val done = Button(this).apply {
            text = "Back to what I was doing"
            setOnClickListener { dismissOverlay(); stopSelf() }
        }
        root.addView(title); root.addView(body); root.addView(done)
        if (extended) {
            val pause = Button(this).apply {
                text = "Pause the Guard for 15 min"
                setOnClickListener {
                    // If a pause isn't allowed yet (minimum gap), the
                    // shield just closes — the Guard stays on.
                    PauseManager.startPause(this@ShieldOverlayService)
                    dismissOverlay(); stopSelf()
                }
            }
            root.addView(pause)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        wm?.addView(root, params)
        view = root
        // Auto-dismiss: a shield is friction, not a trap.
        root.postDelayed(
            { dismissOverlay(); stopSelf() },
            if (extended) EXTENDED_DISMISS_MS else DISMISS_MS,
        )
    }

    private fun dismissOverlay() {
        try { view?.let { wm?.removeView(it) } } catch (_: Exception) { }
        view = null
    }

    override fun onDestroy() {
        dismissOverlay()
        super.onDestroy()
    }
}
