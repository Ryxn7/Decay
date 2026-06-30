package com.decay.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.decay.app.core.DecayCore
import com.decay.app.service.DetectionService
import com.decay.app.whitelist.AppWhitelist

/**
 * The bridge between Decay and the outside world. Two jobs:
 *
 *  1. Watch window-state changes to learn which app is in the foreground, and
 *     start/stop [DetectionService] so the camera only runs inside whitelisted
 *     apps (battery + the honest "not always-on" guarantee).
 *  2. Inject the actual swipe via dispatchGesture() — the one thing only an
 *     AccessibilityService can do on Android.
 */
class DecayAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        DecayCore.accessibility = this
        DecayCore.load(this)
        reevaluate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        // Ignore our own UI and the system UI shade so they don't toggle detection.
        if (pkg == packageName || pkg == "com.android.systemui") return

        DecayCore.foregroundPackage.value = pkg
        reevaluate()
    }

    /** Start or stop detection based on enabled-state + current foreground app. */
    fun reevaluate() {
        val pkg = DecayCore.foregroundPackage.value
        val shouldRun = DecayCore.enabled.value &&
            pkg != null &&
            AppWhitelist.isWhitelisted(this, pkg)

        if (shouldRun) startDetection() else stopDetection()
    }

    private fun startDetection() {
        if (DecayCore.detectionActive.value) return
        val intent = Intent(this, DetectionService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopDetection() {
        if (!DecayCore.detectionActive.value) return
        stopService(Intent(this, DetectionService::class.java))
    }

    /** Inject an upward swipe — "next" in a vertical short-form feed. */
    fun performScrollNext() {
        mainHandler.post {
            val metrics = resources.displayMetrics
            val x = metrics.widthPixels / 2f
            val startY = metrics.heightPixels * 0.72f
            val endY = metrics.heightPixels * 0.28f

            val path = Path().apply {
                moveTo(x, startY)
                lineTo(x, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val callback = object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) {
                    Log.d(TAG, "swipe completed")
                }

                override fun onCancelled(d: GestureDescription?) {
                    Log.w(TAG, "swipe cancelled")
                }
            }
            val dispatched = dispatchGesture(gesture, callback, mainHandler)
            Log.d(TAG, "dispatchGesture -> $dispatched (${startY.toInt()} to ${endY.toInt()})")
        }
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: Intent?): Boolean {
        DecayCore.accessibility = null
        stopDetection()
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "DecayA11yService"
        private const val SWIPE_DURATION_MS = 220L
    }
}
