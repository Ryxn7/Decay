package com.decay.app.core

import android.content.Context
import com.decay.app.accessibility.DecayAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide hub wiring the three live components together:
 *  - MainActivity (UI) reads the StateFlows and flips [setEnabled].
 *  - DecayAccessibilityService registers itself here and is the only thing that
 *    can actually inject a scroll.
 *  - DetectionService pushes eye state in and asks for a scroll on a long blink.
 *
 * Everything lives in one process, so a singleton holder is enough — no IPC.
 */
object DecayCore {

    private const val PREFS = "decay_prefs"
    private const val KEY_ENABLED = "enabled"

    @Volatile
    var accessibility: DecayAccessibilityService? = null

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    val foregroundPackage = MutableStateFlow<String?>(null)
    val detectionActive = MutableStateFlow(false)
    val eyeOpen = MutableStateFlow(1f)
    val lastGesture = MutableStateFlow<String?>(null)
    val scrollsToday = MutableStateFlow(0)

    fun loadEnabled(ctx: Context) {
        _enabled.value = prefs(ctx).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(ctx: Context, value: Boolean) {
        _enabled.value = value
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
        // Re-evaluate immediately so toggling on while a feed is already foreground works.
        accessibility?.reevaluate()
    }

    /** Called by DetectionService when a deliberate long blink is recognized. */
    fun onLongBlink() {
        val acc = accessibility ?: return
        acc.performScrollNext()
        lastGesture.value = "Long blink → Next"
        scrollsToday.value = scrollsToday.value + 1
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
