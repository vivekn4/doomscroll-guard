package com.vn.doomscrollguard

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * V1 main screen. Design rule: zero jargon, one giant switch.
 *
 *  - Huge master toggle with plain-language status:
 *      ON  -> "Guard is ON — Shorts & Reels blocked"
 *      OFF -> "Guard is OFF — everything allowed"
 *  - Per-app rows in plain language (YouTube / Instagram / TikTok / Snapchat / Facebook).
 *  - Warning banner when the accessibility service isn't enabled yet.
 *  - First-run coach marks explaining the three things that matter.
 */
class MainActivity : Activity() {

    private lateinit var repo: RuleRepository
    private lateinit var bundle: RuleBundle
    private lateinit var statusText: TextView
    private lateinit var masterButton: TextView
    private lateinit var warnBanner: TextView
    private lateinit var appRows: LinearLayout

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = RuleRepository(this)

        if (!repo.hasConsented()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        bundle = repo.load()
        buildUi()
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, IntentFilter(GuardAccessibilityService.ACTION_STATE_CHANGED),
                RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, IntentFilter(GuardAccessibilityService.ACTION_STATE_CHANGED))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized) refresh()
        if (::repo.isInitialized && !coachShown()) showCoachMarks()
    }

    override fun onDestroy() {
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) { }
        super.onDestroy()
    }

    // ---------- UI ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#101218"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }

        val title = TextView(this).apply {
            text = "🛡️ Doomscroll Guard"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#B9C0CC"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        col.addView(title); col.addView(statusText)

        warnBanner = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#101218"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.parseColor("#F5C518"), 14f)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java)
                    .putExtra(OnboardingActivity.EXTRA_STEP, 1))
            }
        }
        col.addView(warnBanner)

        // The giant switch.
        masterButton = TextView(this).apply {
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(34), 0, dp(34))
            isClickable = true; isFocusable = true
            setOnClickListener { GuardState.toggle(this@MainActivity) }
        }
        val masterWrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
            addView(masterButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        col.addView(masterWrap)

        val appsLabel = TextView(this).apply {
            text = "Block short videos in:"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(4))
        }
        col.addView(appsLabel)

        appRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(appRows)

        val foot = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        }
        col.addView(foot)
        foot.text = "Rules v${repo.bundleVersion()} · nothing leaves your device · ✦ VN ✦\n" +
            "Tip: long-press your home screen → Widgets → Doomscroll Guard\nfor a one-tap switch."

        scroll.addView(col)
        root.addView(scroll)
        setContentView(root)
        buildAppRows()
        refresh()
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun buildAppRows() {
        appRows.removeAllViews()
        // Plain-language rows. Order matches what users ask for.
        val pretty = mapOf(
            "youtube_shorts" to ("YouTube Shorts" to "Closes the Shorts player, keeps the rest of YouTube"),
            "instagram_reels" to ("Instagram Reels" to "Closes the Reels viewer, keeps feed, Stories & DMs"),
            "tiktok" to ("TikTok" to "Blocks the TikTok app itself — it's all short video"),
            "snapchat_spotlight" to ("Snapchat Spotlight" to "Closes Spotlight, keeps chats & Stories"),
            "facebook_reels" to ("Facebook Reels" to "Closes the Reels viewer, keeps the rest of Facebook"),
        )
        for (target in bundle.targets) {
            val (name, sub) = pretty[target.id] ?: (target.label to "")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
            }
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            labels.addView(TextView(this).apply {
                text = name; textSize = 16f; setTextColor(Color.WHITE)
            })
            labels.addView(TextView(this).apply {
                text = sub; textSize = 12f; setTextColor(Color.parseColor("#9AA3B2"))
            })
            val sw = Switch(this).apply {
                isChecked = repo.isTargetEnabled(target.id)
                setOnCheckedChangeListener { _, on ->
                    repo.setTargetEnabled(target.id, on)
                    GuardState.broadcast(this@MainActivity)
                    refresh()
                }
            }
            row.addView(labels); row.addView(sw)
            appRows.addView(row)
            val div = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                setBackgroundColor(Color.parseColor("#1F2430"))
            }
            appRows.addView(div)
        }
    }

    private fun refresh() {
        if (!::repo.isInitialized) return
        val on = repo.isMasterEnabled()
        val serviceUp = GuardAccessibilityService.isServiceEnabled(this)

        statusText.text = if (on)
            "Guard is ON — Shorts & Reels blocked"
        else
            "Guard is OFF — everything allowed"

        masterButton.text = if (on) "✓ GUARD ON" else "○ GUARD OFF"
        masterButton.setTextColor(if (on) Color.parseColor("#101218") else Color.WHITE)
        masterButton.background = rounded(
            if (on) Color.parseColor("#34D399") else Color.parseColor("#2A2F3A"), 24f)

        if (!serviceUp) {
            warnBanner.visibility = View.VISIBLE
            warnBanner.text = "⚠️ Guard can't run yet — tap here to enable it in Settings (30 seconds)"
        } else {
            warnBanner.visibility = View.GONE
        }
    }

    // ---------- first-run coach marks ----------

    private fun coachShown(): Boolean =
        getSharedPreferences("doomscroll_guard", MODE_PRIVATE).getBoolean("coach_shown", false)

    private fun showCoachMarks() {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC101218"))
            isClickable = true
        }
        val bubble = { text: String ->
            TextView(this).apply {
                this.text = text
                textSize = 15f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#1F2430"))
                setPadding(dp(18), dp(14), dp(18), dp(14))
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        col.addView(bubble("👆 1 · This is the whole app.\nTap the big switch to turn the Guard on or off."))
        col.addView(bubble("🎚 2 · Pick what gets blocked.\nEach row is one app — YouTube, Instagram, TikTok…"))
        col.addView(bubble("🏠 3 · Add the home-screen widget.\nLong-press your home screen → Widgets →\nDoomscroll Guard, for one-tap switching."))
        val done = TextView(this).apply {
            text = "Got it →"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#F5C518"))
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        }
        col.addView(done)
        overlay.addView(col)
        overlay.setOnClickListener {
            (it.parent as ViewGroup).removeView(overlay)
            getSharedPreferences("doomscroll_guard", MODE_PRIVATE)
                .edit().putBoolean("coach_shown", true).apply()
        }
        (findViewById<ViewGroup>(android.R.id.content)).addView(overlay)
    }

    fun openSystemSettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
