package com.vn.doomscrollguard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One-tap onboarding wizard. Three steps, plain language, zero jargon:
 *  1. Welcome + the Play-policy disclosure (data access/use) with affirmative consent.
 *  2. Enable the accessibility service — why it's needed, deep-link to Settings.
 *  3. Done — master switch is on, widget hint.
 *
 * Launched with EXTRA_STEP=1 to jump straight to the enable step (from the
 * main screen's warning banner).
 */
class OnboardingActivity : Activity() {

    companion object { const val EXTRA_STEP = "step" }

    private lateinit var repo: RuleRepository
    private var step = 0
    private lateinit var root: LinearLayout

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = RuleRepository(this)
        step = intent.getIntExtra(EXTRA_STEP, 0)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101218"))
            setPadding(dp(28), dp(28), dp(28), dp(28))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from system Settings — re-render so step 2 reflects reality.
        if (step == 1) render()
    }

    private fun render() = when (step) {
        0 -> renderWelcome()
        1 -> renderEnable()
        else -> renderDone()
    }

    private fun heading(t: String): TextView = TextView(this).apply {
        text = t; textSize = 26f; setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(16))
    }

    private fun body(t: String): TextView = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(Color.parseColor("#C7CDD8"))
        gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24))
        setLineSpacing(dp(4).toFloat(), 1f)
    }

    private fun bigButton(t: String, primary: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = t; textSize = 17f
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(if (primary) Color.parseColor("#F5C518") else Color.parseColor("#2A2F3A"))
                cornerRadius = dp(16).toFloat()
            }
            setTextColor(if (primary) Color.parseColor("#101218") else Color.WHITE)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        }

    private fun dots(): TextView = TextView(this).apply {
        text = when (step) { 0 -> "● ○ ○"; 1 -> "○ ● ○"; else -> "○ ○ ●" }
        textSize = 20f; setTextColor(Color.parseColor("#F5C518"))
        gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24))
    }

    // ---- step 1: welcome + disclosure + consent (Play policy) ----
    private fun renderWelcome() {
        root.removeAllViews()
        root.addView(dots())
        root.addView(heading("🛡️ One switch.\nNo more doomscrolling."))
        root.addView(body(
            "Doomscroll Guard closes YouTube Shorts, Instagram Reels, " +
            "TikTok and Spotlight the moment they open — while keeping " +
            "the rest of those apps working.\n\n" +
            "HOW IT WORKS (please read):\n" +
            "To spot short videos inside other apps, the Guard needs the " +
            "Accessibility permission. With it, the app can see which screen " +
            "is open in YouTube, Instagram, TikTok, Snapchat and Facebook — " +
            "only to detect short-form video players.\n\n" +
            "It cannot see passwords or message contents, and no data ever " +
            "leaves your phone. You can turn it off anytime in Settings."
        ))
        root.addView(bigButton(getString(R.string.disclosure_agree), true) {
            repo.setConsented()
            step = 1; render()
        })
        root.addView(bigButton(getString(R.string.disclosure_decline), false) { finish() })
    }

    // ---- step 2: enable the service ----
    private fun renderEnable() {
        root.removeAllViews()
        root.addView(dots())
        root.addView(heading("🔧 Enable the Guard"))
        val enabled = GuardAccessibilityService.isServiceEnabled(this)
        root.addView(body(
            if (enabled)
                "The Guard is enabled in your system settings. You're all set — tap below to finish."
            else
                "Android needs YOU to flip one system switch — apps can't do it themselves.\n\n" +
                "Tap the button, find “Doomscroll Guard” in the list, and turn it on. " +
                "That's what lets the Guard close Shorts and Reels inside other apps.\n\n" +
                "Nothing else is needed. Come back here when it's on."
        ))
        if (!enabled) {
            root.addView(bigButton("Open Accessibility Settings", true) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }
        root.addView(bigButton(
            if (enabled) "Continue →" else "I've turned it on →",
            enabled
        ) {
            if (GuardAccessibilityService.isServiceEnabled(this)) { step = 2; render() }
            else {
                // Gentle nudge, not a dead end.
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
    }

    // ---- step 3: done ----
    private fun renderDone() {
        root.removeAllViews()
        root.addView(dots())
        // Turn the master switch on as the finale — the wizard ends guarded.
        repo.setMasterEnabled(true)
        GuardState.broadcast(this)
        root.addView(heading("✅ You're guarded"))
        root.addView(body(
            "The big switch is ON. Open YouTube and try the Shorts tab — " +
            "the Guard will bounce you straight back out.\n\n" +
            "PRO TIP: long-press your home screen → Widgets → Doomscroll Guard " +
            "to get a one-tap switch on your home screen."
        ))
        root.addView(bigButton("Start using Doomscroll Guard", true) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        })
    }
}
