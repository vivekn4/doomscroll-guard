package com.vn.doomscrollguard

/** Mirrors shared/rules/rules.json. Parsed with org.json (no external deps). */
data class TargetRule(
    val id: String,
    val label: String,
    val packages: List<String>,
    val viewIds: List<String>,
    val textMarkers: List<String>,
    val intervention: Intervention,
    val wholeAppBlock: Boolean,
    val requiresLayoutCorroboration: Boolean,
    val notes: String,
)

enum class Intervention { BACK, HOME, OVERLAY }

data class EngineConfig(
    val debounceMs: Long,
    val notificationTimeoutMs: Int,
    val eventTypes: List<String>,
)

data class RuleBundle(
    val version: String,
    val targets: List<TargetRule>,
    val engine: EngineConfig,
)
