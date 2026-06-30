package com.decay.app.core

import kotlin.math.abs

/**
 * Turns a stream of [FaceSignal]s into long-blink trigger events.
 *
 * Design notes (why it looks the way it does):
 *  - **Hysteresis.** Eyes enter the "closed" state below [closedThreshold] but
 *    only leave it once they're confidently open (above [openThreshold]). This
 *    stops a value hovering near the threshold from chattering open/closed.
 *  - **Dropout tolerance.** When you hold your eyes shut, ML Kit's tracking
 *    often flickers — a frame or two where the face/eyes aren't confidently
 *    read. We do NOT reset the closure on those ambiguous frames; we only end a
 *    closure on a confidently-open frame, or after [maxGapMs] with no
 *    confirmation at all. Without this, a long blink almost never accumulates
 *    the full hold time.
 *  - Both eyes must be closed together (a wink keeps one eye open), which also
 *    leaves winks free as future gestures.
 *
 * Not thread-safe; feed it from a single thread (the camera analysis executor).
 */
class BlinkClassifier(
    var closedThreshold: Float = 0.45f,
    var openThreshold: Float = 0.65f,
    var longBlinkMs: Long = 700L,
    var cooldownMs: Long = 1200L,
    var minFaceWidthFraction: Float = 0.10f,
    var maxYawDegrees: Float = 35f,
    var maxGapMs: Long = 350L,
) {
    /** -1 means "eyes are open / no active closure". */
    private var closedSinceMs = -1L
    private var lastClosedConfirmMs = 0L
    private var triggeredThisClosure = false
    private var lastTriggerMs = -100_000L

    fun reset() {
        closedSinceMs = -1L
        triggeredThisClosure = false
    }

    fun update(s: FaceSignal): BlinkEvent {
        val validFace = s.faceFound &&
            s.faceWidthFraction >= minFaceWidthFraction &&
            abs(s.yawDegrees) <= maxYawDegrees

        val confidentlyClosed = validFace &&
            s.leftEyeOpen < closedThreshold && s.rightEyeOpen < closedThreshold
        val confidentlyOpen = validFace &&
            s.leftEyeOpen > openThreshold && s.rightEyeOpen > openThreshold

        // 1) A confident open frame always ends any closure.
        if (confidentlyOpen) {
            val wasClosed = closedSinceMs >= 0
            closedSinceMs = -1L
            triggeredThisClosure = false
            return if (wasClosed) BlinkEvent.EyeState(open = true) else BlinkEvent.None
        }

        // 2) A confident closed frame starts or continues a closure.
        if (confidentlyClosed) {
            lastClosedConfirmMs = s.timestampMs
            if (closedSinceMs < 0) {
                closedSinceMs = s.timestampMs
                triggeredThisClosure = false
                return BlinkEvent.EyeState(open = false)
            }
            val held = s.timestampMs - closedSinceMs
            val sinceLastTrigger = s.timestampMs - lastTriggerMs
            if (!triggeredThisClosure && held >= longBlinkMs && sinceLastTrigger >= cooldownMs) {
                triggeredThisClosure = true
                lastTriggerMs = s.timestampMs
                return BlinkEvent.LongBlink
            }
            return BlinkEvent.None
        }

        // 3) Ambiguous frame (no face, partial eyes, looking away). During an
        //    active closure we tolerate a brief gap rather than resetting; if the
        //    gap runs too long we treat it as the eyes having reopened.
        if (closedSinceMs >= 0) {
            if (s.timestampMs - lastClosedConfirmMs > maxGapMs) {
                closedSinceMs = -1L
                triggeredThisClosure = false
                return BlinkEvent.EyeState(open = true)
            }
            // Still mid-blink: allow the hold to keep counting through the gap.
            val held = s.timestampMs - closedSinceMs
            val sinceLastTrigger = s.timestampMs - lastTriggerMs
            if (!triggeredThisClosure && held >= longBlinkMs && sinceLastTrigger >= cooldownMs) {
                triggeredThisClosure = true
                lastTriggerMs = s.timestampMs
                return BlinkEvent.LongBlink
            }
        }
        return BlinkEvent.None
    }
}
