package com.decay.app.core

/**
 * One frame's worth of face information, extracted on the camera thread and fed
 * into [BlinkClassifier]. Timestamps are monotonic (SystemClock.elapsedRealtime).
 */
data class FaceSignal(
    val timestampMs: Long,
    val leftEyeOpen: Float,    // 0f = closed, 1f = open
    val rightEyeOpen: Float,
    val faceWidthFraction: Float, // face box width / image width — proxy for distance
    val yawDegrees: Float,        // head turn left/right; ~0 when looking at the screen
    val faceFound: Boolean
)

/** Output of the classifier for a single frame. */
sealed interface BlinkEvent {
    data object None : BlinkEvent
    data class EyeState(val open: Boolean) : BlinkEvent
    data object LongBlink : BlinkEvent
}
