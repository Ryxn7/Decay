package com.decay.app.core

import android.content.Context
import android.util.Log
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

    private const val TAG = "DecayCore"
    private const val PREFS = "decay_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LONG_BLINK = "long_blink_ms"

    const val MIN_LONG_BLINK_MS = 400L
    const val MAX_LONG_BLINK_MS = 1200L
    const val DEFAULT_LONG_BLINK_MS = 700L

    @Volatile
    var accessibility: DecayAccessibilityService? = null

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    /** Hold duration that triggers a scroll. Lower = looser/easier. */
    val longBlinkMs = MutableStateFlow(DEFAULT_LONG_BLINK_MS)

    val foregroundPackage = MutableStateFlow<String?>(null)
    val detectionActive = MutableStateFlow(false)
    val eyeOpen = MutableStateFlow(1f)
    val lastGesture = MutableStateFlow<String?>(null)
    val scrollsToday = MutableStateFlow(0)

    fun load(ctx: Context) {
        _enabled.value = prefs(ctx).getBoolean(KEY_ENABLED, false)
        longBlinkMs.value = prefs(ctx).getLong(KEY_LONG_BLINK, DEFAULT_LONG_BLINK_MS)
    }

    fun setEnabled(ctx: Context, value: Boolean) {
        _enabled.value = value
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
        // Re-evaluate immediately so toggling on while a feed is already foreground works.
        accessibility?.reevaluate()
    }

    fun setLongBlinkMs(ctx: Context, value: Long) {
        val clamped = value.coerceIn(MIN_LONG_BLINK_MS, MAX_LONG_BLINK_MS)
        longBlinkMs.value = clamped
        prefs(ctx).edit().putLong(KEY_LONG_BLINK, clamped).apply()
    }

    /** Called by DetectionService when a deliberate long blink is recognized. */
    fun onLongBlink() {
        val acc = accessibility
        if (acc == null) {
            Log.w(TAG, "Long blink detected but accessibility service not connected")
            return
        }
        Log.d(TAG, "LONG BLINK -> dispatching scroll")
        acc.performScrollNext()
        lastGesture.value = "Long blink → Next"
        scrollsToday.value = scrollsToday.value + 1
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
