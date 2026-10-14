package com.vn.doomscrollguard

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Small receivers + the watchdog job. All three timed states (pause,
 * turn-off delay) funnel through here so a reboot can never strand them.
 */

/** Re-arms pause resumes + off-delay finalization after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // NOTE: Intent.ACTION_QUICKBOOT_POWERON is @hide in AOSP, so it is
        // not in the compile-time android.jar — the string literal is the
        // same value ("android.intent.action.QUICKBOOT_POWERON").
        if (intent?.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON",
            )
        ) return
        PauseManager.restoreAfterBoot(context)
        OffDelayManager.restoreAfterBoot(context)
        GuardWatchdogJob.schedule(context)
    }
}

/** Fired by the pause-resume alarm. */
class PauseResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PauseManager.resumeIfDue(context)
    }
}

/** Fired by the turn-off-delay finalize alarm. */
class OffDelayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        OffDelayManager.resolve(context)
    }
}

/**
 * Periodic health check: if the master switch is ON but the user (or the
 * system) turned the accessibility service off, the Guard is silently
 * doing nothing — the worst failure mode. The watchdog posts a
 * tap-to-fix notification instead of staying quiet.
 *
 * Battery-friendly: JobScheduler batches this with the system's other
 * periodic work; the check itself is two SharedPreferences reads.
 */
class GuardWatchdogJob : JobService() {

    companion object {
        const val JOB_ID = 1001
        const val CHANNEL_ID = "guard_watchdog"
        private const val NOTIF_ID = 101

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, GuardWatchdogJob::class.java),
            )
                .setPeriodic(15 * 60 * 1000L) // minimum allowed interval
                .setPersisted(true) // survive reboots; re-checked by BootReceiver
                .build()
            js.schedule(job)
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        // Synchronous check; nothing async to wait for.
        val masterOn = RuleRepository(this).isMasterEnabled()
        val serviceUp = GuardAccessibilityService.isServiceEnabled(this)
        // A pending turn-off is a deliberate user decision, not a failure.
        if (masterOn && !serviceUp && !OffDelayManager.isPending(this)) {
            postFixNotification()
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    private fun postFixNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Guard health",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val open = Intent(this, OnboardingActivity::class.java)
            .putExtra(OnboardingActivity.EXTRA_STEP, 1)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Doomscroll Guard is off")
            .setContentText("The switch is on, but the Guard isn't running. Tap to fix it.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIF_ID, notif)
        } catch (_: SecurityException) {
            // Notification permission not granted — nothing we can do
            // silently; the main screen's banner still covers this case.
        }
    }
}
