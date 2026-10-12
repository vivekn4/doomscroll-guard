package com.vn.doomscrollguard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Loads the detection bundle (assets/rules.json — copy of shared/rules/rules.json),
 * exposes toggle state + disclosure consent. A remote-update path can drop a newer
 * signed bundle into internal storage; [load] prefers it only when its
 * bundle_version is newer AND [signatureValid] passes (TODO: wire ed25519 check
 * at release time — see PLAN.md §4.8).
 */
class RuleRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("doomscroll_guard", Context.MODE_PRIVATE)

    companion object {
        const val KEY_MASTER = "master_enabled"
        const val KEY_CONSENT = "disclosure_consent"
        const val KEY_BUNDLE_VERSION = "bundle_version"
        private const val BUNDLED_ASSET = "rules.json"
        private const val UPDATE_FILE = "rules_update.json"
        private const val PREFIX_TARGET = "target_enabled_"
    }

    fun hasConsented(): Boolean = prefs.getBoolean(KEY_CONSENT, false)
    fun setConsented() { prefs.edit().putBoolean(KEY_CONSENT, true).apply() }

    fun isMasterEnabled(): Boolean = prefs.getBoolean(KEY_MASTER, false)
    fun setMasterEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_MASTER, v).apply() }

    /** Per-service toggle; defaults to ON for every known target on first run. */
    fun isTargetEnabled(id: String): Boolean =
        prefs.getBoolean(PREFIX_TARGET + id, true)

    fun setTargetEnabled(id: String, v: Boolean) {
        prefs.edit().putBoolean(PREFIX_TARGET + id, v).apply()
    }

    fun load(): RuleBundle {
        val bundled = JSONObject(
            context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
        )
        val updateFile = context.getFileStreamPath(UPDATE_FILE)
        val chosen = if (updateFile.exists()) {
            try {
                val upd = JSONObject(updateFile.readText())
                val bv = bundled.getString("bundle_version")
                if (isNewer(upd.getString("bundle_version"), bv) && signatureValid(upd)) upd
                else bundled
            } catch (_: Exception) { bundled }
        } else bundled
        prefs.edit().putString(KEY_BUNDLE_VERSION, chosen.getString("bundle_version")).apply()
        return parse(chosen)
    }

    fun bundleVersion(): String = prefs.getString(KEY_BUNDLE_VERSION, "?") ?: "?"

    private fun isNewer(a: String, b: String): Boolean {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** TODO(release): verify ed25519 signature against the baked-in release public key. */
    private fun signatureValid(bundle: JSONObject): Boolean {
        val sig = bundle.optJSONObject("signature") ?: return false
        // Dev builds accept unsigned bundles; release builds MUST enforce this.
        if (BuildConfigGuard.RELEASE_ENFORCE_SIGNATURE) {
            // return Ed25519.verify(RELEASE_PUBKEY, canonical(bundle), sig.getString("value"))
            return false // fail closed until wired
        }
        return sig.optString("algorithm") == "ed25519"
    }

    private fun parse(root: JSONObject): RuleBundle {
        val android = root.getJSONObject("android")
        val engineJson = android.getJSONObject("engine")
        val engine = EngineConfig(
            debounceMs = engineJson.getLong("debounce_ms"),
            notificationTimeoutMs = engineJson.getInt("notification_timeout_ms"),
            eventTypes = jsonStrList(engineJson.getJSONArray("event_types")),
        )
        val targets = mutableListOf<TargetRule>()
        val arr = android.getJSONArray("targets")
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            targets += TargetRule(
                id = t.getString("id"),
                label = t.getString("label"),
                packages = jsonStrList(t.getJSONArray("packages")),
                viewIds = jsonStrList(t.optJSONArray("view_ids")),
                textMarkers = jsonStrList(t.optJSONArray("text_markers")),
                intervention = Intervention.valueOf(t.getString("intervention").uppercase()),
                wholeAppBlock = t.optBoolean("whole_app_block", false),
                requiresLayoutCorroboration = t.optBoolean("requires_layout_corroboration", false),
                notes = t.optString("notes", ""),
            )
        }
        return RuleBundle(root.getString("bundle_version"), targets, engine)
    }

    private fun jsonStrList(a: org.json.JSONArray?): List<String> {
        if (a == null) return emptyList()
        return List(a.length()) { a.getString(it) }
    }
}

/** Compile-time flavor switch so the signature TODO can't silently ship open. */
object BuildConfigGuard {
    const val RELEASE_ENFORCE_SIGNATURE = false // flip true + wire Ed25519 before any release
}
